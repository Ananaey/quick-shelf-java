package quickshelf;

import java.util.Locale;

/** Search matching for the shelf. Plain Java, no Swing. */
public final class ShelfFilter {
    private ShelfFilter() {}

    /**
     * True if every word of the query appears in the item's name or full path, ignoring case.
     * A blank query matches everything.
     */
    public static boolean matches(ShelfItem item, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String haystack = (item.getName() + " " + item.getPath()).toLowerCase(Locale.ROOT);
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
