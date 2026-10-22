package quickshelf;

/** Tiny assertion helpers so the project needs no test library. */
public final class TestRunner {
    private static int run = 0;
    private static int failed = 0;

    private TestRunner() {}

    public static void check(String name, boolean condition) {
        run++;
        if (!condition) {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    public static void equal(String name, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        run++;
        if (!same) {
            failed++;
            System.out.println("FAIL: " + name + " (expected " + expected + ", got " + actual + ")");
        }
    }

    /** Prints the summary and exits non-zero if anything failed. */
    public static void finish() {
        System.out.println(run + " checks, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
