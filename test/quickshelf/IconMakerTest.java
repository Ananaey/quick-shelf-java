package quickshelf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class IconMakerTest {
    public static void main(String[] args) throws IOException {
        int[] sizes = {16, 48, 256};
        byte[] ico = IconMaker.createIco(sizes);
        ByteBuffer in = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN);

        equal("reserved field is 0", (short) 0, in.getShort(0));
        equal("type is 1 (icon)", (short) 1, in.getShort(2));
        equal("image count", (short) 3, in.getShort(4));

        int[] expectedPixels = {16, 48, 256};
        for (int i = 0; i < sizes.length; i++) {
            int entry = 6 + 16 * i;
            int width = in.get(entry) & 0xFF;
            int height = in.get(entry + 1) & 0xFF;
            equal("entry " + i + " width (0 means 256)", sizes[i] >= 256 ? 0 : sizes[i], width);
            equal("entry " + i + " height (0 means 256)", sizes[i] >= 256 ? 0 : sizes[i], height);
            equal("entry " + i + " bits per pixel", (short) 32, in.getShort(entry + 6));
            int length = in.getInt(entry + 8);
            int offset = in.getInt(entry + 12);
            check("entry " + i + " lies inside the file", offset > 0 && length > 0 && offset + length <= ico.length);

            byte[] png = new byte[length];
            System.arraycopy(ico, offset, png, 0, length);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            check("entry " + i + " is a readable PNG", image != null);
            if (image != null) {
                equal("entry " + i + " PNG size", expectedPixels[i], image.getWidth());
                check("entry " + i + " has a transparent corner", (image.getRGB(0, 0) >>> 24) == 0);
            }
        }

        // the real list of sizes writes a file whose total size matches the directory
        Path file = Files.createTempDirectory("icon-test").resolve("nested").resolve("QuickShelf.ico");
        IconMaker.main(new String[] {file.toString()});
        check("main writes the file and its folder", Files.size(file) > 1000);
        equal("main writes every size", (short) IconMaker.SIZES.length,
                ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN).getShort(4));

        TestRunner.finish();
    }
}
