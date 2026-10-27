package quickshelf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Starts Quick Shelf when the user logs in to Windows, using the per-user Run registry key
 * (no administrator rights needed). The registry is behind an interface so tests never touch it.
 */
public final class Autostart {
    static final String VALUE_NAME = "QuickShelf";

    /** Where the startup entry is stored. */
    public interface Registry {
        Optional<String> get(String name);

        void set(String name, String command) throws IOException;

        /** Removing an entry that is not there is not an error. */
        void delete(String name) throws IOException;
    }

    private final Registry registry;
    private final Supplier<String> command;

    public Autostart(Registry registry, Supplier<String> command) {
        this.registry = registry;
        this.command = command;
    }

    /** The real thing: Windows Run key and the command that starts this running copy of the app. */
    public static Autostart forThisApp() {
        return new Autostart(new WindowsRegistry(WindowsRegistry.RUN_KEY), Autostart::currentLaunchCommand);
    }

    public static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public boolean isEnabled() {
        return registry.get(VALUE_NAME).isPresent();
    }

    public void setEnabled(boolean enabled) throws IOException {
        if (enabled) {
            registry.set(VALUE_NAME, command.get());
        } else {
            registry.delete(VALUE_NAME);
        }
    }

    // ---- the command Windows runs at login --------------------------------------

    /**
     * Builds the command line. A packaged app (made by jpackage) has its own launcher, so that is
     * used when known. Otherwise it is javaw (no console window) with this run's class path.
     *
     * @param appPath   the jpackage launcher, or null when running from class files
     * @param javaHome  the JDK or JRE that is running now
     * @param classPath absolute class path entries, joined by the platform separator
     */
    static String buildCommand(String appPath, String javaHome, String classPath, String mainClass) {
        if (appPath != null && !appPath.isBlank()) {
            return quote(appPath);
        }
        String javaw = Path.of(javaHome, "bin", "javaw.exe").toString();
        return quote(javaw) + " -cp " + quote(classPath) + " " + mainClass;
    }

    private static String currentLaunchCommand() {
        StringBuilder absolute = new StringBuilder();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue;
            }
            if (absolute.length() > 0) {
                absolute.append(File.pathSeparator);
            }
            absolute.append(new File(entry).getAbsolutePath());
        }
        return buildCommand(
                System.getProperty("jpackage.app-path"),
                System.getProperty("java.home"),
                absolute.toString(),
                Main.class.getName());
    }

    private static String quote(String text) {
        return "\"" + text + "\"";
    }
}
