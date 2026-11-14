package dev.mercury.client.fx;

import dev.mercury.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/** Footsteps: cada paso abre un portal de chispas (tipo Doctor Strange) que dura unos segundos y se desvanece. */
public final class Portals {
    public static final ColorSet COLOR = new ColorSet(1); // naranja
    public static int life = 2, radius = 7, orient = 0;
    public static boolean sparks = true;

    private static final class Pt { double x, y, z, hx, hz; long born; }
    private static final List<Pt> L = new ArrayList<>();
    private static double lastX, lastZ;
    private static boolean has, left;

    private Portals() {}

    public static boolean on() { return ModuleManager.FOOTSTEPS != null && ModuleManager.FOOTSTEPS.isOn(); }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (!on() || p == null) { L.clear(); has = false; return; }
        if (!has) { lastX = p.getX(); lastZ = p.getZ(); has = true; }
        double dx = p.getX() - lastX, dz = p.getZ() - lastZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 4) { lastX = p.getX(); lastZ = p.getZ(); }
        else if (p.onGround() && d >= 0.9) {
            double hx = dx / d, hz = dz / d;
            left = !left;
            double side = left ? 0.18 : -0.18;
            Pt q = new Pt();
            q.x = p.getX() - hz * side;
            q.z = p.getZ() + hx * side;
            q.y = p.getY() + 0.02;
            q.hx = hx; q.hz = hz;
            q.born = Fx.now();
            L.add(q);
            if (L.size() > 36) L.remove(0);
            lastX = p.getX(); lastZ = p.getZ();
        }
        long now = Fx.now();
        long lifeMs = life * 1000L;
        L.removeIf(q -> now - q.born > lifeMs);

        if (sparks) {
            for (Pt q : L) {
                float age = (now - q.born) / (float) lifeMs;
                if (age > 0.6f || Fx.rnd() > 0.55f) continue;
                double R = radius * 0.1 * Math.min(1f, age * 6f);
                double ang = Fx.rnd() * Math.PI * 2;
                double[] c = center(q, R);
                double[] u = axisU(q), v = axisV(q);
                double ca = Math.cos(ang), sa = Math.sin(ang);
                double px = c[0] + (u[0] * ca + v[0] * sa) * R, py = c[1] + (u[1] * ca + v[1] * sa) * R, pz = c[2] + (u[2] * ca + v[2] * sa) * R;
                // salen disparadas en la tangente
                double tx = -u[0] * sa + v[0] * ca, ty = -u[1] * sa + v[1] * ca, tz = -u[2] * sa + v[2] * ca;
                int rgb = COLOR.rgb(Fx.rnd());
                Fx.spawn(px, py, pz, tx * 0.05 + Fx.rs() * 0.01, 0.03 + Fx.rnd() * 0.05, tz * 0.05 + Fx.rs() * 0.01,
                        12 + (int) (Fx.rnd() * 10), 0.05f, rgb, 0.003f);
            }
        }
    }

    private static double[] axisU(Pt q) { return orient == 0 ? new double[]{1, 0, 0} : new double[]{-q.hz, 0, q.hx}; }
    private static double[] axisV(Pt q) { return orient == 0 ? new double[]{0, 0, 1} : new double[]{0, 1, 0}; }
    private static double[] center(Pt q, double R) { return orient == 0 ? new double[]{q.x, q.y, q.z} : new double[]{q.x, q.y + 0.05 + R, q.z}; }

    public static void draw(Minecraft mc, GuiGraphicsExtractor g, float pt) {
        long now = Fx.now();
        float lifeMs = life * 1000f;
        for (Pt q : L) {
            float age = (now - q.born) / lifeMs;
            if (age >= 1f) continue;
            float open = Math.min(1f, age * 6f);
            open = 1f - (1f - open) * (1f - open) * (1f - open);
            float fade = age > 0.6f ? 1f - (age - 0.6f) / 0.4f : 1f;
            double R = radius * 0.1 * open;
            if (R < 0.03) continue;
            float spin = (now - q.born) / 1000f * 4.5f;
            double[] c = center(q, R), u = axisU(q), v = axisV(q);

            // aro continuo suave
            int segs = 24;
            double lx = 0, ly = 0, lz = 0;
            for (int i = 0; i <= segs; i++) {
                double a = i / (double) segs * Math.PI * 2;
                double ca = Math.cos(a), sa = Math.sin(a);
                double x = c[0] + (u[0] * ca + v[0] * sa) * R, y = c[1] + (u[1] * ca + v[1] * sa) * R, z = c[2] + (u[2] * ca + v[2] * sa) * R;
                if (i > 0) {
                    int rgb = COLOR.rgb(i / (float) segs + spin * 0.05f);
                    Cam.line3(g, lx, ly, lz, x, y, z, rgb, 0.18f * fade, 4);
                    Cam.line3(g, lx, ly, lz, x, y, z, rgb, 0.45f * fade, 1);
                }
                lx = x; ly = y; lz = z;
            }
            // anillo exterior de chispas con cola
            ring(g, c, u, v, R, 28, spin, 2, fade, 0.055);
            // anillo interior en sentido contrario
            ring(g, c, u, v, R * 0.62, 18, -spin * 0.75f, 2, fade, 0.045);
            // nucleo que gira
            ring(g, c, u, v, R * 0.25, 6, spin * 1.6f, 1, fade * 0.8f, 0.04);
        }
    }

    private static void ring(GuiGraphicsExtractor g, double[] c, double[] u, double[] v, double R, int n, float spin, int tail, float fade, double size) {
        for (int i = 0; i < n; i++) {
            for (int k = 0; k <= tail; k++) {
                double a = i / (double) n * Math.PI * 2 + spin - k * 0.09 * Math.signum(spin == 0 ? 1 : spin);
                double ca = Math.cos(a), sa = Math.sin(a);
                double x = c[0] + (u[0] * ca + v[0] * sa) * R, y = c[1] + (u[1] * ca + v[1] * sa) * R, z = c[2] + (u[2] * ca + v[2] * sa) * R;
                float f = (1f - k / (float) (tail + 1)) * fade;
                int rgb = COLOR.rgb(i / (float) n + spin * 0.05f);
                Cam.spark(g, x, y, z, size * (1 - k * 0.25), rgb, f);
            }
        }
    }
}
