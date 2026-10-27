# Quick Shelf

[![tests](https://github.com/Ananaey/quick-shelf-java/actions/workflows/tests.yml/badge.svg)](https://github.com/Ananaey/quick-shelf-java/actions/workflows/tests.yml)

A slide-out sidebar for your desktop, written in plain Java (Swing, no libraries). Keep files and folders in one place and open them with a double-click.

Click the thin tab on the right edge of the screen to slide the shelf open or closed.

## Run

```
run.bat
```

Needs JDK 17 or newer.

## Test

```
.\test.bat
```

The window smoke test briefly opens real windows. It skips itself on machines with no display.

## Roadmap

- [x] Day 1: frameless always-on-top panel, edge handle, slide animation
- [x] Day 2: drag files and folders in from Explorer (shown with their Windows icons)
- [x] Day 3: double-click (or Enter) to open, right-click menu (Open, Show in folder, Move up/down, Remove), Delete key removes
- [x] Day 4: save the shelf between runs (`%APPDATA%\QuickShelf\shelf.txt`, one path per line)
- [x] Day 5: light and dark themes (follows Windows, toggle button in the header) and a search box; keyboard input fixed
- [ ] Later: remember the chosen theme, groups / pinned sections
- [x] Day 6: system tray icon (left-click shows or hides the shelf; right-click for "Start with Windows" and "Quit")
- [ ] Day 7: `jpackage` installer and a GitHub Release
