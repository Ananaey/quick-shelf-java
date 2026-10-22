package quickshelf;

/** Pure animation maths for the slide-out panel, kept free of Swing so it is easy to test. */
public final class SlideMath {
    private SlideMath() {}

    /** Progress 0..1 for the elapsed time. A non-positive duration is already finished. */
    public static double progress(long elapsedMs, long durationMs) {
        if (durationMs <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) elapsedMs / durationMs));
    }

    /** Starts fast and slows to a stop, like a macOS panel. */
    public static double easeOutCubic(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double inverse = 1.0 - clamped;
        return 1.0 - inverse * inverse * inverse;
    }

    /** Linear interpolation between two pixel positions. */
    public static int lerp(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }

    /** Where the panel should be after elapsedMs of an animation from one x to another. */
    public static int xAt(int fromX, int toX, long elapsedMs, long durationMs) {
        return lerp(fromX, toX, easeOutCubic(progress(elapsedMs, durationMs)));
    }

    /**
     * How long a partly-finished slide should take. If the user clicks the handle halfway,
     * the reverse slide only needs to cover the distance that is left.
     */
    public static long durationFor(int fromX, int toX, int fullDistance, long fullDurationMs) {
        if (fullDistance <= 0) {
            return 0;
        }
        double fraction = Math.min(1.0, Math.abs(toX - fromX) / (double) fullDistance);
        return Math.round(fullDurationMs * fraction);
    }
}
