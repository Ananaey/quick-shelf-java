package quickshelf;

import java.awt.Color;

/** A colour palette. Two are built in: LIGHT and DARK. */
public final class Theme {
    public static final Theme DARK = new Theme(
            "dark",
            new Color(30, 30, 34),
            new Color(242, 242, 247),
            new Color(154, 154, 162),
            new Color(255, 255, 255, 28),
            new Color(10, 132, 255),
            new Color(60, 60, 68),
            new Color(95, 95, 108),
            new Color(52, 52, 58));

    public static final Theme LIGHT = new Theme(
            "light",
            new Color(246, 246, 248),
            new Color(28, 28, 30),
            new Color(110, 110, 115),
            new Color(0, 0, 0, 20),
            new Color(10, 132, 255),
            new Color(200, 200, 205),
            new Color(170, 170, 178),
            new Color(228, 228, 232));

    public final String name;
    public final Color panelBackground;
    public final Color text;
    public final Color muted;
    public final Color hover;
    public final Color selected;
    public final Color handle;
    public final Color handleHover;
    public final Color field;

    private Theme(String name, Color panelBackground, Color text, Color muted, Color hover,
                  Color selected, Color handle, Color handleHover, Color field) {
        this.name = name;
        this.panelBackground = panelBackground;
        this.text = text;
        this.muted = muted;
        this.hover = hover;
        this.selected = selected;
        this.handle = handle;
        this.handleHover = handleHover;
        this.field = field;
    }

    public Theme toggled() {
        return this == DARK ? LIGHT : DARK;
    }

    @Override
    public String toString() {
        return name;
    }
}
