package dev.mercury.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/** Guarda la posicion anterior y actual de cada entidad para dibujarla suave (interpolada) entre ticks. */
public final class Track {
    private static final Map<Integer, double[]> M = new HashMap<>(); // px,py,pz, cx,cy,cz, visto
    private static int stamp;

    private Track() {}

    public static void clear() { M.clear(); }

    public static void tick(Minecraft mc) {
        stamp++;
        if (mc.level == null) { M.clear(); return; }
        for (Entity e : mc.level.entitiesForRendering()) {
            double[] a = M.get(e.getId());
            if (a == null) {
                a = new double[]{e.getX(), e.getY(), e.getZ(), e.getX(), e.getY(), e.getZ(), 0};
                M.put(e.getId(), a);
            } else {
                a[0] = a[3]; a[1] = a[4]; a[2] = a[5];
                a[3] = e.getX(); a[4] = e.getY(); a[5] = e.getZ();
            }
            a[6] = stamp;
        }
        final int st = stamp;
        M.values().removeIf(a -> a[6] != st);
    }

    /** Posicion interpolada de los pies de la entidad. */
    public static void pos(Entity e, float pt, double[] out) {
        double[] a = M.get(e.getId());
        if (a == null) { out[0] = e.getX(); out[1] = e.getY(); out[2] = e.getZ(); return; }
        out[0] = a[0] + (a[3] - a[0]) * pt;
        out[1] = a[1] + (a[4] - a[1]) * pt;
        out[2] = a[2] + (a[5] - a[2]) * pt;
    }
}
