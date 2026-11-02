package quickshelf;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A text box with grey "Search" text while empty. Escape clears it. */
public class SearchField extends JTextField {
    private static final long serialVersionUID = 1L;
    private static final String PLACEHOLDER = "Search";

    private transient Theme theme = Theme.DARK;
    private transient Runnable onEnter = () -> { };
    private transient Runnable onDown = () -> { };
    private transient Runnable onEscapeWhenEmpty = () -> { };

    public SearchField(Consumer<String> onChange) {
        setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        setToolTipText("Type to filter. Escape clears.");
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange.accept(getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange.accept(getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange.accept(getText());
            }
        });
        bindKey(KeyEvent.VK_ESCAPE, "escape", () -> {
            if (getText().isEmpty()) {
                onEscapeWhenEmpty.run(); // nothing left to clear: let the panel close
            } else {
                setText("");
            }
        });
        bindKey(KeyEvent.VK_ENTER, "submit", () -> onEnter.run());
        bindKey(KeyEvent.VK_DOWN, "down", () -> onDown.run());
        applyTheme(Theme.DARK);
    }

    /** Called when Enter is pressed in the box. */
    public void setOnEnter(Runnable action) {
        this.onEnter = action;
    }

    /** Called when the Down arrow is pressed in the box. */
    public void setOnDown(Runnable action) {
        this.onDown = action;
    }

    /** Called when Escape is pressed and the box is already empty. */
    public void setOnEscapeWhenEmpty(Runnable action) {
        this.onEscapeWhenEmpty = action;
    }

    private void bindKey(int keyCode, String name, Runnable action) {
        getInputMap().put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    public void applyTheme(Theme newTheme) {
        this.theme = newTheme;
        setBackground(newTheme.field);
        setForeground(newTheme.text);
        setCaretColor(newTheme.text);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(theme.muted);
            g2.setFont(getFont());
            g2.drawString(PLACEHOLDER, getInsets().left, getInsets().top + g2.getFontMetrics().getAscent());
            g2.dispose();
        }
    }
}
