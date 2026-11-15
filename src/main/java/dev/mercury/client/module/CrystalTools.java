package dev.mercury.client.module;

import dev.mercury.client.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crystal Hold: manten click derecho con un cristal en la mano -> coloca y rompe cristales en la obsidiana/bedrock que miras.
 * Crystal Auto: mirando obsidiana/bedrock (con cristales en la hotbar) coloca y rompe solo.
 *
 * Los delays estan en ticks (1 tick = 50 ms) y cada accion usa un valor aleatorio entre min y max,
 * igual que el resto de modulos de Mercury, para que el ritmo no sea perfectamente constante.
 * Con min = 0 la accion se hace en el mismo tick en que se ve el objetivo (lo mas rapido que permite el cliente).
 */
public final class CrystalTools {
    private static final Minecraft mc = Minecraft.getInstance();

    // ---- Crystal Hold ----
    public static int holdPlaceMin = 1, holdPlaceMax = 2, holdBreakMin = 1, holdBreakMax = 2;
    // ---- Crystal Auto ----
    public static int autoPlaceMin = 1, autoPlaceMax = 2, autoBreakMin = 1, autoBreakMax = 2;
    public static boolean autoSwitch = true;
    public static int autoEnemyRange = 10;  // 0 = siempre; si no, solo actua con un jugador (no amigo) a menos de N bloques
    public static int autoMinHealth = 6;    // 0 = desactivado; no rompe cristales si tu vida <= N (te protege de tu propia explosion)

    private static int tick, placeAt = -1, breakAt = -1, switchedAt = -100;
    private static Method useOn;
    private static boolean useOnLooked;
    private static Field rcd;
    private static boolean rcdTried;

    private CrystalTools() {}

    private static boolean on(Module m) { return m != null && m.isOn(); }

    private static int rand(int a, int b) {
        if (b < a) b = a;
        return a + ThreadLocalRandom.current().nextInt(b - a + 1);
    }

    private static void reset() { placeAt = -1; breakAt = -1; }

    public static void tick() {
        tick++;
        holdPlaceMax = Math.max(holdPlaceMax, holdPlaceMin);
        holdBreakMax = Math.max(holdBreakMax, holdBreakMin);
        autoPlaceMax = Math.max(autoPlaceMax, autoPlaceMin);
        autoBreakMax = Math.max(autoBreakMax, autoBreakMin);

        LocalPlayer p = mc.player;
        boolean hold = on(ModuleManager.CRYSTAL_HOLD), auto = on(ModuleManager.CRYSTAL_AUTO);
        if ((!hold && !auto) || p == null || mc.level == null || mc.gameMode == null
                || mc.gui.screen() != null || !p.isAlive()) { reset(); return; }

        boolean holding = p.getMainHandItem().is(Items.END_CRYSTAL);
        boolean manual = hold && holding && mc.options.keyUse.isDown();
        if (!manual && !auto) { reset(); return; }

        int pMin = manual ? holdPlaceMin : autoPlaceMin, pMax = manual ? holdPlaceMax : autoPlaceMax;
        int bMin = manual ? holdBreakMin : autoBreakMin, bMax = manual ? holdBreakMax : autoBreakMax;
        HitResult hr = mc.hitResult;

        // ---- romper el cristal que miras ----
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof EndCrystal crystal && crystal.isAlive()) {
            placeAt = -1;
            if (!manual && autoMinHealth > 0 && p.getHealth() <= autoMinHealth) { breakAt = -1; return; }
            if (manual) suppressVanillaUse();
            if (breakAt < 0) breakAt = tick + rand(bMin, bMax);
            if (tick >= breakAt) {
                mc.gameMode.attack(p, crystal);
                p.swing(InteractionHand.MAIN_HAND);
                breakAt = -1;
            }
            return;
        }
        breakAt = -1;

        // ---- colocar en obsidiana / bedrock ----
        if (!(hr instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) { placeAt = -1; return; }
        BlockPos pos = bhr.getBlockPos();
        var st = mc.level.getBlockState(pos);
        if (!(st.is(Blocks.OBSIDIAN) || st.is(Blocks.BEDROCK)) || !mc.level.getBlockState(pos.above()).isAir()) { placeAt = -1; return; }

        if (!manual) {
            if (autoEnemyRange > 0 && !enemyNear(p, autoEnemyRange)) { placeAt = -1; return; }
            if (!holding) {
                if (!autoSwitch) { placeAt = -1; return; }
                int s = findHotbar(p, Items.END_CRYSTAL);
                if (s < 0) { placeAt = -1; return; }
                p.getInventory().setSelectedSlot(s);
                switchedAt = tick;
                return; // el servidor se entera del cambio de slot; se coloca en el siguiente tick
            }
            if (tick <= switchedAt) return;
        } else {
            suppressVanillaUse();
        }

        if (placeAt < 0) placeAt = tick + rand(pMin, pMax);
        if (tick >= placeAt) {
            place(p, bhr);
            placeAt = -1;
        }
    }

    private static boolean enemyNear(LocalPlayer p, int range) {
        double r2 = (double) range * range;
        for (Player o : mc.level.players()) {
            if (o == p || !o.isAlive() || o.isSpectator()) continue;
            if (Config.isFriend(o.getName().getString())) continue;
            if (o.distanceToSqr(p) <= r2) return true;
        }
        return false;
    }

    private static int findHotbar(LocalPlayer p, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private static void place(LocalPlayer p, BlockHitResult bhr) {
        try {
            if (!useOnLooked) {
                useOnLooked = true;
                for (Method m : mc.gameMode.getClass().getMethods())
                    if (m.getName().equals("useItemOn") && m.getParameterCount() == 3) { useOn = m; break; }
            }
            if (useOn != null) {
                useOn.invoke(mc.gameMode, p, InteractionHand.MAIN_HAND, bhr);
                p.swing(InteractionHand.MAIN_HAND);
            }
        } catch (Throwable ignored) {}
    }

    /** Mientras Crystal Hold actua, evita que el click derecho normal de Minecraft coloque a la vez (paquetes duplicados). */
    private static void suppressVanillaUse() {
        try {
            if (!rcdTried) {
                rcdTried = true;
                rcd = Minecraft.class.getDeclaredField("rightClickDelay");
                rcd.setAccessible(true);
            }
            if (rcd != null && rcd.getInt(mc) < 2) rcd.setInt(mc, 2);
        } catch (Throwable ignored) { rcd = null; }
    }
}
