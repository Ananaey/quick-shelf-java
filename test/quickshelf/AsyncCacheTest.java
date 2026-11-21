package quickshelf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class AsyncCacheTest {
    /** Waits (up to five seconds) for something that happens on another thread. */
    private static boolean eventually(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    public static void main(String[] args) throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        AtomicInteger changes = new AtomicInteger();
        AtomicInteger loads = new AtomicInteger();
        Map<String, String> truth = new ConcurrentHashMap<>();
        truth.put("a", "apple");
        truth.put("b", "banana");
        CountDownLatch gate = new CountDownLatch(1); // a closed gate holds every lookup back
        AtomicInteger useGate = new AtomicInteger(0);

        AsyncCache<String, String> cache = new AsyncCache<>(key -> {
            loads.incrementAndGet();
            if (useGate.get() == 1) {
                gate.await();
            }
            if (key.equals("boom")) {
                throw new IllegalStateException("simulated failure");
            }
            return truth.get(key); // null for an unknown key
        }, "?", now::get, 5_000, changes::incrementAndGet);

        // ---- a value that is not known yet is answered at once, then arrives ----
        equal("an unknown key gives the placeholder", "?", cache.get("a"));
        check("the real value arrives in the background", eventually(() -> cache.get("a").equals("apple")));
        equal("a change was announced", 1, changes.get());
        equal("one lookup was made", 1, loads.get());

        // ---- a trusted value is not looked up again ----
        for (int i = 0; i < 200; i++) {
            cache.get("a");
        }
        Thread.sleep(100);
        equal("200 more reads cause no more lookups", 1, loads.get());
        equal("and no more announcements", 1, changes.get());

        // ---- a stale value is answered at once and refreshed in the background ----
        truth.put("a", "apricot");
        now.addAndGet(6_000);
        equal("a stale value is still answered immediately with the old one", "apple", cache.get("a"));
        check("and refreshed behind the scenes", eventually(() -> cache.get("a").equals("apricot")));
        equal("the change was announced", 2, changes.get());
        equal("one extra lookup", 2, loads.get());

        // ---- a refresh that finds the same value announces nothing ----
        now.addAndGet(6_000);
        cache.get("a");
        check("the refresh ran", eventually(() -> loads.get() == 3));
        Thread.sleep(100);
        equal("an unchanged value is not announced again", 2, changes.get());

        // ---- an unknown key whose lookup finds nothing stays the placeholder ----
        equal("an empty lookup answers the placeholder", "?", cache.get("nothing-here"));
        check("the lookup was made", eventually(() -> loads.get() == 4));
        Thread.sleep(100);
        equal("an empty result is not announced", 2, changes.get());
        equal("and keeps answering the placeholder", "?", cache.get("nothing-here"));

        // ---- a failing lookup does not crash, and is not retried in a loop ----
        equal("a failing key gives the placeholder", "?", cache.get("boom"));
        check("the failing lookup ran", eventually(() -> loads.get() == 5));
        for (int i = 0; i < 100; i++) {
            cache.get("boom");
        }
        Thread.sleep(150);
        equal("a failure is not retried until the value goes stale", 5, loads.get());
        now.addAndGet(6_000);
        cache.get("boom");
        check("after that it is tried again", eventually(() -> loads.get() == 6));
        equal("a failure is never announced", 2, changes.get());

        // ---- the caller is never held up, however slow the lookup is ----
        useGate.set(1);
        long started = System.nanoTime();
        equal("a slow lookup still answers at once", "?", cache.get("b"));
        long millis = (System.nanoTime() - started) / 1_000_000;
        // A blocked call would wait until the gate opens, which never happens during this check,
        // so even a generous limit tells "answers at once" from "waits for the lookup".
        check("without waiting for the lookup (took " + millis + " ms)", millis < 3000);
        for (int i = 0; i < 50; i++) {
            cache.get("b");
        }
        check("the one lookup has started", eventually(() -> loads.get() == 7));
        Thread.sleep(100);
        equal("while it is pending, repeated reads start no second lookup", 7, loads.get());
        check("nothing has arrived yet", cache.get("b").equals("?"));
        gate.countDown();
        check("opening the gate lets the value arrive", eventually(() -> cache.get("b").equals("banana")));
        equal("and it is announced", 3, changes.get());
        useGate.set(0);

        // ---- many slow lookups at once do not hold up reads of other keys ----
        CountDownLatch gate2 = new CountDownLatch(1);
        AtomicInteger started2 = new AtomicInteger();
        AsyncCache<Integer, String> slow = new AsyncCache<>(key -> {
            started2.incrementAndGet();
            gate2.await();
            return "v" + key;
        }, "?", now::get, 0, () -> { });
        long t0 = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            slow.get(i);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        check("100 reads with every lookup stuck still return at once (" + ms + " ms)", ms < 3000);
        check("only a limited number of lookups run at the same time", eventually(() -> started2.get() == 8));
        Thread.sleep(150);
        equal("the others wait their turn instead of starting a thread each", 8, started2.get());
        gate2.countDown();
        check("when the gate opens they all complete", eventually(() -> slow.get(99).equals("v99")));
        slow.close();

        // ---- a cache with one worker runs one lookup at a time ----
        CountDownLatch gate3 = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger mostAtOnce = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        AsyncCache<Integer, String> single = new AsyncCache<>(key -> {
            int now2 = running.incrementAndGet();
            mostAtOnce.accumulateAndGet(now2, Math::max);
            gate3.await();
            running.decrementAndGet();
            finished.incrementAndGet();
            return "v" + key;
        }, "?", now::get, 0, () -> { }, 1);
        for (int i = 0; i < 20; i++) {
            single.get(i);
        }
        check("the first lookup has started", eventually(() -> running.get() == 1));
        Thread.sleep(200);
        equal("with one worker only one lookup is running", 1, running.get());
        gate3.countDown();
        check("they all finish, one after the other", eventually(() -> finished.get() == 20));
        equal("and never more than one ran at a time", 1, mostAtOnce.get());
        check("the answers are right", single.get(19).equals("v19"));
        single.close();

        // ---- no refresh when the time to live is zero ----
        AtomicInteger forever = new AtomicInteger();
        AsyncCache<String, String> never = new AsyncCache<>(k -> {
            forever.incrementAndGet();
            return "x";
        }, "?", now::get, 0, () -> { });
        never.get("k");
        check("loaded once", eventually(() -> never.get("k").equals("x")));
        now.addAndGet(10_000_000L);
        for (int i = 0; i < 20; i++) {
            never.get("k");
        }
        Thread.sleep(100);
        equal("a zero time to live never refreshes", 1, forever.get());
        never.close();

        // ---- the cache cannot grow without limit ----
        AsyncCache<Integer, Integer> big = new AsyncCache<>(k -> k, -1, now::get, 0, () -> { });
        for (int i = 0; i < 5000; i++) {
            big.get(i);
            if (i % 100 == 0) {
                now.incrementAndGet(); // later entries are newer
            }
        }
        check("everything is looked up", eventually(() -> big.size() > 0));
        Thread.sleep(500);
        check("the size stays bounded (" + big.size() + ")", big.size() <= 2000);
        big.close();

        // ---- after close nothing breaks ----
        cache.close();
        equal("reading after close still answers", "apricot", cache.get("a"));
        equal("and a new key after close gives the placeholder", "?", cache.get("fresh"));
        cache.close(); // closing twice is fine

        TestRunner.finish();
    }
}
