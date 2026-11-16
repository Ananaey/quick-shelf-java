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
    /** The text of each menu entry, with " (off)" after the ones that are greyed out. */
    private static List<String> menuLabels(javax.swing.JPopupMenu menu) {
        List<String> labels = new ArrayList<>();
        for (java.awt.Component part : menu.getComponents()) {
            if (part instanceof javax.swing.JMenuItem) {
                javax.swing.JMenuItem entry = (javax.swing.JMenuItem) part;
                labels.add(entry.getText() + (entry.isEnabled() ? "" : " (off)"));
            }
        }
        return labels;
    }

    /** Clicks the menu entry with exactly this text. */
    private static void clickMenuItem(javax.swing.JPopupMenu menu, String text) {
        for (java.awt.Component part : menu.getComponents()) {
            if (part instanceof javax.swing.JMenuItem && ((javax.swing.JMenuItem) part).getText().equals(text)) {
                ((javax.swing.JMenuItem) part).doClick(0);
                return;
            }
        }
        throw new IllegalStateException("no menu entry called " + text);
    }

    private static List<String> names(ShelfView view) {
        return names(view.items());
    }

    private static List<String> names(List<ShelfItem> items) {
        List<String> result = new ArrayList<>();
        items.forEach(item -> result.add(item.getName()));
        return result;
    }

    public static void main(String[] args) throws Exception {
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

        // ---- pinning ----  (shelf is now: budget, report-final, extra)
        Path store3 = dir.resolve("pin-store").resolve("shelf.txt");
        ShelfView p = new ShelfView(store3);
        File one = Files.createFile(dir.resolve("one.txt")).toFile();
        File two = Files.createFile(dir.resolve("two.txt")).toFile();
        File three = Files.createFile(dir.resolve("three.txt")).toFile();
        File four = Files.createFile(dir.resolve("four.txt")).toFile();
        p.addFiles(Arrays.asList(one, two, three, four));
        equal("nothing is pinned at first", List.of("one.txt", "two.txt", "three.txt", "four.txt"), names(p));
        check("row 2 is not pinned", !p.isPinnedAt(2));

        check("pinning works", p.togglePinAt(2));
        equal("a pinned item goes to the top", List.of("three.txt", "one.txt", "two.txt", "four.txt"), names(p));
        check("it is pinned", p.isPinnedAt(0));
        check("the others are not", !p.isPinnedAt(1) && !p.isPinnedAt(3));

        check("pin a second one", p.togglePinAt(3));
        equal("the second pin goes after the first",
                List.of("three.txt", "four.txt", "one.txt", "two.txt"), names(p));

        equal("a pinned item cannot move down past the pinned group", 1, p.moveItem(1, 1));
        equal("a pinned item can move up inside its group", 0, p.moveItem(1, -1));
        equal("the order after moving", List.of("four.txt", "three.txt", "one.txt", "two.txt"), names(p));
        equal("an unpinned item cannot move up into the pinned group", 2, p.moveItem(2, -1));
        equal("an unpinned item can move down inside its group", 3, p.moveItem(2, 1));
        equal("the order after that", List.of("four.txt", "three.txt", "two.txt", "one.txt"), names(p));

        equal("pins are saved: a restart keeps the pinned items first and in order",
                List.of("four.txt", "three.txt", "two.txt", "one.txt"), names(new ShelfView(store3)));
        ShelfView reloaded = new ShelfView(store3);
        check("and still pinned after a restart", reloaded.isPinnedAt(0) && reloaded.isPinnedAt(1) && !reloaded.isPinnedAt(2));

        check("unpin the first pinned item", p.togglePinAt(0));
        equal("an unpinned item goes to the top of the unpinned group",
                List.of("three.txt", "four.txt", "two.txt", "one.txt"), names(p));
        check("and is no longer pinned", !p.isPinnedAt(1) && p.isPinnedAt(0));

        // pinning from a search result acts on the item that is displayed
        p.setQuery("one");
        equal("search shows one row", List.of("one.txt"), names(p.visibleItems()));
        check("pin the displayed row", p.togglePinAt(0));
        p.setQuery("");
        equal("the searched item was the one pinned", List.of("three.txt", "one.txt", "four.txt", "two.txt"), names(p));
        check("pin on a bad row is false", !p.togglePinAt(9) && !p.togglePinAt(-1) && !p.isPinnedAt(9));

        // a hand-edited file with a pinned item listed late is tidied on load
        Path store4 = dir.resolve("hand-edited").resolve("shelf.txt");
        Files.createDirectories(store4.getParent());
        Files.write(store4, ("# edited\n" + one + "\n[pinned] " + two + "\n" + three + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ShelfView tidy = new ShelfView(store4);
        equal("pinned items come first after loading", List.of("two.txt", "one.txt", "three.txt"), names(tidy));
        equal("an item removed while pinned is gone", true, tidy.removeAt(0));
        equal("the rest is intact", List.of("one.txt", "three.txt"), names(tidy));

        // ---- dragging out and copying the path ----
        ShelfView out = new ShelfView(dir.resolve("drag-store").resolve("shelf.txt"));
        File keep = Files.createFile(dir.resolve("keep.txt")).toFile();
        File vanish = Files.createFile(dir.resolve("vanish.txt")).toFile();
        out.addFiles(Arrays.asList(keep, vanish));

        equal("dragging out is switched on whenever there is a display",
                !java.awt.GraphicsEnvironment.isHeadless(), out.isDragEnabled());
        equal("dragging out only copies, it can never move a file", java.awt.dnd.DnDConstants.ACTION_COPY, ShelfView.DRAG_ACTIONS);

        java.awt.datatransfer.Transferable data = out.exportItemAt(0);
        check("a normal item can be dragged out", data != null);
        equal("it is handed over as a file list", true,
                data.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor));
        @SuppressWarnings("unchecked")
        List<File> handedOver = (List<File>) data.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
        equal("exactly one file is handed over", 1, handedOver.size());
        equal("and it is the right one", keep.toPath().toAbsolutePath().normalize(), handedOver.get(0).toPath());

        check("a bad row cannot be dragged", out.exportItemAt(9) == null && out.exportItemAt(-1) == null);
        Files.delete(vanish.toPath());
        check("a file that no longer exists cannot be dragged", out.exportItemAt(1) == null);

        List<String> copied = new ArrayList<>();
        out.setClipboard(copied::add);
        check("copy path works", out.copyPathAt(0));
        equal("the full path is what gets copied",
                List.of(keep.toPath().toAbsolutePath().normalize().toString()), copied);
        check("copy path works for a file that is gone too", out.copyPathAt(1));
        equal("both were copied, one after the other", 2, copied.size());
        check("copy path on a bad row does nothing", !out.copyPathAt(9) && !out.copyPathAt(-1));
        equal("a bad row copied nothing", 2, copied.size());

        out.setQuery("keep");
        copied.clear();
        out.copyPathAt(0);
        check("copy path uses the displayed row while searching",
                copied.get(0).endsWith("keep.txt"));
        equal("a search that hides the other row also hides it from dragging", true, out.exportItemAt(1) == null);

        // ---- tidying up: sort, remove missing, undo ----
        Path tidyDir = Files.createDirectories(dir.resolve("tidy"));
        Path tidyStore = dir.resolve("tidy-store").resolve("shelf.txt");
        File t10 = Files.createFile(tidyDir.resolve("file10.txt")).toFile();
        File tFolder = Files.createDirectory(tidyDir.resolve("zdir")).toFile();
        File t2 = Files.createFile(tidyDir.resolve("file2.txt")).toFile();
        File tA = Files.createFile(tidyDir.resolve("a.txt")).toFile();
        ShelfView tidy2 = new ShelfView(tidyStore);

        check("a menu on an empty shelf has nothing to sort", menuLabels(tidy2.buildTidyMenu())
                .equals(List.of("Sort A-Z (off)", "Folders first (off)", "No missing items (off)", "Undo (off)")));
        tidy2.addFiles(Arrays.asList(t10, tFolder, t2, tA));
        equal("added in this order", List.of("file10.txt", "zdir", "file2.txt", "a.txt"), names(tidy2));
        equal("the menu offers sorting once there is something to sort",
                List.of("Sort A-Z", "Folders first", "No missing items (off)", "Undo (off)"), menuLabels(tidy2.buildTidyMenu()));

        check("sort A-Z changes the order", tidy2.sortByName());
        equal("numbers count by value and folders are sorted with the rest",
                List.of("a.txt", "file2.txt", "file10.txt", "zdir"), names(tidy2));
        equal("the new order was saved", List.of("a.txt", "file2.txt", "file10.txt", "zdir"),
                names(new ShelfView(tidyStore)));
        check("undo is now possible", tidy2.canUndoTidy());
        check("and offered in the menu", menuLabels(tidy2.buildTidyMenu()).contains("Undo"));
        check("sorting an already sorted shelf changes nothing", !tidy2.sortByName());
        check("and does not throw away the earlier undo", tidy2.canUndoTidy());

        check("undo puts the old order back", tidy2.undoTidy());
        equal("the original order is back", List.of("file10.txt", "zdir", "file2.txt", "a.txt"), names(tidy2));
        equal("and saved", List.of("file10.txt", "zdir", "file2.txt", "a.txt"), names(new ShelfView(tidyStore)));
        check("undo works once", !tidy2.canUndoTidy() && !tidy2.undoTidy());

        check("folders first", tidy2.sortFoldersFirst());
        equal("the folder moves up and the files follow A-Z",
                List.of("zdir", "a.txt", "file2.txt", "file10.txt"), names(tidy2));
        check("any other change makes the old undo stale", tidy2.canUndoTidy());
        File tNew = Files.createFile(tidyDir.resolve("new.txt")).toFile();
        tidy2.addFiles(List.of(tNew));
        check("adding a file forgets the undo", !tidy2.canUndoTidy());
        tidy2.sortByName();
        tidy2.togglePinAt(0); // pin a.txt
        check("so does pinning", !tidy2.canUndoTidy());

        // pinned items stay on top when sorting
        tidy2.sortFoldersFirst();
        equal("pinned a.txt stays on top while the rest is sorted",
                List.of("a.txt", "zdir", "file2.txt", "file10.txt", "new.txt"), names(tidy2));
        check("and is still pinned", tidy2.isPinnedAt(0) && !tidy2.isPinnedAt(1));

        // missing items
        Files.delete(t10.toPath());
        Files.delete(tNew.toPath());
        equal("two files are gone", 2, tidy2.missingCount());
        check("the menu says how many", menuLabels(tidy2.buildTidyMenu()).contains("Remove 2 missing items"));
        equal("remove takes exactly those", 2, tidy2.removeMissing());
        equal("the rest is kept in order", List.of("a.txt", "zdir", "file2.txt"), names(tidy2));
        check("a clean-up can be undone", tidy2.canUndoTidy());
        check("undo brings the missing items back", tidy2.undoTidy());
        equal("they are back where they were", List.of("a.txt", "zdir", "file2.txt", "file10.txt", "new.txt"), names(tidy2));
        equal("removing again takes the same two", 2, tidy2.removeMissing());
        equal("and now nothing is missing", 0, tidy2.missingCount());
        equal("so a second clean-up removes nothing", 0, tidy2.removeMissing());
        check("the menu says there is nothing missing", menuLabels(tidy2.buildTidyMenu()).contains("No missing items (off)"));

        Files.delete(t2.toPath());
        check("a single missing item is not plural", menuLabels(tidy2.buildTidyMenu()).contains("Remove 1 missing item"));

        // clicking the menu entries runs the actions
        tidy2.undoTidy(); // brings back file2, file10 and new, which are all missing by now
        equal("the shelf is back to five items", List.of("a.txt", "zdir", "file2.txt", "file10.txt", "new.txt"), names(tidy2));
        clickMenuItem(tidy2.buildTidyMenu(), "Sort A-Z");
        equal("clicking Sort A-Z in the menu sorts the shelf",
                List.of("a.txt", "file2.txt", "file10.txt", "new.txt", "zdir"), names(tidy2));
        check("and it can be undone", tidy2.canUndoTidy());
        clickMenuItem(tidy2.buildTidyMenu(), "Undo");
        equal("clicking Undo in the menu puts it back",
                List.of("a.txt", "zdir", "file2.txt", "file10.txt", "new.txt"), names(tidy2));
        check("and the undo is used up", !tidy2.canUndoTidy());

        // while a search hides rows, none of it applies (three items are missing: file2, file10, new)
        tidy2.setQuery("a.txt");
        check("sorting is off while searching", !tidy2.sortByName() && !tidy2.sortFoldersFirst());
        equal("so is removing missing items", 0, tidy2.removeMissing());
        equal("the menu says so", List.of("Sort A-Z (off)", "Folders first (off)", "Remove 3 missing items (off)", "Undo (off)"),
                menuLabels(tidy2.buildTidyMenu()));
        tidy2.setQuery("");

        // a one-item shelf has nothing to sort
        ShelfView single = new ShelfView(dir.resolve("single-store").resolve("shelf.txt"));
        single.addFiles(List.of(tA));
        check("one item cannot be sorted", menuLabels(single.buildTidyMenu()).get(0).equals("Sort A-Z (off)"));

        // ---- several items at once ----
        ShelfView multi = new ShelfView(dir.resolve("multi-store").resolve("shelf.txt"));
        List<File> made = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            made.add(Files.createFile(dir.resolve("m" + i + ".txt")).toFile());
        }
        multi.addFiles(made);
        List<File> launched3 = new ArrayList<>();
        multi.setOpener(new PathOpener(launched3::add));
        List<String> copied3 = new ArrayList<>();
        multi.setClipboard(copied3::add);

        equal("nothing is selected at first", 0, multi.selectedCount());
        equal("with nothing selected, open does nothing", 0, multi.openSelected());
        equal("with nothing selected, remove does nothing", 0, multi.removeSelected());
        equal("with nothing selected, copy does nothing", 0, multi.copyPathsOfSelected());
        check("with nothing selected, pin does nothing", !multi.togglePinSelected());
        check("with nothing selected, there is nothing to drag", multi.exportSelected() == null);
        equal("and nothing was changed", 5, multi.items().size());

        multi.selectRows(0, 2, 4);
        equal("three rows are selected", 3, multi.selectedCount());
        equal("open opens all three", 3, multi.openSelected());
        equal("in order", List.of("m1.txt", "m3.txt", "m5.txt"),
                List.of(launched3.get(0).getName(), launched3.get(1).getName(), launched3.get(2).getName()));

        equal("copy reports three paths", 3, multi.copyPathsOfSelected());
        equal("one clipboard entry", 1, copied3.size());
        equal("with one path per line", 3, copied3.get(0).split(System.lineSeparator()).length);
        check("the lines are the full paths of the selected items", copied3.get(0).startsWith(dir.toString())
                && copied3.get(0).contains("m3.txt") && !copied3.get(0).contains("m2.txt"));

        java.awt.datatransfer.Transferable dragged = multi.exportSelected();
        @SuppressWarnings("unchecked")
        List<File> draggedFiles = (List<File>) dragged.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
        equal("dragging out hands over all three files", 3, draggedFiles.size());
        Files.delete(made.get(2).toPath());
        @SuppressWarnings("unchecked")
        List<File> afterDelete2 = (List<File>) multi.exportSelected()
                .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
        equal("a file that is gone is left out of the drag", 2, afterDelete2.size());

        // pin several at once
        check("pin the selection", multi.togglePinSelected());
        equal("pinned items go first, in their order", List.of("m1.txt", "m3.txt", "m5.txt", "m2.txt", "m4.txt"), names(multi));
        check("all three are pinned", multi.isPinnedAt(0) && multi.isPinnedAt(1) && multi.isPinnedAt(2));
        check("the others are not", !multi.isPinnedAt(3) && !multi.isPinnedAt(4));
        equal("the three stay selected", 3, multi.selectedCount());
        check("unpin when all are pinned", multi.togglePinSelected());
        check("none are pinned now", !multi.isPinnedAt(0) && !multi.isPinnedAt(1) && !multi.isPinnedAt(2));
        equal("the order is kept", List.of("m1.txt", "m3.txt", "m5.txt", "m2.txt", "m4.txt"), names(multi));
        equal("still three selected", 3, multi.selectedCount());

        multi.selectRows(0, 3); // m1 and m2
        multi.togglePinSelected();
        equal("pinning two that were apart brings them together at the top",
                List.of("m1.txt", "m2.txt", "m3.txt", "m5.txt", "m4.txt"), names(multi));
        multi.selectRows(1, 2); // m2 is pinned, m3 is not
        multi.togglePinSelected();
        check("a mixed selection pins all of it", multi.isPinnedAt(0) && multi.isPinnedAt(1) && multi.isPinnedAt(2));
        check("and only that", !multi.isPinnedAt(3) && !multi.isPinnedAt(4));
        equal("pins were saved", true, new ShelfView(dir.resolve("multi-store").resolve("shelf.txt")).isPinnedAt(2));

        // remove several at once
        multi.selectRows(1, 3); // m2 and m5
        equal("remove takes both", 2, multi.removeSelected());
        equal("the right ones went", List.of("m1.txt", "m3.txt", "m4.txt"), names(multi));
        equal("the selection moved to a neighbour", 1, multi.selectedCount());
        equal("and the removal was saved", 3, new ShelfView(dir.resolve("multi-store").resolve("shelf.txt")).items().size());

        // a search limits the selection to what is shown
        multi.setQuery("m4");
        multi.selectRows(0);
        equal("remove acts on the displayed row only", 1, multi.removeSelected());
        multi.setQuery("");
        equal("so the hidden items are untouched", List.of("m1.txt", "m3.txt"), names(multi));

        // opening too many at once is refused
        ShelfView many = new ShelfView(dir.resolve("many-store").resolve("shelf.txt"));
        List<File> twelve = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            twelve.add(Files.createFile(dir.resolve("x" + i + ".txt")).toFile());
        }
        many.addFiles(twelve);
        List<File> launched4 = new ArrayList<>();
        many.setOpener(new PathOpener(launched4::add));
        many.selectRows(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        equal("twelve selected: nothing opens", 0, many.openSelected());
        equal("and nothing was launched", 0, launched4.size());
        many.selectRows(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        equal("ten selected: all open", 10, many.openSelected());
        equal("the limit is ten", 10, ShelfView.MAX_OPEN_AT_ONCE);

        Files.delete(twelve.get(1).toPath());
        launched4.clear();
        many.selectRows(0, 1, 2);
        equal("a missing file among several does not stop the others", 2, many.openSelected());
        equal("the two that exist were launched", 2, launched4.size());

        TestRunner.finish();
    }
}
