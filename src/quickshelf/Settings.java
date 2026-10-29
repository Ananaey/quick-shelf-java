package quickshelf;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;

/**
 * Small user preferences, kept in %APPDATA%\QuickShelf\settings.properties.
 * Right now that is only the theme. Loading never fails: a missing or damaged file means defaults.
 */
public final class Settings {
    static final String THEME_KEY = "theme";
    static final String AUTO = "auto";

    private final Path file; // null means "never written to disk"
    private final Properties values = new Properties();

    private Settings(Path file) {
        this.file = file;
    }

    /** Settings stored next to the shelf file. */
    public static Path defaultPath() {
        return ShelfStorage.defaultPath().resolveSibling("settings.properties");
    }

    /** Reads the file. A missing, unreadable or damaged file gives the defaults. */
    public static Settings load(Path file) {
        Settings settings = new Settings(file);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                settings.values.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                settings.values.clear(); // damaged: start from the defaults
            }
        }
        return settings;
    }

    /** Settings that are only kept in memory (used by tests so they never touch real files). */
    public static Settings inMemory() {
        return new Settings(null);
    }

    /** "light", "dark", or "auto" (follow Windows). Anything else counts as auto. */
    public String themePreference() {
        return normalize(values.getProperty(THEME_KEY));
    }

    public void setThemePreference(String preference) {
        values.setProperty(THEME_KEY, normalize(preference));
    }

    /** Writes the file via a temp copy so a crash cannot leave it half-written. */
    public void save() throws IOException {
        if (file == null) {
            return;
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            values.store(writer, "Quick Shelf settings");
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String normalize(String preference) {
        if (preference == null) {
            return AUTO;
        }
        String lower = preference.strip().toLowerCase(Locale.ROOT);
        return lower.equals("light") || lower.equals("dark") ? lower : AUTO;
    }
}
