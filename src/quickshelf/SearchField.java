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
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        getActionMap().put("clear", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                setText("");
            }
        });
        applyTheme(Theme.DARK);
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
