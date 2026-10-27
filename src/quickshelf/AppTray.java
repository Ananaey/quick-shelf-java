package quickshelf;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

/**
 * The system tray icon. Left-click shows or hides the shelf; right-click opens a menu with
 * "Start with Windows" and "Quit". The app keeps running in the tray when the shelf is closed.
 */
public final class AppTray {
    static final String TOGGLE_LABEL = "Show / hide shelf";
    static final String AUTOSTART_LABEL = "Start with Windows";
    static final String QUIT_LABEL = "Quit";

    private final SlidePanel panel;
    private final Autostart autostart;
    private final TrayIcon icon;
    private final PopupMenu menu = new PopupMenu();
    private final CheckboxMenuItem autostartItem = new CheckboxMenuItem(AUTOSTART_LABEL);

    private AppTray(SlidePanel panel, Autostart autostart, Runnable onQuit) {
        this.panel = panel;
        this.autostart = autostart;

        MenuItem toggleItem = new MenuItem(TOGGLE_LABEL);
        toggleItem.addActionListener(event -> panel.toggle());
        menu.add(toggleItem);

        if (Autostart.isSupported()) {
            autostartItem.setState(autostart.isEnabled());
            autostartItem.addItemListener(event -> onAutostartChanged(autostartItem.getState()));
            menu.add(autostartItem);
        }

        menu.addSeparator();
        MenuItem quitItem = new MenuItem(QUIT_LABEL);
        quitItem.addActionListener(event -> onQuit.run());
        menu.add(quitItem);

        int size = SystemTray.getSystemTray().getTrayIconSize().width;
        this.icon = new TrayIcon(TrayImage.create(size), "Quick Shelf", menu);
        icon.setImageAutoSize(true);
        icon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    panel.toggle();
                }
            }
        });
    }

    /**
     * Puts the icon in the tray. Returns null (and the app still works through the edge handle)
     * when the system has no tray or the icon cannot be added.
     */
    public static AppTray install(SlidePanel panel, Autostart autostart, Runnable onQuit) {
        if (!SystemTray.isSupported()) {
            return null;
        }
        AppTray tray = new AppTray(panel, autostart, onQuit);
        try {
            SystemTray.getSystemTray().add(tray.icon);
        } catch (AWTException e) {
            return null;
        }
        return tray;
    }

    public void remove() {
        SystemTray.getSystemTray().remove(icon);
    }

    private void onAutostartChanged(boolean enabled) {
        try {
            autostart.setEnabled(enabled);
        } catch (IOException e) {
            // Could not change the setting: show the real state again instead of a wrong tick.
            autostartItem.setState(autostart.isEnabled());
            System.err.println("Quick Shelf: " + e.getMessage());
        }
    }

    // ---- visible to tests --------------------------------------------------------

    TrayIcon icon() {
        return icon;
    }

    PopupMenu menu() {
        return menu;
    }

    CheckboxMenuItem autostartItem() {
        return autostartItem;
    }

    SlidePanel panel() {
        return panel;
    }
}
