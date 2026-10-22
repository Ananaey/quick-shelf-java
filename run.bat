@echo off
rem Build and start Quick Shelf.
if not exist out mkdir out
javac -d out src\quickshelf\*.java || exit /b 1
start "" javaw -cp out quickshelf.Main
