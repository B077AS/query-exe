package com.queryexe.theme;

/**
 * Selectable accent color schemes. Each theme is a 10-step tint/shade ramp
 * (c0 lightest tint through c9 darkest shade, c5 the "true" accent color),
 * hand-tuned to match the visual weight of the app's default purple ramp so
 * swapping themes reads as a hue change rather than a different palette.
 * The purple ramp itself is shared with the komm project's theme picker.
 */
public enum AppTheme {

    PURPLE("Purple",
            "#e5dfff", "#d5ccff", "#c5b9ff", "#b5a6ff", "#a593ff",
            "#9580ff", "#7f6dd9", "#685ab3", "#52468c", "#3c3366"),

    RED("Red",
            "#ffe5ea", "#ffccd5", "#ffb3bf", "#ff99aa", "#ff6680",
            "#ff3355", "#d92244", "#b31133", "#8c0022", "#660011"),

    ORANGE("Orange",
            "#fff0e5", "#ffe2cc", "#ffd4b3", "#ffc699", "#ffb880",
            "#ff8c42", "#d97538", "#b35e2d", "#8c4722", "#663017"),

    YELLOW("Yellow",
            "#fff9e5", "#fff3cc", "#ffedb3", "#ffe799", "#ffde6a",
            "#f0c040", "#cca030", "#a88020", "#846010", "#604800"),

    GREEN("Green",
            "#e5fff4", "#ccffe9", "#b3ffde", "#80ffc8", "#65e0a8",
            "#50d090", "#40b078", "#328c5e", "#246844", "#16442c"),

    LIGHT_GREEN("Light Green",
            "#f0ffe5", "#e2ffcc", "#d4ffb3", "#c6ff99", "#b8ff80",
            "#a0ff60", "#80d948", "#60b330", "#448c1a", "#2c6606"),

    TEAL("Teal",
            "#e0fafc", "#c0f5f9", "#a0f0f6", "#60e6f0", "#30d6e8",
            "#00c8d8", "#00a8b5", "#008890", "#00686c", "#004848"),

    LIGHT_BLUE("Light Blue",
            "#e5f6ff", "#ccedff", "#b3e4ff", "#99dbff", "#80d2ff",
            "#7dd3fc", "#5cb0d4", "#3d8dac", "#1f6a84", "#09485c"),

    BLUE("Blue",
            "#e5eeff", "#ccdcff", "#b3cbff", "#99b9ff", "#80a8ff",
            "#5b9cf6", "#4478cc", "#2d56a2", "#183578", "#09194e"),

    PINK("Pink",
            "#ffe5f5", "#ffcce9", "#ffb3de", "#ff99d2", "#ff80c7",
            "#ff79c6", "#d95ea0", "#b3437c", "#8c2858", "#660c34"),

    MAGENTA("Magenta",
            "#fae5ff", "#f5ccff", "#f0b3ff", "#eb99ff", "#e680ff",
            "#e040fb", "#bc30d4", "#9820ac", "#741084", "#50005c");

    private final String displayName;
    private final String[] ramp;

    AppTheme(String displayName, String c0, String c1, String c2, String c3, String c4,
             String c5, String c6, String c7, String c8, String c9) {
        this.displayName = displayName;
        this.ramp = new String[]{c0, c1, c2, c3, c4, c5, c6, c7, c8, c9};
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The 10-step tint/shade ramp, index 0 (lightest) through 9 (darkest). */
    public String[] getRamp() {
        return ramp;
    }

    public String getAccent(int index) {
        return ramp[index];
    }

    /** The "true" accent color (ramp index 5), used for swatches and previews. */
    public String getSwatchColor() {
        return ramp[5];
    }
}
