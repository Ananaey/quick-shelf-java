package quickshelf;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class PanelLayoutTest {
    private static String layout(int screenHeight) {
        return PanelLayout.vertical(screenHeight).toString();
    }

    public static void main(String[] args) {
        equal("a tall screen gets the preferred size", "top=60 height=520", layout(1040));
        equal("a very tall screen is no different", "top=60 height=520", layout(2160));
        equal("exactly enough room", "top=60 height=520", layout(596));
        equal("a little less room shrinks the panel", "top=60 height=519", layout(595));
        equal("a laptop screen", "top=60 height=424", layout(500));
        equal("a short screen keeps the gap above", "top=60 height=264", layout(340));
        equal("below the minimum the gap shrinks first", "top=40 height=260", layout(300));
        equal("just fitting the minimum", "top=0 height=260", layout(260));
        equal("shorter than the minimum uses the whole screen", "top=0 height=200", layout(200));
        equal("a zero-height screen does not crash", "top=0 height=0", layout(0));
        equal("a negative height is treated as zero", "top=0 height=0", layout(-50));

        // whatever the screen, the panel never starts above it or runs off the bottom
        boolean always = true;
        for (int h = 0; h <= 3000; h++) {
            PanelLayout.Vertical v = PanelLayout.vertical(h);
            always &= v.top >= 0 && v.height >= 0 && v.top + v.height <= Math.max(h, 0);
            always &= v.height <= PanelLayout.PREFERRED_HEIGHT;
            if (h >= PanelLayout.MIN_HEIGHT) {
                always &= v.height >= PanelLayout.MIN_HEIGHT;
            }
        }
        check("for every height from 0 to 3000 the panel stays inside the screen", always);

        // growing the screen never makes the panel smaller
        boolean monotonic = true;
        int last = 0;
        for (int h = 0; h <= 3000; h++) {
            int height = PanelLayout.vertical(h).height;
            monotonic &= height >= last;
            last = height;
        }
        check("a taller screen never gives a shorter panel", monotonic);

        equal("equal layouts are equal", PanelLayout.vertical(700), PanelLayout.vertical(700));
        check("different layouts differ", !PanelLayout.vertical(700).equals(PanelLayout.vertical(400)));

        TestRunner.finish();
    }
}
