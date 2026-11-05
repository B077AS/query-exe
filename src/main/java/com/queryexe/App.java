package com.queryexe;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.queryexe.api.Api;
import com.queryexe.app.Params;
import com.queryexe.app.Rpc;
import com.queryexe.app.SystemClipboard;
import com.queryexe.app.ThemeRequest;
import com.queryexe.store.AppSettings;
import com.queryexe.connection.DatabaseConnection;

import io.github.b077as.jolt.AppOptions;
import io.github.b077as.jolt.AppResources;
import io.github.b077as.jolt.Application;
import io.github.b077as.jolt.ColorScheme;
import io.github.b077as.jolt.FileDialogOptions;
import io.github.b077as.jolt.FileFilter;
import io.github.b077as.jolt.WebWindow;
import io.github.b077as.jolt.WindowOptions;
import lombok.extern.slf4j.Slf4j;

/**
 * QueryExe: a database client for desktop. Java owns connections, SQL execution and storage; the Vue page in
 * {@code frontend/} is the whole UI, shown in a Jolt window (one process, no HTTP server).
 *
 * <p>Normally the page is the build output in {@code src/main/resources/ui}. With {@code -Dqueryexe.dev=true} it loads the
 * Vite dev server instead ({@code npm run dev} in {@code frontend/}) for hot reload. {@code -Dqueryexe.data=<folder>} uses
 * that folder instead of the real profile.
 */
@Slf4j
public class App {

    private static final String APP_ID = "com.queryexe.app";
    private static final String DEV_SERVER = "http://127.0.0.1:5173";
    /** The normal size of the window (what Restore gives after maximizing), and the smallest it can be resized to: the same as Parsel. */
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final int MIN_WIDTH = WINDOW_WIDTH;
    private static final int MIN_HEIGHT = WINDOW_HEIGHT;
    /** How long to wait for the page before showing the window anyway, so a broken page can never leave the app invisible. */
    private static final Duration SHOW_ANYWAY_AFTER = Duration.ofSeconds(10);

    private static final Gson GSON = new Gson();
    private static final String APPEARANCE = "appearance";
    private static final String UI = "ui";
    /** UI preferences the page may remember, and what kind of value each is. */
    private static final Map<String, Class<?>> UI_KEYS = Map.of(
            "treeWidth", Number.class, "resultHeight", Number.class, "connectionsView", String.class,
            "editorFontSize", Number.class, "rowLimitNotice", Boolean.class, "wordWrap", Boolean.class, "treeMode", String.class);
    private static final Set<String> CONNECTION_VIEWS = Set.of("grid", "list");
    private static final Set<String> TREE_MODES = Set.of("all", "focus");

    private App() {
    }

    public static void main(String[] args) {
        boolean dev = Boolean.getBoolean("queryexe.dev");
        boolean smoke = Boolean.getBoolean("queryexe.smoke");

        AppOptions appOptions = AppOptions.of(APP_ID).withQuietNativeLogs(true)
                .withDataDirectory(Launcher.getAppDataDirectory().resolve("webview"));
        Application app = Application.create(appOptions);
        try {
            app.run(() -> {
                Appearance saved = appearance();
                // Hidden until the page says its content is on screen (window.ready): while the web view starts, a visible
                // window shows an empty body for a second or two. A timer shows it anyway if the page never answers.
                WindowOptions options = inSavedColours(WindowOptions.defaults()
                        .withTitle("QueryExe")
                        .withSize(WINDOW_WIDTH, WINDOW_HEIGHT)
                        .withMinSize(MIN_WIDTH, MIN_HEIGHT)
                        .withMaximized(true)
                        .withIcon(readIcon())
                        .withDevTools(dev)
                        .withVisible(false), saved);
                WebWindow window = app.createWindow(options);
                showAnywayLater(window);
                window.addInitScript(bootScript(saved, dev));

                Rpc rpc = new Rpc(window);
                registerWindowRpc(app, window, rpc);
                new Api(rpc, fileChooser(window)).register();

                if (dev) {
                    // The dev server is another origin, so the (secure by default) policy must be told to trust it.
                    window.setSecurity(window.security().withTrustedOrigins(DEV_SERVER));
                    window.loadUrl(DEV_SERVER);
                } else {
                    window.loadResources(AppResources.classpath("ui"));
                }
                if (smoke) {
                    startSmokeCheck(window);
                }
            });
        } finally {
            DatabaseConnection.getInstance().shutdown();
            app.close();
        }
    }

