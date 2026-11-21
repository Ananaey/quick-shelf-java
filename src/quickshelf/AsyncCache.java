package quickshelf;

import java.awt.AWTError;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * A cache whose {@link #get} never waits. A value that is not known yet is answered with a
 * placeholder straight away while a background thread works it out; when it arrives (and is
 * different from the placeholder or the old value) a callback runs so the screen can repaint.
 *
 * <p>This keeps the shelf responsive when something it asks about is slow, for example a file on
 * a network share that is switched off. Plain Java, no Swing.
 */
final class AsyncCache<K, V> implements AutoCloseable {
    /** Works out the real value. May be slow, may throw. */
    interface Loader<K, V> {
        V load(K key) throws Exception;
    }

    private static final int WORKERS = 8;
    private static final int MAX_ENTRIES = 2000;

    private static final class Entry<V> {
        final V value;
        final long at;

        Entry(V value, long at) {
            this.value = value;
            this.at = at;
        }
    }

    private final Loader<K, V> loader;
    private final V unknown;
    private final LongSupplier clock;
    private final long ttlMillis;
    private final Runnable onChange;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Set<K> pending = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers;

    /**
     * @param unknown   what {@link #get} answers until the real value is known (not null)
     * @param ttlMillis how long a value is trusted before it is looked up again in the background;
     *                  zero or less means never
     * @param onChange  called on a background thread when a value arrives or changes
     */
    AsyncCache(Loader<K, V> loader, V unknown, LongSupplier clock, long ttlMillis, Runnable onChange) {
        this(loader, unknown, clock, ttlMillis, onChange, WORKERS);
    }

    /** Like the other constructor, with a chosen number of background threads. */
    AsyncCache(Loader<K, V> loader, V unknown, LongSupplier clock, long ttlMillis, Runnable onChange, int workerCount) {
        this.loader = loader;
        this.unknown = Objects.requireNonNull(unknown);
        this.clock = clock;
        this.ttlMillis = ttlMillis;
        this.onChange = onChange;
        this.workers = new ThreadPoolExecutor(workerCount, workerCount, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
            Thread thread = new Thread(task, "quickshelf-async-cache");
            thread.setDaemon(true); // never keeps the program alive
            return thread;
        });
        this.workers.allowCoreThreadTimeOut(true); // idle threads go away by themselves
    }

    /** The value if known, otherwise the placeholder. Never waits for the loader. */
    V get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            schedule(key);
            return unknown;
        }
        if (ttlMillis > 0 && clock.getAsLong() - entry.at >= ttlMillis) {
            schedule(key); // stale: answer with the old value now, refresh in the background
        }
        return entry.value;
    }

    int size() {
        return entries.size();
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }

    private void schedule(K key) {
        if (!pending.add(key)) {
            return; // already being worked out: one lookup per key at a time
        }
        try {
            workers.execute(() -> load(key));
        } catch (RejectedExecutionException e) {
            pending.remove(key); // closed
        }
    }

    private void load(K key) {
        try {
            V value = loader.load(key);
            store(key, value == null ? unknown : value);
        } catch (Exception | LinkageError | AWTError e) {
            // The lookup failed. Keep what we had (or the placeholder), and note the time so a
            // failing lookup is not retried in a tight loop.
            Entry<V> old = entries.get(key);
            entries.put(key, new Entry<>(old == null ? unknown : old.value, clock.getAsLong()));
        } finally {
            pending.remove(key);
        }
    }

    private void store(K key, V value) {
        Entry<V> old = entries.put(key, new Entry<>(value, clock.getAsLong()));
        boolean changed = old == null ? !Objects.equals(value, unknown) : !Objects.equals(old.value, value);
        if (entries.size() > MAX_ENTRIES) {
            prune();
        }
        if (changed) {
            onChange.run();
        }
    }

    /** Drops the oldest half so the cache cannot grow without limit. */
    private void prune() {
        entries.entrySet().stream()
                .sorted((a, b) -> Long.compare(a.getValue().at, b.getValue().at))
                .limit(entries.size() / 2)
                .map(Map.Entry::getKey)
                .forEach(entries::remove);
    }
}
