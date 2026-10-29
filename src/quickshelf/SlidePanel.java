package quickshelf;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;

/**
 * A frameless, always-on-top panel that slides in from the right edge of the screen,
 * plus a thin handle tab that toggles it. The handle rides along the panel's left edge.
 *
 * <p>The panel is an undecorated JFrame, not a JWindow: a JWindow can never take keyboard
 * focus, so typing in the search box and the Enter / Delete keys would not work.
 */
public class SlidePanel {
    static final int PANEL_WIDTH = 320;
    static final int PANEL_HEIGHT = 520;
    static final int HANDLE_WIDTH = 14;
    static final int HANDLE_HEIGHT = 80;
    static final int TOP_MARGIN = 60;
    static final int ANIMATION_MS = 220;
    private static final int FRAME_MS = 15;

    private final JFrame panel = new JFrame("Quick Shelf");
    private final JWindow handle = new JWindow();
    private final ShelfView shelf;
    private final Settings settings;
    private final Rectangle screen;
    private final Timer timer;

    private final JPanel content = new JPanel(new BorderLayout(0, 8));
    private final JPanel header = new JPanel();
    private final JLabel title = new JLabel("Quick Shelf");
    private final JLabel hint = new JLabel("Drag files or folders here");
    private final JButton themeButton = new JButton();
    private final SearchField search;
    private final JPanel handleTab = new JPanel();

    private Theme theme = Theme.DARK;
    private boolean open = false;
    private int fromX;
    private int toX;
    private long startedAt;
    private long durationMs;

    /** The real app: shelf and settings in %APPDATA%\QuickShelf, theme as last chosen (or the Windows one). */
    public SlidePanel() {
        this(ShelfStorage.defaultPath(), Settings.load(Settings.defaultPath()));
    }

    /** A panel with its own shelf file and nothing saved about settings (tests use temporary files). */
    public SlidePanel(Path storageFile) {
        this(storageFile, Theme.DARK, Settings.inMemory());
    }

    /** The theme comes from the settings: the saved choice, or the Windows setting when there is none. */
    public SlidePanel(Path storageFile, Settings settings) {
        this(storageFile, ThemeDetector.detect(settings.themePreference()), settings);
    }

    public SlidePanel(Path storageFile, Theme initialTheme, Settings settings) {
        this.settings = settings;
        this.shelf = new ShelfView(storageFile);
        this.search = new SearchField(shelf::setQuery);
        this.screen = usableScreen();
        buildPanel();
        buildHandle();
        this.timer = new Timer(FRAME_MS, event -> tick());
        setTheme(initialTheme);
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
            panel.toFront();
        }
        fromX = panel.getX();
        toX = open ? shownX() : hiddenX();
        startedAt = System.currentTimeMillis();
        durationMs = SlideMath.durationFor(fromX, toX, PANEL_WIDTH, ANIMATION_MS);
        timer.restart();
    }

    public void setTheme(Theme newTheme) {
        this.theme = newTheme;
        content.setBackground(newTheme.panelBackground);
        title.setForeground(newTheme.text);
        hint.setForeground(newTheme.muted);
        search.applyTheme(newTheme);
        shelf.setTheme(newTheme);
        handleTab.setBackground(newTheme.handle);
        themeButton.repaint();
    }

    public Theme getTheme() {
        return theme;
    }

    /** Saves the current theme as the user's choice, so it is used on the next start. */
    private void rememberTheme() {
        settings.setThemePreference(theme.name);
        try {
            settings.save();
        } catch (IOException e) {
            // Not being able to remember the choice must never break the toggle.
            System.err.println("Quick Shelf: could not save settings: " + e.getMessage());
        }
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

    /** True if the panel can receive key presses (needed for search, Enter and Delete). */
    boolean acceptsKeyboardInput() {
        return panel.isFocusableWindow();
    }

    ShelfView shelf() {
        return shelf;
    }

    SearchField search() {
        return search;
    }

    JButton themeButton() {
        return themeButton;
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
        panel.setUndecorated(true);
        panel.setType(Window.Type.UTILITY); // no taskbar button
        panel.setAlwaysOnTop(true);
        panel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        // The shape is wider than the window, so only the left corners are rounded.
        panel.setShape(new RoundRectangle2D.Double(0, 0, PANEL_WIDTH + 40, PANEL_HEIGHT, 32, 32));

        content.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));

        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        themeButton.setIcon(new HalfMoonIcon());
        themeButton.setToolTipText("Switch light / dark");
        themeButton.setBorderPainted(false);
        themeButton.setContentAreaFilled(false);
        themeButton.setFocusPainted(false);
        themeButton.setFocusable(false);
        themeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        themeButton.addActionListener(event -> {
            setTheme(theme.toggled());
            rememberTheme();
        });

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(themeButton, BorderLayout.EAST);

        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        for (JComponent part : new JComponent[] {titleRow, hint, search}) {
            part.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        header.add(titleRow);
        header.add(Box.createVerticalStrut(2));
        header.add(hint);
        header.add(Box.createVerticalStrut(10));
        header.add(search);

        content.add(header, BorderLayout.NORTH);
        content.add(shelf, BorderLayout.CENTER);
        panel.setContentPane(content);
    }

    private void buildHandle() {
        handle.setAlwaysOnTop(true);
        handle.setSize(new Dimension(HANDLE_WIDTH, HANDLE_HEIGHT));
        handle.setShape(new RoundRectangle2D.Double(0, 0, HANDLE_WIDTH + 14, HANDLE_HEIGHT, 14, 14));

        handleTab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        handleTab.setToolTipText("Quick Shelf");
        handleTab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggle();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                handleTab.setBackground(theme.handleHover);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                handleTab.setBackground(theme.handle);
            }
        });
        handle.setContentPane(handleTab);
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

    /** A small circle with one half filled, drawn in the current theme's muted colour. */
    private final class HalfMoonIcon implements Icon {
        private static final int SIZE = 18;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(theme.muted);
            g2.draw(new Ellipse2D.Double(x + 1, y + 1, SIZE - 3, SIZE - 3));
            g2.fill(new Arc2D.Double(x + 1, y + 1, SIZE - 3, SIZE - 3, 90, 180, Arc2D.CHORD));
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
