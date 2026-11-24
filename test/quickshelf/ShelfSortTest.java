package quickshelf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class ShelfSortTest {
    private static ShelfItem item(String name) {
        return new ShelfItem(Path.of("sort-test", name));
    }

    private static ShelfItem pinned(String name) {
        return new ShelfItem(Path.of("sort-test", name), true);
    }

    private static List<String> names(List<ShelfItem> items) {
        List<String> result = new ArrayList<>();
        for (ShelfItem item : items) {
            result.add((item.isPinned() ? "*" : "") + item.getName());
        }
        return result;
    }

    private static int sign(int value) {
        return Integer.signum(value);
    }

    public static void main(String[] args) throws IOException {
        // ---- natural comparison ----
        check("a2 comes before a10", ShelfSort.naturalCompare("a2", "a10") < 0);
        check("a10 comes after a2", ShelfSort.naturalCompare("a10", "a2") > 0);
        check("img9 before img10", ShelfSort.naturalCompare("img9", "img10") < 0);
        check("plain letters work", ShelfSort.naturalCompare("apple", "banana") < 0);
        check("case is ignored", ShelfSort.naturalCompare("Apple", "banana") < 0 && ShelfSort.naturalCompare("apple", "Banana") < 0);
        check("a prefix comes first", ShelfSort.naturalCompare("a", "a1") < 0 && ShelfSort.naturalCompare("file", "file.txt") < 0);
        check("empty comes first", ShelfSort.naturalCompare("", "a") < 0 && ShelfSort.naturalCompare("a", "") > 0);
        check("digits come before letters", ShelfSort.naturalCompare("1", "a") < 0);
        check("two numbers in one name", ShelfSort.naturalCompare("s2e10", "s2e9") > 0 && ShelfSort.naturalCompare("s2e10", "s10e1") < 0);
        check("leading zeros do not change the value", ShelfSort.naturalCompare("a007", "a8") < 0 && ShelfSort.naturalCompare("a010", "a9") > 0);
        check("very long numbers do not overflow", ShelfSort.naturalCompare("n99999999999999999999", "n100000000000000000000") < 0);
        equal("identical names are equal", 0, ShelfSort.naturalCompare("same", "same"));
        check("names that differ only in case are still ordered", ShelfSort.naturalCompare("File", "file") != 0);
        check("names that differ only in leading zeros are still ordered", ShelfSort.naturalCompare("a01", "a1") != 0);

        // The order must be a real order: opposite results when swapped, and the same result
        // whatever order the list started in.
        String[] sample = {"a", "A", "a1", "a01", "a2", "a10", "b", "B2", "b10", "10", "9", "09", "", "file", "File.txt", "file2.txt", "file10.txt", "x-1", "x-2"};
        boolean symmetric = true;
        for (String x : sample) {
            for (String y : sample) {
                symmetric &= sign(ShelfSort.naturalCompare(x, y)) == -sign(ShelfSort.naturalCompare(y, x));
            }
        }
        check("swapping two names always reverses the result", symmetric);

        List<String> reference = new ArrayList<>(List.of(sample));
        reference.sort(ShelfSort::naturalCompare);
        boolean sameEveryTime = true;
        Random random = new Random(7);
        for (int round = 0; round < 50; round++) {
            List<String> shuffled = new ArrayList<>(List.of(sample));
            Collections.shuffle(shuffled, random);
            shuffled.sort(ShelfSort::naturalCompare);
            sameEveryTime &= shuffled.equals(reference);
        }
        check("sorting gives the same list however it started", sameEveryTime);

        // ---- sorting by name ----
        equal("sorts A to Z with numbers by value",
                List.of("a.txt", "file2.txt", "file10.txt", "Zebra.txt"),
                names(ShelfSort.byName(List.of(item("Zebra.txt"), item("file10.txt"), item("a.txt"), item("file2.txt")))));
        equal("pinned items stay on top and are sorted among themselves",
                List.of("*b", "*d", "a", "c"),
                names(ShelfSort.byName(List.of(pinned("d"), item("c"), pinned("b"), item("a")))));
        equal("sorting an empty shelf gives an empty shelf", List.of(), names(ShelfSort.byName(List.of())));
        equal("one item stays", List.of("only"), names(ShelfSort.byName(List.of(item("only")))));
        equal("same name in two folders is ordered by path",
                List.of("one/x.txt", "two/x.txt"),
                pathsOf(ShelfSort.byName(List.of(
                        new ShelfItem(Path.of("sort-test", "two", "x.txt")),
                        new ShelfItem(Path.of("sort-test", "one", "x.txt"))))));
        List<ShelfItem> original = new ArrayList<>(List.of(item("b"), item("a")));
        ShelfSort.byName(original);
        equal("the original list is not changed", List.of("b", "a"), names(original));
        equal("sorting twice changes nothing the second time",
                names(ShelfSort.byName(List.of(item("c"), item("a"), item("b")))),
                names(ShelfSort.byName(ShelfSort.byName(List.of(item("c"), item("a"), item("b"))))));

        // ---- sorting follows the display name, the way the list shows it ----
        ShelfItem zebraAsAlpha = new ShelfItem(Path.of("sort-test", "zebra.txt"), false, "Alpha");
        ShelfItem plainApple = item("apple.txt");
        ShelfItem mangoAsZulu = new ShelfItem(Path.of("sort-test", "mango.txt"), false, "Zulu");
        List<String> shown = new ArrayList<>();
        for (ShelfItem sorted : ShelfSort.byName(List.of(mangoAsZulu, plainApple, zebraAsAlpha))) {
            shown.add(sorted.getDisplayName());
        }
        equal("items are sorted by the name shown on screen", List.of("Alpha", "apple.txt", "Zulu"), shown);
        List<String> labelsKept = new ArrayList<>();
        for (ShelfItem sorted : ShelfSort.byName(List.of(mangoAsZulu, zebraAsAlpha))) {
            labelsKept.add(sorted.getLabel() + "/" + sorted.getName());
        }
        equal("sorting keeps each item's own name", List.of("Alpha/zebra.txt", "Zulu/mango.txt"), labelsKept);

        // ---- folders first (needs real folders) ----
        Path dir = Files.createTempDirectory("shelf-sort-test");
        ShelfItem folderB = new ShelfItem(Files.createDirectory(dir.resolve("b-folder")));
        ShelfItem folderA = new ShelfItem(Files.createDirectory(dir.resolve("z-folder")));
        ShelfItem fileA = new ShelfItem(Files.createFile(dir.resolve("a-file.txt")));
        ShelfItem fileC = new ShelfItem(Files.createFile(dir.resolve("c-file.txt")));
        equal("folders come first, each group A to Z",
                List.of("b-folder", "z-folder", "a-file.txt", "c-file.txt"),
                names(ShelfSort.foldersFirst(List.of(fileC, folderA, fileA, folderB))));
        equal("pinned stays on top even for a file, folders first inside each group",
                List.of("*c-file.txt", "b-folder", "z-folder", "a-file.txt"),
                names(ShelfSort.foldersFirst(List.of(fileA, folderA, new ShelfItem(fileC.getPath(), true), folderB))));

        // ---- missing items ----
        ShelfItem gone = new ShelfItem(dir.resolve("gone.txt"));
        equal("only items that no longer exist are reported, in order", List.of("gone.txt", "gone2.txt"),
                names(ShelfSort.missing(List.of(fileA, gone, folderA, new ShelfItem(dir.resolve("gone2.txt"))))));
        equal("nothing is missing when everything exists", List.of(), names(ShelfSort.missing(List.of(fileA, folderB))));
        equal("an empty shelf has nothing missing", List.of(), names(ShelfSort.missing(List.of())));

        TestRunner.finish();
    }

    private static List<String> pathsOf(List<ShelfItem> items) {
        List<String> result = new ArrayList<>();
        for (ShelfItem item : items) {
            Path p = item.getPath();
            result.add(p.getParent().getFileName() + "/" + p.getFileName());
        }
        return result;
    }
}
