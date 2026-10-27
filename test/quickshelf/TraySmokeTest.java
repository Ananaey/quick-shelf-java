package quickshelf;

import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

/**
 * Puts the real icon in the system tray for a moment. Uses a fake registry, so the real Windows
 * startup list is never touched. Skips itself where there is no display or no tray.
 */
public class TraySmokeTest {
    private static <T> T onEdt(Supplier<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(action.get()));
        return result.get();
    }

    private static MenuItem find(AppTray tray, String label) {
        for (int i = 0; i < tray.menu().getItemCount(); i++) {
            if (tray.menu().getItem(i).getLabel().equals(label)) {
                return tray.menu().getItem(i);
            }
        }
        return null;
    }

    private static void waitForAnimation(SlidePanel panel) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (onEdt(panel::isAnimating)) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("animation did not finish");
            }
            Thread.sleep(20);
        }
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            System.out.println("No display or tray: tray smoke test skipped");
            return;
        }

        Path store = Files.createTempDirectory("tray-test").resolve("shelf.txt");
        AutostartTest.FakeRegistry registry = new AutostartTest.FakeRegistry();
        Autostart autostart = new Autostart(registry, () -> "fake-command");
        AtomicBoolean quit = new AtomicBoolean(false);

        SlidePanel panel = onEdt(() -> new SlidePanel(store));
        AppTray tray = onEdt(() -> AppTray.install(panel, autostart, () -> quit.set(true)));
        try {
            check("the tray icon was installed", tray != null);
            if (tray == null) {
                return;
            }
            boolean inTray = false;
            for (TrayIcon icon : SystemTray.getSystemTray().getTrayIcons()) {
                inTray |= icon == tray.icon();
            }
            check("the icon is in the system tray", inTray);
            equal("tooltip", "Quick Shelf", tray.icon().getToolTip());

            MenuItem toggle = find(tray, AppTray.TOGGLE_LABEL);
            MenuItem quitItem = find(tray, AppTray.QUIT_LABEL);
            check("menu has Show / hide", toggle != null);
            check("menu has Quit", quitItem != null);
            check("menu has Start with Windows", find(tray, AppTray.AUTOSTART_LABEL) != null || !Autostart.isSupported());

            // Show / hide
            ActionEvent click = new ActionEvent(toggle, ActionEvent.ACTION_PERFORMED, "toggle");
            onEdt(() -> {
                toggle.getActionListeners()[0].actionPerformed(click);
                return null;
            });
            check("menu item opens the shelf", onEdt(panel::isOpen));
            waitForAnimation(panel);
            onEdt(() -> {
                toggle.getActionListeners()[0].actionPerformed(click);
                return null;
            });
            check("menu item closes the shelf", !onEdt(panel::isOpen));
            waitForAnimation(panel);

            // Start with Windows (fake registry)
            if (Autostart.isSupported()) {
                check("autostart starts unticked", !tray.autostartItem().getState());
                tick(tray, true);
                check("ticking writes the startup entry", autostart.isEnabled());
                equal("with the launch command", "fake-command", registry.values.get(Autostart.VALUE_NAME));
                tick(tray, false);
                check("unticking removes it", !autostart.isEnabled());

                registry.failWrites = true;
                tick(tray, true);
                check("a failed write leaves it off", !autostart.isEnabled());
                check("and the tick goes back to unticked", !tray.autostartItem().getState());
                registry.failWrites = false;
            }

            // Quit runs the quit action
            onEdt(() -> {
                quitItem.getActionListeners()[0].actionPerformed(
                        new ActionEvent(quitItem, ActionEvent.ACTION_PERFORMED, "quit"));
                return null;
            });
            check("Quit runs the quit action", quit.get());
        } finally {
            if (tray != null) {
                onEdt(() -> {
                    tray.remove();
                    return null;
                });
            }
            onEdt(() -> {
                panel.dispose();
                return null;
            });
        }
        boolean stillThere = false;
        for (TrayIcon icon : SystemTray.getSystemTray().getTrayIcons()) {
            stillThere |= icon == tray.icon();
        }
        check("the icon is gone after remove", !stillThere);
        TestRunner.finish();
    }

    /** Sets the menu tick and fires the same event a real click would. */
    private static void tick(AppTray tray, boolean state) throws Exception {
        onEdt(() -> {
            tray.autostartItem().setState(state);
            ItemEvent event = new ItemEvent(tray.autostartItem(), ItemEvent.ITEM_STATE_CHANGED,
                    tray.autostartItem().getLabel(), state ? ItemEvent.SELECTED : ItemEvent.DESELECTED);
            for (ItemListener listener : tray.autostartItem().getItemListeners()) {
                listener.itemStateChanged(event);
            }
            return null;
        });
    }
}
