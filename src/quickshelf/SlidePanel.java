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
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
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
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A frameless, always-on-top panel that slides in from the right edge of the screen,
 * plus a thin handle tab that toggles it. The handle rides along the panel's left edge.
 *
 * <p>The panel is an undecorated JFrame, not a JWindow: a JWindow can never take keyboard
 * focus, so typing in the search box and the Enter / Delete keys would not work.
 */
public class SlidePanel {
    /** The width a new installation starts with. The user can drag the left edge to change it. */
    static final int PANEL_WIDTH = PanelLayout.DEFAULT_WIDTH;
    static final int GRIP_WIDTH = 8;
    static final int HANDLE_WIDTH = 14;
    static final int HANDLE_HEIGHT = 80;
    static final int ANIMATION_MS = 220;
    private static final int FRAME_MS = 15;
    /** How often to check that the screen is still the size the shelf was laid out for. */
    private static final int SCREEN_CHECK_MS = 2000;

    private final JFrame panel = new JFrame("Quick Shelf");
    private final JWindow handle = new JWindow();
    private final ShelfView shelf;
    private final Settings settings;
    private final Supplier<Rectangle> screenSource;
    private final Timer timer;
    private final Timer screenWatcher;

    private Rectangle screen;
    private int panelTop;
    private int panelHeight;
    /** The width the user chose. The width in use can be less on a narrow screen. */
    private int preferredWidth;
    private int panelWidth;

