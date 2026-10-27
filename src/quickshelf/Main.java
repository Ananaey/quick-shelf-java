package quickshelf;

import javax.swing.SwingUtilities;

/** Starts Quick Shelf: java -cp out quickshelf.Main */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::start);
    }

    private static void start() {
        SlidePanel panel = new SlidePanel();
        panel.show();

        AppTray[] tray = new AppTray[1];
        tray[0] = AppTray.install(panel, Autostart.forThisApp(), () -> {
            if (tray[0] != null) {
                tray[0].remove();
            }
            panel.dispose();
            System.exit(0);
        });
    }
}
