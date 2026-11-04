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

    /** Sends a real key press to a component, the way the keyboard would. */
    private static void press(java.awt.Component target, int keyCode, int modifiers) throws Exception {
        onEdt(() -> {
            target.dispatchEvent(new java.awt.event.KeyEvent(
                    target, java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, keyCode,
                    java.awt.event.KeyEvent.CHAR_UNDEFINED));
            return null;
        });
    }

    /** Pretends the screen changes (docking, resolution, taskbar) and checks the shelf follows. */
    private static void screenScenario(Path folder) throws Exception {
        java.util.concurrent.atomic.AtomicReference<java.awt.Rectangle> area =
                new java.util.concurrent.atomic.AtomicReference<>(new java.awt.Rectangle(0, 0, 1920, 1040));
        SlidePanel p = onEdt(() -> new SlidePanel(folder.resolve("screen.txt"), Theme.DARK, Settings.inMemory(), area::get));
        try {
            onEdt(() -> {
                p.show();
                return null;
            });
            equal("big screen: parked off the right edge", 1920, (int) onEdt(p::panelX));
            equal("big screen: preferred height", 520, (int) onEdt(p::panelHeight));
            equal("big screen: 60 px from the top", 60, (int) onEdt(p::panelY));
            equal("big screen: the handle sits on the edge", 1920 - SlidePanel.HANDLE_WIDTH, (int) onEdt(p::handleX));
            check("nothing changed yet", !onEdt(p::refreshScreen));

            // closed, then the screen gets smaller
            area.set(new java.awt.Rectangle(0, 0, 1280, 600));
            check("a new screen size is noticed", onEdt(p::refreshScreen));
            equal("the shelf is parked at the new edge", 1280, (int) onEdt(p::panelX));
            equal("and so is the handle", 1280 - SlidePanel.HANDLE_WIDTH, (int) onEdt(p::handleX));
            equal("600 px high still fits the full height", 520, (int) onEdt(p::panelHeight));
            check("the same size again is not a change", !onEdt(p::refreshScreen));

            area.set(new java.awt.Rectangle(0, 0, 1280, 400));
            onEdt(p::refreshScreen);
            equal("a 400 px screen shrinks the panel", 324, (int) onEdt(p::panelHeight));
            equal("the gap above stays", 60, (int) onEdt(p::panelY));
            equal("the handle moves with it", 60, (int) onEdt(p::handleY));

            area.set(new java.awt.Rectangle(0, 0, 1280, 300));
            onEdt(p::refreshScreen);
            equal("a 300 px screen uses the minimum height", 260, (int) onEdt(p::panelHeight));
            equal("and a smaller gap", 40, (int) onEdt(p::panelY));

            // opening picks up a change that nobody told it about, on an offset screen
            area.set(new java.awt.Rectangle(100, 50, 1366, 728));
            onEdt(() -> {
                p.toggle();
                return null;
            });
            waitForAnimation(p);
            equal("opening noticed the new screen and slid to its edge", 100 + 1366 - SlidePanel.PANEL_WIDTH,
                    (int) onEdt(p::panelX));
            equal("offset screen: top is relative to that screen", 50 + 60, (int) onEdt(p::panelY));
            equal("offset screen: full height", 520, (int) onEdt(p::panelHeight));

            // the screen changes while the shelf is open (taskbar moved, display changed)
            area.set(new java.awt.Rectangle(0, 0, 1024, 500));
            check("a change while open is noticed", onEdt(p::refreshScreen));
            check("the shelf stays open", onEdt(p::isOpen));
            equal("and moves to the new edge", 1024 - SlidePanel.PANEL_WIDTH, (int) onEdt(p::panelX));
            equal("and resizes", 424, (int) onEdt(p::panelHeight));
            equal("and the handle follows", 1024 - SlidePanel.PANEL_WIDTH - SlidePanel.HANDLE_WIDTH, (int) onEdt(p::handleX));

            // the screen changes in the middle of a slide
            onEdt(() -> {
                p.toggle(); // starts closing
                return null;
            });
            area.set(new java.awt.Rectangle(0, 0, 800, 600));
            onEdt(p::refreshScreen);
            waitForAnimation(p);
            check("closed", !onEdt(p::isOpen));
            equal("a slide in progress ends at the new edge", 800, (int) onEdt(p::panelX));
            equal("and the handle is there too", 800 - SlidePanel.HANDLE_WIDTH, (int) onEdt(p::handleX));
        } finally {
            onEdt(() -> {
                p.dispose();
                return null;
            });
        }
    }

    /** Drives the shelf with key presses only: search, open, move around, delete, escape. */
    private static void keyboardScenario(Path folder) throws Exception {
        Path files = Files.createDirectories(folder.resolve("keys"));
        java.io.File one = Files.createFile(files.resolve("one.txt")).toFile();
        java.io.File two = Files.createFile(files.resolve("two.txt")).toFile();
        java.io.File three = Files.createFile(files.resolve("three.txt")).toFile();

        SlidePanel keys = onEdt(() -> new SlidePanel(files.resolve("shelf.txt")));
        try {
            java.util.List<java.io.File> launched = new java.util.ArrayList<>();
            onEdt(() -> {
                keys.shelf().addFiles(java.util.List.of(one, two, three));
                keys.shelf().setOpener(new PathOpener(launched::add));
                keys.toggle();
                return null;
            });
            waitForAnimation(keys);
            java.awt.Component box = keys.search();
            java.awt.Component list = keys.shelf().keyTarget();
            final int none = 0;
            final int enter = java.awt.event.KeyEvent.VK_ENTER;
            final int escape = java.awt.event.KeyEvent.VK_ESCAPE;
            final int down = java.awt.event.KeyEvent.VK_DOWN;

            // Enter in the search box opens the first match
            onEdt(() -> {
                keys.search().setText("two");
                return null;
            });
            press(box, enter, none);
            equal("Enter in the search box opens one file", 1, launched.size());
            equal("and it is the match", "two.txt", launched.get(0).getName());
            onEdt(() -> {
                keys.search().setText("zzz");
                return null;
            });
            press(box, enter, none);
            equal("Enter with no match opens nothing", 1, launched.size());

            // Down moves from the box into the list
            onEdt(() -> {
                keys.search().setText("");
                return null;
            });
            press(box, down, none);
            equal("Down in the search box selects the first row", 0, (int) onEdt(() -> keys.shelf().selectedRow()));

            // Arrow, Enter and Delete in the list (the keys that used to do nothing)
            press(list, down, none);
            equal("Down in the list moves to the next row", 1, (int) onEdt(() -> keys.shelf().selectedRow()));
            press(list, enter, none);
            equal("Enter in the list opens the selected item", 2, launched.size());
            equal("it is the selected row", "two.txt", launched.get(1).getName());
            press(list, java.awt.event.KeyEvent.VK_DELETE, none);
            equal("Delete removes the selected item", 2, (int) onEdt(() -> keys.shelf().items().size()));
            equal("the right one went", "three.txt", onEdt(() -> keys.shelf().items().get(1).getName()));

            // Ctrl+F selects the search text, even with the keyboard in the list
            onEdt(() -> {
                keys.search().setText("hello");
                return null;
            });
            press(list, java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK);
            equal("Ctrl+F selects the text in the search box", "hello", onEdt(() -> keys.search().getSelectedText()));

            // Escape clears first, then closes
            press(box, escape, none);
            equal("Escape clears the search", "", onEdt(() -> keys.search().getText()));
            check("and leaves the panel open", onEdt(keys::isOpen));
            press(box, escape, none);
            check("Escape on an empty box closes the panel", !onEdt(keys::isOpen));
            waitForAnimation(keys);

            onEdt(() -> {
                keys.toggle();
                return null;
            });
            waitForAnimation(keys);
            check("reopened", onEdt(keys::isOpen));
            press(list, escape, none);
            check("Escape with the keyboard in the list also closes the panel", !onEdt(keys::isOpen));
            waitForAnimation(keys);
        } finally {
            onEdt(() -> {
                keys.dispose();
                return null;
            });
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

            // open() is what a second launch triggers: open if closed, stay open if already open
            check("closed before open()", !onEdt(panel::isOpen));
            onEdt(() -> {
                panel.open();
                return null;
            });
            check("open() opens a closed shelf", onEdt(panel::isOpen));
            waitForAnimation(panel);
            onEdt(() -> {
                panel.open();
                return null;
            });
            check("open() on an open shelf keeps it open", onEdt(panel::isOpen));
            waitForAnimation(panel);
            equal("and it stays fully in view", onEdt(panel::shownX), onEdt(panel::panelX));
            onEdt(() -> {
                panel.close();
                return null;
            });
            check("close() closes it", !onEdt(panel::isOpen));
            onEdt(() -> {
                panel.close();
                return null;
            });
            check("close() on a closed shelf does nothing", !onEdt(panel::isOpen));
            waitForAnimation(panel);

            keyboardScenario(store.getParent());
            screenScenario(store.getParent());

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
