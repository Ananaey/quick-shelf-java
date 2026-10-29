package quickshelf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static quickshelf.TestRunner.check;
import static quickshelf.TestRunner.equal;

public class SettingsTest {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("settings-test");
        Path file = dir.resolve("nested").resolve("settings.properties");

        equal("a missing file means auto", "auto", Settings.load(file).themePreference());
        equal("in-memory settings start as auto", "auto", Settings.inMemory().themePreference());

        Settings settings = Settings.load(file);
        settings.setThemePreference("light");
        equal("a change is visible straight away", "light", settings.themePreference());
        check("nothing is written until save", !Files.exists(file));

        settings.save();
        check("save creates the folder and file", Files.exists(file));
        equal("a saved choice survives a reload", "light", Settings.load(file).themePreference());
        check("no temp file left behind", !Files.exists(file.resolveSibling("settings.properties.tmp")));

        settings.setThemePreference("dark");
        settings.save();
        equal("saving again overwrites", "dark", Settings.load(file).themePreference());

        settings.setThemePreference("  LIGHT ");
        equal("case and spaces are ignored", "light", settings.themePreference());
        settings.setThemePreference("neon");
        equal("an unknown choice becomes auto", "auto", settings.themePreference());
        settings.setThemePreference(null);
        equal("null becomes auto", "auto", settings.themePreference());

        Files.write(file, "theme=purple\n".getBytes(StandardCharsets.UTF_8));
        equal("a bad value in the file means auto", "auto", Settings.load(file).themePreference());
        Files.write(file, "# just a comment\nother=1\n".getBytes(StandardCharsets.UTF_8));
        equal("a file without the key means auto", "auto", Settings.load(file).themePreference());
        Files.write(file, "theme=dar\\uZZZZ\n".getBytes(StandardCharsets.UTF_8));
        equal("a damaged file means auto", "auto", Settings.load(file).themePreference());
        Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28, (byte) 0xFF});
        equal("a file that is not text means auto", "auto", Settings.load(file).themePreference());

        Settings memory = Settings.inMemory();
        memory.setThemePreference("dark");
        memory.save(); // must do nothing, and not fail
        equal("in-memory settings keep the value", "dark", memory.themePreference());

        equal("default file name", "settings.properties", Settings.defaultPath().getFileName().toString());
        equal("it sits next to the shelf file", ShelfStorage.defaultPath().getParent(), Settings.defaultPath().getParent());

        TestRunner.finish();
    }
}
