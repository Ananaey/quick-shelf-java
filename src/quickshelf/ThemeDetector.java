package quickshelf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Picks the theme: a fixed choice, or whatever Windows is set to. */
public final class ThemeDetector {
    private static final String VALUE_NAME = "AppsUseLightTheme";

    private ThemeDetector() {}

    /** The theme for the system setting. Dark when it cannot be read. */
    public static Theme detect() {
        return pick("auto", ThemeDetector::readWindowsLightFlag);
    }

    /**
     * preference is "light", "dark" or anything else (meaning follow the system).
     * The reader says whether the system uses a light theme; empty means "unknown".
     */
    public static Theme pick(String preference, Supplier<Optional<Boolean>> systemIsLight) {
        if ("light".equalsIgnoreCase(preference)) {
            return Theme.LIGHT;
        }
        if ("dark".equalsIgnoreCase(preference)) {
            return Theme.DARK;
        }
        return systemIsLight.get().map(light -> light ? Theme.LIGHT : Theme.DARK).orElse(Theme.DARK);
    }

    /**
     * Reads the light/dark flag out of the text printed by
     * {@code reg query ... /v AppsUseLightTheme}, which has a line like
     * {@code AppsUseLightTheme    REG_DWORD    0x1}.
     */
    static Optional<Boolean> parseLightFlag(String regOutput) {
        if (regOutput == null) {
            return Optional.empty();
        }
        for (String line : regOutput.split("\\R")) {
            if (!line.contains(VALUE_NAME)) {
                continue;
            }
            String[] words = line.trim().split("\\s+");
            String last = words[words.length - 1];
            if (last.startsWith("0x") || last.startsWith("0X")) {
                try {
                    return Optional.of(Integer.parseInt(last.substring(2), 16) != 0);
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Boolean> readWindowsLightFlag() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return Optional.empty();
        }
        try {
            Process process = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", VALUE_NAME)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            return parseLightFlag(output);
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
