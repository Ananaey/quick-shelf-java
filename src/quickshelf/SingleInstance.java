package quickshelf;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Makes sure only one Quick Shelf runs at a time. The first copy listens on a port that only this
 * computer can reach. A second copy finds the port taken, asks the first one to show itself, and
 * can then exit. Two copies would both write the same shelf file and the later one would win.
 */
public final class SingleInstance implements Closeable {
    /** A port that is unlikely to be used by anything else. */
    public static final int DEFAULT_PORT = 47831;

    static final String REQUEST = "quickshelf-show";
    static final String REPLY = "quickshelf-ok";
    private static final int TIMEOUT_MS = 1000;

    public enum Status {
        /** This is the first copy and is now listening for later ones. */
        PRIMARY,
        /** Another Quick Shelf is running and has been asked to show itself. */
        ALREADY_RUNNING,
        /** The port is used by something else, so this copy just runs without the protection. */
        UNAVAILABLE
    }

    private final int port;
    private final Runnable onShow;
    private volatile boolean closed;
    private ServerSocket server;

    /**
     * @param port   the port to use
     * @param onShow called (on a background thread) when a later copy asks this one to show itself
     */
    public SingleInstance(int port, Runnable onShow) {
        this.port = port;
        this.onShow = onShow;
    }

    /** Becomes the primary copy, or finds out that one is already running. */
    public Status start() {
        try {
            server = new ServerSocket(port, 5, InetAddress.getLoopbackAddress());
        } catch (BindException e) {
            return askRunningCopyToShow() ? Status.ALREADY_RUNNING : Status.UNAVAILABLE;
        } catch (IOException e) {
            return Status.UNAVAILABLE;
        }
        Thread listener = new Thread(this::listen, "quickshelf-single-instance");
        listener.setDaemon(true); // never keeps the program alive on its own
        listener.start();
        return Status.PRIMARY;
    }

    @Override
    public void close() {
        closed = true;
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                // already closing
            }
        }
    }

    // ---- the first copy: wait for later copies -------------------------------------

    private void listen() {
        while (!closed) {
            try {
                Socket client = server.accept();
                // Each caller gets its own short-lived thread, so one that connects and says
                // nothing cannot keep a real second copy waiting.
                Thread handler = new Thread(() -> handle(client), "quickshelf-single-instance-caller");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                if (closed) {
                    return;
                }
            }
        }
    }

    private void handle(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(TIMEOUT_MS);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            if (REQUEST.equals(in.readLine())) {
                Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                out.write(REPLY + "\n");
                out.flush();
                onShow.run();
            }
        } catch (SocketTimeoutException e) {
            // the caller said nothing: just hang up
        } catch (IOException e) {
            // the caller went away: nothing to do
        } catch (RuntimeException e) {
            // a problem in onShow must not stop later callers
            System.err.println("Quick Shelf: " + e.getMessage());
        }
    }

    // ---- a later copy: ask the first one ---------------------------------------------

    private boolean askRunningCopyToShow() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            out.write(REQUEST + "\n");
            out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            return REPLY.equals(in.readLine());
        } catch (IOException e) {
            return false; // something else owns the port, or nothing answered
        }
    }
}
