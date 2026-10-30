package quickshelf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class PinningTest {
    /** Items are written as a letter, upper case for pinned: "aBc" is a, B (pinned), c. */
    private static List<ShelfItem> items(String spec) {
        List<ShelfItem> result = new ArrayList<>();
        for (char c : spec.toCharArray()) {
            result.add(new ShelfItem(Path.of("shelf-test", String.valueOf(Character.toLowerCase(c))), Character.isUpperCase(c)));
        }
        return result;
    }

    private static String spec(List<ShelfItem> items) {
        StringBuilder out = new StringBuilder();
        for (ShelfItem item : items) {
            String letter = item.getName();
            out.append(item.isPinned() ? letter.toUpperCase() : letter);
        }
        return out.toString();
    }

    private static ShelfItem item(String letter) {
        return new ShelfItem(Path.of("shelf-test", letter));
    }

    public static void main(String[] args) {
        // ---- ShelfItem basics ----
        ShelfItem plain = item("a");
        check("an item starts unpinned", !plain.isPinned());
        check("withPinned pins it", plain.withPinned(true).isPinned());
        check("withPinned returns the same object when nothing changes", plain.withPinned(false) == plain);
        equal("pinned and unpinned are the same item", plain, plain.withPinned(true));
        equal("and have the same hash code", plain.hashCode(), plain.withPinned(true).hashCode());

        // ---- counting and normalising ----
        equal("no pinned items", 0, Pinning.pinnedCount(items("abc")));
        equal("two pinned items", 2, Pinning.pinnedCount(items("aBcD")));
        equal("normalising moves pinned items first and keeps each group's order",
                "BDace", spec(Pinning.normalized(items("aBcDe"))));
        equal("an already normal list is unchanged", "ABcd", spec(Pinning.normalized(items("ABcd"))));
        equal("an empty list stays empty", "", spec(Pinning.normalized(items(""))));
        List<ShelfItem> original = items("aB");
        Pinning.normalized(original);
        equal("normalising does not change the original list", "aB", spec(original));

        // ---- pinning and unpinning ----
        equal("pinning goes to the end of the pinned group", "ACbd", spec(Pinning.toggled(items("Abcd"), item("c"))));
        equal("pinning the first item when nothing is pinned", "Abc", spec(Pinning.toggled(items("abc"), item("a"))));
        equal("pinning the last item when nothing is pinned", "Cab", spec(Pinning.toggled(items("abc"), item("c"))));
        equal("pinning a middle item", "Bac", spec(Pinning.toggled(items("abc"), item("b"))));
        equal("a second pin goes after the first", "BCad", spec(Pinning.toggled(items("Bacd"), item("c"))));
        equal("unpinning goes to the top of the unpinned group", "Ab" + "cd", spec(Pinning.toggled(items("ABcd"), item("b"))));
        equal("unpinning the only pinned item", "abc", spec(Pinning.toggled(items("Abc"), item("a"))));
        equal("an unknown item changes nothing", "aBc", spec(Pinning.toggled(items("aBc"), item("z"))));
        List<ShelfItem> before = items("abc");
        Pinning.toggled(before, item("b"));
        equal("toggling does not change the original list", "abc", spec(before));
        equal("toggling twice is back to unpinned", false,
                Pinning.toggled(Pinning.toggled(items("abc"), item("b")), item("b")).get(0).isPinned());

        // ---- groups ----
        List<ShelfItem> mixed = items("ABcde");
        check("pinned group bounds", java.util.Arrays.equals(new int[] {0, 1}, Pinning.groupBounds(mixed, 1)));
        check("unpinned group bounds", java.util.Arrays.equals(new int[] {2, 4}, Pinning.groupBounds(mixed, 3)));
        check("a list with no pins is one group", java.util.Arrays.equals(new int[] {0, 2}, Pinning.groupBounds(items("abc"), 1)));
        check("a single item is its own group", java.util.Arrays.equals(new int[] {0, 0}, Pinning.groupBounds(items("A"), 0)));

        // ---- moving inside a group ----
        equal("an unpinned item moves down inside its group", 3, Pinning.target(mixed, 2, 1));
        equal("an unpinned item cannot move up into the pinned group", 2, Pinning.target(mixed, 2, -1));
        equal("a pinned item cannot move down into the unpinned group", 1, Pinning.target(mixed, 1, 1));
        equal("a pinned item moves up inside its group", 0, Pinning.target(mixed, 1, -1));
        equal("a big jump is stopped at the end of the group", 4, Pinning.target(mixed, 2, 99));
        equal("a big jump up is stopped at the start of the group", 2, Pinning.target(mixed, 4, -99));
        equal("an index outside the list is returned unchanged", 9, Pinning.target(mixed, 9, 1));
        equal("a negative index is returned unchanged", -1, Pinning.target(mixed, -1, 1));
        equal("with no pins moving works as before", 2, Pinning.target(items("abc"), 1, 1));

        TestRunner.finish();
    }
}
