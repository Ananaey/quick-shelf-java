package quickshelf;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Reads and writes text values in one Windows registry key by running the built-in reg.exe. */
final class WindowsRegistry implements Autostart.Registry {
    static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    private final String key;

    WindowsRegistry(String key) {
        this.key = key;
    }

    @Override
    public Optional<String> get(String name) {
        try {
            Result result = run(List.of("reg", "query", key, "/v", name));
            return result.exitCode == 0 ? parseValue(result.output, name) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void set(String name, String value) throws IOException {
        Result result = run(List.of("reg", "add", key, "/v", name, "/t", "REG_SZ", "/d", quoteForReg(value), "/f"));
        if (result.exitCode != 0) {
            throw new IOException("Could not write the startup entry: " + result.output.strip());
        }
    }

    @Override
    public void delete(String name) throws IOException {
        if (get(name).isEmpty()) {
            return; // nothing to remove
        }
        Result result = run(List.of("reg", "delete", key, "/v", name, "/f"));
        if (result.exitCode != 0) {
            throw new IOException("Could not remove the startup entry: " + result.output.strip());
        }
    }

    /**
     * Wraps a value in quotes for reg.exe, escaping any quotes inside it. Java's own quoting of
     * arguments that contain quotes is rejected by reg.exe ("Invalid syntax"), so it is done here.
     * Backslashes directly before a quote are doubled, the standard Windows command-line rule.
     */
    static String quoteForReg(String value) {
        StringBuilder out = new StringBuilder("\"");
        int backslashes = 0;
        for (char c : value.toCharArray()) {
            if (c == '\\') {
                backslashes++;
                out.append(c);
            } else if (c == '"') {
                out.append("\\".repeat(backslashes + 1)).append('"');
                backslashes = 0;
            } else {
                backslashes = 0;
                out.append(c);
            }
        }
        out.append("\\".repeat(backslashes)).append('"');
        return out.toString();
    }

    /**
     * Pulls the text out of {@code reg query} output, which has a line like
     * {@code     QuickShelf    REG_SZ    "C:\Program Files\x.exe" -cp "D:\a b"}.
     */
    static Optional<String> parseValue(String output, String name) {
        if (output == null) {
            return Optional.empty();
        }
        for (String line : output.split("\\R")) {
            String trimmed = line.strip();
            if (!trimmed.startsWith(name + " ") && !trimmed.startsWith(name + "\t")) {
                continue;
            }
            int type = trimmed.indexOf("REG_SZ");
            if (type < 0) {
                return Optional.empty();
            }
            return Optional.of(trimmed.substring(type + "REG_SZ".length()).strip());
        }
        return Optional.empty();
    }

    private static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static Result run(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        // reg.exe prints in the console's code page. Non-ASCII characters may come back garbled,
        // which is fine: callers only check that a value exists, they never reuse the text.
        Charset charset = Charset.forName(System.getProperty("native.encoding", "UTF-8"));
        String output = new String(process.getInputStream().readAllBytes(), charset);
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("reg.exe did not finish");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running reg.exe", e);
        }
        return new Result(process.exitValue(), output);
    }
}
