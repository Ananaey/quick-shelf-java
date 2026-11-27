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
- **Paste:** copy files in Explorer (Ctrl+C), then click the shelf list and press **Ctrl+V** (or use **Paste from clipboard** in the menu). Text with one path per line works too, including Windows' "Copy as path". Paths that do not exist, and relative paths, are skipped. The clipboard is only read when you paste.
- **Open:** double-click an item, or select it and press Enter.
- **Rename:** select an item and press **F2** (or right-click, **Rename...**) to give it a short display name of your own, such as "Budget" for `Q3_final_v7.xlsx`. Only the name on the shelf changes: the file itself is never renamed. Leave the box empty to go back to the file name. Search finds an item by either name, and sorting follows the name you see.
- **Tidy up:** the ⋮ button in the header (or a right-click on the empty space below the items) has **Sort A-Z** (numbers count by value, so `file2` comes before `file10`), **Folders first**, **Remove N missing items** (files that no longer exist), and **Undo** for the last one of those. Pinned items always stay on top. These are switched off while a search is active.
- **Select several:** Ctrl-click or Shift-click to pick more than one item, or press Ctrl+A for all of them. Then Enter opens them all (up to 10 at once), Delete removes them, and the right-click menu can copy all their paths (one per line), pin or unpin them together, or remove them. Dragging out takes the whole selection.
- **Drag out:** drag an item from the shelf into an email, a chat window or an Explorer folder. It is always copied, never moved, so your original file stays where it was.
- **More:** right-click an item for Open, Show in folder, Copy path, Pin to top, Move up, Move down and Remove. Delete also removes.
- **Pin:** pinned items stay at the top of the shelf, marked with a blue dot. Move up and Move down work inside the pinned group and inside the rest, so a pinned item never drifts down.
- **Search:** type in the box at the top. The box is ready to type in as soon as the shelf opens.
- **Keyboard only:** type to search, press **Enter** to open the first match, **Down** to move into the list (then Enter opens, Delete removes), **Ctrl+F** to get back to the search box, and **Escape** to clear the search and then close the shelf.
- **Theme:** the half-filled circle switches light and dark, and the app remembers your choice. Until you pick one it follows your Windows setting.
- **Tray icon:** left-click shows or hides the shelf. Right-click for **Start with Windows** and **Quit**.
- **Never freezes on a dead drive:** whether a file still exists, and its icon, are looked up on background threads. A file on a network share that is switched off no longer freezes the shelf: its row looks normal until the answer arrives, then shows as "(not found)". A file that disappears is noticed within a few seconds.
- **Resize:** drag the left edge of the open shelf to make it wider or narrower (240 to 640 px, and never more than half the screen). It remembers the width. On a narrower screen it shrinks to fit and goes back to your width on a bigger one.
- **Fits your screen:** the shelf shrinks on short screens so it is never cut off, and it re-checks the screen every couple of seconds and every time it opens, so docking a laptop, changing resolution or scaling, or moving the taskbar does not leave it off-screen.
- **One copy at a time:** starting Quick Shelf again (for example from the Start menu while it is already in the tray) does not open a second one. The copy that is already running slides the shelf open and the new one exits. It uses a port on this computer only (47831), and if another program owns that port Quick Shelf still runs.

Your shelf is saved in `%APPDATA%\QuickShelf\shelf.txt`, one path per line, with `[pinned]` in front of pinned ones and your display name after the path (separated by a tab). Your theme choice and shelf width are in `settings.properties` in the same folder (delete that file to go back to following Windows and the default width).

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
- [x] Day 13: the shelf fits short screens and follows screen changes (docking, resolution, taskbar)
- [x] Day 14: select several items (Ctrl/Shift-click, Ctrl+A) to open, pin, copy paths, remove or drag out together
- [x] Day 15: tidy-up menu: sort A-Z (natural order), folders first, remove missing items, undo
- [x] Day 16: drag the left edge to resize the shelf; the width is remembered
- [x] Day 17: file status and icons are looked up in the background, so a dead network drive cannot freeze the shelf
- [x] Day 18: give items a display name of your own (F2 or right-click, Rename...)
- [x] Day 19: paste files or paths from the clipboard (Ctrl+V or the menu)
- [ ] Later: named groups, a real installer (needs WiX), code signing
