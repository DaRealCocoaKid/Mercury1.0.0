package dev.mercury.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;

/** Auto Walk (camina solo hacia adelante) y Auto Water Drop (water drop automatico con cubeta de agua en la hotbar). */
public final class MoveTools {
    private static final Minecraft mc = Minecraft.getInstance();

    // ---- ajustes ----
    public static int minFall = 5;            // bloques caidos antes de preparar el water drop
    public static boolean pickUp = true;      // recoger el agua al aterrizar
    public static boolean restore = true;     // volver al slot y a la mirada de antes

    private static final double REACH = 2.7;  // distancia pies -> suelo a la que se tira el agua (alcance de bloque ~4.5 menos la altura de los ojos)

    private static boolean walking;
    private static double fallStartY;
    private static int phase, timer, prevSlot = -1;
    private static float prevPitch;
    private static Method useItem;
    private static boolean useLooked;

    private MoveTools() {}

    private static boolean on(Module m) { return m != null && m.isOn(); }

    // ================= Auto Walk (se llama al INICIO del tick para que el juego lea la tecla ya puesta) =================
    public static void startTick(Minecraft m) {
        LocalPlayer p = m.player;
        boolean want = on(ModuleManager.AUTOWALK) && p != null && m.level != null && m.gui.screen() == null && p.isAlive();
        if (want) { m.options.keyUp.setDown(true); walking = true; }
        else if (walking) { walking = false; m.options.keyUp.setDown(false); }
    }

    // ================= Auto Water Drop (se llama al FINAL del tick) =================
    public static void tick() {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) { phase = 0; prevSlot = -1; return; }

        boolean grounded = p.onGround() || p.isInWater() || p.isFallFlying() || p.getAbilities().flying || p.onClimbable();
        if (grounded) fallStartY = p.getY(); else fallStartY = Math.max(fallStartY, p.getY());
        double fall = fallStartY - p.getY();
        boolean enabled = on(ModuleManager.WATER_DROP);

        switch (phase) {
            case 0 -> {
                if (!enabled || grounded || mc.gui.screen() != null || p.isDeadOrDying()) return;
                if (fall < minFall || p.getDeltaMovement().y > -0.5) return;
                int slot = findHotbar(p, Items.WATER_BUCKET);
                if (slot < 0) return;
                if (groundDistance(p) < 0) return; // agua/lava debajo o vacio: no hace falta
                prevSlot = p.getInventory().getSelectedSlot();
                prevPitch = p.getXRot();
                p.getInventory().setSelectedSlot(slot);
                p.setXRot(90f);
                phase = 1; timer = 0;
            }
            case 1 -> { // apuntando abajo, esperando estar al alcance del suelo
                timer++;
                if (!enabled || grounded || timer > 160) { finish(p); return; }
                p.setXRot(90f);
                double d = groundDistance(p);
                if (d == -2) { finish(p); return; }
                if (timer >= 1 && d >= 0 && d <= REACH && p.getMainHandItem().is(Items.WATER_BUCKET)) {
                    use(p);
                    phase = 2; timer = 0;
                }
            }
            case 2 -> { // agua puesta: esperar a aterrizar y recogerla
                timer++;
                p.setXRot(90f);
                if (timer > 80) { finish(p); return; }
                if (p.getMainHandItem().is(Items.WATER_BUCKET) && timer >= 3) { // no se coloco: reintentar si todavia cae
                    if (grounded) finish(p); else phase = 1;
                    return;
                }
                if (!pickUp) { if (timer >= 3) finish(p); return; }
                if (timer >= 2 && grounded && p.getMainHandItem().is(Items.BUCKET)) {
                    use(p);
                    phase = 3; timer = 0;
                }
            }
            default -> { // recogiendo
                timer++;
                p.setXRot(90f);
                if (timer >= 3) finish(p);
            }
        }
    }

    private static void finish(LocalPlayer p) {
        if (restore) {
            if (prevSlot >= 0) p.getInventory().setSelectedSlot(prevSlot);
            p.setXRot(prevPitch);
        }
        phase = 0; timer = 0; prevSlot = -1;
    }

    /** Distancia de tus pies al primer bloque solido de abajo. -1 = nada solido en 40 bloques, -2 = hay fluido debajo. */
    private static double groundDistance(LocalPlayer p) {
        int x = Mth.floor(p.getX()), z = Mth.floor(p.getZ()), y0 = Mth.floor(p.getY());
        for (int i = 0; i < 40; i++) {
            BlockPos pos = new BlockPos(x, y0 - i, z);
            var st = mc.level.getBlockState(pos);
            if (!st.getFluidState().isEmpty()) return -2;
            var shape = st.getCollisionShape(mc.level, pos);
            if (!shape.isEmpty()) return p.getY() - (pos.getY() + shape.max(Direction.Axis.Y));
        }
        return -1;
    }

    private static int findHotbar(LocalPlayer p, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private static void use(LocalPlayer p) {
        try {
            if (!useLooked) {
                useLooked = true;
                for (Method m : mc.gameMode.getClass().getMethods())
                    if (m.getName().equals("useItem") && m.getParameterCount() == 2) { useItem = m; break; }
            }
            if (useItem != null) { useItem.invoke(mc.gameMode, p, InteractionHand.MAIN_HAND); p.swing(InteractionHand.MAIN_HAND); }
        } catch (Throwable ignored) {}
    }
}
