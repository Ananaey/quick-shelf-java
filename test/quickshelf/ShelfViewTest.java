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

/** Uses the real list and drop handler, but never opens a window, so it runs without a display. */
public class ShelfViewTest {
    private static List<String> names(ShelfView view) {
        List<String> result = new ArrayList<>();
        view.items().forEach(item -> result.add(item.getName()));
        return result;
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("shelf-view-test");
        File a = Files.createFile(dir.resolve("alpha.txt")).toFile();
        File b = Files.createFile(dir.resolve("beta.txt")).toFile();
        File folder = Files.createDirectory(dir.resolve("docs")).toFile();
        File missing = dir.resolve("gone.txt").toFile();

        ShelfView view = new ShelfView();
        equal("starts empty", 0, view.items().size());

        equal("first drop adds three", 3, view.addFiles(Arrays.asList(a, b, folder)));
        equal("items keep drop order", List.of("alpha.txt", "beta.txt", "docs"), names(view));

        equal("dropping the same files again adds none", 0, view.addFiles(Arrays.asList(a, b)));
        equal("missing files are ignored", 0, view.addFiles(List.of(missing)));
        equal("null entries are ignored", 0, view.addFiles(Arrays.asList((File) null)));
        equal("list unchanged by rejected drops", 3, view.items().size());

        File c = Files.createFile(dir.resolve("gamma.txt")).toFile();
        equal("a mixed drop adds only the new file", 1, view.addFiles(Arrays.asList(a, c, missing)));
        equal("new item goes to the end", "gamma.txt", names(view).get(3));

        check("folder is recognised", view.items().get(2).isDirectory());

        check("drop works on the whole view, not only the rows",
                view.getTransferHandler() != null
                        && view.getViewport().getTransferHandler() != null
                        && view.getViewport().getView() instanceof javax.swing.JComponent
                        && ((javax.swing.JComponent) view.getViewport().getView()).getTransferHandler() != null);

        TestRunner.finish();
    }
}
