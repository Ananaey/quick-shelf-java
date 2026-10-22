package quickshelf;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class SlideMathTest {
    public static void main(String[] args) {
        equal("progress at start", 0.0, SlideMath.progress(0, 200));
        equal("progress halfway", 0.5, SlideMath.progress(100, 200));
        equal("progress is clamped high", 1.0, SlideMath.progress(999, 200));
        equal("progress is clamped low", 0.0, SlideMath.progress(-5, 200));
        equal("zero duration is finished", 1.0, SlideMath.progress(0, 0));

        equal("ease starts at 0", 0.0, SlideMath.easeOutCubic(0));
        equal("ease ends at 1", 1.0, SlideMath.easeOutCubic(1));
        check("ease is ahead of linear at the start", SlideMath.easeOutCubic(0.25) > 0.25);
        check("ease never goes backwards",
                SlideMath.easeOutCubic(0.3) < SlideMath.easeOutCubic(0.6)
                        && SlideMath.easeOutCubic(0.6) < SlideMath.easeOutCubic(0.9));
        equal("ease clamps input above 1", 1.0, SlideMath.easeOutCubic(7));

        equal("lerp start", 100, SlideMath.lerp(100, 400, 0));
        equal("lerp end", 400, SlideMath.lerp(100, 400, 1));
        equal("lerp middle", 250, SlideMath.lerp(100, 400, 0.5));
        equal("lerp works leftwards", 250, SlideMath.lerp(400, 100, 0.5));

        equal("xAt at the start", 1600, SlideMath.xAt(1600, 1280, 0, 220));
        equal("xAt at the end", 1280, SlideMath.xAt(1600, 1280, 220, 220));
        equal("xAt after the end stays put", 1280, SlideMath.xAt(1600, 1280, 5000, 220));
        int mid = SlideMath.xAt(1600, 1280, 110, 220);
        check("xAt is between start and end midway", mid < 1600 && mid > 1280);

        equal("full slide takes the full duration", 220L, SlideMath.durationFor(1600, 1280, 320, 220));
        equal("half slide takes half the time", 110L, SlideMath.durationFor(1440, 1280, 320, 220));
        equal("no distance takes no time", 0L, SlideMath.durationFor(1280, 1280, 320, 220));
        equal("distance beyond full is capped", 220L, SlideMath.durationFor(0, 5000, 320, 220));
        equal("zero full distance is safe", 0L, SlideMath.durationFor(0, 10, 0, 220));

        TestRunner.finish();
    }
}
