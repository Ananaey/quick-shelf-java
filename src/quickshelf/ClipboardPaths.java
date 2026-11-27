package quickshelf;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads file paths out of what is on the clipboard: files copied in Explorer, or text with one
 * path per line (including Windows' "Copy as path", which puts each path in quotes). Plain Java.
 */
public final class ClipboardPaths {
    /** Pasting a huge block of text must not turn into thousands of lookups. */
    static final int MAX_LINES = 1000;

    private ClipboardPaths() {}

    /**
     * The paths found in what was copied, in order. Files copied in Explorer come first-hand;
     * otherwise any text is read line by line. Anything unreadable gives an empty list.
     */
    public static List<Path> fromTransferable(Transferable contents) {
        if (contents == null) {
            return List.of();
        }
        try {
            if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<Path> paths = new ArrayList<>();
                Object data = contents.getTransferData(DataFlavor.javaFileListFlavor);
                if (data instanceof List<?>) {
                    for (Object entry : (List<?>) data) {
                        if (entry instanceof File) {
                            paths.add(((File) entry).toPath());
                        }
                    }
                }
                return paths;
            }
            if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Object data = contents.getTransferData(DataFlavor.stringFlavor);
                return data instanceof String ? fromText((String) data) : List.of();
            }
        } catch (UnsupportedFlavorException | IOException | RuntimeException e) {
            // another program may be using the clipboard, or it holds something odd: treat as empty
        }
        return List.of();
    }

    /**
     * One path per line. Blank lines, text that is not a path, and relative paths (which would be
     * measured from wherever the app happens to be running) are skipped. Surrounding quotes are
     * removed, and file: links are understood.
     */
    public static List<Path> fromText(String text) {
        List<Path> paths = new ArrayList<>();
        if (text == null) {
            return paths;
        }
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Path path = parseLine(lines[i]);
            if (path != null) {
                paths.add(path);
            }
        }
        return paths;
    }

    private static Path parseLine(String line) {
        String text = line.strip();
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                text = text.substring(1, text.length() - 1).strip();
            }
        }
        if (text.isEmpty()) {
            return null;
        }
        try {
            Path path = text.regionMatches(true, 0, "file:", 0, 5) ? Path.of(URI.create(text)) : Path.of(text);
            return path.isAbsolute() ? path : null;
        } catch (IllegalArgumentException | FileSystemNotFoundException | SecurityException e) {
            // IllegalArgumentException also covers InvalidPathException (text that is not a path)
            return null;
        }
    }
}
