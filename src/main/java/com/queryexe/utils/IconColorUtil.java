package com.queryexe.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Colors {@link FontIcon}s with arbitrary colors (hex literals or theme
 * variables like {@code -color-fg-default}) by injecting a tiny generated
 * stylesheet as a {@code data:text/css;base64,...} URI on the icon's parent.
 *
 * <p>Plain {@code setStyle("-fx-icon-color: ...")} can't resolve theme
 * variables on a {@code FontIcon}, so every colored/sized icon in the app
 * goes through this class instead of ad-hoc CSS classes.
 */
public class IconColorUtil {

    private static final Map<String, String> URI_CACHE = new ConcurrentHashMap<>();

    /** Creates a new icon and colors it. */
    public static FontIcon colored(Ikon ikon, String color, int size) {
        FontIcon icon = new FontIcon(ikon);
        apply(icon, color, size);
        return icon;
    }

    /**
     * Colors an existing icon by attaching a generated stylesheet to its
     * parent as soon as it's attached to the scene graph.
     */
    public static void apply(FontIcon icon, String color, int size) {
        String key = color + ":" + size;
        String cls = "dyn-icon-" + Integer.toHexString(key.hashCode());
        String uri = URI_CACHE.computeIfAbsent(key, k -> {
            String css = "." + cls + "{-fx-icon-color:" + color + ";-fx-icon-size:" + size + "px;}";
            return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
        });
        icon.getStyleClass().add(cls);
        icon.parentProperty().addListener((obs, old, parent) -> {
            if (parent != null && !parent.getStylesheets().contains(uri))
                parent.getStylesheets().add(uri);
        });
    }
}
