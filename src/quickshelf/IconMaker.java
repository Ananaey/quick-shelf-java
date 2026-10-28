package quickshelf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Builds a Windows .ico file from the tray icon drawing, so the packaged .exe has the same icon.
 * Used by package.bat: java -cp build\classes quickshelf.IconMaker build\QuickShelf.ico
 */
public final class IconMaker {
    static final int[] SIZES = {16, 32, 48, 64, 128, 256};

    private IconMaker() {}

    /** An .ico holding one PNG-compressed image per size (Windows Vista and later read these). */
    public static byte[] createIco(int[] sizes) throws IOException {
        List<byte[]> pngs = new ArrayList<>();
        for (int size : sizes) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(TrayImage.create(size), "png", png)) {
                throw new IOException("No PNG writer available");
            }
            pngs.add(png.toByteArray());
        }

        int headerSize = 6 + 16 * sizes.length;
        int total = headerSize;
        for (byte[] png : pngs) {
            total += png.length;
        }
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0);              // reserved
        out.putShort((short) 1);              // type 1 = icon
        out.putShort((short) sizes.length);   // image count

        int offset = headerSize;
        for (int i = 0; i < sizes.length; i++) {
            int size = Math.max(16, sizes[i]);
            out.put((byte) (size >= 256 ? 0 : size)); // width, 0 means 256
            out.put((byte) (size >= 256 ? 0 : size)); // height
            out.put((byte) 0);                        // palette colours
            out.put((byte) 0);                        // reserved
            out.putShort((short) 1);                  // colour planes
            out.putShort((short) 32);                 // bits per pixel
            out.putInt(pngs.get(i).length);           // image size in bytes
            out.putInt(offset);                       // where the image starts
            offset += pngs.get(i).length;
        }
        for (byte[] png : pngs) {
            out.put(png);
        }
        return out.array();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: IconMaker <output.ico>");
            System.exit(2);
        }
        Path target = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(target.getParent());
        Files.write(target, createIco(SIZES));
        System.out.println("Wrote " + target);
    }
}
