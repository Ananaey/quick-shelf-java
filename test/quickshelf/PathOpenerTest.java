package quickshelf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class PathOpenerTest {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("opener-test");
        Path file = Files.createFile(dir.resolve("a.txt"));
        Path missing = dir.resolve("gone.txt");

        List<File> opened = new ArrayList<>();
        PathOpener opener = new PathOpener(opened::add);

        check("opens an existing file", opener.open(file));
        equal("launcher got that file", List.of(file.toFile()), opened);

        opened.clear();
        check("opens a folder", opener.open(dir));
        equal("launcher got the folder", List.of(dir.toFile()), opened);

        opened.clear();
        check("missing path returns false", !opener.open(missing));
        equal("missing path never reaches the launcher", 0, opened.size());
        check("null path returns false", !opener.open(null));

        opened.clear();
        check("show-in-folder succeeds", opener.openParent(file));
        equal("show-in-folder opens the containing folder", List.of(dir.toFile()), opened);
        check("show-in-folder for null returns false", !opener.openParent(null));

        PathOpener failing = new PathOpener(f -> {
            throw new IOException("no app for this file type");
        });
        check("launcher IOException returns false", !failing.open(file));

        PathOpener crashing = new PathOpener(f -> {
            throw new IllegalArgumentException("bad file");
        });
        check("launcher RuntimeException returns false", !crashing.open(file));

        TestRunner.finish();
    }
}
