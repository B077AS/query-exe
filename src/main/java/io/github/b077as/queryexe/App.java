package io.github.b077as.queryexe;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.b077as.queryexe.api.Api;
import io.github.b077as.queryexe.app.Async;
import io.github.b077as.queryexe.app.ErrorPage;
import io.github.b077as.queryexe.app.OnScreen;
import io.github.b077as.queryexe.app.PageLifecycle;
import io.github.b077as.queryexe.app.PageRpc;
import io.github.b077as.queryexe.app.Params;
import io.github.b077as.jolt.Rpc;
import io.github.b077as.queryexe.app.ThemeRequest;
import io.github.b077as.queryexe.store.AppSettings;
import io.github.b077as.queryexe.connection.DatabaseConnection;

import io.github.b077as.jolt.AppOptions;
import io.github.b077as.jolt.AppResources;
import io.github.b077as.jolt.Application;
import io.github.b077as.jolt.CloseDecision;
import io.github.b077as.jolt.ColorScheme;
import io.github.b077as.jolt.CrashEvent;
import io.github.b077as.jolt.FileDialogOptions;
import io.github.b077as.jolt.FileFilter;
import io.github.b077as.jolt.LoadFailure;
import io.github.b077as.jolt.SecurityPolicy;
import io.github.b077as.jolt.SessionEndReason;
import io.github.b077as.jolt.TaskbarProgress;
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
 *
 * <p>The window asks the page before it closes ({@link PageLifecycle}), reloads a page whose process crashed, is made again
 * when the whole web view is lost, and closes the databases when the system ends the session.
 */
@Slf4j
public class App {

    private static final String APP_ID = "com.queryexe.app";
    /** Dark with the rose accent until the user chooses otherwise; the colour is the dark sidebar (#2b2d30), so the first paint is dark too. */
    private static final Appearance DEFAULT_APPEARANCE = new Appearance("dark", "rose", "dark", 0x2B2D30);
    private static final String DEV_SERVER = "http://127.0.0.1:5173";
    /** The normal size of the window (what Restore gives after maximizing), and the smallest it can be resized to: the same as Parsel. */
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final int MIN_WIDTH = WINDOW_WIDTH;
    private static final int MIN_HEIGHT = WINDOW_HEIGHT;
    /** How long to wait for the page before showing the window anyway, so a broken page can never leave the app invisible. */
    private static final Duration SHOW_ANYWAY_AFTER = Duration.ofSeconds(10);
    /** How many times the window is made again after the whole web view was lost, before giving up. */
    private static final int MAX_NEW_WINDOWS = 3;
    /** The system may end the process soon after announcing the end of a session. */
    private static final Duration SESSION_END_WAIT = Duration.ofSeconds(4);
    /** How long closing waits for the query tabs' connections before closing everything regardless. */
    private static final Duration CLOSE_TABS_WAIT = Duration.ofSeconds(3);
    private static final Duration SMOKE_WAIT = Duration.ofSeconds(20);

    private static final Gson GSON = new Gson();
    private static final String APPEARANCE = "appearance";
    private static final String UI = "ui";
    /** UI preferences the page may remember, and what kind of value each is. */
    private static final Map<String, Class<?>> UI_KEYS = Map.of(
            "treeWidth", Number.class, "resultHeight", Number.class, "connectionsView", String.class,
            "editorFontSize", Number.class, "rowLimitNotice", Boolean.class, "wordWrap", Boolean.class, "treeMode", String.class);
    private static final Set<String> CONNECTION_VIEWS = Set.of("grid", "list");
    private static final Set<String> TREE_MODES = Set.of("all", "focus");

    private final Application app;
    private final boolean dev;
    private final boolean smoke;
    /** The API of the window that is open now. */
    private volatile Api api;
    private int newWindows;
    private CompletableFuture<Void> closingDatabases;

    private App(Application app, boolean dev, boolean smoke) {
        this.app = app;
        this.dev = dev;
        this.smoke = smoke;
    }

