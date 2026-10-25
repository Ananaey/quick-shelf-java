package quickshelf;

import java.awt.Color;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileSystemView;

/**
 * The scrolling list of shelf items.
 * Drag files in from Explorer, double-click (or press Enter) to open, right-click for more,
 * Delete to remove.
 */
public class ShelfView extends JScrollPane {
    private static final long serialVersionUID = 1L;
    private static final Color BACKGROUND = new Color(30, 30, 34);
    private static final Color TEXT = new Color(242, 242, 247);
    private static final Color SELECTED = new Color(10, 132, 255);

    private final DefaultListModel<ShelfItem> model = new DefaultListModel<>();
    private final JList<ShelfItem> list = new JList<>(model) {
        private static final long serialVersionUID = 1L;

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = rowAt(event);
            if (index < 0) {
                return null;
            }
            ShelfItem item = model.get(index);
            return item.exists() ? item.getPath().toString() : item.getPath() + "  (not found)";
        }
    };
    private transient PathOpener opener = new PathOpener();
    private final transient ShelfStorage storage;

    /** A shelf saved in the default location (%APPDATA%\QuickShelf). */
    public ShelfView() {
        this(ShelfStorage.defaultPath());
    }

    /** A shelf saved in the given file. Items saved earlier are loaded right away. */
    public ShelfView(Path storageFile) {
        this.storage = new ShelfStorage(storageFile);
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

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    openAt(rowAt(e));
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                showMenuIfPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showMenuIfPopup(e);
            }
        });
        bindKey(KeyEvent.VK_ENTER, "open", () -> openAt(list.getSelectedIndex()));
        bindKey(KeyEvent.VK_DELETE, "remove", () -> removeAt(list.getSelectedIndex()));

        loadSaved();
    }

    /** Restores the saved shelf. Files that have since gone missing stay listed, dimmed. */
    private void loadSaved() {
        for (Path path : storage.load()) {
            ShelfItem item = new ShelfItem(path);
            if (!model.contains(item)) {
                model.addElement(item);
            }
        }
    }

    private void persist() {
        List<Path> paths = new ArrayList<>();
        for (ShelfItem item : items()) {
            paths.add(item.getPath());
        }
        try {
            storage.save(paths);
        } catch (IOException e) {
            // A failed save must never break the shelf; the items are still in memory.
            System.err.println("Quick Shelf: could not save the shelf: " + e.getMessage());
        }
    }

    // ---- actions (package-private so tests can call them without a mouse) ---------------

    /** Adds files and folders to the shelf and returns how many were new. */
    public int addFiles(List<File> files) {
        List<Path> paths = new ArrayList<>();
        for (File file : files) {
            paths.add(file == null ? null : file.toPath());
        }
        List<ShelfItem> added = ShelfItem.newItems(paths, items());
        added.forEach(model::addElement);
        if (!added.isEmpty()) {
            persist();
        }
        return added.size();
    }

    public List<ShelfItem> items() {
        return Collections.list(model.elements());
    }

    /** Opens the item at index. A missing file is not an error: the row just shows as not found. */
    boolean openAt(int index) {
        if (index < 0 || index >= model.size()) {
            return false;
        }
        boolean opened = opener.open(model.get(index).getPath());
        list.repaint(); // refresh the dimmed "not found" look
        return opened;
    }

    boolean showInFolderAt(int index) {
        return index >= 0 && index < model.size() && opener.openParent(model.get(index).getPath());
    }

    /** Removes the item at index and keeps a sensible row selected. Returns false if index is invalid. */
    boolean removeAt(int index) {
        if (index < 0 || index >= model.size()) {
            return false;
        }
        model.remove(index);
        if (!model.isEmpty()) {
            list.setSelectedIndex(Math.min(index, model.size() - 1));
        }
        persist();
        return true;
    }

    /** Moves an item up (negative delta) or down and returns its new index. */
    int moveItem(int index, int delta) {
        int target = Reorder.target(model.size(), index, delta);
        if (target == index || index < 0 || index >= model.size()) {
            return index;
        }
        ShelfItem item = model.remove(index);
        model.add(target, item);
        list.setSelectedIndex(target);
        persist();
        return target;
    }

    void setOpener(PathOpener opener) {
        this.opener = opener;
    }

    // ---- UI plumbing ----------------------------------------------------------------

    /** Row under the mouse, or -1 if the pointer is on empty space. */
    private int rowAt(MouseEvent event) {
        int index = list.locationToIndex(event.getPoint());
        if (index < 0 || !list.getCellBounds(index, index).contains(event.getPoint())) {
            return -1;
        }
        return index;
    }

    private void showMenuIfPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int index = rowAt(e);
        if (index < 0) {
            return;
        }
        list.setSelectedIndex(index);

        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Open", true, () -> openAt(index)));
        menu.add(menuItem("Show in folder", true, () -> showInFolderAt(index)));
        menu.addSeparator();
        menu.add(menuItem("Move up", index > 0, () -> moveItem(index, -1)));
        menu.add(menuItem("Move down", index < model.size() - 1, () -> moveItem(index, 1)));
        menu.addSeparator();
        menu.add(menuItem("Remove from shelf", true, () -> removeAt(index)));
        menu.show(list, e.getX(), e.getY());
    }

    private static JMenuItem menuItem(String text, boolean enabled, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setEnabled(enabled);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void bindKey(int keyCode, String name, Runnable action) {
        list.getInputMap().put(KeyStroke.getKeyStroke(keyCode, 0), name);
        list.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
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
