package quickshelf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** The tray icon, drawn in code so the app needs no image files. */
public final class TrayImage {
    private TrayImage() {}

    /** A blue rounded square with a white bar, like the shelf's edge handle. Always at least 16 px. */
    public static BufferedImage create(int requestedSize) {
        int size = Math.max(16, requestedSize);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double margin = size * 0.09;
        double box = size - 2 * margin;
        g.setColor(new Color(10, 132, 255));
        g.fill(new RoundRectangle2D.Double(margin, margin, box, box, size * 0.25, size * 0.25));

        g.setColor(Color.WHITE);
        double barWidth = size * 0.22;
        double barHeight = size * 0.56;
        g.fill(new RoundRectangle2D.Double(
                size * 0.60, (size - barHeight) / 2, barWidth, barHeight, barWidth * 0.5, barWidth * 0.5));
        g.dispose();
        return image;
    }
}
