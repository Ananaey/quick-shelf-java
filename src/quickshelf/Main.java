package quickshelf;

import javax.swing.SwingUtilities;

/** Starts Quick Shelf: java -cp out quickshelf.Main */
public class Main {
    /** The shelf of this copy, once it exists. A later copy asking to show it can arrive before that. */
    private static volatile SlidePanel running;

    public static void main(String[] args) {
        // -Dquickshelf.port=... lets a test run its own copies without meeting a real one.
        int port = Integer.getInteger("quickshelf.port", SingleInstance.DEFAULT_PORT);
        SingleInstance single = new SingleInstance(port, Main::showRunningShelf);
        if (single.start() == SingleInstance.Status.ALREADY_RUNNING) {
            System.exit(0); // the copy that is already running has been asked to show itself
        }
        SwingUtilities.invokeLater(() -> start(single));
    }

    private static void start(SingleInstance single) {
        SlidePanel panel = new SlidePanel();
        running = panel;
        panel.show();

        AppTray[] tray = new AppTray[1];
        tray[0] = AppTray.install(panel, Autostart.forThisApp(), () -> {
            if (tray[0] != null) {
                tray[0].remove();
            }
            single.close();
            panel.dispose();
            System.exit(0);
        });
    }

    /** Called when the user starts a second copy: slide the shelf open in this one. */
    private static void showRunningShelf() {
        SlidePanel panel = running;
        if (panel != null) {
            SwingUtilities.invokeLater(panel::open);
        }
    }
}
