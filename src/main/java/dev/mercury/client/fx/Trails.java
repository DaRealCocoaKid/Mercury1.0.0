package dev.mercury.client.fx;

import dev.mercury.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Estelas brillantes detras de items, totems, proyectiles, cristales y jugadores. */
public final class Trails {
    public static final ColorSet COLOR = new ColorSet(13); // arcoiris
    public static int length = 18, width = 2;
    public static boolean items = true, projectiles = true, crystals = true, players = false, totems = true, sparks = true;

    private static final Map<Integer, ArrayDeque<double[]>> H = new HashMap<>();
    private static final double[] TMP = new double[3];

    private Trails() {}

    public static boolean on() { return ModuleManager.TRAILS != null && ModuleManager.TRAILS.isOn(); }

    private static boolean hasTotem(Player p) {
        return p.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private static boolean wants(Entity e) {
        if (e instanceof ItemEntity ie) return items || (totems && ie.getItem().is(Items.TOTEM_OF_UNDYING));
        if (e instanceof Projectile) return projectiles;
        if (e instanceof EndCrystal) return crystals;
        if (e instanceof Player pl) return players || (totems && hasTotem(pl));
        return false;
    }

    public static void tick(Minecraft mc) {
        if (!on() || mc.level == null || mc.player == null) { H.clear(); return; }
        Set<Integer> seen = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!wants(e) || e.distanceToSqr(mc.player) > 96 * 96) continue;
            ArrayDeque<double[]> q = H.computeIfAbsent(e.getId(), k -> new ArrayDeque<>());
            double[] p = {e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ()};
            double[] last = q.peekFirst();
            boolean moved = last == null || Math.abs(last[0] - p[0]) + Math.abs(last[1] - p[1]) + Math.abs(last[2] - p[2]) > 0.02;
            q.addFirst(p);
            while (q.size() > length) q.removeLast();
            seen.add(e.getId());
            if (sparks && moved && Fx.rnd() < 0.7f) {
                int rgb = COLOR.rgb(Fx.time() * 0.3f + Fx.rnd() * 0.2f);
                Fx.spawn(p[0] + Fx.rs() * 0.12, p[1] + Fx.rs() * 0.12, p[2] + Fx.rs() * 0.12,
                        Fx.rs() * 0.01, 0.005 + Fx.rnd() * 0.01, Fx.rs() * 0.01, 14 + (int) (Fx.rnd() * 14), 0.045f, rgb, 0f);
            }
        }
        H.keySet().retainAll(seen);
    }

    public static void draw(Minecraft mc, GuiGraphicsExtractor g, float pt) {
        if (H.isEmpty()) return;
        float t = Fx.time();
        for (Entity e : mc.level.entitiesForRendering()) {
            ArrayDeque<double[]> q = H.get(e.getId());
            if (q == null || q.isEmpty()) continue;
            Track.pos(e, pt, TMP);
            double px = TMP[0], py = TMP[1] + e.getBbHeight() * 0.5, pz = TMP[2];
            int n = q.size(), i = 0;
            for (double[] s : q) {
                float frac = 1f - (i + 1f) / (n + 1f);
                int rgb = COLOR.rgb(t * 0.25f + i * 0.03f);
                int th = Math.max(1, Math.round(width * (0.5f + 0.5f * frac)));
                Cam.line3(g, px, py, pz, s[0], s[1], s[2], rgb, frac * 0.25f, th + 3);
                Cam.line3(g, px, py, pz, s[0], s[1], s[2], rgb, frac, th);
                px = s[0]; py = s[1]; pz = s[2];
                i++;
            }
        }
    }
}
