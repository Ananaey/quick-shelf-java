package quickshelf;

import java.util.Optional;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class ThemeTest {
    private static final String LIGHT_OUTPUT =
            "\r\nHKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\r\n"
                    + "    AppsUseLightTheme    REG_DWORD    0x1\r\n\r\n";
    private static final String DARK_OUTPUT = LIGHT_OUTPUT.replace("0x1", "0x0");

    public static void main(String[] args) {
        equal("toggle dark gives light", Theme.LIGHT, Theme.DARK.toggled());
        equal("toggle light gives dark", Theme.DARK, Theme.LIGHT.toggled());
        check("palettes differ", !Theme.DARK.panelBackground.equals(Theme.LIGHT.panelBackground));
        check("dark has light text, light has dark text",
                Theme.DARK.text.getRed() > 200 && Theme.LIGHT.text.getRed() < 60);

        equal("reads a light setting", Optional.of(true), ThemeDetector.parseLightFlag(LIGHT_OUTPUT));
        equal("reads a dark setting", Optional.of(false), ThemeDetector.parseLightFlag(DARK_OUTPUT));
        equal("unix line endings work", Optional.of(true), ThemeDetector.parseLightFlag(LIGHT_OUTPUT.replace("\r\n", "\n")));
        equal("null output is unknown", Optional.empty(), ThemeDetector.parseLightFlag(null));
        equal("empty output is unknown", Optional.empty(), ThemeDetector.parseLightFlag(""));
        equal("error text is unknown", Optional.empty(),
                ThemeDetector.parseLightFlag("ERROR: The system was unable to find the specified registry key or value."));
        equal("a value that is not hex is unknown", Optional.empty(),
                ThemeDetector.parseLightFlag("    AppsUseLightTheme    REG_DWORD    banana"));
        equal("bad hex is unknown", Optional.empty(),
                ThemeDetector.parseLightFlag("    AppsUseLightTheme    REG_DWORD    0xZZ"));

        equal("explicit light", Theme.LIGHT, ThemeDetector.pick("light", () -> Optional.of(false)));
        equal("explicit dark", Theme.DARK, ThemeDetector.pick("DARK", () -> Optional.of(true)));
        equal("auto follows a light system", Theme.LIGHT, ThemeDetector.pick("auto", () -> Optional.of(true)));
        equal("auto follows a dark system", Theme.DARK, ThemeDetector.pick("auto", () -> Optional.of(false)));
        equal("auto defaults to dark when unknown", Theme.DARK, ThemeDetector.pick("auto", Optional::empty));
        equal("unknown preference means auto", Theme.LIGHT, ThemeDetector.pick("neon", () -> Optional.of(true)));
        check("real detection returns some theme", ThemeDetector.detect() != null);

        TestRunner.finish();
    }
}
