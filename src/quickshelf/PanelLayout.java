package quickshelf;

/**
 * How tall the shelf panel is and how far down it starts, for a given screen height. Plain Java.
 * The panel wants to be 520 px tall with a 60 px gap above it, but on a short screen it shrinks
 * (down to a minimum) and finally moves up, so it is never cut off by the bottom of the screen.
 */
public final class PanelLayout {
    static final int PREFERRED_HEIGHT = 520;
    static final int MIN_HEIGHT = 260;
    static final int TOP_MARGIN = 60;
    static final int BOTTOM_MARGIN = 16;

    static final int DEFAULT_WIDTH = 320;
    static final int MIN_WIDTH = 240;
    static final int MAX_WIDTH = 640;

    /** Where the panel starts (relative to the top of the usable screen) and how tall it is. */
    public static final class Vertical {
        public final int top;
        public final int height;

        Vertical(int top, int height) {
            this.top = top;
            this.height = height;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Vertical && ((Vertical) other).top == top && ((Vertical) other).height == height;
        }

        @Override
        public int hashCode() {
            return top * 31 + height;
        }

        @Override
        public String toString() {
            return "top=" + top + " height=" + height;
        }
    }

    private PanelLayout() {}

    /** Keeps a width the user asked for between the smallest and largest the shelf allows. */
    public static int clampWidth(int requested) {
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, requested));
    }

    /**
     * The width to use for a requested width on a screen of the given width: within the normal
     * limits, and never more than half the screen so the shelf cannot cover most of it. On a very
     * narrow screen the minimum wins over the half-screen rule, and below that the shelf simply
     * takes the whole screen.
     */
    public static int width(int requested, int screenWidth) {
        int screen = Math.max(0, screenWidth);
        int cap = Math.min(MAX_WIDTH, screen / 2);
        int floor = Math.min(MIN_WIDTH, screen);
        return Math.max(floor, Math.min(cap, requested));
    }

    public static Vertical vertical(int screenHeight) {
        int screen = Math.max(0, screenHeight);
        int available = screen - TOP_MARGIN - BOTTOM_MARGIN;
        int height = Math.max(MIN_HEIGHT, Math.min(PREFERRED_HEIGHT, available));
        if (height > screen) {
            height = screen; // an absurdly short screen: use all of it
        }
        int top = Math.max(0, Math.min(TOP_MARGIN, screen - height));
        return new Vertical(top, height);
    }
}
