package quickshelf;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;

/**
 * A frameless, always-on-top panel that slides in from the right edge of the screen,
 * plus a thin handle tab that toggles it. The handle rides along the panel's left edge.
 */
public class SlidePanel {
    static final int PANEL_WIDTH = 320;
    static final int PANEL_HEIGHT = 520;
    static final int HANDLE_WIDTH = 14;
    static final int HANDLE_HEIGHT = 80;
    static final int TOP_MARGIN = 60;
    static final int ANIMATION_MS = 220;
    private static final int FRAME_MS = 15;

    private static final Color PANEL_BG = new Color(30, 30, 34);
    private static final Color HANDLE_BG = new Color(60, 60, 68);
    private static final Color HANDLE_HOVER = new Color(95, 95, 108);
    private static final Color TEXT = new Color(242, 242, 247);
    private static final Color MUTED = new Color(154, 154, 162);

    private final JWindow panel = new JWindow();
    private final JWindow handle = new JWindow();
    private final ShelfView shelf;
    private final Rectangle screen;
    private final Timer timer;

    private boolean open = false;
    private int fromX;
    private int toX;
    private long startedAt;
    private long durationMs;

    /** A panel whose shelf is saved in the default location. */
    public SlidePanel() {
        this(ShelfStorage.defaultPath());
    }

    /** A panel whose shelf is saved in the given file (tests use a temporary one). */
    public SlidePanel(Path storageFile) {
        this.shelf = new ShelfView(storageFile);
        this.screen = usableScreen();
        buildPanel();
        buildHandle();
        this.timer = new Timer(FRAME_MS, event -> tick());
        placeAt(hiddenX());
    }

    // ---- public API -------------------------------------------------------------

    /** Shows the handle (the panel itself stays hidden until toggled). */
    public void show() {
        handle.setVisible(true);
    }

    public void toggle() {
        open = !open;
        if (open) {
            panel.setVisible(true);
        }
        fromX = panel.getX();
        toX = open ? shownX() : hiddenX();
        startedAt = System.currentTimeMillis();
        durationMs = SlideMath.durationFor(fromX, toX, PANEL_WIDTH, ANIMATION_MS);
        timer.restart();
    }

    public void dispose() {
        timer.stop();
        panel.dispose();
        handle.dispose();
    }

    // ---- state, visible to tests ------------------------------------------------

    boolean isOpen() {
        return open;
    }

    boolean isAnimating() {
        return timer.isRunning();
    }

    int panelX() {
        return panel.getX();
    }

    boolean isPanelVisible() {
        return panel.isVisible();
    }

    ShelfView shelf() {
        return shelf;
    }

    int shownX() {
        return screen.x + screen.width - PANEL_WIDTH;
    }

    int hiddenX() {
        return screen.x + screen.width;
    }

    // ---- animation --------------------------------------------------------------

    private void tick() {
        long elapsed = System.currentTimeMillis() - startedAt;
        placeAt(SlideMath.xAt(fromX, toX, elapsed, durationMs));
        if (elapsed >= durationMs) {
            placeAt(toX);
            timer.stop();
            if (!open) {
                panel.setVisible(false);
            }
        }
    }

    private void placeAt(int panelX) {
        int y = screen.y + TOP_MARGIN;
        panel.setBounds(panelX, y, PANEL_WIDTH, PANEL_HEIGHT);
        handle.setBounds(panelX - HANDLE_WIDTH, y, HANDLE_WIDTH, HANDLE_HEIGHT);
    }

    // ---- window construction ----------------------------------------------------

    private void buildPanel() {
        panel.setAlwaysOnTop(true);
        panel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        // The shape is wider than the window, so only the left corners are rounded.
        panel.setShape(new RoundRectangle2D.Double(0, 0, PANEL_WIDTH + 40, PANEL_HEIGHT, 32, 32));

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBackground(PANEL_BG);
        content.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));

        JLabel title = new JLabel("Quick Shelf");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel hint = new JLabel("Drag files or folders here");
        hint.setForeground(MUTED);

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.CENTER);
        content.add(header, BorderLayout.NORTH);
        content.add(shelf, BorderLayout.CENTER);
        panel.setContentPane(content);
    }

    private void buildHandle() {
        handle.setAlwaysOnTop(true);
        handle.setSize(new Dimension(HANDLE_WIDTH, HANDLE_HEIGHT));
        handle.setShape(new RoundRectangle2D.Double(0, 0, HANDLE_WIDTH + 14, HANDLE_HEIGHT, 14, 14));

        JPanel tab = new JPanel();
        tab.setBackground(HANDLE_BG);
        tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tab.setToolTipText("Quick Shelf");
        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggle();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                tab.setBackground(HANDLE_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                tab.setBackground(HANDLE_BG);
            }
        });
        handle.setContentPane(tab);
    }

    /** The primary screen minus the taskbar, so the panel never hides behind it. */
    private static Rectangle usableScreen() {
        GraphicsConfiguration config =
                GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle bounds = config.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom);
    }
}
