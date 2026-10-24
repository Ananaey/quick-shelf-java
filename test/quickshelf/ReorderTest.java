package quickshelf;

import static quickshelf.TestRunner.equal;

public class ReorderTest {
    public static void main(String[] args) {
        equal("move down one", 2, Reorder.target(5, 1, 1));
        equal("move up one", 0, Reorder.target(5, 1, -1));
        equal("first item cannot go up", 0, Reorder.target(5, 0, -1));
        equal("last item cannot go down", 4, Reorder.target(5, 4, 1));
        equal("big jump is clamped to the end", 4, Reorder.target(5, 1, 99));
        equal("big jump is clamped to the start", 0, Reorder.target(5, 3, -99));
        equal("zero delta stays put", 2, Reorder.target(5, 2, 0));
        equal("negative index is returned unchanged", -1, Reorder.target(5, -1, 1));
        equal("index past the end is returned unchanged", 7, Reorder.target(5, 7, -1));
        equal("empty list returns the index", 0, Reorder.target(0, 0, 1));
        equal("single item stays put", 0, Reorder.target(1, 0, 1));
        TestRunner.finish();
    }
}
