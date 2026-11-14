package dev.mercury.client.fx;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.lang.reflect.Method;

/**
 * Sprites suaves (brillo, rayo, destello) dibujados con texturas difuminadas, con rotacion y escala a cualquier tamano.
 * Un solo blit por brillo o linea: mucho mas liso y mas barato que pintar cuadraditos con fill().
 * El color se aplica con el blit teñido si existe; si no, usa texturas pre-coloreadas por tono.
 */
public final class Spr {
    public static final int GLOW = 0, BEAM = 1, FLARE = 2;
    private static final String[] NAME = {"glow", "beam", "flare"};
    private static final int[] TW = {128, 256, 128}, TH = {128, 32, 128};
    private static final Identifier[] WHITE = new Identifier[3];
    private static final Identifier[][] HUE = new Identifier[3][24];

    /** si algo falla al dibujar se apaga y Cam usa el dibujo por cuadraditos de respaldo */
    public static boolean enabled = true;

    private static Method tint;
    private static int tintN;
    private static boolean tintTried, tintBad;

    static {
        for (int k = 0; k < 3; k++) {
            WHITE[k] = id("textures/fx/" + NAME[k] + ".png");
            for (int i = 0; i < 24; i++) HUE[k][i] = id("textures/fx/" + NAME[k] + "_h" + i + ".png");
        }
    }

    private Spr() {}

    private static Identifier id(String p) { return Identifier.fromNamespaceAndPath("mercuryclient", p); }

    private static void findTint() {
        if (tintTried) return;
        tintTried = true;
        try {
            for (Method m : GuiGraphicsExtractor.class.getMethods()) {
                if (!m.getName().equals("blit")) continue;
                Class<?>[] p = m.getParameterTypes();
                int n = p.length;
                if ((n != 11 && n != 13) || p[1] != Identifier.class || p[n - 1] != int.class) continue;
                if (!p[0].getSimpleName().equals("RenderPipeline")) continue;
                if (p[2] != int.class || p[3] != int.class) continue;
                if (p[4] != float.class && p[4] != int.class) continue;
                boolean ok = true;
                for (int i = 6; i < n; i++) ok &= p[i] == int.class;
                if (ok) { tint = m; tintN = n; return; }
            }
        } catch (Throwable ignored) {}
    }

    private static int hueIndex(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        if (mx <= 0.001f || (mx - mn) / mx < 0.22f) return -1;
        float d = mx - mn, h;
        if (mx == r) h = ((g - b) / d) % 6f;
        else if (mx == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return Math.round(h * 24) % 24;
    }

    /** Dibuja el sprite centrado en (cx,cy) con tamano w x h (px de GUI), rotado rot radianes. */
    public static void draw(GuiGraphicsExtractor g, int kind, double cx, double cy, double w, double h, float rot, int rgb, float alpha) {
        if (!enabled || alpha <= 0.01f || w < 0.5 || h < 0.5) return;
        int tw = TW[kind], th = TH[kind];
        Matrix3x2fStack m = g.pose();
        m.pushMatrix();
        try {
            m.translate((float) cx, (float) cy);
            if (rot != 0f) m.rotate(rot);
            m.scale((float) (w / tw), (float) (h / th));
            int x = -tw / 2, y = -th / 2;
            findTint();
            if (tint != null && !tintBad) {
                try {
                    Class<?>[] p = tint.getParameterTypes();
                    Object[] a = new Object[tintN];
                    a[0] = RenderPipelines.GUI_TEXTURED;
                    a[1] = WHITE[kind];
                    a[2] = x; a[3] = y;
                    a[4] = p[4] == float.class ? (Object) 0f : (Object) 0;
                    a[5] = p[5] == float.class ? (Object) 0f : (Object) 0;
                    a[6] = tw; a[7] = th; a[8] = tw; a[9] = th;
                    if (tintN == 13) { a[10] = tw; a[11] = th; }
                    a[tintN - 1] = Col.argb(rgb, alpha);
                    tint.invoke(g, a);
                    return;
                } catch (Throwable t) { tintBad = true; }
            }
            // respaldo: textura pre-coloreada por tono (sin transparencia: se achica al desvanecerse)
            int hi = hueIndex(rgb);
            float f = (float) Math.sqrt(Math.min(1f, alpha));
            m.scale(f, f);
            g.blit(RenderPipelines.GUI_TEXTURED, hi < 0 ? WHITE[kind] : HUE[kind][hi], x, y, 0f, 0f, tw, th, tw, th);
        } catch (Throwable t) {
            enabled = false;
        } finally {
            m.popMatrix();
        }
    }

    public static void glow(GuiGraphicsExtractor g, double x, double y, double size, int rgb, float alpha) {
        draw(g, GLOW, x, y, size, size, 0f, rgb, alpha);
    }

    public static void flare(GuiGraphicsExtractor g, double x, double y, double size, int rgb, float alpha) {
        draw(g, FLARE, x, y, size, size, 0f, rgb, alpha);
    }

    /** Rayo suave entre dos puntos de pantalla; thick = grosor del nucleo en px. */
    public static void beam(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, double thick, int rgb, float alpha) {
        double dx = x2 - x1, dy = y2 - y1, len = Math.hypot(dx, dy);
        if (len < 0.8) { glow(g, x1, y1, thick * 4, rgb, alpha); return; }
        draw(g, BEAM, (x1 + x2) / 2, (y1 + y2) / 2, len + thick * 2.4, thick * 4.2, (float) Math.atan2(dy, dx), rgb, alpha);
    }
}
