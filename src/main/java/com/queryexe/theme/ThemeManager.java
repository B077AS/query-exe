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
 * <p>The base stylesheet's {@code -color-accent-*} tokens are purple, and
 * (unlike a neutral-gray UI) several background/border/selection tokens in
 * style.css are also purple-tinted hex literals rather than pure grays. So
 * switching themes isn't just swapping the accent ramp: those extra tokens
 * are rotated by the same hue delta as the theme's accent color, keeping
 * their saturation/lightness identical to the default theme and making the
 * result read as a hue change rather than a new palette.
 *
 * <p>The override is a small {@code .root{...}} rule containing only the
 * tokens above, encoded as a {@code data:text/css;base64,...} stylesheet
 * (see {@link com.queryexe.utils.IconColorUtil} for the same technique) and
 * added to the app's single {@link Scene} after the base style.css, so it
 * wins the cascade for just those properties.
 */
@Slf4j
public class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();

    // Purple-tinted tokens in style.css that aren't part of the accent ramp
    // itself but still need to rotate with the chosen theme.
    private static final String BG_DEFAULT = "#282a36";
    private static final String BG_OVERLAY = "#22242f";
    private static final String BG_SUBTLE = "#3d3f4a";
    private static final String BG_INSET = "#181920";
    private static final String SELECTION_MUTED = "#68637c";
    private static final String SYNTAX_KEYWORD = "#ac80ea";

    private static final double BASE_HUE = ColorUtil.hueOf(AppTheme.PURPLE.getSwatchColor());

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
        double hueShift = ColorUtil.hueOf(theme.getSwatchColor()) - BASE_HUE;

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < ramp.length; i++) {
            sb.append("-color-accent-").append(i).append(':').append(ramp[i]).append(';');
        }

        sb.append("-color-accent-fg:").append(ramp[5]).append(';');
        sb.append("-color-accent-emphasis:").append(ramp[5]).append(';');
        sb.append("-color-accent-muted:").append(ColorUtil.rgba(ramp[5], 0.4)).append(';');
        sb.append("-color-accent-subtle:").append(ColorUtil.rgba(ramp[5], 0.1)).append(';');
        sb.append("-color-accent-9-alpha10:").append(ColorUtil.rgba(ramp[9], 0.1)).append(';');
        sb.append("-color-accent-9-alpha20:").append(ColorUtil.rgba(ramp[9], 0.2)).append(';');
        sb.append("-color-accent-9-alpha30:").append(ColorUtil.rgba(ramp[9], 0.3)).append(';');
        sb.append("-color-accent-9-alpha40:").append(ColorUtil.rgba(ramp[9], 0.4)).append(';');
        sb.append("-color-accent-7-alpha10:").append(ColorUtil.rgba(ramp[7], 0.1)).append(';');
        sb.append("-color-border-default:").append(ramp[7]).append(';');
        sb.append("-color-border-muted:").append(ramp[8]).append(';');
        sb.append("-color-border-subtle:").append(ramp[9]).append(';');

        sb.append("-color-selection-muted:").append(ColorUtil.rotateHex(SELECTION_MUTED, hueShift)).append(';');
        sb.append("-color-syntax-keyword:").append(ColorUtil.rotateHex(SYNTAX_KEYWORD, hueShift)).append(';');
        sb.append("-color-bg-default:").append(ColorUtil.rotateHex(BG_DEFAULT, hueShift)).append(';');
        sb.append("-color-bg-overlay:").append(ColorUtil.rotateHex(BG_OVERLAY, hueShift)).append(';');
        sb.append("-color-bg-subtle:").append(ColorUtil.rotateHex(BG_SUBTLE, hueShift)).append(';');
        sb.append("-color-bg-inset:").append(ColorUtil.rotateHex(BG_INSET, hueShift)).append(';');

        return sb.toString();
    }
}
