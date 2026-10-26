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
        return names(view.items());
    }

    private static List<String> names(List<ShelfItem> items) {
        List<String> result = new ArrayList<>();
        items.forEach(item -> result.add(item.getName()));
        return result;
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("shelf-view-test");
        File a = Files.createFile(dir.resolve("alpha.txt")).toFile();
        File b = Files.createFile(dir.resolve("beta.txt")).toFile();
        File folder = Files.createDirectory(dir.resolve("docs")).toFile();
        File missing = dir.resolve("gone.txt").toFile();

        Path store = dir.resolve("store").resolve("shelf.txt");
        ShelfView view = new ShelfView(store);
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

        // ---- saving between runs: a second view on the same file is a "restart" ----
        File d = Files.createFile(dir.resolve("delta.txt")).toFile();
        File e = Files.createFile(dir.resolve("epsilon.txt")).toFile();
        view.addFiles(Arrays.asList(d, e));
        view.moveItem(2, -1); // alpha, epsilon, delta
        equal("order before restart", List.of("alpha.txt", "epsilon.txt", "delta.txt"), names(view));

        ShelfView restarted = new ShelfView(store);
        equal("a restart restores the same items in the same order",
                List.of("alpha.txt", "epsilon.txt", "delta.txt"), names(restarted));

        restarted.removeAt(1);
        equal("a removal is saved", List.of("alpha.txt", "delta.txt"), names(new ShelfView(store)));

        Files.delete(d.toPath());
        ShelfView afterDelete = new ShelfView(store);
        equal("a file deleted while closed stays listed", List.of("alpha.txt", "delta.txt"), names(afterDelete));
        check("and shows as missing", !afterDelete.items().get(1).exists());

        equal("a drop with only duplicates saves nothing new", 0, afterDelete.addFiles(List.of(a)));
        equal("no duplicate rows after reload", 2, new ShelfView(store).items().size());

        ShelfView unsaved = new ShelfView(dir.resolve("never-saved").resolve("shelf.txt"));
        equal("a fresh location starts empty", 0, unsaved.items().size());

        // ---- search ----
        Path store2 = dir.resolve("search-store").resolve("shelf.txt");
        ShelfView s = new ShelfView(store2);
        File finalReport = Files.createFile(dir.resolve("report-final.txt")).toFile();
        File budget = Files.createFile(dir.resolve("budget.txt")).toFile();
        File draftReport = Files.createFile(dir.resolve("report-draft.txt")).toFile();
        s.addFiles(Arrays.asList(finalReport, budget, draftReport));
        equal("no search shows everything", 3, s.visibleItems().size());

        s.setQuery("report");
        equal("search keeps only matches", 2, s.visibleItems().size());
        equal("search keeps their order", "report-final.txt", s.visibleItems().get(0).getName());
        equal("search never changes the full shelf", 3, s.items().size());
        equal("search never changes what is saved", 3, new ShelfView(store2).items().size());
        check("searching is reported", s.isSearching());

        s.setQuery("zzz");
        equal("a search with no matches shows nothing", 0, s.visibleItems().size());
        check("actions on an empty result are safe", !s.openAt(0) && !s.removeAt(0) && s.moveItem(0, 1) == 0);
        s.setQuery("   ");
        equal("a blank search shows everything again", 3, s.visibleItems().size());
        check("a blank search is not searching", !s.isSearching());
        s.setQuery(null);
        equal("a null search is treated as blank", 3, s.visibleItems().size());

        s.setQuery("draft");
        equal("one match", List.of("report-draft.txt"), names(s.visibleItems()));
        equal("moving is blocked while searching", 0, s.moveItem(0, 1));
        equal("blocked move changes nothing", List.of("report-final.txt", "budget.txt", "report-draft.txt"),
                names(s.items()));

        List<File> opened2 = new ArrayList<>();
        s.setOpener(new PathOpener(opened2::add));
        check("open acts on the displayed row", s.openAt(0));
        equal("it opened the matching file, not the first on the shelf",
                draftReport.toPath().toAbsolutePath().normalize(), opened2.get(0).toPath());

        check("remove acts on the displayed row", s.removeAt(0));
        equal("the right item was removed", List.of("report-final.txt", "budget.txt"), names(s.items()));
        equal("the removal is saved", 2, new ShelfView(store2).items().size());
        equal("the search still applies after a removal", 0, s.visibleItems().size());

        s.setQuery("budget");
        File extra = Files.createFile(dir.resolve("extra.txt")).toFile();
        equal("a drop while searching still adds", 1, s.addFiles(List.of(extra)));
        equal("the new item is on the shelf", 3, s.items().size());
        equal("but is hidden because it does not match", List.of("budget.txt"), names(s.visibleItems()));
        s.setQuery("");
        equal("clearing the search reveals it", 3, s.visibleItems().size());

        // moving works again once the search is cleared
        equal("move works when not searching", 1, s.moveItem(0, 1));
        equal("order after the move", List.of("budget.txt", "report-final.txt", "extra.txt"), names(s.items()));

        s.setTheme(Theme.LIGHT);
        s.setTheme(Theme.DARK);
        equal("switching theme keeps the items", 3, s.items().size());

        TestRunner.finish();
    }
}
