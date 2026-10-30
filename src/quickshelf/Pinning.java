package quickshelf;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordering rules for pinned items: pinned items always come first, each group keeps its own
 * order, and an item can only be moved around inside its own group. Plain Java, no Swing.
 */
public final class Pinning {
    private Pinning() {}

    public static int pinnedCount(List<ShelfItem> items) {
        int count = 0;
        for (ShelfItem item : items) {
            if (item.isPinned()) {
                count++;
            }
        }
        return count;
    }

    /** A copy with the pinned items first. Each group keeps the order it had. */
    public static List<ShelfItem> normalized(List<ShelfItem> items) {
        List<ShelfItem> result = new ArrayList<>(items.size());
        for (ShelfItem item : items) {
            if (item.isPinned()) {
                result.add(item);
            }
        }
        for (ShelfItem item : items) {
            if (!item.isPinned()) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * A copy with the given item pinned if it was not, or unpinned if it was. A newly pinned item
     * goes to the end of the pinned group; an unpinned one to the top of the unpinned group. Both
     * are the same position. An item that is not on the list leaves the list unchanged.
     */
    public static List<ShelfItem> toggled(List<ShelfItem> items, ShelfItem target) {
        int index = items.indexOf(target);
        if (index < 0) {
            return new ArrayList<>(items);
        }
        List<ShelfItem> rest = new ArrayList<>(items);
        ShelfItem current = rest.remove(index);
        rest.add(pinnedCount(rest), current.withPinned(!current.isPinned()));
        return rest;
    }

    /** First and last index of the group (pinned or unpinned) that the item at index belongs to. */
    public static int[] groupBounds(List<ShelfItem> items, int index) {
        boolean pinned = items.get(index).isPinned();
        int start = index;
        int end = index;
        while (start > 0 && items.get(start - 1).isPinned() == pinned) {
            start--;
        }
        while (end < items.size() - 1 && items.get(end + 1).isPinned() == pinned) {
            end++;
        }
        return new int[] {start, end};
    }

    /**
     * Where an item lands after moving by delta, without leaving its group. An index outside the
     * list is returned unchanged.
     */
    public static int target(List<ShelfItem> items, int index, int delta) {
        if (index < 0 || index >= items.size()) {
            return index;
        }
        int[] bounds = groupBounds(items, index);
        int wanted = Reorder.target(items.size(), index, delta);
        return Math.max(bounds[0], Math.min(bounds[1], wanted));
    }
}
