package quickshelf;

import java.awt.Color;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileSystemView;

/** The scrolling list of shelf items. Accepts files and folders dragged in from Explorer. */
public class ShelfView extends JScrollPane {
    private static final long serialVersionUID = 1L;
    private static final Color BACKGROUND = new Color(30, 30, 34);
    private static final Color TEXT = new Color(242, 242, 247);
    private static final Color SELECTED = new Color(10, 132, 255);

    private final DefaultListModel<ShelfItem> model = new DefaultListModel<>();
    private final JList<ShelfItem> list = new JList<>(model) {
        @Override
        public String getToolTipText(MouseEvent event) {
            int index = locationToIndex(event.getPoint());
            if (index < 0 || !getCellBounds(index, index).contains(event.getPoint())) {
                return null;
            }
            ShelfItem item = model.get(index);
            return item.exists() ? item.getPath().toString() : item.getPath() + "  (not found)";
        }
    };

    public ShelfView() {
        list.setBackground(BACKGROUND);
        list.setForeground(TEXT);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ItemRenderer());
        list.setFixedCellHeight(34);
        ToolTipManager.sharedInstance().registerComponent(list);

        setViewportView(list);
        setBorder(BorderFactory.createEmptyBorder());
        getViewport().setBackground(BACKGROUND);

        // Accept drops anywhere in the view, not just on rows (the list may be short or empty).
        TransferHandler handler = new FileDropHandler();
        list.setTransferHandler(handler);
        setTransferHandler(handler);
        getViewport().setTransferHandler(handler);
    }

    /** Adds files and folders to the shelf and returns how many were new. */
    public int addFiles(List<File> files) {
        List<Path> paths = new ArrayList<>();
        for (File file : files) {
            paths.add(file == null ? null : file.toPath());
        }
        List<ShelfItem> added = ShelfItem.newItems(paths, Collections.list(model.elements()));
        added.forEach(model::addElement);
        return added.size();
    }

    public List<ShelfItem> items() {
        return Collections.list(model.elements());
    }

    private class FileDropHandler extends TransferHandler {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                return addFiles(droppedFiles(support)) > 0;
            } catch (UnsupportedFlavorException | IOException e) {
                return false;
            }
        }

        @SuppressWarnings("unchecked") // javaFileListFlavor always carries a List<File>
        private List<File> droppedFiles(TransferSupport support) throws UnsupportedFlavorException, IOException {
            return (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
        }
    }

    /** Shows the Windows icon and the name; missing files are dimmed. */
    private static class ItemRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        private static final Color DIMMED = new Color(110, 110, 115);

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, false);
            ShelfItem item = (ShelfItem) value;
            setText(item.getName());
            setIcon(FileSystemView.getFileSystemView().getSystemIcon(item.getPath().toFile()));
            setIconTextGap(10);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setOpaque(true);
            setBackground(isSelected ? SELECTED : BACKGROUND);
            setForeground(item.exists() ? TEXT : DIMMED);
            return this;
        }
    }
}
