package dev.mercury.client.fx;

import dev.mercury.client.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Camara + proyeccion mundo -> pantalla y primitivas de dibujo (lineas, cajas, chispas con brillo).
 * Todo se dibuja en el HUD con fill(), igual que el Crystal Hitbox: se ve a traves de paredes.
 * Usa la camara real del juego (por reflexion) para que funcione con Freelook / tercera persona / Freecam.
 */
public final class Cam {
    public static double ex, ey, ez, scale;
    public static final double[] fwd = new double[3], right = new double[3], up = new double[3];
    public static int W, H;
    public static boolean ok;
    /** resultado de project() */
    public static double sx, sy, sz;

    private static final double NEAR = 0.05;
    private static final Map<String, Optional<Method>> MC = new HashMap<>();

    private Cam() {}

    // ---------------- reflexion ----------------
    private static Object call(Object o, String... names) {
        if (o == null) return null;
        for (String n : names) {
            String key = o.getClass().getName() + "#" + n;
            Optional<Method> om = MC.get(key);
            if (om == null) {
                Method m = null;
                try { m = o.getClass().getMethod(n); m.setAccessible(true); } catch (Throwable t) { m = null; }
                om = Optional.ofNullable(m);
                MC.put(key, om);
            }
            if (om.isPresent()) {
                try { return om.get().invoke(o); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static Object field(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch (Throwable ignored) {}
        }
        return null;
    }

    // ---------------- camara ----------------
    public static void update(Minecraft mc, GuiGraphicsExtractor g, float pt) {
        ok = false;
        W = g.guiWidth();
        H = g.guiHeight();
        var p = mc.player;
        if (p == null) return;

        double x = 0, y = 0, z = 0;
        float yaw = 0, pitch = 0;
        boolean got = false;
        try {
            Object cam = call(mc.gameRenderer, "getMainCamera", "mainCamera");
            if (cam == null) cam = field(mc.gameRenderer, "mainCamera");
            Object pv = call(cam, "position", "getPosition");
            Object xr = call(cam, "getXRot", "xRot");
            Object yr = call(cam, "getYRot", "yRot");
            if (pv instanceof Vec3 v && xr instanceof Number nx && yr instanceof Number ny) {
                x = v.x; y = v.y; z = v.z;
                pitch = nx.floatValue();
                yaw = ny.floatValue();
                got = true;
            }
        } catch (Throwable ignored) {}
        if (got) {
            double dx = x - p.getX(), dy = y - p.getY(), dz = z - p.getZ();
            if (dx * dx + dy * dy + dz * dz > 80 * 80) got = false; // camara aun sin actualizar
        }
        if (!got) {
            Vec3 eye = p.getEyePosition(pt);
            x = eye.x; y = eye.y; z = eye.z;
            yaw = p.getViewYRot(pt);
            pitch = p.getViewXRot(pt);
        }
        ex = x; ey = y; ez = z;
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double sy_ = Math.sin(yr), cy = Math.cos(yr), sp = Math.sin(pr), cp = Math.cos(pr);
        fwd[0] = -sy_ * cp; fwd[1] = -sp; fwd[2] = cy * cp;
        right[0] = -cy; right[1] = 0; right[2] = -sy_;
        up[0] = -sy_ * sp; up[1] = cp; up[2] = cy * sp;
        scale = (H / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        ok = true;
    }

    /** Proyecta un punto del mundo. Guarda el resultado en sx/sy/sz. false = detras de la camara. */
    public static boolean project(double x, double y, double z) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double zc = dx * fwd[0] + dy * fwd[1] + dz * fwd[2];
        if (zc < NEAR) return false;
        double xc = dx * right[0] + dy * right[1] + dz * right[2];
        double yc = dx * up[0] + dy * up[1] + dz * up[2];
        sx = W / 2.0 + xc / zc * scale;
        sy = H / 2.0 - yc / zc * scale;
        sz = zc;
        return true;
    }

    // ---------------- primitivas ----------------
    /** Linea 2D con recorte a pantalla y grosor t (px). */
    public static void line2(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int rgb, float alpha, int t) {
        double dx = x2 - x1, dy = y2 - y1;
        double t0 = 0, t1 = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 + 2, W + 2 - x1, y1 + 2, H + 2 - y1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) { if (q[i] < 0) return; }
            else {
                double r = q[i] / p[i];
                if (p[i] < 0) { if (r > t1) return; if (r > t0) t0 = r; }
                else { if (r < t0) return; if (r < t1) t1 = r; }
            }
        }
        double ax = x1 + t0 * dx, ay = y1 + t0 * dy, bx = x1 + t1 * dx, by = y1 + t1 * dy;
        double len = Math.hypot(bx - ax, by - ay);
        if (Spr.enabled) { Spr.beam(g, ax, ay, bx, by, t * 1.4, rgb, alpha); return; }
        int col = Col.argb(rgb, alpha);
        int h = t / 2;
        if (len < 1) { g.fill((int) ax - h, (int) ay - h, (int) ax - h + t, (int) ay - h + t, col); return; }
        int steps = (int) Math.min(2400, Math.ceil(len / Math.max(1, t)));
        double stx = (bx - ax) / steps, sty = (by - ay) / steps;
        for (int i = 0; i <= steps; i++) {
            int px = (int) (ax + stx * i) - h, py = (int) (ay + sty * i) - h;
            g.fill(px, py, px + t, py + t, col);
        }
    }

    /** Linea 3D con recorte contra el plano cercano de la camara. */
    public static void line3(GuiGraphicsExtractor g, double x1, double y1, double z1, double x2, double y2, double z2,
                             int rgb, float alpha, int t) {
        double ax = x1 - ex, ay = y1 - ey, az = z1 - ez, bx = x2 - ex, by = y2 - ey, bz = z2 - ez;
        double za = ax * fwd[0] + ay * fwd[1] + az * fwd[2];
        double zb = bx * fwd[0] + by * fwd[1] + bz * fwd[2];
        if (za < NEAR && zb < NEAR) return;
        if (za < NEAR) { double k = (NEAR - za) / (zb - za); ax += (bx - ax) * k; ay += (by - ay) * k; az += (bz - az) * k; za = NEAR; }
        else if (zb < NEAR) { double k = (NEAR - zb) / (za - zb); bx += (ax - bx) * k; by += (ay - by) * k; bz += (az - bz) * k; zb = NEAR; }
        double xa = ax * right[0] + ay * right[1] + az * right[2], ya = ax * up[0] + ay * up[1] + az * up[2];
        double xb = bx * right[0] + by * right[1] + bz * right[2], yb = bx * up[0] + by * up[1] + bz * up[2];
        line2(g, W / 2.0 + xa / za * scale, H / 2.0 - ya / za * scale, W / 2.0 + xb / zb * scale, H / 2.0 - yb / zb * scale,
                rgb, alpha, t);
    }

    /** Linea 3D con brillo (capa suave debajo). */
    public static void glowLine3(GuiGraphicsExtractor g, double x1, double y1, double z1, double x2, double y2, double z2,
                                 int rgb, float alpha, int t, boolean glow) {
        if (glow) line3(g, x1, y1, z1, x2, y2, z2, rgb, alpha * 0.22f, t + 3);
        line3(g, x1, y1, z1, x2, y2, z2, rgb, alpha, t);
    }

    /** Caja 3D (12 aristas). */
    public static void box(GuiGraphicsExtractor g, double x1, double y1, double z1, double x2, double y2, double z2,
                           int rgb, float alpha, int t, boolean glow) {
        for (int i = 0; i < 8; i++) {
            for (int b = 0; b < 3; b++) {
                int j = i ^ (1 << b);
                if (j < i) continue;
                glowLine3(g, (i & 1) == 0 ? x1 : x2, (i & 2) == 0 ? y1 : y2, (i & 4) == 0 ? z1 : z2,
                        (j & 1) == 0 ? x1 : x2, (j & 2) == 0 ? y1 : y2, (j & 4) == 0 ? z1 : z2, rgb, alpha, t, glow);
            }
        }
    }

    /** Chispa brillante: el tamano es en bloques (se achica con la distancia). */
    public static void spark(GuiGraphicsExtractor g, double wx, double wy, double wz, double size, int rgb, float alpha) {
        if (alpha <= 0.01f || !project(wx, wy, wz) || sz > 220) return;
        int x = (int) sx, y = (int) sy;
        if (x < -30 || y < -30 || x > W + 30 || y > H + 30) return;
        if (Spr.enabled) {
            double d = Math.max(4, Math.min(90, size * scale / sz * 3.2));
            Spr.glow(g, sx, sy, d, rgb, alpha * 0.9f);
            if (d > 13 && alpha > 0.3f) Spr.flare(g, sx, sy, d * 1.7, rgb, alpha * 0.5f);
            return;
        }
        int r = (int) Math.max(1, Math.min(12, Math.round(size * scale / sz)));
        if (r >= 3) {
            Gfx.disc(g, x, y, r * 2, Col.argb(rgb, alpha * 0.10f));
            Gfx.disc(g, x, y, r + 1, Col.argb(rgb, alpha * 0.28f));
        } else if (r == 2) {
            g.fill(x - 3, y - 3, x + 3, y + 3, Col.argb(rgb, alpha * 0.18f));
        }
        int h = r / 2;
        g.fill(x - h, y - h, x + h + 1, y + h + 1, Col.argb(Col.lerp(rgb, 0xFFFFFF, 0.45f), alpha));
    }

    /** Linea guia desde abajo al centro de la pantalla hacia un punto del mundo (si esta detras, flecha al borde). */
    public static void tracer(GuiGraphicsExtractor g, double wx, double wy, double wz, int rgb, float alpha, int t) {
        double dx = wx - ex, dy = wy - ey, dz = wz - ez;
        double zc = dx * fwd[0] + dy * fwd[1] + dz * fwd[2];
        double xc = dx * right[0] + dy * right[1] + dz * right[2];
        double yc = dx * up[0] + dy * up[1] + dz * up[2];
        if (zc > 0.1) {
            line2(g, W / 2.0, H - 2.0, W / 2.0 + xc / zc * scale, H / 2.0 - yc / zc * scale, rgb, alpha, t);
        } else {
            double vx = xc, vy = -yc, len = Math.hypot(vx, vy);
            if (len > 1e-4) line2(g, W / 2.0, H / 2.0, W / 2.0 + vx / len * W, H / 2.0 + vy / len * W, rgb, alpha * 0.7f, t);
        }
    }

    /** Halo grande y suave centrado en un punto del mundo (diametro en bloques). */
    public static void halo(GuiGraphicsExtractor g, double wx, double wy, double wz, double diameter, int rgb, float alpha) {
        if (!project(wx, wy, wz) || sz > 220) return;
        double d = Math.min(Math.max(diameter * scale / sz, 3), 700);
        if (sx < -d || sy < -d || sx > W + d || sy > H + d) return;
        if (Spr.enabled) Spr.glow(g, sx, sy, d, rgb, alpha);
        else { int h = (int) (d * 0.25); g.fill((int) sx - h, (int) sy - h, (int) sx + h, (int) sy + h, Col.argb(rgb, alpha * 0.25f)); }
    }
}
