package quickshelf;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

/**
 * Opens the real windows for about a second, so it needs a display.
 * Skips itself (and passes) on machines without one, such as CI.
 */
public class SlidePanelSmokeTest {
    private static <T> T onEdt(Supplier<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(action.get()));
        return result.get();
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
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("No display: smoke test skipped");
            return;
        }

        Path store = Files.createTempDirectory("smoke-test").resolve("shelf.txt");
        SlidePanel panel = onEdt(() -> new SlidePanel(store));
        try {
            onEdt(() -> {
                panel.show();
                return null;
            });
            check("shelf is inside the panel", onEdt(() -> panel.shelf() != null));
            check("starts closed", !onEdt(panel::isOpen));
            check("panel hidden at the start", !onEdt(panel::isPanelVisible));
            equal("panel parked off-screen", onEdt(panel::hiddenX), onEdt(panel::panelX));

            onEdt(() -> {
                panel.toggle();
                return null;
            });
            check("is open after toggle", onEdt(panel::isOpen));
            check("panel visible while opening", onEdt(panel::isPanelVisible));
            waitForAnimation(panel);
            equal("panel fully slid in", onEdt(panel::shownX), onEdt(panel::panelX));
            check("panel can take keyboard input (search, Enter, Delete)", onEdt(panel::acceptsKeyboardInput));

            // theme button: dark -> light -> dark
            equal("starts dark", Theme.DARK, onEdt(panel::getTheme));
            onEdt(() -> {
                panel.themeButton().doClick();
                return null;
            });
            equal("button switches to light", Theme.LIGHT, onEdt(panel::getTheme));
            onEdt(() -> {
                panel.themeButton().doClick();
                return null;
            });
            equal("button switches back to dark", Theme.DARK, onEdt(panel::getTheme));

            // search box filters the shelf
            Path folder = store.getParent();
            java.io.File one = Files.createFile(folder.resolve("one.txt")).toFile();
            java.io.File two = Files.createFile(folder.resolve("two.txt")).toFile();
            equal("two files added", 2, onEdt(() -> panel.shelf().addFiles(java.util.List.of(one, two))));
            onEdt(() -> {
                panel.search().setText("two");
                return null;
            });
            equal("typing filters the list", 1, onEdt(() -> panel.shelf().visibleItems().size()));
            onEdt(() -> {
                panel.search().setText("");
                return null;
            });
            equal("clearing the box shows everything", 2, onEdt(() -> panel.shelf().visibleItems().size()));

            onEdt(() -> {
                panel.toggle();
                return null;
            });
            waitForAnimation(panel);
            check("closed after second toggle", !onEdt(panel::isOpen));
            equal("panel slid back out", onEdt(panel::hiddenX), onEdt(panel::panelX));
            check("panel hidden again", !onEdt(panel::isPanelVisible));

            // Click twice quickly: the panel must still end up where the last click says.
            onEdt(() -> {
                panel.toggle();
                panel.toggle();
                return null;
            });
            waitForAnimation(panel);
            equal("double toggle ends closed", onEdt(panel::hiddenX), onEdt(panel::panelX));

            // The theme choice is remembered: click the button, then open a second panel.
            Path settingsFile = store.getParent().resolve("settings.properties");
            Theme systemTheme = ThemeDetector.detect("auto");
            SlidePanel first = onEdt(() -> new SlidePanel(store.getParent().resolve("a.txt"), Settings.load(settingsFile)));
            try {
                equal("with nothing saved the system theme is used", systemTheme, onEdt(first::getTheme));
                onEdt(() -> {
                    first.themeButton().doClick();
                    return null;
                });
                equal("the button switches the theme", systemTheme.toggled(), onEdt(first::getTheme));
                check("the choice is written to disk", Files.exists(settingsFile));
            } finally {
                onEdt(() -> {
                    first.dispose();
                    return null;
                });
            }
            SlidePanel second = onEdt(() -> new SlidePanel(store.getParent().resolve("b.txt"), Settings.load(settingsFile)));
            try {
                equal("a new panel starts with the saved choice", systemTheme.toggled(), onEdt(second::getTheme));
            } finally {
                onEdt(() -> {
                    second.dispose();
                    return null;
                });
            }
        } finally {
            onEdt(() -> {
                panel.dispose();
                return null;
            });
        }
        TestRunner.finish();
    }
}
