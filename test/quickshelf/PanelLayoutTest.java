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

        // ---- width ----
        equal("the default width on a big screen", 320, PanelLayout.width(320, 1920));
        equal("a wider request is honoured", 500, PanelLayout.width(500, 1920));
        equal("too narrow is raised to the minimum", 240, PanelLayout.width(100, 1920));
        equal("too wide is cut to the maximum", 640, PanelLayout.width(1000, 1920));
        equal("exactly the minimum", 240, PanelLayout.width(240, 1920));
        equal("exactly the maximum", 640, PanelLayout.width(640, 1920));
        equal("never more than half of a 1000 px screen", 500, PanelLayout.width(900, 1000));
        equal("a normal request on a 1000 px screen", 320, PanelLayout.width(320, 1000));
        equal("on a 600 px screen half is the limit", 300, PanelLayout.width(320, 600));
        equal("and a smaller request still fits", 250, PanelLayout.width(250, 600));
        equal("on a 400 px screen the minimum beats half the screen", 240, PanelLayout.width(320, 400));
        equal("on a 200 px screen the shelf takes it all", 200, PanelLayout.width(320, 200));
        equal("on no screen there is no width", 0, PanelLayout.width(320, 0));
        equal("a negative screen is treated as none", 0, PanelLayout.width(320, -10));
        equal("a negative request is raised to the minimum", 240, PanelLayout.width(-5, 1920));
        equal("clampWidth below the minimum", 240, PanelLayout.clampWidth(0));
        equal("clampWidth above the maximum", 640, PanelLayout.clampWidth(5000));
        equal("clampWidth leaves a good value alone", 333, PanelLayout.clampWidth(333));
        equal("the default width is inside the limits", PanelLayout.DEFAULT_WIDTH, PanelLayout.clampWidth(PanelLayout.DEFAULT_WIDTH));

        boolean widthOk = true;
        for (int screenWidth = 0; screenWidth <= 4000; screenWidth += 13) {
            int previous = -1;
            for (int requested = 0; requested <= 1500; requested += 7) {
                int w = PanelLayout.width(requested, screenWidth);
                widthOk &= w >= 0 && w <= screenWidth && w <= PanelLayout.MAX_WIDTH;
                widthOk &= w >= Math.min(PanelLayout.MIN_WIDTH, screenWidth);
                widthOk &= w >= previous; // asking for more never gives less
                previous = w;
            }
        }
        check("for every screen and request the width is sensible and never shrinks as the request grows", widthOk);

        equal("equal layouts are equal", PanelLayout.vertical(700), PanelLayout.vertical(700));
        check("different layouts differ", !PanelLayout.vertical(700).equals(PanelLayout.vertical(400)));

        TestRunner.finish();
    }
}
