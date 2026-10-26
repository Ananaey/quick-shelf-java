package quickshelf;

import java.nio.file.Path;

import static quickshelf.TestRunner.check;

public class ShelfFilterTest {
    public static void main(String[] args) {
        ShelfItem report = new ShelfItem(Path.of("Work", "Report Final.docx"));
        ShelfItem budget = new ShelfItem(Path.of("Work", "budget.xlsx"));
        ShelfItem trip = new ShelfItem(Path.of("Photos", "trip"));

        check("null query matches", ShelfFilter.matches(report, null));
        check("empty query matches", ShelfFilter.matches(report, ""));
        check("blank query matches", ShelfFilter.matches(report, "   "));

        check("matches part of the name", ShelfFilter.matches(report, "final"));
        check("ignores case in the query", ShelfFilter.matches(report, "REPORT"));
        check("ignores case in the name", ShelfFilter.matches(budget, "Budget"));
        check("does not match another name", !ShelfFilter.matches(budget, "report"));

        check("matches a folder in the path", ShelfFilter.matches(trip, "photos"));
        check("every word must match", ShelfFilter.matches(report, "work final"));
        check("one missing word fails the match", !ShelfFilter.matches(trip, "photos budget"));
        check("word order does not matter", ShelfFilter.matches(report, "final work"));
        check("extra spaces between words are fine", ShelfFilter.matches(report, "  work    final "));
        check("matches the extension", ShelfFilter.matches(budget, ".xlsx"));

        TestRunner.finish();
    }
}
