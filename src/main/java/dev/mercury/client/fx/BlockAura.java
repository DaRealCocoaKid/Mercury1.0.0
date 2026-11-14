package dev.mercury.client.fx;

import dev.mercury.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;

/** Aura del bloque que miras: brilla y se desvanece dejando estela al mover la mira. */
public final class BlockAura {
    public static final ColorSet COLOR = new ColorSet(6);
    public static int fade = 1400, glow = 2;
    public static boolean sparks = true;

    private static final class E { int x, y, z; long last; }
    private static final Map<Long, E> M = new HashMap<>();

    private BlockAura() {}

    public static boolean on() { return ModuleManager.BLOCK_AURA != null && ModuleManager.BLOCK_AURA.isOn(); }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    public static void tick(Minecraft mc) {
        if (!on() || mc.level == null || mc.player == null) { M.clear(); return; }
        long now = Fx.now();
        if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos bp = bhr.getBlockPos();
            final int x = bp.getX(), y = bp.getY(), z = bp.getZ();
            E e = M.computeIfAbsent(key(x, y, z), k -> { E n = new E(); n.x = x; n.y = y; n.z = z; return n; });
            e.last = now;
            if (sparks) {
                int rgb = COLOR.rgb(Fx.time() * 0.2f);
                for (int i = 0; i < 2; i++) {
                    // chispas que suben desde las aristas del bloque
                    double px = x + (Fx.rnd() < 0.5 ? 0 : 1), pz = z + (Fx.rnd() < 0.5 ? 0 : 1), py = y + Fx.rnd();
                    Fx.spawn(px, py, pz, Fx.rs() * 0.01, 0.02 + Fx.rnd() * 0.02, Fx.rs() * 0.01, 16 + (int) (Fx.rnd() * 10), 0.05f, rgb, 0f);
                }
            }
        }
        final long fd = fade;
        M.values().removeIf(e -> now - e.last > fd);
    }

    public static void draw(Minecraft mc, GuiGraphicsExtractor g, float pt) {
        long now = Fx.now();
        float t = Fx.time();
        for (E e : M.values()) {
            long age = now - e.last;
            boolean looking = age < 70;
            float a = looking ? 1f : 1f - (age / (float) fade);
            a = Math.max(0f, a);
            a *= a;
            if (a <= 0.02f) continue;
            int rgb = COLOR.rgb(t * 0.15f + (e.x + e.z) * 0.04f);
            Cam.halo(g, e.x + 0.5, e.y + 0.5, e.z + 0.5, 2.6, rgb, a * 0.5f);
            double pulse = looking ? 0.012 * Math.sin(t * 8) : 0;
            Cam.box(g, e.x - pulse, e.y - pulse, e.z - pulse, e.x + 1 + pulse, e.y + 1 + pulse, e.z + 1 + pulse, rgb, a, 2, false);
            for (int k = 1; k <= glow; k++) {
                double d = 0.018 * k;
                Cam.box(g, e.x - d, e.y - d, e.z - d, e.x + 1 + d, e.y + 1 + d, e.z + 1 + d, rgb, a * 0.32f / k, 2 + k, false);
            }
        }
    }
}
