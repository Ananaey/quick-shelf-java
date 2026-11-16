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

        // ---- several at once ----
        // The shelf always keeps pinned items first, so these lists do too: B and E are pinned.
        List<ShelfItem> six = items("BEacdf");
        List<ShelfItem> all = List.of(item("a"), item("b"), item("c"), item("d"), item("e"), item("f"));

        check("pin when one of them is not pinned", Pinning.shouldPin(six, List.of(item("b"), item("c"))));
        check("pin when none of them is pinned", Pinning.shouldPin(six, List.of(item("c"), item("d"))));
        check("unpin when all of them are pinned", !Pinning.shouldPin(six, List.of(item("b"), item("e"))));
        check("a single pinned item means unpin", !Pinning.shouldPin(six, List.of(item("b"))));
        check("nothing selected means nothing to pin", !Pinning.shouldPin(six, List.of()));
        check("items not on the list are ignored", !Pinning.shouldPin(six, List.of(item("zz"), item("b"))));

        equal("pinning two goes after the existing pinned ones, in list order", "BECDaf",
                spec(Pinning.withPinned(six, List.of(item("d"), item("c")), true)));
        equal("pinning items that are already pinned changes nothing", "BEacdf",
                spec(Pinning.withPinned(six, List.of(item("b"), item("e")), true)));
        equal("pinning a mix moves only the ones that were not pinned", "BECDaf",
                spec(Pinning.withPinned(six, List.of(item("b"), item("c"), item("d")), true)));
        equal("unpinning two puts them at the top of the unpinned group, in order", "beacdf",
                spec(Pinning.withPinned(six, List.of(item("e"), item("b")), false)));
        equal("unpinning one of two keeps the other pinned", "Beacdf",
                spec(Pinning.withPinned(six, List.of(item("e")), false)));
        equal("unpinning items that are not pinned changes nothing", "BEacdf",
                spec(Pinning.withPinned(six, List.of(item("a"), item("f")), false)));
        equal("no targets changes nothing", "BEacdf", spec(Pinning.withPinned(six, List.of(), true)));
        equal("targets that are not on the list change nothing", "BEacdf",
                spec(Pinning.withPinned(six, List.of(item("zz")), true)));
        equal("pinning everything keeps the pinned ones first and the order after them", "BEACDF",
                spec(Pinning.withPinned(six, all, true)));
        equal("unpinning everything keeps the order", "beacdf", spec(Pinning.withPinned(six, all, false)));
        equal("the original list is not changed", "BEacdf", spec(six));
        equal("the result always has the same number of items", 6,
                Pinning.withPinned(six, List.of(item("a"), item("e")), true).size());
        equal("pinning a and c then unpinning them again gives back the original order", "BEacdf",
                spec(Pinning.withPinned(Pinning.withPinned(six, List.of(item("a"), item("c")), true),
                        List.of(item("a"), item("c")), false)));

        // pinned items always end up first, whatever was selected
        List<ShelfItem> result = Pinning.withPinned(items("abcdef"), List.of(item("f"), item("b")), true);
        boolean pinnedFirst = true;
        boolean seenUnpinned = false;
        for (ShelfItem it : result) {
            if (!it.isPinned()) {
                seenUnpinned = true;
            } else if (seenUnpinned) {
                pinnedFirst = false;
            }
        }
        check("pinned items are always first", pinnedFirst);
        equal("pinning f and b from a plain list puts b before f", "BFacde", spec(result));

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
