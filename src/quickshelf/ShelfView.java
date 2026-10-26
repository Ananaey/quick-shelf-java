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
 * Delete to remove, and type in the search box to filter.
 *
 * <p>{@code model} holds every item and is what gets saved. {@code shown} is what the list
 * displays: the items that match the current search. Row positions passed to the action
 * methods are positions in the displayed list.
 */
public class ShelfView extends JScrollPane {
    private static final long serialVersionUID = 1L;

    private final DefaultListModel<ShelfItem> model = new DefaultListModel<>();
    private final DefaultListModel<ShelfItem> shown = new DefaultListModel<>();
    private final JList<ShelfItem> list = new JList<>(shown) {
        private static final long serialVersionUID = 1L;

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = rowAt(event);
            if (index < 0) {
                return null;
            }
            ShelfItem item = shown.get(index);
            return item.exists() ? item.getPath().toString() : item.getPath() + "  (not found)";
        }
    };
    private transient PathOpener opener = new PathOpener();
    private final transient ShelfStorage storage;
    private transient Theme theme = Theme.DARK;
    private String query = "";

    /** A shelf saved in the default location (%APPDATA%\QuickShelf). */
    public ShelfView() {
        this(ShelfStorage.defaultPath());
    }

    /** A shelf saved in the given file. Items saved earlier are loaded right away. */
    public ShelfView(Path storageFile) {
        this.storage = new ShelfStorage(storageFile);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ItemRenderer());
        list.setFixedCellHeight(34);
        ToolTipManager.sharedInstance().registerComponent(list);

        setViewportView(list);
        setBorder(BorderFactory.createEmptyBorder());
        setTheme(Theme.DARK);

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

    // ---- public API -------------------------------------------------------------

    /** Adds files and folders to the shelf and returns how many were new. */
    public int addFiles(List<File> files) {
        List<Path> paths = new ArrayList<>();
        for (File file : files) {
            paths.add(file == null ? null : file.toPath());
        }
        List<ShelfItem> added = ShelfItem.newItems(paths, items());
        added.forEach(model::addElement);
        if (!added.isEmpty()) {
            refresh();
            persist();
        }
        return added.size();
    }

    /** Every item on the shelf, in saved order, whatever the search says. */
    public List<ShelfItem> items() {
        return Collections.list(model.elements());
    }

    /** Only the items the current search shows. */
    public List<ShelfItem> visibleItems() {
        return Collections.list(shown.elements());
    }

    /** Filters the list. A blank query shows everything. */
    public void setQuery(String text) {
        this.query = text == null ? "" : text;
        refresh();
    }

    public void setTheme(Theme newTheme) {
        this.theme = newTheme;
        list.setBackground(newTheme.panelBackground);
        list.setForeground(newTheme.text);
        getViewport().setBackground(newTheme.panelBackground);
        list.repaint();
    }

    // ---- actions on a displayed row (package-private so tests can call them) ------------

    /** Opens the item at a displayed row. A missing file is not an error: the row shows as not found. */
    boolean openAt(int index) {
        if (index < 0 || index >= shown.size()) {
            return false;
        }
        boolean opened = opener.open(shown.get(index).getPath());
        list.repaint(); // refresh the dimmed "not found" look
        return opened;
    }

    boolean showInFolderAt(int index) {
        return index >= 0 && index < shown.size() && opener.openParent(shown.get(index).getPath());
    }

    /** Removes the item at a displayed row and keeps a sensible row selected. */
    boolean removeAt(int index) {
        if (index < 0 || index >= shown.size()) {
            return false;
        }
        model.removeElement(shown.get(index));
        refresh();
        if (!shown.isEmpty()) {
            list.setSelectedIndex(Math.min(index, shown.size() - 1));
        }
        persist();
        return true;
    }

    /**
     * Moves an item up (negative delta) or down and returns its new row. Reordering is only
     * possible while no search is active, because hidden rows would make the result confusing.
     */
    int moveItem(int index, int delta) {
        if (isSearching() || index < 0 || index >= model.size()) {
            return index;
        }
        int target = Reorder.target(model.size(), index, delta);
        if (target == index) {
            return index;
        }
        ShelfItem item = model.remove(index);
        model.add(target, item);
        refresh();
        list.setSelectedIndex(target);
        persist();
        return target;
    }

    boolean isSearching() {
        return !query.isBlank();
    }

    void setOpener(PathOpener opener) {
        this.opener = opener;
    }

    // ---- internals ------------------------------------------------------------------

    /** Restores the saved shelf. Files that have since gone missing stay listed, dimmed. */
    private void loadSaved() {
        for (Path path : storage.load()) {
            ShelfItem item = new ShelfItem(path);
            if (!model.contains(item)) {
                model.addElement(item);
            }
        }
        refresh();
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

    /** Rebuilds the displayed rows from the full shelf and the search, keeping the selection if it survives. */
    private void refresh() {
        ShelfItem selected = list.getSelectedValue();
        shown.clear();
        for (ShelfItem item : Collections.list(model.elements())) {
            if (ShelfFilter.matches(item, query)) {
                shown.addElement(item);
            }
        }
        if (selected != null && shown.contains(selected)) {
            list.setSelectedValue(selected, true);
        }
    }

    /** Displayed row under the mouse, or -1 if the pointer is on empty space. */
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

        boolean canMove = !isSearching();
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Open", true, () -> openAt(index)));
        menu.add(menuItem("Show in folder", true, () -> showInFolderAt(index)));
        menu.addSeparator();
        menu.add(menuItem("Move up", canMove && index > 0, () -> moveItem(index, -1)));
        menu.add(menuItem("Move down", canMove && index < shown.size() - 1, () -> moveItem(index, 1)));
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

    /** Shows the Windows icon and the name in the current theme; missing files are dimmed. */
    private class ItemRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

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
            setBackground(isSelected ? theme.selected : theme.panelBackground);
            if (isSelected) {
                setForeground(Color.WHITE);
            } else {
                setForeground(item.exists() ? theme.text : theme.muted);
            }
            return this;
        }
    }
}
