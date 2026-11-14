package dev.mercury.client.fx;

/** Utilidades de color (RGB 0xRRGGBB, alpha aparte). */
public final class Col {
    private Col() {}

    public static int argb(int rgb, float a) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static int c255(float v) { return Math.max(0, Math.min(255, Math.round(v * 255f))); }

    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float hh = h * 6f;
        int i = (int) hh;
        float f = hh - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (c255(r) << 16) | (c255(g) << 8) | c255(b);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int scale(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * k));
        int b = Math.min(255, (int) ((rgb & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    public static int add(int a, int b) {
        int r = Math.min(255, ((a >> 16) & 255) + ((b >> 16) & 255));
        int g = Math.min(255, ((a >> 8) & 255) + ((b >> 8) & 255));
        int bl = Math.min(255, (a & 255) + (b & 255));
        return (r << 16) | (g << 8) | bl;
    }
}
