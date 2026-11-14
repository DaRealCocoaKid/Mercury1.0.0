package dev.mercury.client.fx;

import dev.mercury.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** Aura: bolitas de luz con estela orbitando a tu alrededor, destellos y rayos opcionales. */
public final class AuraOrbs {
    public static final ColorSet COLOR = new ColorSet(14); // galaxia
    public static int count = 10, radius = 12, speed = 4, size = 5;
    public static boolean lightning = true, trail = true;

    private static final double[] TMP = new double[3];

    private AuraOrbs() {}

    public static boolean on() { return ModuleManager.AURA != null && ModuleManager.AURA.isOn(); }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (!on() || p == null) return;
        // destellos suaves subiendo alrededor del jugador
        double r = radius * 0.1;
        for (int i = 0; i < 2; i++) {
            double a = Fx.rnd() * Math.PI * 2, rr = r * (0.5 + Fx.rnd() * 0.7);
            Fx.spawn(p.getX() + Math.cos(a) * rr, p.getY() + Fx.rnd() * 1.9, p.getZ() + Math.sin(a) * rr,
                    0, 0.01 + Fx.rnd() * 0.02, 0, 16 + (int) (Fx.rnd() * 14), 0.04f, COLOR.rgb(Fx.rnd()), 0f);
        }
    }

    private static void orb(Minecraft mc, int i, float t, double px, double py, double pz, double[] out) {
        double ph = i * Math.PI * 2 / Math.max(1, count);
        double ang = t * speed * 0.35 + ph;
        double r = radius * 0.1 * (0.85 + 0.3 * Math.sin(i * 1.7));
        out[0] = px + Math.cos(ang) * r;
        out[1] = py + 0.95 + Math.sin(t * 1.3 + i * 2.3) * 0.7;
        out[2] = pz + Math.sin(ang) * r;
    }

    public static void draw(Minecraft mc, GuiGraphicsExtractor g, float pt) {
        LocalPlayer p = mc.player;
        Track.pos(p, pt, TMP);
        double px = TMP[0], py = TMP[1], pz = TMP[2];
        float t = Fx.time();
        double[] o = new double[3];
        for (int i = 0; i < count; i++) {
            float pulse = 0.8f + 0.3f * (float) Math.sin(t * 4 + i);
            double sz = size * 0.02 * pulse;
            if (trail) {
                for (int k = 6; k >= 1; k--) {
                    orb(mc, i, t - k * 0.04f, px, py, pz, o);
                    Cam.spark(g, o[0], o[1], o[2], sz * (1 - k * 0.12), COLOR.rgb(t * 0.2f + i * 0.07f), (1f - k / 7f) * 0.7f);
                }
            }
            orb(mc, i, t, px, py, pz, o);
            Cam.spark(g, o[0], o[1], o[2], sz * 1.3, COLOR.rgb(t * 0.2f + i * 0.07f), 1f);
        }
        if (lightning && count > 1) {
            int seed = (int) (t * 12);
            if (seed % 4 == 0) {
                java.util.Random r = new java.util.Random(seed * 7919L);
                for (int b = 0; b < 2; b++) {
                    orb(mc, r.nextInt(count), t, px, py, pz, o);
                    double tx = px + (r.nextDouble() - 0.5) * 0.6, ty = py + 0.4 + r.nextDouble() * 1.2, tz = pz + (r.nextDouble() - 0.5) * 0.6;
                    double lx = o[0], ly = o[1], lz = o[2];
                    int rgb = COLOR.rgb(t * 0.3f + b * 0.4f);
                    for (int s = 1; s <= 5; s++) {
                        double k = s / 5.0;
                        double nx = o[0] + (tx - o[0]) * k + (s < 5 ? (r.nextDouble() - 0.5) * 0.45 : 0);
                        double ny = o[1] + (ty - o[1]) * k + (s < 5 ? (r.nextDouble() - 0.5) * 0.45 : 0);
                        double nz = o[2] + (tz - o[2]) * k + (s < 5 ? (r.nextDouble() - 0.5) * 0.45 : 0);
                        Cam.line3(g, lx, ly, lz, nx, ny, nz, rgb, 0.3f, 5);
                        Cam.line3(g, lx, ly, lz, nx, ny, nz, 0xFFFFFF, 0.95f, 1);
                        lx = nx; ly = ny; lz = nz;
                    }
                }
            }
        }
    }
}
