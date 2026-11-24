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
 * Two items are the same item if they have the same path; whether one is pinned, or has a
 * display name of its own, does not matter.
 */
public final class ShelfItem {
    /** The longest display name that is kept. */
    public static final int MAX_LABEL = 80;

    private final Path path;
    private final boolean pinned;
    private final String label;

    public ShelfItem(Path path) {
        this(path, false, "");
    }

    public ShelfItem(Path path, boolean pinned) {
        this(path, pinned, "");
    }

    public ShelfItem(Path path, boolean pinned, String label) {
        this.path = path.toAbsolutePath().normalize();
        this.pinned = pinned;
        this.label = cleanLabel(label);
    }

    public Path getPath() {
        return path;
    }

    /** Pinned items are kept at the top of the shelf. */
    public boolean isPinned() {
        return pinned;
    }

    /** The same item, pinned or not (and keeping its display name). Returns this when nothing changes. */
    public ShelfItem withPinned(boolean value) {
        return value == pinned ? this : new ShelfItem(path, value, label);
    }

    /** The name the user gave this item, or an empty string. */
    public String getLabel() {
        return label;
    }

    public boolean hasLabel() {
        return !label.isEmpty();
    }

    /** The same item with another display name; an empty name goes back to the file name. */
    public ShelfItem withLabel(String text) {
        String cleaned = cleanLabel(text);
        return cleaned.equals(label) ? this : new ShelfItem(path, pinned, cleaned);
    }

    /**
     * Makes a display name safe to show and to save: control characters (including tabs and line
     * breaks, which the save file uses to separate things) become spaces, runs of spaces become one,
     * the ends are trimmed, and it is cut to {@link #MAX_LABEL} characters.
     */
    public static String cleanLabel(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        boolean lastWasSpace = true; // drops leading spaces
        for (int i = 0; i < text.length() && cleaned.length() < MAX_LABEL; i++) {
            char c = text.charAt(i);
            boolean space = Character.isWhitespace(c) || Character.isISOControl(c);
            if (space) {
                if (!lastWasSpace) {
                    cleaned.append(' ');
                }
                lastWasSpace = true;
            } else {
                cleaned.append(c);
                lastWasSpace = false;
            }
        }
        // Cutting at the limit must not split a character made of two chars (an emoji, for example).
        if (cleaned.length() > 0 && Character.isHighSurrogate(cleaned.charAt(cleaned.length() - 1))) {
            cleaned.setLength(cleaned.length() - 1);
        }
        return cleaned.toString().stripTrailing();
    }

    /** What the list shows: the name the user gave it, otherwise the file or folder name. */
    public String getDisplayName() {
        return hasLabel() ? label : getName();
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
