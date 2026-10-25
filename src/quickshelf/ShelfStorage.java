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
 * No libraries needed, and the file is easy to read or fix by hand.
 */
public final class ShelfStorage {
    static final String HEADER = "# Quick Shelf - one path per line";

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

    /**
     * Returns the saved paths in order. A missing file gives an empty shelf. A file that cannot be
     * read as text is renamed to shelf.txt.bad (so nothing is lost) and the shelf starts empty.
     * Blank lines, comments and lines that are not valid paths are skipped.
     */
    public List<Path> load() {
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
        List<Path> paths = new ArrayList<>();
        for (String line : lines) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            try {
                paths.add(Path.of(text));
            } catch (InvalidPathException e) {
                // skip a damaged line, keep the rest
            }
        }
        return paths;
    }

    /** Writes a temp file first and then replaces, so a crash cannot leave a half-written shelf. */
    public void save(List<Path> paths) throws IOException {
        Path folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Path path : paths) {
            lines.add(path.toString());
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
