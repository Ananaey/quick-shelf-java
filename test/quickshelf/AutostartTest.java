package quickshelf;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class AutostartTest {
    /** An in-memory stand-in for the registry, so these tests never touch the real one. */
    static class FakeRegistry implements Autostart.Registry {
        final Map<String, String> values = new HashMap<>();
        boolean failWrites = false;

        @Override
        public Optional<String> get(String name) {
            return Optional.ofNullable(values.get(name));
        }

        @Override
        public void set(String name, String command) throws IOException {
            if (failWrites) {
                throw new IOException("access denied");
            }
            values.put(name, command);
        }

        @Override
        public void delete(String name) throws IOException {
            if (failWrites) {
                throw new IOException("access denied");
            }
            values.remove(name);
        }
    }

    public static void main(String[] args) throws IOException {
        FakeRegistry registry = new FakeRegistry();
        Autostart autostart = new Autostart(registry, () -> "\"C:\\app\\QuickShelf.exe\"");

        check("disabled by default", !autostart.isEnabled());
        autostart.setEnabled(true);
        check("enabled after turning on", autostart.isEnabled());
        equal("the command is stored under the app name",
                "\"C:\\app\\QuickShelf.exe\"", registry.values.get(Autostart.VALUE_NAME));
        autostart.setEnabled(true);
        equal("turning on twice keeps one entry", 1, registry.values.size());
        autostart.setEnabled(false);
        check("disabled after turning off", !autostart.isEnabled());
        autostart.setEnabled(false);
        check("turning off when already off is fine", !autostart.isEnabled());

        registry.failWrites = true;
        boolean threw = false;
        try {
            autostart.setEnabled(true);
        } catch (IOException e) {
            threw = true;
        }
        check("a failed write is reported", threw);
        check("and nothing is enabled", !autostart.isEnabled());

        // ---- the command line Windows will run ----
        equal("a packaged app uses its own launcher",
                "\"C:\\Program Files\\Quick Shelf\\QuickShelf.exe\"",
                Autostart.buildCommand("C:\\Program Files\\Quick Shelf\\QuickShelf.exe", "C:\\jdk", "C:\\out", "q.Main"));
        equal("running from classes uses javaw and the class path",
                "\"" + java.nio.file.Path.of("C:\\jdk", "bin", "javaw.exe") + "\" -cp \"D:\\my stuff\\out\" q.Main",
                Autostart.buildCommand(null, "C:\\jdk", "D:\\my stuff\\out", "q.Main"));
        check("a blank launcher path falls back to javaw",
                Autostart.buildCommand("  ", "C:\\jdk", "C:\\out", "q.Main").contains("javaw.exe"));
        check("paths with spaces are quoted",
                Autostart.buildCommand(null, "C:\\Program Files\\Java", "C:\\out", "q.Main")
                        .startsWith("\"" + java.nio.file.Path.of("C:\\Program Files\\Java", "bin", "javaw.exe") + "\""));

        // ---- quoting a value for reg.exe ----
        equal("a quoted command gets its inner quotes escaped",
                "\"\\\"C:\\Program Files\\Java\\bin\\javaw.exe\\\" -cp \\\"D:\\out\\\" q.Main\"",
                WindowsRegistry.quoteForReg("\"C:\\Program Files\\Java\\bin\\javaw.exe\" -cp \"D:\\out\" q.Main"));
        equal("plain text is just wrapped", "\"C:\\simple\\app.exe\"", WindowsRegistry.quoteForReg("C:\\simple\\app.exe"));
        equal("a trailing backslash is doubled so it cannot swallow the closing quote",
                "\"C:\\dir\\\\\"", WindowsRegistry.quoteForReg("C:\\dir\\"));
        equal("a backslash before an inner quote is doubled and the quote escaped",
                "\"x\\\\\\\"y\"", WindowsRegistry.quoteForReg("x\\\"y"));
        equal("an empty value is an empty quoted string", "\"\"", WindowsRegistry.quoteForReg(""));

        // ---- reading reg query output ----
        String output = "\r\nHKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\r\n"
                + "    QuickShelf    REG_SZ    \"C:\\Program Files\\x.exe\" -cp \"D:\\a  b\" q.Main\r\n\r\n";
        equal("reads a stored command, spaces and quotes intact",
                Optional.of("\"C:\\Program Files\\x.exe\" -cp \"D:\\a  b\" q.Main"),
                WindowsRegistry.parseValue(output, "QuickShelf"));
        equal("a different name is not found", Optional.empty(), WindowsRegistry.parseValue(output, "Other"));
        equal("a name that only starts the same is not found", Optional.empty(),
                WindowsRegistry.parseValue(output, "Quick"));
        equal("null output is empty", Optional.empty(), WindowsRegistry.parseValue(null, "QuickShelf"));
        equal("error text is empty", Optional.empty(),
                WindowsRegistry.parseValue("ERROR: The system was unable to find the specified registry key or value.", "QuickShelf"));
        equal("a non-text value is ignored", Optional.empty(),
                WindowsRegistry.parseValue("    QuickShelf    REG_DWORD    0x1", "QuickShelf"));

        TestRunner.finish();
    }
}
