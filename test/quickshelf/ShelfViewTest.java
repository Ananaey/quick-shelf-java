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
        equal("four items now", 4, view.items().size());
        equal("new item goes to the end", "gamma.txt", names(view).get(3));

        check("folder is recognised", view.items().get(2).isDirectory());

        check("drop works on the whole view, not only the rows",
                view.getTransferHandler() != null
                        && view.getViewport().getTransferHandler() != null
                        && view.getViewport().getView() instanceof javax.swing.JComponent
                        && ((javax.swing.JComponent) view.getViewport().getView()).getTransferHandler() != null);

        // ---- open, move, remove ----
        List<File> launched = new ArrayList<>();
        view.setOpener(new PathOpener(launched::add));

        check("open at a valid row", view.openAt(0));
        equal("opened the first item", a.toPath().toAbsolutePath().normalize(), launched.get(0).toPath());
        check("open at a bad row is false", !view.openAt(99) && !view.openAt(-1));

        Files.delete(c.toPath());
        check("opening a deleted file returns false", !view.openAt(3));
        equal("deleted file never launched", 1, launched.size());
        equal("deleted file stays on the shelf", 4, view.items().size());

        // order is now alpha, beta, docs, gamma
        equal("move down returns the new index", 1, view.moveItem(0, 1));
        equal("order after move down", List.of("beta.txt", "alpha.txt", "docs", "gamma.txt"), names(view));
        equal("move up returns the new index", 0, view.moveItem(1, -1));
        equal("order after move up", List.of("alpha.txt", "beta.txt", "docs", "gamma.txt"), names(view));
        equal("first item cannot move up", 0, view.moveItem(0, -1));
        equal("last item cannot move down", 3, view.moveItem(3, 1));
        equal("bad index is left alone", 99, view.moveItem(99, 1));
        equal("order unchanged by no-op moves", List.of("alpha.txt", "beta.txt", "docs", "gamma.txt"), names(view));

        check("remove a middle item", view.removeAt(1));
        equal("order after remove", List.of("alpha.txt", "docs", "gamma.txt"), names(view));
        check("remove at a bad index is false", !view.removeAt(9) && !view.removeAt(-1));
        check("remove the rest", view.removeAt(0) && view.removeAt(0) && view.removeAt(0));
        equal("shelf is empty again", 0, view.items().size());
        check("removing from an empty shelf is false", !view.removeAt(0));
        equal("a removed file can be added again", 1, view.addFiles(List.of(a)));

        TestRunner.finish();
    }
}
