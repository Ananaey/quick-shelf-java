package quickshelf;

/** Index maths for moving a list entry up or down. */
public final class Reorder {
    private Reorder() {}

    /**
     * Where an entry at index lands after moving by delta, clamped to the ends.
     * An index outside the list is returned unchanged so callers can treat it as "nothing to do".
     */
    public static int target(int size, int index, int delta) {
        if (index < 0 || index >= size) {
            return index;
        }
        return Math.max(0, Math.min(size - 1, index + delta));
    }
}
