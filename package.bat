@echo off
rem Builds a portable Windows app: dist\QuickShelf\QuickShelf.exe (with its own Java runtime)
rem and dist\QuickShelf-<version>-windows.zip. Needs JDK 17 or newer (jpackage is included).
rem Usage: package.bat [version]     e.g. package.bat 1.0.0
setlocal
set VERSION=%~1
if "%VERSION%"=="" set VERSION=1.0.0

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\classes build\lib dist || exit /b 1

echo Compiling...
javac -d build\classes src\quickshelf\*.java || exit /b 1

echo Making the icon...
java -cp build\classes quickshelf.IconMaker build\QuickShelf.ico || exit /b 1

echo Making the jar...
jar --create --file build\lib\quickshelf.jar --main-class quickshelf.Main -C build\classes . || exit /b 1

echo Packaging with jpackage...
jpackage --type app-image --name QuickShelf --app-version %VERSION% ^
    --input build\lib --main-jar quickshelf.jar --main-class quickshelf.Main ^
    --icon build\QuickShelf.ico --add-modules java.desktop ^
    --description "Quick Shelf - a slide-out sidebar for your files and folders" ^
    --dest dist || exit /b 1

echo Zipping...
rem Antivirus can briefly lock freshly built files, so try a few times.
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; for ($i=1; $i -le 6; $i++) { try { Compress-Archive -Path 'dist\QuickShelf' -DestinationPath 'dist\QuickShelf-%VERSION%-windows.zip' -Force; exit 0 } catch { if ($i -eq 6) { Write-Error $_; exit 1 }; Start-Sleep -Seconds 3 } }" || exit /b 1
if not exist "dist\QuickShelf-%VERSION%-windows.zip" (
    echo ERROR: the zip file was not created
    exit /b 1
)

echo.
echo Done: dist\QuickShelf-%VERSION%-windows.zip
