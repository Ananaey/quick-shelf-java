package quickshelf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class ShelfStorageTest {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("shelf-storage-test");
        Path file = dir.resolve("nested").resolve("shelf.txt");
        ShelfStorage storage = new ShelfStorage(file);

        equal("missing file loads empty", List.of(), storage.load());

        Path a = dir.resolve("alpha.txt");
        Path b = dir.resolve("my docs").resolve("beta.txt");
        storage.save(List.of(a, b));
        check("save creates the folder", Files.exists(file));
        equal("round trip keeps paths and order", List.of(a, b), storage.load());
        check("no temp file left behind", !Files.exists(file.resolveSibling("shelf.txt.tmp")));

        storage.save(List.of(b));
        equal("save overwrites the old content", List.of(b), storage.load());
        storage.save(List.of());
        equal("an empty shelf round trips", List.of(), storage.load());

        // Unicode and spaces in names survive, since the file is UTF-8.
        Path unicode = dir.resolve("résumé – final.txt");
        storage.save(List.of(unicode));
        equal("unicode names round trip", List.of(unicode), storage.load());

        // A hand-edited file: comments, blank lines, indentation and a line that is not a path.
        String text = "# my shelf\n\n  " + a + "  \n\u0000bad\n" + b + "\n";
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        equal("comments, blanks and bad lines are skipped", List.of(a, b), storage.load());

        // A file that is not text at all is kept as .bad and the shelf starts empty.
        Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28, (byte) 0xFF, (byte) 0xFE});
        equal("corrupt file loads empty", List.of(), storage.load());
        check("corrupt file is kept as .bad", Files.exists(file.resolveSibling("shelf.txt.bad")));
        check("corrupt file no longer in the way", !Files.exists(file));
        storage.save(List.of(a));
        equal("saving works again after corruption", List.of(a), storage.load());

        // ---- pinned items ----
        storage.saveEntries(List.of(
                new ShelfStorage.Entry(a, true), new ShelfStorage.Entry(b, false), new ShelfStorage.Entry(unicode, true)));
        equal("pinned flags round trip", List.of(
                new ShelfStorage.Entry(a, true), new ShelfStorage.Entry(b, false), new ShelfStorage.Entry(unicode, true)),
                storage.loadEntries());
        equal("load() still returns just the paths, in order", List.of(a, b, unicode), storage.load());
        check("the file shows pinned lines with the marker",
                Files.readAllLines(file, StandardCharsets.UTF_8).contains("[pinned] " + a));

        // an older file, written before pinning existed, loads as all unpinned
        Files.write(file, ("# Quick Shelf - one path per line\n" + a + "\n" + b + "\n").getBytes(StandardCharsets.UTF_8));
        equal("an older file loads as unpinned",
                List.of(new ShelfStorage.Entry(a, false), new ShelfStorage.Entry(b, false)), storage.loadEntries());

        // hand-edited: extra spaces after the marker, and a marker with no path
        Files.write(file, ("[pinned]    " + a + "\n[pinned]\n  [pinned] " + b + "\n").getBytes(StandardCharsets.UTF_8));
        equal("extra spaces after the marker are fine and a marker without a path is skipped",
                List.of(new ShelfStorage.Entry(a, true), new ShelfStorage.Entry(b, true)), storage.loadEntries());

        storage.save(List.of(a, b));
        equal("save(paths) writes everything unpinned",
                List.of(new ShelfStorage.Entry(a, false), new ShelfStorage.Entry(b, false)), storage.loadEntries());

        // ---- display names ----
        storage.saveEntries(List.of(
                new ShelfStorage.Entry(a, false, "Budget"), new ShelfStorage.Entry(b, true, "Photos from trip")));
        equal("display names round trip", List.of(
                new ShelfStorage.Entry(a, false, "Budget"), new ShelfStorage.Entry(b, true, "Photos from trip")),
                storage.loadEntries());
        List<String> written = Files.readAllLines(file, StandardCharsets.UTF_8);
        check("the file has the path, a tab, then the name", written.contains(a + "\tBudget"));
        check("and the pin marker in front when pinned", written.contains("[pinned] " + b + "\tPhotos from trip"));
        equal("load() still gives just the paths", List.of(a, b), storage.load());

        storage.saveEntries(List.of(new ShelfStorage.Entry(unicode, false, "Café – 日本語")));
        equal("a name with accents and other scripts survives", "Café – 日本語",
                storage.loadEntries().get(0).label);

        equal("an entry tidies its name, so nothing can break the file format", "x y",
                new ShelfStorage.Entry(a, false, "x\ty").label);
        storage.saveEntries(List.of(new ShelfStorage.Entry(a, false, "line one\nline two\ttabbed")));
        equal("a name with a line break or tab is saved on one line", 2, Files.readAllLines(file, StandardCharsets.UTF_8).size());
        equal("and comes back tidied", "line one line two tabbed", storage.loadEntries().get(0).label);

        // hand-edited: extra spaces around the name, an empty name after a tab, and no name at all
        Files.write(file, (a + "\t  Spaced   out \n" + b + "\t\n" + unicode + "\n").getBytes(StandardCharsets.UTF_8));
        List<ShelfStorage.Entry> edited = storage.loadEntries();
        equal("three lines are read", 3, edited.size());
        equal("spaces around a name are tidied", "Spaced out", edited.get(0).label);
        equal("a tab with nothing after it means no name", "", edited.get(1).label);
        equal("the path of that line is intact", b, edited.get(1).path);
        equal("a line with no tab has no name", "", edited.get(2).label);

        // older files (no names, no tabs) are read exactly as before
        Files.write(file, ("# older file\n" + a + "\n[pinned] " + b + "\n").getBytes(StandardCharsets.UTF_8));
        equal("an older file loads with no names", List.of(new ShelfStorage.Entry(a, false), new ShelfStorage.Entry(b, true)),
                storage.loadEntries());
        check("entries with and without a name are different", !new ShelfStorage.Entry(a, false, "x").equals(new ShelfStorage.Entry(a, false)));
        equal("entries with the same name are equal", new ShelfStorage.Entry(a, true, "x"), new ShelfStorage.Entry(a, true, " x "));

        Path def = ShelfStorage.defaultPath();
        equal("default file name", "shelf.txt", def.getFileName().toString());
        equal("default folder name", "QuickShelf", def.getParent().getFileName().toString());

        TestRunner.finish();
    }
}
