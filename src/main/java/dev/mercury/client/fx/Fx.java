package dev.mercury.client.fx;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Motor de efectos visuales: tick, orden de dibujo y particulas compartidas. */
public final class Fx {
    private static final long T0 = System.nanoTime();
    private static final Random R = new Random();
    private static final int MAX_PARTS = 700;

    /** Particula (chispa) con posicion/velocidad en el mundo. */
    private static final class P {
        double x, y, z, vx, vy, vz;
        int age, life, rgb;
        float size, grav;
    }

    private static final List<P> PARTS = new ArrayList<>();

    private Fx() {}

    public static long now() { return (System.nanoTime() - T0) / 1_000_000L; }
    public static float time() { return (System.nanoTime() - T0) / 1e9f; }
    public static float rnd() { return R.nextFloat(); }
    /** -1..1 */
    public static float rs() { return R.nextFloat() * 2f - 1f; }

    public static void spawn(double x, double y, double z, double vx, double vy, double vz, int life, float size, int rgb, float grav) {
        if (PARTS.size() >= MAX_PARTS) return;
        P p = new P();
        p.x = x; p.y = y; p.z = z; p.vx = vx; p.vy = vy; p.vz = vz;
        p.life = Math.max(2, life); p.size = size; p.rgb = rgb; p.grav = grav;
        PARTS.add(p);
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("mercuryclient", "fx"),
                (g, dt) -> draw(g, dt.getGameTimeDeltaPartialTick(false)));
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { PARTS.clear(); Track.clear(); return; }
        try {
            Track.tick(mc);
            Skybox.tick(mc);
            BlockAura.tick(mc);
            Trails.tick(mc);
            Portals.tick(mc);
            AuraOrbs.tick(mc);
            SpectatorWatch.tick(mc);
            OreEsp.XRAY.tick(mc);
            OreEsp.DEBRIS.tick(mc);
            Iterator<P> it = PARTS.iterator();
            while (it.hasNext()) {
                P p = it.next();
                p.x += p.vx; p.y += p.vy; p.z += p.vz;
                p.vy -= p.grav;
                p.vx *= 0.97; p.vz *= 0.97;
                if (++p.age >= p.life) it.remove();
            }
        } catch (Throwable ignored) {}
    }

    private static void draw(GuiGraphicsExtractor g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (TargetHud.on()) { try { TargetHud.draw(mc, g); } catch (Throwable ignored) {} }
        if (!(Skybox.on() || BlockAura.on() || Trails.on() || Portals.on() || AuraOrbs.on() || PlayerEsp.on() || SpectatorWatch.on() || OreEsp.XRAY.on() || OreEsp.DEBRIS.on())) return;
        try {
            Cam.update(mc, g, pt);
            if (!Cam.ok) return;
            if (Skybox.on()) Skybox.draw(mc, g, pt);
            if (BlockAura.on()) BlockAura.draw(mc, g, pt);
            if (Portals.on()) Portals.draw(mc, g, pt);
            if (Trails.on()) Trails.draw(mc, g, pt);
            if (AuraOrbs.on()) AuraOrbs.draw(mc, g, pt);
            if (OreEsp.XRAY.on()) OreEsp.XRAY.draw(mc, g, pt);
            if (OreEsp.DEBRIS.on()) OreEsp.DEBRIS.draw(mc, g, pt);
            if (PlayerEsp.on()) PlayerEsp.draw(mc, g, pt);
            if (SpectatorWatch.on()) SpectatorWatch.draw(mc, g, pt, true);
            for (P p : PARTS) {
                float a = 1f - (p.age + pt) / p.life;
                Cam.spark(g, p.x + p.vx * pt, p.y + p.vy * pt, p.z + p.vz * pt, p.size * (0.5 + 0.5 * a), p.rgb, a);
            }
        } catch (Throwable ignored) {}
    }
}
