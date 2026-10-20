package com.queryexe.theme;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import lombok.extern.slf4j.Slf4j;

import com.queryexe.service.AppSettings;
import com.queryexe.utils.ColorUtil;

/**
 * Applies and persists the app's accent {@link AppTheme}.
 *
 * <p>Same scheme as the komm project: style.css defines every neutral (backgrounds,
 * foregrounds, borders, syntax colors) once, and a theme only swaps the
 * {@code -color-accent-*} ramp plus the accent-tinted {@code -color-highlight}
 * (text selection). Borders deliberately stay neutral gray for every theme, so the
 * accent shows up only where it signals something (focus, selection, primary actions).
 *
 * <p>The override is a small {@code .root{...}} rule containing only those tokens,
 * encoded as a {@code data:text/css;base64,...} stylesheet (see
 * {@link com.queryexe.utils.IconColorUtil} for the same technique) and added to the
 * app's single {@link Scene} after the base style.css, so it wins the cascade for just
 * those properties.
 */
@Slf4j
public class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();

    private final ObjectProperty<AppTheme> currentTheme = new SimpleObjectProperty<>(AppTheme.PURPLE);
    private String appliedStylesheetUri;

    private ThemeManager() {
    }

    public static ThemeManager get() {
        return INSTANCE;
    }

    public AppTheme getCurrentTheme() {
        return currentTheme.get();
    }

    public ObjectProperty<AppTheme> themeProperty() {
        return currentTheme;
    }

    /** Loads the theme saved in settings.json (if any) and applies it to the scene. */
    public void applyFromSettings(Scene scene) {
        AppTheme theme = AppTheme.PURPLE;
        String saved = AppSettings.get().getTheme();
        if (saved != null) {
            try {
                theme = AppTheme.valueOf(saved);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown saved theme '{}', falling back to default", saved);
            }
        }
        apply(theme, scene, false);
    }

    /** Applies a theme to the given scene immediately and persists the selection. */
    public void apply(AppTheme theme, Scene scene) {
        apply(theme, scene, true);
    }

    private void apply(AppTheme theme, Scene scene, boolean persist) {
        if (scene != null) {
            if (appliedStylesheetUri != null) {
                scene.getStylesheets().remove(appliedStylesheetUri);
            }
            appliedStylesheetUri = buildStylesheetUri(theme);
            scene.getStylesheets().add(appliedStylesheetUri);
        }
        currentTheme.set(theme);
        if (persist) {
            AppSettings.get().setTheme(theme.name());
        }
    }

    private String buildStylesheetUri(AppTheme theme) {
        String css = ".root{" + buildOverrideBody(theme) + "}";
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    private String buildOverrideBody(AppTheme theme) {
        String[] ramp = theme.getRamp();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < ramp.length; i++) {
            sb.append("-color-accent-").append(i).append(':').append(ramp[i]).append(';');
        }

        sb.append("-color-accent-fg:").append(ramp[5]).append(';');
        sb.append("-color-accent-emphasis:").append(ramp[5]).append(';');
        sb.append("-color-accent-muted:").append(ColorUtil.rgba(ramp[5], 0.4)).append(';');
        sb.append("-color-accent-subtle:").append(ColorUtil.rgba(ramp[5], 0.1)).append(';');
        sb.append("-color-fg-self:").append(ramp[1]).append(';');
        sb.append("-color-highlight:").append(theme.getHighlight()).append(';');

        return sb.toString();
    }
}
