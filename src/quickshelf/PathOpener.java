package quickshelf;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens a file with its default app, or a folder in Explorer. The launcher can be swapped for tests. */
public final class PathOpener {
    /** How a file actually gets opened. */
    public interface Launcher {
        void open(File file) throws IOException;
    }

    private final Launcher launcher;

    public PathOpener() {
        this(PathOpener::desktopOpen);
    }

    public PathOpener(Launcher launcher) {
        this.launcher = launcher;
    }

    /** Returns false if the path no longer exists or could not be opened. */
    public boolean open(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        try {
            launcher.open(path.toFile());
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Opens the folder that contains the path (for "Show in folder"). */
    public boolean openParent(Path path) {
        Path parent = path == null ? null : path.toAbsolutePath().getParent();
        return open(parent);
    }

    private static void desktopOpen(File file) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IOException("Opening files is not supported on this system");
        }
        Desktop.getDesktop().open(file);
    }
}
