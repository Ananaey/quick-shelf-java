package quickshelf;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.List;

/**
 * What gets handed to another program when a shelf item is dragged out: a list of files, in the
 * format Explorer, mail clients and chat windows all understand.
 */
public final class FileTransferable implements Transferable {
    private static final DataFlavor[] FLAVORS = {DataFlavor.javaFileListFlavor};

    private final List<File> files;

    public FileTransferable(File... files) {
        this.files = List.of(files);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return FLAVORS.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.javaFileListFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return files;
    }
}
