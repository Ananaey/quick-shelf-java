package quickshelf;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class ClipboardPathsTest {
    private static final boolean WINDOWS = File.separatorChar == '\\';

    /** An absolute path that exists on this kind of system, written as text. */
    private static String abs(String name) {
        return (WINDOWS ? "C:\\Users\\Someone\\" : "/home/someone/") + name;
    }

    private static Path p(String name) {
        return Path.of(abs(name));
    }

    /** Clipboard contents that fail in the way a busy or odd clipboard can. */
    private static Transferable broken(Exception problem) {
        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return true;
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                if (problem instanceof IOException) {
                    throw (IOException) problem;
                }
                if (problem instanceof UnsupportedFlavorException) {
                    throw (UnsupportedFlavorException) problem;
                }
                throw (RuntimeException) problem;
            }
        };
    }

    public static void main(String[] args) {
        // ---- text ----
        equal("one plain path", List.of(p("a.txt")), ClipboardPaths.fromText(abs("a.txt")));
        equal("two lines, in order", List.of(p("a.txt"), p("b.txt")),
                ClipboardPaths.fromText(abs("a.txt") + "\n" + abs("b.txt")));
        equal("Windows line endings", List.of(p("a.txt"), p("b.txt")),
                ClipboardPaths.fromText(abs("a.txt") + "\r\n" + abs("b.txt") + "\r\n"));
        equal("old Mac line endings", List.of(p("a.txt"), p("b.txt")),
                ClipboardPaths.fromText(abs("a.txt") + "\r" + abs("b.txt")));
        equal("Copy as path puts each path in double quotes", List.of(p("my file.txt"), p("b.txt")),
                ClipboardPaths.fromText("\"" + abs("my file.txt") + "\"\r\n\"" + abs("b.txt") + "\""));
        equal("single quotes are removed too", List.of(p("a.txt")), ClipboardPaths.fromText("'" + abs("a.txt") + "'"));
        equal("spaces around a line are ignored", List.of(p("a.txt")), ClipboardPaths.fromText("   " + abs("a.txt") + "   "));
        equal("spaces inside quotes are ignored too", List.of(p("a.txt")), ClipboardPaths.fromText("\"  " + abs("a.txt") + " \""));
        equal("blank lines are skipped", List.of(p("a.txt"), p("b.txt")),
                ClipboardPaths.fromText("\n\n" + abs("a.txt") + "\n   \n" + abs("b.txt") + "\n\n"));
        equal("the same path twice is passed on twice (the shelf drops duplicates)", 2,
                ClipboardPaths.fromText(abs("a.txt") + "\n" + abs("a.txt")).size());

        equal("a relative path is not accepted", List.of(), ClipboardPaths.fromText("notes.txt"));
        equal("neither is a relative path with folders", List.of(), ClipboardPaths.fromText("docs" + File.separator + "notes.txt"));
        equal("ordinary sentences are skipped but real paths among them are kept", List.of(p("a.txt")),
                ClipboardPaths.fromText("Here are the files I mentioned:\n" + abs("a.txt") + "\nThanks!"));
        equal("an empty string gives nothing", List.of(), ClipboardPaths.fromText(""));
        equal("null gives nothing", List.of(), ClipboardPaths.fromText(null));
        equal("only blanks gives nothing", List.of(), ClipboardPaths.fromText(" \n \r\n\t"));
        equal("a lone quote is not a path", List.of(), ClipboardPaths.fromText("\""));
        equal("empty quotes are not a path", List.of(), ClipboardPaths.fromText("\"\""));
        equal("a NUL character makes a line unusable, not the whole paste", List.of(p("good.txt")),
                ClipboardPaths.fromText(abs("bad\u0000.txt") + "\n" + abs("good.txt")));

        // file: links
        String link = WINDOWS ? "file:///C:/Users/Someone/linked%20file.txt" : "file:///home/someone/linked%20file.txt";
        equal("a file: link is understood", List.of(p("linked file.txt")), ClipboardPaths.fromText(link));
        equal("and its scheme may be in capitals", List.of(p("linked file.txt")), ClipboardPaths.fromText(link.replace("file:", "FILE:")));
        equal("a broken file: link is skipped", List.of(), ClipboardPaths.fromText("file:///%%%not-a-link"));
        equal("a link to nowhere useful is skipped", List.of(), ClipboardPaths.fromText("file:relative.txt"));

        // a pasted wall of text is limited
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < ClipboardPaths.MAX_LINES + 500; i++) {
            many.append(abs("f" + i + ".txt")).append('\n');
        }
        equal("only the first lines of a huge paste are read", ClipboardPaths.MAX_LINES, ClipboardPaths.fromText(many.toString()).size());

        // ---- what is on the clipboard ----
        File f1 = p("one.txt").toFile();
        File f2 = p("two.txt").toFile();
        equal("copied files come through, in order", List.of(p("one.txt"), p("two.txt")),
                ClipboardPaths.fromTransferable(new FileTransferable(f1, f2)));
        equal("copied text is read as paths", List.of(p("a.txt")),
                ClipboardPaths.fromTransferable(new StringSelection(abs("a.txt"))));
        equal("copied text without any path gives nothing", List.of(),
                ClipboardPaths.fromTransferable(new StringSelection("just words")));
        equal("an empty clipboard gives nothing", List.of(), ClipboardPaths.fromTransferable(null));
        equal("an empty file list gives nothing", List.of(), ClipboardPaths.fromTransferable(new FileTransferable()));

        equal("a clipboard that throws IOException is treated as empty", List.of(),
                ClipboardPaths.fromTransferable(broken(new IOException("busy"))));
        equal("one that throws UnsupportedFlavorException too", List.of(),
                ClipboardPaths.fromTransferable(broken(new UnsupportedFlavorException(DataFlavor.stringFlavor))));
        equal("and one that throws a runtime exception", List.of(),
                ClipboardPaths.fromTransferable(broken(new IllegalStateException("clipboard unavailable"))));

        check("when both are offered, the files win over the text", ClipboardPaths.fromTransferable(new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return true;
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return flavor.equals(DataFlavor.javaFileListFlavor) ? List.of(f1) : abs("from-text.txt");
            }
        }).equals(List.of(p("one.txt"))));

        TestRunner.finish();
    }
}
