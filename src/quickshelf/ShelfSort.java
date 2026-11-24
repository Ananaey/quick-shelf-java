package quickshelf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ways to sort the shelf. Pinned items always stay on top: each group (pinned, then the rest) is
 * sorted on its own. Plain Java, no Swing.
 */
public final class ShelfSort {
    private ShelfSort() {}

    /** A to Z by name. Numbers inside names count by value, so "file2" comes before "file10". */
    public static List<ShelfItem> byName(List<ShelfItem> items) {
        return sortEachGroup(items, byNameThenPath());
    }

    /** Folders first, then files, each A to Z by name. */
    public static List<ShelfItem> foldersFirst(List<ShelfItem> items) {
        Comparator<ShelfItem> foldersOnTop =
                Comparator.comparing((ShelfItem item) -> item.isDirectory() ? 0 : 1);
        return sortEachGroup(items, foldersOnTop.thenComparing(byNameThenPath()));
    }

    /** The items whose file or folder no longer exists, in shelf order. */
    public static List<ShelfItem> missing(List<ShelfItem> items) {
        List<ShelfItem> result = new ArrayList<>();
        for (ShelfItem item : items) {
            if (!item.exists()) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Compares two names the way a person expects: ignoring case, and with runs of digits compared
     * as numbers ("a2" before "a10"). Two names that differ only in case or leading zeros still get
     * a consistent order, so the result never depends on the order they were given in.
     */
    public static int naturalCompare(String first, String second) {
        String a = first.toLowerCase(Locale.ROOT);
        String b = second.toLowerCase(Locale.ROOT);
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i);
            char y = b.charAt(j);
            if (isDigit(x) && isDigit(y)) {
                int iEnd = endOfDigits(a, i);
                int jEnd = endOfDigits(b, j);
                String left = withoutLeadingZeros(a.substring(i, iEnd));
                String right = withoutLeadingZeros(b.substring(j, jEnd));
                int byLength = Integer.compare(left.length(), right.length());
                if (byLength != 0) {
                    return byLength; // more digits means a bigger number
                }
                int byDigits = left.compareTo(right);
                if (byDigits != 0) {
                    return byDigits;
                }
                i = iEnd;
                j = jEnd;
            } else {
                if (x != y) {
                    return Character.compare(x, y);
                }
                i++;
                j++;
            }
        }
        int byRemaining = Integer.compare(a.length() - i, b.length() - j);
        if (byRemaining != 0) {
            return byRemaining; // the shorter one is a prefix of the other, so it comes first
        }
        return first.compareTo(second); // same apart from case or leading zeros: keep it consistent
    }

    // ---- internals ------------------------------------------------------------------

    private static Comparator<ShelfItem> byNameThenPath() {
        return (x, y) -> {
            int byName = naturalCompare(x.getDisplayName(), y.getDisplayName()); // as shown on screen
            return byName != 0 ? byName : x.getPath().toString().compareTo(y.getPath().toString());
        };
    }

    private static List<ShelfItem> sortEachGroup(List<ShelfItem> items, Comparator<ShelfItem> order) {
        List<ShelfItem> pinned = new ArrayList<>();
        List<ShelfItem> others = new ArrayList<>();
        for (ShelfItem item : items) {
            (item.isPinned() ? pinned : others).add(item);
        }
        pinned.sort(order);
        others.sort(order);
        List<ShelfItem> result = new ArrayList<>(items.size());
        result.addAll(pinned);
        result.addAll(others);
        return result;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int endOfDigits(String text, int start) {
        int end = start;
        while (end < text.length() && isDigit(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static String withoutLeadingZeros(String digits) {
        int start = 0;
        while (start < digits.length() - 1 && digits.charAt(start) == '0') {
            start++;
        }
        return digits.substring(start);
    }
}