    public static void main(String[] args) {
        boolean dev = Boolean.getBoolean("queryexe.dev");
        boolean smoke = Boolean.getBoolean("queryexe.smoke");

        // After a self-update the installer's version in Apps & features is stale; fix it off the start-up path.
        Thread.ofVirtual().start(io.github.b077as.queryexe.app.AppsFeatures::syncVersion);

        AppOptions appOptions = AppOptions.of(APP_ID).withQuietNativeLogs(true)
                .withDataDirectory(Launcher.getAppDataDirectory().resolve("webview"));
        Application app = Application.create(appOptions);
        App queryExe = new App(app, dev, smoke);
        try {
            app.run(() -> {
                app.onSessionEnd(queryExe::onSessionEnd);
                queryExe.openWindow(false);
            });
        } finally {
            queryExe.closeDatabases().join();
            app.close();
        }
    }

    // ------------------------------------------------------------------------------------ the window

    /** @param replacement whether this window takes the place of one whose web view was lost. UI thread. */
    private void openWindow(boolean replacement) {
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
                .withSecurity(security(dev))
                .withVisible(false), saved);
        WebWindow window = app.createWindow(options);
        showAnywayLater(app, window);
        window.addInitScript(bootScript(saved, dev));

        Rpc rpc = PageRpc.create(window);
        PageLifecycle page = new PageLifecycle(rpc::emit, replacement);
        Api windowApi = new Api(rpc, fileChooser(window));
        CompletableFuture<Void> ready = new CompletableFuture<>();
        registerWindowRpc(window, rpc, page, windowApi, ready);
        windowApi.register();
        api = windowApi;
        watchWindow(window, page, ready);

