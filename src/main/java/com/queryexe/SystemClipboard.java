package com.queryexe;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * Reads the text on the system clipboard for the page's Paste menu item.
 *
 * <p>The web view lets the page write to the clipboard (Copy and Cut) but Jolt refuses its request to read it, so a menu
 * cannot paste by itself; Ctrl+V is handled by the web view and is not affected. Until Jolt has a clipboard API this goes
 * through Java's own clipboard, which only gives plain text and needs a desktop AWT can reach (on Linux that means X11 or XWayland).
 */
public final class SystemClipboard {

    private static final int ATTEMPTS = 4;
    private static final long RETRY_MS = 60;

    private SystemClipboard() {}

    /**
     * @return the text on the clipboard, or an empty string if it holds something else (an image, files) or nothing
     * @throws IllegalStateException with a sentence for the user if the clipboard cannot be reached
     */
    public static String readText() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("The clipboard cannot be read here. Use Ctrl+V to paste.");
        }
        for (int attempt = 1; ; attempt++) {
            try {
                var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    return "";
                }
                return (String) clipboard.getData(DataFlavor.stringFlavor);
            } catch (HeadlessException e) {
                throw new IllegalStateException("The clipboard cannot be read here. Use Ctrl+V to paste.", e);
            } catch (UnsupportedFlavorException | IOException e) {
                return ""; // the content changed or is not text
            } catch (IllegalStateException e) {
                // Another program has the clipboard open for a moment; that passes.
                if (attempt >= ATTEMPTS) {
                    throw new IllegalStateException("The clipboard is in use by another program. Try again.", e);
                }
                try {
                    Thread.sleep(RETRY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Cancelled", interrupted);
                }
            }
        }
    }
}
