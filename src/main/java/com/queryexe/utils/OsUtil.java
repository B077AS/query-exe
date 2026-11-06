package com.queryexe.utils;

/**
 * Platform helpers for OS-specific UI behavior.
 * <p>
 * Keyboard shortcuts use JavaFX's "shortcut" modifier
 * ({@link javafx.scene.input.KeyCombination#SHORTCUT_DOWN},
 * {@link javafx.scene.input.KeyEvent#isShortcutDown()}), which is Cmd on macOS
 * and Ctrl everywhere else. {@link #SHORTCUT_KEY} is the matching label for UI text.
 */
public final class OsUtil {

    public static final boolean IS_MAC =
            System.getProperty("os.name", "").toLowerCase().startsWith("mac");

    /** Display name of the platform shortcut modifier: "⌘" on macOS, "Ctrl" elsewhere. */
    public static final String SHORTCUT_KEY = IS_MAC ? "⌘" : "Ctrl";

    private OsUtil() {
    }
}
