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

        TestRunner.finish();
    }
}
