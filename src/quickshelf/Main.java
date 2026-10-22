package quickshelf;

import javax.swing.SwingUtilities;

/** Starts Quick Shelf: java -cp out quickshelf.Main */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SlidePanel().show());
    }
}
