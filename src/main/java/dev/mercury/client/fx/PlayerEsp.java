package dev.mercury.client.fx;

import com.visionplus.client.Freecam;
import dev.mercury.client.Config;
import dev.mercury.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;

/** ESP de jugadores: caja (hitbox) y linea guia vistas a traves de paredes, nombre y vida. */
public final class PlayerEsp {
    public static final ColorSet COLOR = new ColorSet(0);
    public static final String[] MODES = {"Completo", "Solo hitbox", "Solo tracer"};
    public static int mode = 0, range = 128, width = 1;
    public static boolean names = true, health = true, friendsGreen = true, invisible = true, healthColor = false, glowFill = true;

    private static final double[] TMP = new double[3];

    private PlayerEsp() {}

    public static boolean on() { return ModuleManager.PLAYER_ESP != null && ModuleManager.PLAYER_ESP.isOn(); }

    public static void draw(Minecraft mc, GuiGraphicsExtractor g, float pt) {
        boolean boxes = mode == 0 || mode == 1, tracers = mode == 0 || mode == 2, labels = mode == 0;
        float t = Fx.time();
        int drawn = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player pl) || pl.isSpectator() || !pl.isAlive()) continue;
            if (pl == mc.player && !Freecam.active) continue;
            if (pl.isInvisible() && !invisible) continue;
            double dx = pl.getX() - Cam.ex, dy = pl.getY() - Cam.ey, dz = pl.getZ() - Cam.ez;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > range) continue;

            Track.pos(pl, pt, TMP);
            double ox = TMP[0] - pl.getX(), oy = TMP[1] - pl.getY(), oz = TMP[2] - pl.getZ();
            AABB bb = pl.getBoundingBox();
            double x1 = bb.minX + ox, y1 = bb.minY + oy, z1 = bb.minZ + oz, x2 = bb.maxX + ox, y2 = bb.maxY + oy, z2 = bb.maxZ + oz;

            String name = pl.getName().getString();
            int rgb = COLOR.rgb(t * 0.2f);
            if (healthColor) {
                float hp = Math.max(0f, Math.min(1f, pl.getHealth() / Math.max(1f, pl.getMaxHealth())));
                rgb = Col.lerp(0xFF2020, 0x30FF50, hp);
            }
            if (friendsGreen && Config.isFriend(name)) rgb = 0x40FF40;

            boolean glow = drawn < 8;
            drawn++;
            if (boxes) {
                Cam.box(g, x1, y1, z1, x2, y2, z2, rgb, 1f, width, glow);
                if (glowFill && dist < 60) Cam.halo(g, (x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2, Math.max(1.8, (y2 - y1) * 1.5), rgb, 0.22f);
            }

            if (tracers) {
                double cx = (x1 + x2) / 2, cz = (z1 + z2) / 2, cy = (y1 + y2) / 2;
                double ddx = cx - Cam.ex, ddy = cy - Cam.ey, ddz = cz - Cam.ez;
                double zc = ddx * Cam.fwd[0] + ddy * Cam.fwd[1] + ddz * Cam.fwd[2];
                double xc = ddx * Cam.right[0] + ddy * Cam.right[1] + ddz * Cam.right[2];
                double yc = ddx * Cam.up[0] + ddy * Cam.up[1] + ddz * Cam.up[2];
                double fromX = Cam.W / 2.0, fromY = Cam.H - 2.0;
                if (zc > 0.1) {
                    Cam.line2(g, fromX, fromY, Cam.W / 2.0 + xc / zc * Cam.scale, Cam.H / 2.0 - yc / zc * Cam.scale, rgb, 0.9f, Math.max(1, width));
                } else {
                    // detras de ti: flecha desde el centro hacia el borde en esa direccion
                    double vx = xc, vy = -yc, len = Math.hypot(vx, vy);
                    if (len > 1e-4) {
                        vx /= len; vy /= len;
                        Cam.line2(g, Cam.W / 2.0, Cam.H / 2.0, Cam.W / 2.0 + vx * Cam.W, Cam.H / 2.0 + vy * Cam.W, rgb, 0.6f, Math.max(1, width));
                    }
                }
            }

            if (labels && (names || health) && Cam.project((x1 + x2) / 2, y2 + 0.3, (z1 + z2) / 2)) {
                int px = (int) Cam.sx, py = (int) Cam.sy;
                String txt = (names ? name + " " : "") + Math.round(dist) + "m";
                int tw = mc.font.width(txt);
                g.text(mc.font, txt, px - tw / 2, py - 10, Col.argb(rgb, 1f), true);
                if (health) {
                    float hp = Math.max(0f, Math.min(1f, pl.getHealth() / Math.max(1f, pl.getMaxHealth())));
                    int bw = 34;
                    g.fill(px - bw / 2 - 1, py - 1, px + bw / 2 + 1, py + 3, 0xA0000000);
                    g.fill(px - bw / 2, py, px - bw / 2 + (int) (bw * hp), py + 2, Col.argb(Col.lerp(0xFF2020, 0x30FF50, hp), 1f));
                }
            }
        }
    }
}
