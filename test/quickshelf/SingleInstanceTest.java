package quickshelf;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class SingleInstanceTest {
    /** A port nobody is using right now. */
    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = freePort();
        AtomicInteger shown = new AtomicInteger();
        CountDownLatch firstShow = new CountDownLatch(1);

        SingleInstance first = new SingleInstance(port, () -> {
            shown.incrementAndGet();
            firstShow.countDown();
        });
        equal("the first copy becomes the primary", SingleInstance.Status.PRIMARY, first.start());
        equal("starting does not show anything", 0, shown.get());

        SingleInstance second = new SingleInstance(port, () -> shown.addAndGet(100));
        equal("a second copy finds the first one", SingleInstance.Status.ALREADY_RUNNING, second.start());
        check("and the first copy is asked to show itself", firstShow.await(5, TimeUnit.SECONDS));
        equal("exactly once, and the second copy's own callback is never used", 1, shown.get());

        // a third and fourth copy behave the same
        equal("a third copy also finds it", SingleInstance.Status.ALREADY_RUNNING, new SingleInstance(port, () -> { }).start());
        Thread.sleep(300);
        equal("the first copy was asked again", 2, shown.get());

        // a stranger on the port: connects, sends nonsense
        try (Socket stranger = new Socket(InetAddress.getLoopbackAddress(), port)) {
            OutputStream out = stranger.getOutputStream();
            out.write("hello there\n".getBytes());
            out.flush();
            Thread.sleep(300);
        }
        equal("an unknown request does not show the shelf", 2, shown.get());

        // a connection that says nothing must not block a real request
        Socket silent = new Socket(InetAddress.getLoopbackAddress(), port);
        long started = System.currentTimeMillis();
        equal("a real request still works while a silent one is waiting",
                SingleInstance.Status.ALREADY_RUNNING, new SingleInstance(port, () -> { }).start());
        silent.close();
        Thread.sleep(300);
        equal("it was handled", 3, shown.get());
        check("without waiting long", System.currentTimeMillis() - started < 5000);

        // closing frees the port for a new primary
        first.close();
        first.close(); // closing twice is fine
        SingleInstance replacement = new SingleInstance(port, () -> { });
        equal("after closing, a new copy can become primary", SingleInstance.Status.PRIMARY, replacement.start());
        replacement.close();

        // something else that owns the port and does not speak our language
        int busyPort = freePort();
        ServerSocket other = new ServerSocket(busyPort, 5, InetAddress.getLoopbackAddress());
        Thread otherThread = new Thread(() -> {
            try {
                while (true) {
                    other.accept().close(); // takes the call, says nothing, hangs up
                }
            } catch (IOException e) {
                // closed at the end of the test
            }
        });
        otherThread.setDaemon(true);
        otherThread.start();
        equal("a port owned by another program means no protection, not a crash",
                SingleInstance.Status.UNAVAILABLE, new SingleInstance(busyPort, () -> { }).start());
        other.close();

        // a callback that fails must not stop the listener
        int shakyPort = freePort();
        AtomicInteger calls = new AtomicInteger();
        SingleInstance shaky = new SingleInstance(shakyPort, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("simulated problem");
        });
        equal("shaky starts", SingleInstance.Status.PRIMARY, shaky.start());
        new SingleInstance(shakyPort, () -> { }).start();
        Thread.sleep(300);
        new SingleInstance(shakyPort, () -> { }).start();
        Thread.sleep(300);
        equal("the listener survives a failing callback", 2, calls.get());
        shaky.close();

        equal("the default port is a high, unprivileged one", true, SingleInstance.DEFAULT_PORT > 1024);

        TestRunner.finish();
    }
}