    // ------------------------------------------------------------------------------------ appearance

    /** What the window needs at startup, before any page has loaded. */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class Appearance {
        private String preference;
        private String accent;
        private String scheme;
        private int background;
    }

    private static Appearance appearance() {
        JsonElement raw = AppSettings.get().getRaw(APPEARANCE);
        if (raw == null || !raw.isJsonObject()) {
            return new Appearance("system", "blue", null, 0);
        }
        JsonObject o = raw.getAsJsonObject();
        try {
            String preference = o.get("preference").getAsString();
            String accent = o.get("accent").getAsString();
            String scheme = o.has("scheme") && !o.get("scheme").isJsonNull() ? o.get("scheme").getAsString() : null;
            int background = o.has("background") ? o.get("background").getAsInt() : 0;
            if (!ThemeRequest.PREFERENCES.contains(preference) || !ThemeRequest.ACCENTS.contains(accent)) {
                return new Appearance("system", "blue", null, 0);
            }
            return new Appearance(preference, accent, scheme, background);
        } catch (RuntimeException e) {
            return new Appearance("system", "blue", null, 0);
        }
    }

    /** Starts a window in the colours the user last had, so a dark app never flashes white. */
    private static WindowOptions inSavedColours(WindowOptions options, Appearance saved) {
        if (saved.getScheme() == null) {
            return options;
        }
        return options
                .withTitleBarScheme("dark".equals(saved.getScheme()) ? ColorScheme.DARK : ColorScheme.LIGHT)
                .withBackgroundColor(saved.getBackground());
    }

    /** What the page reads before its first paint ({@code window.__QUERYEXE__}). Every value was validated when it was saved. */
    private static String bootScript(Appearance saved, boolean dev) {
        Map<String, Object> boot = new LinkedHashMap<>();
        boot.put("preference", saved.getPreference());
        boot.put("accent", saved.getAccent());
        boot.put("version", version());
        boot.put("dev", dev);
        boot.put("ui", uiSettings());
        return "window.__QUERYEXE__ = " + GSON.toJson(boot) + ";";
    }

    private static JsonObject uiSettings() {
        JsonElement raw = AppSettings.get().getRaw(UI);
        return raw != null && raw.isJsonObject() ? raw.getAsJsonObject() : new JsonObject();
    }

    private static String version() {
        try (InputStream in = App.class.getResourceAsStream("/app.properties")) {
            if (in != null) {
                java.util.Properties properties = new java.util.Properties();
                properties.load(in);
                return properties.getProperty("app.version", "dev");
            }
        } catch (IOException e) {
            log.debug("No app.properties: {}", e.getMessage());
        }
        return "dev";
    }

    // ------------------------------------------------------------------------------------ window rpc

