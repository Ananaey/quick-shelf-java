package quickshelf;

import java.awt.image.BufferedImage;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class TrayImageTest {
    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    public static void main(String[] args) {
        BufferedImage image = TrayImage.create(64);
        equal("width", 64, image.getWidth());
        equal("height", 64, image.getHeight());

        check("the corner is transparent", alpha(image, 0, 0) == 0);
        check("the body is solid", alpha(image, 16, 32) == 255);
        int body = image.getRGB(16, 32) & 0xFFFFFF;
        equal("the body is blue", 0x0A84FF, body);
        int bar = image.getRGB(44, 32) & 0xFFFFFF;
        equal("the bar is white", 0xFFFFFF, bar);

        equal("tiny sizes are raised to 16", 16, TrayImage.create(4).getWidth());
        equal("zero is raised to 16", 16, TrayImage.create(0).getHeight());
        equal("negative is raised to 16", 16, TrayImage.create(-5).getWidth());
        check("a large size works", TrayImage.create(256).getWidth() == 256);

        TestRunner.finish();
    }
}
