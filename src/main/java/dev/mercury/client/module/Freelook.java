package dev.mercury.client.module;

import com.visionplus.client.Freecam;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;

/**
 * Freelook: mientras se usa, la camara gira 360 alrededor de tu personaje sin mover tu cuerpo
 * ni hacia donde caminas/pegas, y no choca con bloques (los atraviesa).
 */
public final class Freelook {
    public static float yaw, pitch;
    public static int mode = 0;          // 0 = mantener tecla, 1 = alternar
    public static boolean noClip = true; // la camara atraviesa bloques
    public static int distance = 4;      // bloques detras del personaje

    private static boolean active;
    private static Object prevType;

    private Freelook() {}

    public static boolean active() { return active && !Freecam.active; }

    public static void begin(Minecraft mc) {
        if (active || mc.player == null) return;
        active = true;
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        prevType = getType(mc);
        Object back = thirdPersonBack();
        if (back != null) setType(mc, back);
    }

    public static void end(Minecraft mc) {
        if (!active) return;
        active = false;
        if (prevType != null) setType(mc, prevType);
        prevType = null;
    }

    /** Llamado desde el mixin de Entity.turn: el mouse mueve la camara en vez del jugador. */
    public static void turn(double dYaw, double dPitch) {
        yaw += (float) (dYaw * 0.15);
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) (dPitch * 0.15)));
    }

    // ---- tecla ----
    private static boolean keyWas = true;

    static void tick(Minecraft mc, boolean down) {
        Module m = ModuleManager.FREELOOK;
        boolean usable = m != null && m.isOn() && mc.player != null && !Freecam.active;
        if (!usable) { end(mc); keyWas = true; return; }
        if (mc.gui.screen() != null) {
            if (mode == 0) end(mc);
            keyWas = true;
            return;
        }
        if (mode == 0) {
            if (down) begin(mc); else end(mc);
        } else if (down && !keyWas) {
            if (active) end(mc); else begin(mc);
        }
        keyWas = down;
    }

    // ---- cambio de camara por reflexion (asi no depende del nombre exacto en cada version) ----
    private static Object getType(Minecraft mc) {
        try { return mc.options.getClass().getMethod("getCameraType").invoke(mc.options); }
        catch (Throwable t) { return null; }
    }

    private static void setType(Minecraft mc, Object type) {
        try {
            for (Method m : mc.options.getClass().getMethods())
                if (m.getName().equals("setCameraType") && m.getParameterCount() == 1) { m.invoke(mc.options, type); return; }
        } catch (Throwable ignored) {}
    }

    private static Object thirdPersonBack() {
        try {
            for (Object o : Class.forName("net.minecraft.client.CameraType").getEnumConstants())
                if (((Enum<?>) o).name().equals("THIRD_PERSON_BACK")) return o;
        } catch (Throwable ignored) {}
        return null;
    }
}
