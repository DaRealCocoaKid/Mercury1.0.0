package dev.mercury.client.fx;

import dev.mercury.client.Config;
import dev.mercury.client.module.ModuleManager;
import dev.mercury.client.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Detector de cazadores / espectadores.
 * Con lo que el cliente puede ver: jugadores en modo espectador o invisibles cerca de ti, y si uno esta
 * PEGADO a ti con tu misma rotacion (asi se ve un espectador que te esta "poseyendo" la camara).
 * Limite: si el servidor oculta a la persona con un plugin de vanish, el cliente no recibe su entidad y no hay forma de verla.
 */
public final class SpectatorWatch {
    public static final String[] MODES = {"Lista", "Todos"};
    public static int mode = 0, range = 32, close = 3;
    public static boolean alert = true, sound = true, panel = true, esp = true;

    private static final class D { String name; Player ent; int level; double dist; int closeTicks; int lastAlert = -1000; }
    private static final Map<String, D> M = new HashMap<>();
    private static final Map<String, Integer> LAST = new HashMap<>();
    private static int tick;

    private SpectatorWatch() {}

    public static boolean on() { return ModuleManager.SPEC_WATCH != null && ModuleManager.SPEC_WATCH.isOn(); }

    private static boolean watched(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        if (Config.isFriend(l)) return false;
        return mode == 1 || Config.hunters.isEmpty() || Config.hunters.contains(l);
    }

    public static void tick(Minecraft mc) {
        tick++;
        LocalPlayer p = mc.player;
        if (!on() || p == null || mc.level == null) { M.clear(); LAST.clear(); return; }
        if ((tick & 1) != 0) return;
        List<String> seen = new ArrayList<>();
        for (Player o : mc.level.players()) {
            if (o == p || !o.isAlive()) continue;
            String n = o.getName().getString();
            if (!watched(n)) continue;
            boolean spec = o.isSpectator(), inv = o.isInvisible();
            if (!spec && !inv) continue;
            double dist = Math.sqrt(o.distanceToSqr(p));
            if (dist > range) continue;
            D d = M.computeIfAbsent(n, k -> { D x = new D(); x.name = k; return x; });
            d.ent = o; d.dist = dist;
            boolean near = spec && dist <= close;
            boolean rot = Math.abs(Mth.wrapDegrees(o.getYRot() - p.getYRot())) < 3f && Math.abs(o.getXRot() - p.getXRot()) < 3f;
            d.closeTicks = near ? d.closeTicks + 2 : Math.max(0, d.closeTicks - 2);
            d.level = near && (rot || d.closeTicks >= 20) ? 3 : spec ? 2 : 1;
            seen.add(n);

            int prev = LAST.getOrDefault(n, 0);
            if (d.level > prev && tick - d.lastAlert > 60) {
                d.lastAlert = tick;
                if (alert) {
                    String msg = d.level == 3 ? "\u26A0 " + n + " te esta ESPECTANDO"
                            : d.level == 2 ? n + " en espectador a " + Math.round(dist) + "m" : n + " invisible a " + Math.round(dist) + "m";
                    if (mc.gui != null) mc.gui.hud.setOverlayMessage(Component.literal(msg), false);
                }
                if (sound && d.level >= 2) beep(mc);
            }
            LAST.put(n, d.level);
        }
        M.keySet().retainAll(seen);
        LAST.keySet().retainAll(seen);
    }

    private static Method forUI, play;
    private static Object pling;
    private static boolean snd;

    private static void beep(Minecraft mc) {
        try {
            if (!snd) {
                snd = true;
                pling = Class.forName("net.minecraft.sounds.SoundEvents").getField("NOTE_BLOCK_PLING").get(null);
                for (Method m : Class.forName("net.minecraft.client.resources.sounds.SimpleSoundInstance").getMethods())
                    if (m.getName().equals("forUI") && m.getParameterCount() == 2 && m.getParameterTypes()[1] == float.class
                            && m.getParameterTypes()[0].isInstance(pling)) { forUI = m; break; }
            }
            if (forUI == null) return;
            Object inst = forUI.invoke(null, pling, 1.2f);
            Object sm = mc.getSoundManager();
            if (play == null) for (Method m : sm.getClass().getMethods()) if (m.getName().equals("play") && m.getParameterCount() == 1) { play = m; break; }
            if (play != null) play.invoke(sm, inst);
        } catch (Throwable ignored) { forUI = null; }
    }

    private static int colorOf(int level, float t) {
        if (level >= 3) return Col.lerp(0xFF2A2A, 0xFFA0A0, 0.5f + 0.5f * (float) Math.sin(t * 8));
        return level == 2 ? 0xFFA030 : 0xFFE24A;
    }

    public static void draw(Minecraft mc, GuiGraphicsExtractor g, float pt, boolean camOk) {
        if (M.isEmpty()) return;
        float t = Fx.time();
        List<D> list = new ArrayList<>(M.values());
        list.sort((a, b) -> b.level != a.level ? b.level - a.level : Double.compare(a.dist, b.dist));

        if (esp && camOk) {
            for (D d : list) {
                if (d.ent == null || !d.ent.isAlive()) continue;
                int rgb = colorOf(d.level, t);
                AABB bb = d.ent.getBoundingBox();
                Cam.box(g, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, rgb, 1f, 1, true);
                Cam.halo(g, (bb.minX + bb.maxX) / 2, (bb.minY + bb.maxY) / 2, (bb.minZ + bb.maxZ) / 2, 2.2, rgb, 0.3f);
                Cam.tracer(g, (bb.minX + bb.maxX) / 2, (bb.minY + bb.maxY) / 2, (bb.minZ + bb.maxZ) / 2, rgb, 0.85f, 1);
            }
        }

        if (panel) {
            int w = 0, rows = Math.min(list.size(), 5);
            String[] lines = new String[rows];
            for (int i = 0; i < rows; i++) {
                D d = list.get(i);
                String tag = d.level == 3 ? "ESPECTANDOTE" : d.level == 2 ? "espectador" : "invisible";
                lines[i] = d.name + "  -  " + tag + "  " + Math.round(d.dist) + "m";
                w = Math.max(w, mc.font.width(lines[i]));
            }
            w += 22;
            int x = (g.guiWidth() - w) / 2, y = 16, h = rows * 13 + 8;
            Gfx.rrect(g, x, y, x + w, y + h, 5, 0xC0101218);
            for (int i = 0; i < rows; i++) {
                int c = colorOf(list.get(i).level, t);
                Gfx.rrect(g, x + 7, y + 8 + i * 13, x + 12, y + 13 + i * 13, 2, Col.argb(c, 1f));
                g.text(mc.font, lines[i], x + 17, y + 5 + i * 13, Col.argb(c, 1f), true);
            }
        }
    }
}