        if (smoke) {
            // collected from the first moment, so the check can report anything the Content-Security-Policy refused
            window.addInitScript("window.__cspViolations = []; document.addEventListener('securitypolicyviolation',"
                    + " (e) => window.__cspViolations.push(e.violatedDirective + ' ' + e.blockedURI));");
        }
        if (dev) {
            window.loadUrl(DEV_SERVER);
        } else {
            window.loadResources(AppResources.classpath("ui"));
        }
        if (smoke) {
            startSmokeCheck(window, ready);
        }
    }

    /**
     * Jolt's secure defaults, minus the Content-Security-Policy source for binary channels (none are opened here, and on
     * Linux it would also let the page send HTTP requests to this machine). The dev server is another origin, so the policy
     * must be told to trust it.
     */
    static SecurityPolicy security(boolean dev) {
        SecurityPolicy policy = SecurityPolicy.standard()
                .withAppContentSecurityPolicy(SecurityPolicy.DEFAULT_APP_CSP.replace(" ws://127.0.0.1:*", ""));
        return dev ? policy.withTrustedOrigins(DEV_SERVER) : policy;
    }

    /** What happens when the window is asked to close, or its page is in trouble. */
    private void watchWindow(WebWindow window, PageLifecycle page, CompletableFuture<Void> ready) {
        window.onCloseRequest(() -> CloseDecision.when(page.askToClose()));
        window.setReloadOnCrash(true);
        window.onCrash(event -> onCrash(window, page, event));
        window.onLoadFailed(failure -> {
            onLoadFailed(window, page, failure);
            ready.completeExceptionally(new IllegalStateException("The page failed to load: " + failure));
        });
        window.onBlocked(event -> {
            String url = String.valueOf(event.url());
            log.warn("Blocked {}: {}", event.kind(), url.length() > 200 ? url.substring(0, 200) + "..." : url);
        });
    }

    private void onCrash(WebWindow window, PageLifecycle page, CrashEvent event) {
        if (event.reason() == CrashEvent.Reason.UNRESPONSIVE) {
            return; // it may recover; a close request still gets through (see PageLifecycle)
        }
        log.error("The page crashed: {} (reloading: {})", event.reason(), event.reloading());
        page.gone();
        if (event.reason() == CrashEvent.Reason.WEB_VIEW_LOST) {
            replaceWindow(window);
        } else if (!event.reloading()) {
            api.closePageState();
            showError(window, "The interface keeps crashing", "Close the window and start QueryExe again.", null);
        }
        // otherwise the page is loading again, and window.attach drops what the old one left behind
    }

    /** The whole web view is gone, so reloading cannot help: a new window takes over, with the connections still open. */
    private void replaceWindow(WebWindow lost) {
        try {
            if (++newWindows > MAX_NEW_WINDOWS) {
                log.error("The web view was lost {} times; closing", newWindows);
                return;
            }
            api.closePageState();
            openWindow(true);
        } finally {
            lost.close(); // after the new window exists: closing the last window ends the application
        }
    }

    private void onLoadFailed(WebWindow window, PageLifecycle page, LoadFailure failure) {
        log.error("Could not load the page {}: {} ({})", failure.url(), failure.reason(), failure.detail());
        page.gone();
        String text = dev
                ? "Nothing usable answers at " + DEV_SERVER + ". Run \"npm run dev\" in frontend/."
                : "The page could not be loaded (" + failure.detail() + "). Reinstall QueryExe; the log has the details.";
        showError(window, "QueryExe could not open its interface", text, dev ? DEV_SERVER : null);
    }

    private void showError(WebWindow window, String title, String text, String retryUrl) {
        boolean dark = !"light".equals(appearance().getScheme());
        window.loadHtml(ErrorPage.html(dark, title, text, retryUrl));
        OnScreen.show(app, window);
    }

    // ------------------------------------------------------------------------------------ ending

    /** Sign-out, shutdown or termination: close the databases now, because there may be no later. */
    private void onSessionEnd(SessionEndReason reason) {
        log.info("The session is ending ({}): closing the database connections", reason);
        try {
            closeDatabases().get(SESSION_END_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.warn("The databases did not close in time: {}", e.toString());
        }
    }

    /** Rolls back what is uncommitted and closes every connection. Done once; later calls get the same future. */
    private synchronized CompletableFuture<Void> closeDatabases() {
        if (closingDatabases == null) {
            CompletableFuture<Void> tabs = api == null ? CompletableFuture.completedFuture(null) : api.closePageState();
            closingDatabases = tabs
                    .orTimeout(CLOSE_TABS_WAIT.toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(e -> {
                        log.warn("Not every query tab closed: {}", e.toString());
                        return null;
                    })
                    .thenRunAsync(() -> DatabaseConnection.getInstance().shutdown(), Async::run)
                    .exceptionally(e -> {
                        log.warn("Could not close the connections: {}", e.toString());
                        return null;
                    });
        }
        return closingDatabases;
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
            return DEFAULT_APPEARANCE;
        }
        JsonObject o = raw.getAsJsonObject();
        try {
            String preference = o.get("preference").getAsString();
            String accent = o.get("accent").getAsString();
            String scheme = o.has("scheme") && !o.get("scheme").isJsonNull() ? o.get("scheme").getAsString() : null;
            int background = o.has("background") ? o.get("background").getAsInt() : 0;
            if (!ThemeRequest.PREFERENCES.contains(preference) || !ThemeRequest.ACCENTS.contains(accent)) {
                return DEFAULT_APPEARANCE;
            }
            return new Appearance(preference, accent, scheme, background);
        } catch (RuntimeException e) {
            return DEFAULT_APPEARANCE;
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

    /** A reloaded page must start from what is saved now, not from what was saved when the window was made. */
    private void refreshBootScript(WebWindow window) {
        window.addInitScript(bootScript(appearance(), dev)); // runs after the earlier ones, so it wins
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

    /** What the page can ask of the window itself: show, close, restyle the title bar, remember preferences, open links. */
    private void registerWindowRpc(WebWindow window, Rpc rpc, PageLifecycle page, Api windowApi, CompletableFuture<Void> ready) {
        rpc.on("app.info", JsonObject.class, params -> Map.of(
                "version", version(),
                "os", System.getProperty("os.name"),
                "java", System.getProperty("java.version")));
        // The first thing a page does. After a crash or a reload the earlier page's tabs are dropped, and the new one is told
        // so it can show the connections that are still open.
        rpc.on("window.attach", JsonObject.class, params -> {
            boolean reloaded = page.attach();
            if (reloaded) {
                windowApi.closePageState();
                window.setTaskbarProgress(TaskbarProgress.none()); // the earlier page may have left it moving
            }
            return Map.of("reloaded", reloaded);
        });
        // The page has its content on screen: now the window can appear without a blank flash.
        // OnScreen also tells the launcher, which waits for that to close.
        rpc.on("window.ready", JsonObject.class, params -> {
            OnScreen.show(app, window);
            page.ready();
            ready.complete(null);
            return Map.of("ok", true);
        });
        // The replies to window.closeRequested: heard, and then what the user decided.
        rpc.on("window.closeAsked", JsonObject.class, params -> {
            page.acknowledge(Params.integer(params, "id"));
            return Map.of("ok", true);
        });
        rpc.on("window.closeAnswer", JsonObject.class, params -> {
            page.answer(Params.integer(params, "id"), Params.bool(params, "close"));
            return Map.of("ok", true);
        });
        // Files dropped on the window (Jolt 0.4.1): the paths come from the operating system, never from the page. The page
        // decides what to do with them (.sql files open as query tabs). Registered before the page loads.
        window.onFileDrop(drop -> {
            if (!"scripts".equals(drop.zone())) {
                return; // only the editor area (data-jolt-drop="scripts") takes files, not the toolbar or the tree
            }
            List<Map<String, String>> files = new ArrayList<>();
            for (Path file : drop.files()) {
                Path name = file.getFileName();
                files.add(Map.of("path", file.toString(), "name", name == null ? file.toString() : name.toString()));
            }
            rpc.emit("files.dropped", Map.of("files", files));
        });
        // A moving bar on the taskbar icon while a query runs (Windows; a launcher that draws it on Linux; harmless elsewhere).
        rpc.on("window.setBusy", JsonObject.class, params -> {
            window.setTaskbarProgress(Params.bool(params, "busy") ? TaskbarProgress.indeterminate() : TaskbarProgress.none());
            return Map.of("ok", true);
        });
        // The page cannot restyle the operating system's title bar, but it can ask Java to. The window icon is fixed: it
        // never takes the accent colour.
        rpc.on("window.setTheme", JsonObject.class, params -> {
            ThemeRequest theme = ThemeRequest.parse(params);
            window.setTitleBarScheme(theme.getScheme());
            window.setTitleBarColors(theme.getBackground(), theme.getText());
            window.setBackgroundColor(theme.getBackground());
            JsonObject stored = new JsonObject();
            stored.addProperty("preference", theme.getPreference());
            stored.addProperty("accent", theme.getAccent());
            stored.addProperty("scheme", theme.getScheme().name().toLowerCase(java.util.Locale.ROOT));
            stored.addProperty("background", theme.getBackground());
            if (!stored.equals(AppSettings.get().getRaw(APPEARANCE))) {
                AppSettings.get().setRaw(APPEARANCE, stored);
                refreshBootScript(window);
            }
            return Map.of("ok", true);
        });
        rpc.on("settings.setUi", JsonObject.class, params -> {
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
            refreshBootScript(window);
            return Map.of("ok", true);
        });
        // The Paste item of the page's context menus (the web view lets the page copy but not read the clipboard; Ctrl+V is not affected).
        // Jolt's own clipboard (plain text only; an image or files on it read as an empty string).
        rpc.on("clipboard.readText", JsonObject.class, params -> {
            try {
                return Map.of("text", app.clipboard().readText().get(5, TimeUnit.SECONDS).orElse(""));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Cancelled", e);
            } catch (ExecutionException | TimeoutException e) {
                log.debug("Could not read the clipboard: {}", e.toString());
                throw new IllegalStateException("The clipboard cannot be read now. Use Ctrl+V to paste.", e);
            }
        });
        // A link clicked in the page. Jolt only opens http, https and mailto.
        rpc.on("app.openExternal", JsonObject.class, params -> {
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

    private static void showAnywayLater(Application app, WebWindow window) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(SHOW_ANYWAY_AFTER);
            } catch (InterruptedException e) {
                return;
            }
            OnScreen.show(app, window); // harmless if the window is already showing
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
    private static io.github.b077as.queryexe.api.FileChooser fileChooser(WebWindow window) {
        return new io.github.b077as.queryexe.api.FileChooser() {
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

    /** Waits for the page to show its content, then reports what it rendered and closes the window. */
    private static void startSmokeCheck(WebWindow window, CompletableFuture<Void> ready) {
        Thread.ofVirtual().start(() -> {
            try {
                ready.get(SMOKE_WAIT.toMillis(), TimeUnit.MILLISECONDS);
                Thread.sleep(1_000);
                String script = "JSON.stringify({theme: document.documentElement.dataset.theme,"
                        + " accent: getComputedStyle(document.documentElement).getPropertyValue('--accent').trim(),"
                        + " title: document.title, cards: document.querySelectorAll('[data-connection]').length,"
                        + " csp: window.__cspViolations})";
                System.out.println("SMOKE " + window.eval(script).get());
            } catch (Exception e) {
                System.out.println("SMOKE failed: " + e);
            } finally {
                window.close();
            }
        });
    }

}
