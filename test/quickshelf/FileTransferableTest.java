package quickshelf;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.List;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class FileTransferableTest {
    public static void main(String[] args) throws Exception {
        File a = new File("some-folder", "a.txt");
        File b = new File("some-folder", "b.txt");
        FileTransferable one = new FileTransferable(a);

        equal("offers exactly one format", 1, one.getTransferDataFlavors().length);
        check("the format is a file list", DataFlavor.javaFileListFlavor.equals(one.getTransferDataFlavors()[0]));
        check("a file list is supported", one.isDataFlavorSupported(DataFlavor.javaFileListFlavor));
        check("plain text is not supported", !one.isDataFlavorSupported(DataFlavor.stringFlavor));
        check("an image is not supported", !one.isDataFlavorSupported(DataFlavor.imageFlavor));
        check("null is not supported", !one.isDataFlavorSupported(null));

        equal("the data is the file", List.of(a), one.getTransferData(DataFlavor.javaFileListFlavor));
        equal("several files keep their order", List.of(a, b),
                new FileTransferable(a, b).getTransferData(DataFlavor.javaFileListFlavor));
        equal("no files gives an empty list", List.of(),
                new FileTransferable().getTransferData(DataFlavor.javaFileListFlavor));

        boolean threw = false;
        try {
            one.getTransferData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException e) {
            threw = true;
        }
        check("asking for another format is refused", threw);

        // The list handed out cannot be changed by whoever receives it.
        boolean modified = true;
        try {
            @SuppressWarnings("unchecked")
            List<File> data = (List<File>) one.getTransferData(DataFlavor.javaFileListFlavor);
            data.add(b);
        } catch (UnsupportedOperationException e) {
            modified = false;
        }
        check("the receiver cannot change the list", !modified);

        // Changing the flavour array it returned does not change what it offers.
        one.getTransferDataFlavors()[0] = DataFlavor.stringFlavor;
        check("the offered format cannot be swapped from outside",
                DataFlavor.javaFileListFlavor.equals(one.getTransferDataFlavors()[0]));

        TestRunner.finish();
    }
}
