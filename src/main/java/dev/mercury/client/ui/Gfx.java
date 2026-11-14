package dev.mercury.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Helpers de dibujo: esquinas redondeadas, brillos, easing. Solo usa fill()/fillGradient(). */
public final class Gfx {
    public static final int ACC = 0xFF8EC5FF;        // azul hielo
    public static final int PANEL = 0xD0181A20;      // gris oscuro traslucido
    public static final int CARD = 0x38FFFFFF;
    public static final int TEXT = 0xFFF2F4F8;
    public static final int DIM = 0xFF9AA0AE;

    private Gfx() {}

    public static float clamp(float v) { return v < 0 ? 0 : Math.min(v, 1); }
    public static float ease(float t) { float u = 1 - clamp(t); return 1 - u * u * u; }

    public static int a(int c, float f) {
        int al = Math.max(0, Math.min(255, (int) (((c >>> 24) & 255) * f)));
        return (al << 24) | (c & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int ca = (a >> s) & 255, cb = (b >> s) & 255;
            r |= ((int) (ca + (cb - ca) * t) & 255) << s;
        }
        return r;
    }

    public static boolean in(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    /** Rectangulo con esquinas redondeadas. */
    public static void rrect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int r, int color) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { g.fill(x1, y1, x2, y2, color); return; }
        g.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int dx = r - (int) Math.round(Math.sqrt(r * r - d * d));
            g.fill(x1 + dx, y1 + i, x2 - dx, y1 + i + 1, color);
            g.fill(x1 + dx, y2 - i - 1, x2 - dx, y2 - i, color);
        }
    }

    /** Borde fino redondeado (dibuja dos rrect: borde y relleno). */
    public static void rrectBorder(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int r, int fill, int border) {
        rrect(g, x1, y1, x2, y2, r, border);
        rrect(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), fill);
    }

    /** Disco suave (para los orbes de fondo). */
    public static void disc(GuiGraphicsExtractor g, int cx, int cy, int R, int color) {
        for (int dy = -R; dy < R; dy += 2) {
            int half = (int) Math.sqrt(Math.max(0, (double) R * R - (double) (dy + 1) * (dy + 1)));
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 2, color);
        }
    }

    /** Switch animado on/off. */
    public static void toggle(GuiGraphicsExtractor g, int x, int y, float anim, float alpha) {
        rrect(g, x, y, x + 22, y + 10, 5, a(lerp(0xFF3A3D46, ACC, anim), alpha));
        int kx = x + 1 + (int) (12 * anim);
        rrect(g, kx, y + 1, kx + 8, y + 9, 4, a(0xFFFFFFFF, alpha));
    }

    public static Component bold(String s) { return Component.literal(s).withStyle(st -> st.withBold(true)); }

    public static String keyName(int k) {
        if (k <= 0) return "NONE";
        return switch (k) {
            case 344 -> "RSHIFT"; case 340 -> "LSHIFT"; case 345 -> "RCTRL"; case 341 -> "LCTRL";
            case 346 -> "RALT"; case 342 -> "LALT"; case 260 -> "INSERT"; case 261 -> "DELETE";
            case 268 -> "HOME"; case 269 -> "END"; case 32 -> "SPACE"; case 258 -> "TAB";
            case 280 -> "CAPS"; case 262 -> "RIGHT"; case 263 -> "LEFT"; case 264 -> "DOWN"; case 265 -> "UP";
            default -> {
                if (k >= 290 && k <= 314) yield "F" + (k - 289);
                String s = org.lwjgl.glfw.GLFW.glfwGetKeyName(k, 0);
                yield s != null ? s.toUpperCase() : "KEY " + k;
            }
        };
    }
}
