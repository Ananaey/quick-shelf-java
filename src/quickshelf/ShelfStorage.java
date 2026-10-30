package quickshelf;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves the shelf as plain text: one path per line, UTF-8, lines starting with # are comments.
 * A pinned item has "[pinned] " in front of its path. Older files without any marker load as
 * unpinned. No libraries needed, and the file is easy to read or fix by hand.
 */
public final class ShelfStorage {
    static final String HEADER = "# Quick Shelf - one path per line, [pinned] in front for pinned items";
    static final String PINNED_MARKER = "[pinned]";

    /** One saved line: a path and whether it is pinned. */
    public static final class Entry {
        public final Path path;
        public final boolean pinned;

        public Entry(Path path, boolean pinned) {
            this.path = path;
            this.pinned = pinned;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Entry && ((Entry) other).path.equals(path) && ((Entry) other).pinned == pinned;
        }

        @Override
        public int hashCode() {
            return path.hashCode() * 31 + (pinned ? 1 : 0);
        }

        @Override
        public String toString() {
            return (pinned ? PINNED_MARKER + " " : "") + path;
        }
    }

    private final Path file;

    public ShelfStorage(Path file) {
        this.file = file;
    }

    /** Windows: %APPDATA%\QuickShelf\shelf.txt. Elsewhere: ~/.config/QuickShelf/shelf.txt. */
    public static Path defaultPath() {
        String appData = System.getenv("APPDATA");
        Path base = appData != null && !appData.isBlank()
                ? Path.of(appData)
                : Path.of(System.getProperty("user.home"), ".config");
        return base.resolve("QuickShelf").resolve("shelf.txt");
    }

    public Path getFile() {
        return file;
    }

    /** Just the paths, in order (pinned markers are read but ignored). */
    public List<Path> load() {
        List<Path> paths = new ArrayList<>();
        for (Entry entry : loadEntries()) {
            paths.add(entry.path);
        }
        return paths;
    }

    /**
     * Returns the saved lines in order. A missing file gives an empty shelf. A file that cannot be
     * read as text is renamed to shelf.txt.bad (so nothing is lost) and the shelf starts empty.
     * Blank lines, comments and lines that are not valid paths are skipped.
     */
    public List<Entry> loadEntries() {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return new ArrayList<>();
        } catch (CharacterCodingException e) {
            keepAsBad();
            return new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
        List<Entry> entries = new ArrayList<>();
        for (String line : lines) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            boolean pinned = text.startsWith(PINNED_MARKER);
            if (pinned) {
                text = text.substring(PINNED_MARKER.length()).strip();
            }
            if (text.isEmpty()) {
                continue; // a marker with no path: an empty path would mean the current folder
            }
            try {
                entries.add(new Entry(Path.of(text), pinned));
            } catch (InvalidPathException e) {
                // skip a damaged line, keep the rest
            }
        }
        return entries;
    }

    /** Saves paths, none of them pinned. */
    public void save(List<Path> paths) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (Path path : paths) {
            entries.add(new Entry(path, false));
        }
        saveEntries(entries);
    }

    /** Writes a temp file first and then replaces, so a crash cannot leave a half-written shelf. */
    public void saveEntries(List<Entry> entries) throws IOException {
        Path folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Entry entry : entries) {
            lines.add(entry.toString());
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private void keepAsBad() {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // nothing more we can do; the next save will overwrite it
        }
    }
}