    /** What the page can ask of the window itself: show, restyle the title bar, remember preferences, open links. */
    private static void registerWindowRpc(Application app, WebWindow window, Rpc rpc) {
        rpc.on("app.info", params -> Map.of(
                "version", version(),
                "os", System.getProperty("os.name"),
                "java", System.getProperty("java.version")));
        // The page has its content on screen: now the window can appear without a blank flash.
        rpc.on("window.ready", params -> {
            window.show();
            return Map.of("ok", true);
        });
        // The page cannot restyle the operating system's title bar, but it can ask Java to. The window icon is fixed: it
        // never takes the accent colour.
        rpc.on("window.setTheme", params -> {
            ThemeRequest theme = ThemeRequest.parse(params);
            window.setTitleBarScheme(theme.getScheme());
            window.setTitleBarColors(theme.getBackground(), theme.getText());
            window.setBackgroundColor(theme.getBackground());
            JsonObject stored = new JsonObject();
            stored.addProperty("preference", theme.getPreference());
            stored.addProperty("accent", theme.getAccent());
            stored.addProperty("scheme", theme.getScheme().name().toLowerCase(java.util.Locale.ROOT));
            stored.addProperty("background", theme.getBackground());
            AppSettings.get().setRaw(APPEARANCE, stored);
            return Map.of("ok", true);
        });
        rpc.on("settings.setUi", params -> {
            String key = Params.string(params, "key");
            Class<?> kind = UI_KEYS.get(key);
            JsonElement value = params.get("value");
            if (kind == null || value == null || !value.isJsonPrimitive()) {
                throw new IllegalArgumentException("Unknown setting: " + key);
            }
            boolean valid = kind == Number.class ? value.getAsJsonPrimitive().isNumber()
                    : kind == Boolean.class ? value.getAsJsonPrimitive().isBoolean() : value.getAsJsonPrimitive().isString();
            if (!valid || (key.equals("connectionsView") && !CONNECTION_VIEWS.contains(value.getAsString()))
                    || (key.equals("treeMode") && !TREE_MODES.contains(value.getAsString()))) {
                throw new IllegalArgumentException("Invalid value for " + key);
            }
            JsonObject ui = uiSettings().deepCopy();
            ui.add(key, value);
            AppSettings.get().setRaw(UI, ui);
            return Map.of("ok", true);
        });
        // The Paste item of the page's context menus (the web view lets the page copy but not read the clipboard; Ctrl+V is not affected).
        rpc.on("clipboard.readText", params -> Map.of("text", SystemClipboard.readText()));
        // A link clicked in the page. Jolt only opens http, https and mailto.
        rpc.on("app.openExternal", params -> {
            try {
                URI uri = new URI(Params.string(params, "url"));
                if (!app.openExternal(uri).get()) {
                    throw new IllegalStateException("The system could not open this link.");
                }
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("This is not a link that can be opened.");
            } catch (InterruptedException | ExecutionException e) {
                throw new IllegalStateException("The system could not open this link.");
            }
            return Map.of("ok", true);
        });
    }

    private static void showAnywayLater(WebWindow window) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(SHOW_ANYWAY_AFTER);
            } catch (InterruptedException e) {
                return;
            }
            window.show(); // harmless if the window is already showing
        });
    }

    /** The window icon: one .ico holding the same picture drawn at 16 to 256 px, so Windows never has to scale it. */
    private static byte[] readIcon() {
        try (InputStream in = App.class.getResourceAsStream("/icon.ico")) {
            return Objects.requireNonNull(in, "icon.ico is missing from the resources").readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------------------------ dialogs

    /** The system's file dialogs, owned by the window. Each call waits for the user, so it must not run on the UI thread. */
    private static com.queryexe.api.FileChooser fileChooser(WebWindow window) {
        return new com.queryexe.api.FileChooser() {
            @Override
            public Optional<Path> open(String title, String filterName, String... extensions) {
                return await(window.dialogs().openFile(FileDialogOptions.defaults().withTitle(title)
                        .withFilters(FileFilter.of(filterName, extensions))));
            }

            @Override
            public Optional<Path> save(String title, String suggestedName, String filterName, String... extensions) {
                return await(window.dialogs().saveFile(FileDialogOptions.defaults().withTitle(title)
                        .withFileName(suggestedName).withFilters(FileFilter.of(filterName, extensions))));
            }
        };
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the file dialog", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("The file dialog failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    /** Waits for the page to load, then reports what it rendered and closes the window. */
    private static void startSmokeCheck(WebWindow window) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(4_000);
                String script = "JSON.stringify({theme: document.documentElement.dataset.theme,"
                        + " accent: getComputedStyle(document.documentElement).getPropertyValue('--accent').trim(),"
                        + " title: document.title, cards: document.querySelectorAll('[data-connection]').length})";
                System.out.println("SMOKE " + window.eval(script).get());
            } catch (Exception e) {
                System.out.println("SMOKE failed: " + e);
            } finally {
                window.close();
            }
        });
    }

}
