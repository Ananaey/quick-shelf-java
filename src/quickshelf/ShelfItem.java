package quickshelf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One file or folder on the shelf. Plain Java, no Swing.
 * Two items are the same item if they have the same path; whether one is pinned does not matter.
 */
public final class ShelfItem {
    private final Path path;
    private final boolean pinned;

    public ShelfItem(Path path) {
        this(path, false);
    }

    public ShelfItem(Path path, boolean pinned) {
        this.path = path.toAbsolutePath().normalize();
        this.pinned = pinned;
    }

    public Path getPath() {
        return path;
    }

    /** Pinned items are kept at the top of the shelf. */
    public boolean isPinned() {
        return pinned;
    }

    /** The same item, pinned or not. Returns this when nothing changes. */
    public ShelfItem withPinned(boolean value) {
        return value == pinned ? this : new ShelfItem(path, value);
    }

    /** Display name. Drive roots like C:\ have no file name, so the full path is used. */
    public String getName() {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    public boolean isDirectory() {
        return Files.isDirectory(path);
    }

    public boolean exists() {
        return Files.exists(path);
    }

    /** Identity for duplicate checks. Windows paths ignore case, so the key does too. */
    String key() {
        String text = path.toString();
        return java.io.File.separatorChar == '\\' ? text.toLowerCase() : text;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShelfItem && ((ShelfItem) other).key().equals(key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Turns dropped paths into new (unpinned) items. Skips null paths, paths that do not exist,
     * repeats within the drop, and anything already on the shelf. Keeps drop order.
     */
    public static List<ShelfItem> newItems(Collection<Path> dropped, Collection<ShelfItem> existing) {
        Set<ShelfItem> seen = new HashSet<>(existing);
        List<ShelfItem> result = new ArrayList<>();
        for (Path path : dropped) {
            if (path == null) {
                continue;
            }
            ShelfItem item = new ShelfItem(path);
            if (item.exists() && seen.add(item)) {
                result.add(item);
            }
        }
        return result;
    }
}
