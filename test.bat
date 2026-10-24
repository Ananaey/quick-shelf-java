@echo off
rem Compile and run all tests. The smoke test briefly opens real windows.
if exist out rmdir /s /q out
mkdir out
javac -Xlint:all -d out src\quickshelf\*.java test\quickshelf\*.java || exit /b 1
for %%T in (SlideMathTest ReorderTest PathOpenerTest ShelfItemTest ShelfViewTest SlidePanelSmokeTest) do (
    java -cp out quickshelf.%%T || exit /b 1
)
echo All tests passed.
