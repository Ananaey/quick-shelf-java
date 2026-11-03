# Quick Shelf

[![tests](https://github.com/Ananaey/quick-shelf-java/actions/workflows/tests.yml/badge.svg)](https://github.com/Ananaey/quick-shelf-java/actions/workflows/tests.yml)

A slide-out sidebar for your desktop, written in plain Java (Swing, no libraries). Keep files and folders in one place and open them with a double-click.

Click the thin tab on the right edge of the screen (or the tray icon) to slide the shelf open or closed.

## Download (Windows)

1. Get the latest `QuickShelf-...-windows.zip` from the [Releases](https://github.com/Ananaey/quick-shelf-java/releases) page.
2. Unzip it anywhere.
3. Run `QuickShelf\QuickShelf.exe`.

Java is already bundled, so you do not need to install anything. The app is not code-signed, so Windows SmartScreen may say "unknown publisher": choose **More info**, then **Run anyway**.

## Use

- **Add:** drag files or folders from Explorer onto the open shelf.
- **Open:** double-click an item, or select it and press Enter.
- **Drag out:** drag an item from the shelf into an email, a chat window or an Explorer folder. It is always copied, never moved, so your original file stays where it was.
- **More:** right-click an item for Open, Show in folder, Copy path, Pin to top, Move up, Move down and Remove. Delete also removes.
- **Pin:** pinned items stay at the top of the shelf, marked with a blue dot. Move up and Move down work inside the pinned group and inside the rest, so a pinned item never drifts down.
- **Search:** type in the box at the top. The box is ready to type in as soon as the shelf opens.
- **Keyboard only:** type to search, press **Enter** to open the first match, **Down** to move into the list (then Enter opens, Delete removes), **Ctrl+F** to get back to the search box, and **Escape** to clear the search and then close the shelf.
- **Theme:** the half-filled circle switches light and dark, and the app remembers your choice. Until you pick one it follows your Windows setting.
- **Tray icon:** left-click shows or hides the shelf. Right-click for **Start with Windows** and **Quit**.
- **One copy at a time:** starting Quick Shelf again (for example from the Start menu while it is already in the tray) does not open a second one. The copy that is already running slides the shelf open and the new one exits. It uses a port on this computer only (47831), and if another program owns that port Quick Shelf still runs.

Your shelf is saved in `%APPDATA%\QuickShelf\shelf.txt`, one path per line, with `[pinned]` in front of pinned ones. Your theme choice is in `settings.properties` in the same folder (delete that file to go back to following Windows).

## Run from source

```
run.bat
```

Needs JDK 17 or newer.

## Test

```
.\test.bat
```

The suite runs twice: once normally (the window and tray smoke tests briefly open real windows and put an icon in the tray), then again with no display, which is how GitHub Actions runs it. The smoke tests skip themselves when there is no display.

## Build the portable app

```
package.bat 1.0.0
```

This makes `dist\QuickShelf\QuickShelf.exe` (with its own Java runtime) and a zip next to it. It needs JDK 17 or newer, which includes `jpackage`.

## Publish a release

Push a version tag and GitHub builds and publishes the zip by itself:

```
git tag v0.2.0
git push origin v0.2.0
```

## Roadmap

- [x] Day 1: frameless always-on-top panel, edge handle, slide animation
- [x] Day 2: drag files and folders in from Explorer (shown with their Windows icons)
- [x] Day 3: double-click (or Enter) to open, right-click menu (Open, Show in folder, Move up/down, Remove), Delete key removes
- [x] Day 4: save the shelf between runs (`%APPDATA%\QuickShelf\shelf.txt`, one path per line)
- [x] Day 5: light and dark themes (follows Windows, toggle button in the header) and a search box; keyboard input fixed
- [x] Day 6: system tray icon (left-click shows or hides the shelf; right-click for "Start with Windows" and "Quit")
- [x] Day 7: portable Windows app built with `jpackage` and published as a GitHub Release
- [x] Day 8: remember the chosen theme between runs
- [x] Day 9: pinned items
- [x] Day 10: drag items out of the shelf (copy only) and a Copy path menu item
- [x] Day 11: keyboard control (Ctrl+F, Enter to open the first match, Down, Escape), tested with real key events
- [x] Day 12: only one copy runs at a time; starting it again opens the running one
- [ ] Later: named groups, a real installer (needs WiX), code signing