    private final JPanel frame = new JPanel(new BorderLayout());
    private final JPanel grip = new JPanel();
    private final JPanel content = new JPanel(new BorderLayout(0, 8));
    private final JPanel header = new JPanel();
    private final JLabel title = new JLabel("Quick Shelf");
    private final JLabel hint = new JLabel("Drag files or folders here");
    private final JButton themeButton = new JButton();
    private final JButton moreButton = new JButton();
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
        this(storageFile, initialTheme, settings, SlidePanel::usableScreen);
    }

    /** Takes the screen area from the given source, so tests can pretend the screen changes. */
    SlidePanel(Path storageFile, Theme initialTheme, Settings settings, Supplier<Rectangle> screenSource) {
        this.settings = settings;
        this.screenSource = screenSource;
        this.shelf = new ShelfView(storageFile);
        this.search = new SearchField(shelf::setQuery);
        this.preferredWidth = settings.panelWidth();
        applyScreen(screenSource.get());
        buildPanel();
        buildHandle();
        this.timer = new Timer(FRAME_MS, event -> tick());
        this.screenWatcher = new Timer(SCREEN_CHECK_MS, event -> refreshScreen());
        setTheme(initialTheme);
        placeAt(hiddenX());
    }

    // ---- public API -------------------------------------------------------------

    /** Shows the handle (the panel itself stays hidden until toggled). */
    public void show() {
        handle.setVisible(true);
        screenWatcher.start();
    }

    public void toggle() {
        refreshScreen(); // the screen may have changed since the last time
        open = !open;
        if (open) {
            panel.setVisible(true);
            panel.toFront();
            // Ready to type: the search box gets the keyboard as soon as the panel opens.
            SwingUtilities.invokeLater(search::requestFocusInWindow);
        }
        fromX = panel.getX();
        toX = open ? shownX() : hiddenX();
        startedAt = System.currentTimeMillis();
        durationMs = SlideMath.durationFor(fromX, toX, panelWidth, ANIMATION_MS);
        timer.restart();
    }

    /** Slides the panel open if it is closed; if it is already open, just brings it to the front. */
    public void open() {
        if (open) {
            panel.toFront();
        } else {
            toggle();
        }
    }

    /** Slides the panel away if it is open. */
    public void close() {
        if (open) {
            toggle();
        }
    }

    /** Puts the keyboard in the search box with its text selected, ready to be typed over. */
    void focusSearch() {
        search.requestFocusInWindow();
        search.selectAll();
    }

    public void setTheme(Theme newTheme) {
        this.theme = newTheme;
        frame.setBackground(newTheme.panelBackground);
        grip.setBackground(newTheme.panelBackground);
        content.setBackground(newTheme.panelBackground);
        title.setForeground(newTheme.text);
        hint.setForeground(newTheme.muted);
        search.applyTheme(newTheme);
        shelf.setTheme(newTheme);
        handleTab.setBackground(newTheme.handle);
        themeButton.repaint();
        moreButton.repaint();
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
        screenWatcher.stop();
        panel.dispose();
        handle.dispose();
    }

    /**
     * Re-reads the usable screen (it changes when a laptop is docked, the resolution or scaling
     * changes, or the taskbar moves) and, if it differs, lays the shelf out for the new one.
     * Returns true if anything changed.
     */
    boolean refreshScreen() {
        Rectangle now = screenSource.get();
        if (now.equals(screen)) {
            return false;
        }
        applyScreen(now);
        panel.setShape(panelShape());
        if (timer.isRunning()) {
            toX = open ? shownX() : hiddenX(); // a slide in progress now ends at the new place
        } else {
            placeAt(open ? shownX() : hiddenX());
        }
        return true;
    }

    /** Remembers the screen and works out how big the panel should be on it. */
    private void applyScreen(Rectangle area) {
        this.screen = area;
        PanelLayout.Vertical layout = PanelLayout.vertical(area.height);
        this.panelTop = area.y + layout.top;
        this.panelHeight = layout.height;
        this.panelWidth = PanelLayout.width(preferredWidth, area.width);
    }

    private RoundRectangle2D panelShape() {
        // The shape is wider than the window, so only the left corners are rounded.
        return new RoundRectangle2D.Double(0, 0, panelWidth + 40, panelHeight, 32, 32);
    }

    /**
     * Makes the open shelf the requested width (within the limits and the screen), keeping its
     * right edge on the edge of the screen. Only works while it is fully open and not sliding.
     * Returns true if the width changed. Call {@link #rememberWidth()} when the user lets go.
     */
    boolean resizeTo(int requestedWidth) {
        if (!open || timer.isRunning()) {
            return false;
        }
        preferredWidth = PanelLayout.clampWidth(requestedWidth);
        int effective = PanelLayout.width(preferredWidth, screen.width);
        if (effective == panelWidth) {
            return false;
        }
        panelWidth = effective;
        panel.setShape(panelShape());
        placeAt(shownX());
        return true;
    }

    /** Saves the chosen width, so the shelf opens at it next time. */
    void rememberWidth() {
        if (preferredWidth == settings.panelWidth()) {
            return; // nothing changed (for example a plain click on the edge): do not rewrite the file
        }
        settings.setPanelWidth(preferredWidth);
        try {
            settings.save();
        } catch (IOException e) {
            // Not being able to remember the width must never break resizing.
            System.err.println("Quick Shelf: could not save settings: " + e.getMessage());
        }
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

    int panelY() {
        return panel.getY();
    }

    int panelHeight() {
        return panel.getHeight();
    }

    int panelWidth() {
        return panel.getWidth();
    }

    int preferredWidth() {
        return preferredWidth;
    }

    /** The strip on the shelf's left edge that is dragged to resize it. */
    JPanel grip() {
        return grip;
    }

    int handleX() {
        return handle.getX();
    }

    int handleY() {
        return handle.getY();
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

    JButton moreButton() {
        return moreButton;
    }

    int shownX() {
        return screen.x + screen.width - panelWidth;
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
        panel.setBounds(panelX, panelTop, panelWidth, panelHeight);
        handle.setBounds(panelX - HANDLE_WIDTH, panelTop, HANDLE_WIDTH, HANDLE_HEIGHT);
    }

    // ---- window construction ----------------------------------------------------

    private void buildPanel() {
        panel.setUndecorated(true);
        panel.setType(Window.Type.UTILITY); // no taskbar button
        panel.setAlwaysOnTop(true);
        panel.setSize(panelWidth, panelHeight);
        panel.setShape(panelShape());

        content.setBorder(BorderFactory.createEmptyBorder(22, 14, 22, 22));

        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        plainIconButton(themeButton, new HalfMoonIcon(), "Switch light / dark");
        themeButton.addActionListener(event -> {
            setTheme(theme.toggled());
            rememberTheme();
        });
        plainIconButton(moreButton, new DotsIcon(), "Sort and tidy up");
        moreButton.addActionListener(event -> shelf.showTidyMenu(moreButton));

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 2, 0));
        buttons.setOpaque(false);
        buttons.add(moreButton);
        buttons.add(themeButton);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(buttons, BorderLayout.EAST);

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

        // The left edge is a thin strip that can be dragged to make the shelf wider or narrower.
        grip.setPreferredSize(new Dimension(GRIP_WIDTH, 0));
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
        grip.setToolTipText("Drag to resize");
        MouseAdapter dragging = new MouseAdapter() {
            private int pressedAtX;
            private int widthWhenPressed;

            @Override
            public void mousePressed(MouseEvent e) {
                pressedAtX = e.getXOnScreen();
                widthWhenPressed = panelWidth;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // Screen coordinates, because the panel moves while it resizes. Dragging left widens it.
                resizeTo(widthWhenPressed + (pressedAtX - e.getXOnScreen()));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                rememberWidth();
            }
        };
        grip.addMouseListener(dragging);
        grip.addMouseMotionListener(dragging);

        frame.add(grip, BorderLayout.WEST);
        frame.add(content, BorderLayout.CENTER);
        panel.setContentPane(frame);
        installKeyboardShortcuts();
    }

    /** A flat button that shows only an icon. */
    private static void plainIconButton(JButton button, Icon icon, String tooltip) {
        button.setIcon(icon);
        button.setToolTipText(tooltip);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Keyboard use: Ctrl+F to search, Enter in the box to open the first match, Down to move into
     * the list, Escape to clear the search and then close the panel.
     */
    private void installKeyboardShortcuts() {
        search.setOnEnter(shelf::openFirstVisible);
        search.setOnDown(shelf::focusList);
        search.setOnEscapeWhenEmpty(this::close);

        // These work wherever the keyboard is inside the panel (for example while the list has it).
        javax.swing.JRootPane root = panel.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "focus-search");
        root.getActionMap().put("focus-search", action(this::focusSearch));
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-panel");
        root.getActionMap().put("close-panel", action(this::close));
    }

    private static AbstractAction action(Runnable body) {
        return new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                body.run();
            }
        };
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

    /** Three small dots in a column, drawn in the current theme's muted colour. */
    private final class DotsIcon implements Icon {
        private static final int SIZE = 18;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(theme.muted);
            for (int i = 0; i < 3; i++) {
                g2.fill(new Ellipse2D.Double(x + SIZE / 2.0 - 1.75, y + 3.0 + i * 5.0, 3.5, 3.5));
            }
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
