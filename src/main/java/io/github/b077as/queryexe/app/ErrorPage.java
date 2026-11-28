package io.github.b077as.queryexe.app;

/**
 * The page shown, with {@code loadHtml}, when the app's own interface cannot be: it failed to load or keeps crashing. One
 * self-contained string with the dark or light colours of the app, because nothing of the real page is available.
 */
public final class ErrorPage {

    private ErrorPage() {
    }

    /**
     * @param retryUrl an address to offer as "Try again", or null for none
     */
    public static String html(boolean dark, String title, String text, String retryUrl) {
        String background = dark ? "#2b2d30" : "#f3f3f1";
        String foreground = dark ? "#dfe1e5" : "#1c1c1a";
        String muted = dark ? "#9a9ea6" : "#55554f";
        String retry = retryUrl == null ? "" : "<p><a href=\"" + escape(retryUrl) + "\">Try again</a></p>";
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><title>QueryExe</title><style>
                html,body{height:100%%;margin:0}
                body{display:grid;place-content:center;text-align:center;padding:0 24px;background:%s;color:%s;
                font:13px/1.5 system-ui,'Segoe UI','Noto Sans',sans-serif}
                h1{font-size:18px;font-weight:600;margin:0 0 8px}
                p{margin:4px 0;color:%s;max-width:560px;overflow-wrap:anywhere}
                a{color:%s}
                </style></head><body><h1>%s</h1><p>%s</p>%s</body></html>
                """.formatted(background, foreground, muted, foreground, escape(title), escape(text), retry);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
