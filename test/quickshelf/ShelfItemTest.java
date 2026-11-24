package quickshelf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class ShelfItemTest {
    private static List<String> names(List<ShelfItem> items) {
        List<String> result = new ArrayList<>();
        items.forEach(item -> result.add(item.getName()));
        return result;
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("shelf-item-test");
        Path file = Files.createFile(dir.resolve("notes.txt"));
        Path folder = Files.createDirectory(dir.resolve("docs"));
        Path missing = dir.resolve("gone.txt");

        ShelfItem fileItem = new ShelfItem(file);
        equal("file name", "notes.txt", fileItem.getName());
        check("file is not a directory", !fileItem.isDirectory());
        check("folder is a directory", new ShelfItem(folder).isDirectory());
        check("existing file exists", fileItem.exists());
        check("missing file does not exist", !new ShelfItem(missing).exists());

        Path root = dir.getRoot();
        equal("drive root falls back to its full path", root.toString(), new ShelfItem(root).getName());

        equal("same path is equal", new ShelfItem(file), new ShelfItem(file));
        equal("equal items have equal hash codes",
                new ShelfItem(file).hashCode(), new ShelfItem(file).hashCode());
        equal("path with .. is normalised",
                new ShelfItem(file), new ShelfItem(dir.resolve("docs").resolve("..").resolve("notes.txt")));

        if (File.separatorChar == '\\') {
            Path upper = Path.of(file.toString().toUpperCase());
            equal("Windows paths ignore case", new ShelfItem(file), new ShelfItem(upper));
        }

        List<ShelfItem> added = ShelfItem.newItems(Arrays.asList(file, folder), List.of());
        equal("adds files and folders in drop order", List.of("notes.txt", "docs"), names(added));

        equal("skips missing paths", List.of(), names(ShelfItem.newItems(List.of(missing), List.of())));
        equal("skips null entries", List.of("notes.txt"),
                names(ShelfItem.newItems(Arrays.asList(null, file), List.of())));
        equal("skips repeats within one drop", 1, ShelfItem.newItems(List.of(file, file), List.of()).size());
        equal("skips items already on the shelf", List.of(),
                names(ShelfItem.newItems(List.of(file), List.of(fileItem))));
        equal("empty drop adds nothing", 0, ShelfItem.newItems(List.of(), List.of()).size());

        // ---- display names ----
        ShelfItem plain = new ShelfItem(file);
        check("no display name at first", !plain.hasLabel() && plain.getLabel().isEmpty());
        equal("the display name is the file name", "notes.txt", plain.getDisplayName());
        ShelfItem named = plain.withLabel("  My   notes ");
        equal("a display name is tidied", "My notes", named.getLabel());
        equal("and shown instead of the file name", "My notes", named.getDisplayName());
        equal("the real file name is still there", "notes.txt", named.getName());
        equal("it is the same item whatever its display name", plain, named);
        equal("with the same hash code", plain.hashCode(), named.hashCode());
        check("the same name again returns the same object", named.withLabel("My notes") == named);
        check("an empty name clears it", !named.withLabel("").hasLabel() && named.withLabel("").getDisplayName().equals("notes.txt"));
        check("null clears it too", !named.withLabel(null).hasLabel());
        check("so does a name of only spaces and tabs", !named.withLabel("   \t ").hasLabel());
        equal("pinning keeps the display name", "My notes", named.withPinned(true).getLabel());
        check("and naming keeps the pin", new ShelfItem(file, true).withLabel("x").isPinned());
        equal("the constructor tidies the name too", "a b", new ShelfItem(file, false, "  a   b ").getLabel());

        equal("tabs and line breaks become spaces", "a b c", ShelfItem.cleanLabel("a\tb\nc"));
        equal("other control characters become spaces", "a b", ShelfItem.cleanLabel("a\u0000b"));
        equal("a carriage return cannot reach the save file", "a b", ShelfItem.cleanLabel("a\r\nb"));
        equal("runs of spaces become one", "a b", ShelfItem.cleanLabel("a      b"));
        equal("a name made only of control characters is empty", "", ShelfItem.cleanLabel("\u0001\u0002\t\n"));
        equal("accents and other scripts are kept", "Café 日本語", ShelfItem.cleanLabel("Café 日本語"));
        equal("long names are cut to the limit", ShelfItem.MAX_LABEL, ShelfItem.cleanLabel("x".repeat(500)).length());
        equal("a cut that ends on a space leaves no trailing space", 79,
                ShelfItem.cleanLabel("a".repeat(79) + " zzz").length());
        equal("a cut never splits an emoji in half", 79,
                ShelfItem.cleanLabel("a".repeat(79) + "😀").length());
        equal("an emoji that fits exactly is kept whole (78 letters + 2 chars = 80)", 80,
                ShelfItem.cleanLabel("a".repeat(78) + "😀").length());

        TestRunner.finish();
    }
}
