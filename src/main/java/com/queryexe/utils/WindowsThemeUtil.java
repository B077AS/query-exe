package com.queryexe.utils;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public class WindowsThemeUtil {

    private static final int DWMWA_CLOAK                  = 13;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int WM_NCPAINT                   = 0x0085;
    private static final int SWP_NOMOVE                   = 0x0002;
    private static final int SWP_NOSIZE                   = 0x0001;
    private static final int SWP_NOZORDER                 = 0x0004;
    private static final int SWP_FRAMECHANGED             = 0x0020;

    public interface DwmApi extends StdCallLibrary {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class, W32APIOptions.DEFAULT_OPTIONS);
        int DwmSetWindowAttribute(HWND hwnd, int attribute, Object value, int valueSize);
        int DwmFlush();
    }

    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);
        HWND FindWindowEx(HWND hwndParent, HWND hwndChildAfter, String lpszClass, String lpszWindow);
        HWND GetActiveWindow();
        boolean SetWindowPos(HWND hwnd, HWND hwndInsertAfter, int x, int y, int cx, int cy, int flags);
        boolean SendMessageW(HWND hwnd, int msg, WPARAM wParam, LPARAM lParam);
    }

    /**
     * Applies dark mode to every top-level window whose title matches windowTitle.
     * Using FindWindowEx in a loop handles multiple app instances with the same title.
     * Must be called from a background thread — DwmFlush() blocks until DWM commits a frame.
     */
    public static void enableDarkTitleBar(String windowTitle) {
        if (!Platform.isWindows()) return;

        try {
            HWND hwnd = User32.INSTANCE.FindWindowEx(null, null, null, windowTitle);

            if (hwnd == null) {
                hwnd = User32.INSTANCE.GetActiveWindow();
                if (hwnd == null) { System.err.println("Could not find window"); return; }
                applyDarkMode(hwnd);
                return;
            }

            while (hwnd != null) {
                applyDarkMode(hwnd);
                hwnd = User32.INSTANCE.FindWindowEx(null, hwnd, null, windowTitle);
            }

        } catch (Exception e) {
            System.err.println("Could not enable dark title bar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void applyDarkMode(HWND hwnd) {
        // Cloak so DWM never composites a light frame — no animation possible.
        DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_CLOAK, new int[]{1}, 4);

        DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, new int[]{1}, 4);

        // Prod DWM to register the NC change.
        User32.INSTANCE.SendMessageW(hwnd, WM_NCPAINT, new WPARAM(1), new LPARAM(0));
        User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED);

        // Block until DWM commits one frame — the event-driven gate.
        DwmApi.INSTANCE.DwmFlush();

        // Uncloak: DWM composites the window for the first time already dark.
        DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_CLOAK, new int[]{0}, 4);
    }
}
