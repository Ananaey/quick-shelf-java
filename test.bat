@echo off
rem Compile and run all tests twice: once normally (the smoke tests briefly open real windows
rem and put an icon in the tray), then again with no display, which is how GitHub Actions runs
rem them. Some Swing calls only fail without a display, so the second pass catches those here.
setlocal
set TESTS=SlideMathTest ReorderTest PathOpenerTest ThemeTest ShelfFilterTest ShelfItemTest ShelfStorageTest SettingsTest AutostartTest TrayImageTest IconMakerTest PinningTest FileTransferableTest SingleInstanceTest PanelLayoutTest ShelfViewTest SlidePanelSmokeTest TraySmokeTest

if exist out rmdir /s /q out
mkdir out
javac -Xlint:all -d out src\quickshelf\*.java test\quickshelf\*.java || exit /b 1

echo === With a display ===
for %%T in (%TESTS%) do (
    java -cp out quickshelf.%%T || exit /b 1
)

echo === Without a display (like CI) ===
for %%T in (%TESTS%) do (
    java -Djava.awt.headless=true -cp out quickshelf.%%T || exit /b 1
)

echo All tests passed.
