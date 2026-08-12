package com.queryexe.utils;

/**
 * Hex/HSL color math used by the theming system to derive alternate accent
 * hues from the app's base purple palette. Rotating only the hue channel
 * (keeping saturation/lightness fixed) is what makes a generated theme
 * "feel like" the original colors instead of an arbitrary new palette.
 */
public class ColorUtil {

    /** Hue (0-360) of a "#rrggbb" color. */
    public static double hueOf(String hex) {
        int[] rgb = hexToRgb(hex);
        return rgbToHsl(rgb[0], rgb[1], rgb[2])[0];
    }

    /** Rotates a "#rrggbb" color's hue by the given degrees, keeping saturation/lightness unchanged. */
    public static String rotateHex(String hex, double hueShiftDeg) {
        if (hueShiftDeg == 0) {
            return hex;
        }
        int[] rgb = hexToRgb(hex);
        int[] rotated = rotateRgb(rgb[0], rgb[1], rgb[2], hueShiftDeg);
        return rgbToHex(rotated[0], rotated[1], rotated[2]);
    }

    public static int[] rotateRgb(int r, int g, int b, double hueShiftDeg) {
        if (hueShiftDeg == 0) {
            return new int[]{r, g, b};
        }
        double[] hsl = rgbToHsl(r, g, b);
        double h = normalizeHue(hsl[0] + hueShiftDeg);
        double s = hsl[1] * perceptualSaturationScale(h);
        return hslToRgb(h, s, hsl[2]);
    }

    private static double perceptualSaturationScale(double hue) {
        double distance = Math.abs(hue - 120);
        if (distance > 180) {
            distance = 360 - distance;
        }
        if (distance >= 60) {
            return 1.0;
        }
        return 1.0 - 0.5 * (1.0 - distance / 60.0);
    }

    public static int[] hexToRgb(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int g = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        return new int[]{r, g, b};
    }

    public static String rgbToHex(int r, int g, int b) {
        return String.format("#%02x%02x%02x", clamp(r), clamp(g), clamp(b));
    }

    /** {@code rgba(r,g,b,alpha)} literal for a "#rrggbb" color. */
    public static String rgba(String hex, double alpha) {
        int[] rgb = hexToRgb(hex);
        return rgba(rgb, alpha);
    }

    public static String rgba(int[] rgb, double alpha) {
        return "rgba(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + "," + alpha + ")";
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static double normalizeHue(double h) {
        double r = h % 360;
        return r < 0 ? r + 360 : r;
    }

    /** Returns {@code {hue(0-360), saturation(0-1), lightness(0-1)}}. */
    public static double[] rgbToHsl(int r, int g, int b) {
        double rf = r / 255.0, gf = g / 255.0, bf = b / 255.0;
        double max = Math.max(rf, Math.max(gf, bf));
        double min = Math.min(rf, Math.min(gf, bf));
        double l = (max + min) / 2.0;
        double d = max - min;

        double h;
        double s;
        if (d == 0) {
            h = 0;
            s = 0;
        } else {
            s = l > 0.5 ? d / (2.0 - max - min) : d / (max + min);
            if (max == rf) {
                h = ((gf - bf) / d) % 6;
            } else if (max == gf) {
                h = (bf - rf) / d + 2;
            } else {
                h = (rf - gf) / d + 4;
            }
            h *= 60;
            if (h < 0) {
                h += 360;
            }
        }
        return new double[]{h, s, l};
    }

    public static int[] hslToRgb(double h, double s, double l) {
        if (s == 0) {
            int v = (int) Math.round(l * 255);
            return new int[]{v, v, v};
        }
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
        double m = l - c / 2.0;
        double rf, gf, bf;
        if (h < 60) {
            rf = c; gf = x; bf = 0;
        } else if (h < 120) {
            rf = x; gf = c; bf = 0;
        } else if (h < 180) {
            rf = 0; gf = c; bf = x;
        } else if (h < 240) {
            rf = 0; gf = x; bf = c;
        } else if (h < 300) {
            rf = x; gf = 0; bf = c;
        } else {
            rf = c; gf = 0; bf = x;
        }
        return new int[]{
                (int) Math.round((rf + m) * 255),
                (int) Math.round((gf + m) * 255),
                (int) Math.round((bf + m) * 255)
        };
    }
}
