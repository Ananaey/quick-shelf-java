package quickshelf;

import java.awt.GraphicsEnvironment;
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

        SlidePanel panel = onEdt(SlidePanel::new);
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
        } finally {
            onEdt(() -> {
                panel.dispose();
                return null;
            });
        }
        TestRunner.finish();
    }
}
