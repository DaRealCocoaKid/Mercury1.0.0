package dev.mercury.client.module;

import dev.mercury.client.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Herramientas de PvP ligeras (Sprint, FastPlace, Fullbright, AimAssist, AutoTotem, AutoArmor, AutoGapple, PearlThrow).
 * Todo sale temprano si el modulo esta apagado y no reserva memoria por tick, para no costar FPS.
 */
public final class PvpTools {
    private static final Minecraft mc = Minecraft.getInstance();
    private static int tick;

    // ---- ajustes ----
    public static int aimRange = 5, aimFov = 90, aimSpeed = 5, aimTarget = 0;
    public static boolean aimClick = true, aimVertical = true, aimFriends = true;
    public static final String[] AIM_TARGETS = {"Pecho", "Cabeza"};
    public static int totemDelayMin = 1, totemDelayMax = 3, totemHealth = 0;
    public static int armorDelay = 4;
    public static int gapHealth = 10;
    public static boolean gapEnchanted = false, gapRestore = true;
    public static boolean pearlRestore = true;
    public static boolean sprintAlways = false;

    private PvpTools() {}

    private static boolean on(Module m) { return m != null && m.isOn(); }

    private static final List<Step> steps = new ArrayList<>();
    private static final class Step { int at; Runnable r; }
    private static void schedule(int delay, Runnable r) { Step s = new Step(); s.at = tick + delay; s.r = r; steps.add(s); }

    private static void swap(LocalPlayer p, int slotId, int button) {
        if (mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slotId, button, ContainerInput.SWAP, p);
    }

    private static int rand(int a, int b) { return b <= a ? a : a + ThreadLocalRandom.current().nextInt(b - a + 1); }

    // ================= tick =================
    private static int armorCd, totemAt = -1, gapCd, gapPhase, gapTimer, gapPrevSlot = -1, pearlPhase, pearlTimer, pearlPrev = -1;
    private static boolean fbAdded;
    private static Field rcd;
    private static boolean rcdTried;

    public static void tick() {
        tick++;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { steps.clear(); return; }

        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            if (tick >= s.at) { steps.remove(i--); s.r.run(); }
        }

        if (on(ModuleManager.SPRINT)) sprint(p);
        if (on(ModuleManager.FAST_PLACE)) fastPlace();
        fullbright(p, on(ModuleManager.FULLBRIGHT));
        if (on(ModuleManager.AIM_ASSIST)) aim(p);
        if (on(ModuleManager.AUTO_TOTEM)) autoTotem(p);
        if (on(ModuleManager.AUTO_ARMOR)) autoArmor(p);
        gapple(p);
        pearl(p);
    }

    // ---------------- Sprint ----------------
    private static void sprint(LocalPlayer p) {
        if (!mc.options.keyUp.isDown() || p.isShiftKeyDown() || p.isUsingItem() || p.getFoodData().getFoodLevel() <= 6) return;
        if (!p.isSprinting()) p.setSprinting(true);
    }

    // ---------------- FastPlace ----------------
    private static void fastPlace() {
        try {
            if (!rcdTried) {
                rcdTried = true;
                rcd = Minecraft.class.getDeclaredField("rightClickDelay");
                rcd.setAccessible(true);
            }
            if (rcd != null && rcd.getInt(mc) > 0) rcd.setInt(mc, 0);
        } catch (Throwable ignored) { rcd = null; }
    }

    // ---------------- Fullbright ----------------
    private static void fullbright(LocalPlayer p, boolean want) {
        if (want) {
            if (tick % 40 == 0 || !p.hasEffect(MobEffects.NIGHT_VISION)) {
                if (!p.hasEffect(MobEffects.NIGHT_VISION) || fbAdded)
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, false, false));
                if (!fbAdded && p.hasEffect(MobEffects.NIGHT_VISION)) fbAdded = true;
            }
        } else if (fbAdded) {
            p.removeEffect(MobEffects.NIGHT_VISION);
            fbAdded = false;
        }
    }

    // ---------------- Aim assist ----------------
    private static void aim(LocalPlayer p) {
        if (mc.gui.screen() != null || (aimClick && !mc.options.keyAttack.isDown())) return;
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYRot(), pitch = p.getXRot();
        double bestAng = aimFov / 2.0;
        float bdy = 0, bdp = 0;
        boolean found = false;
        double r2 = (double) aimRange * aimRange;
        for (Player o : mc.level.players()) {
            if (o == p || !o.isAlive() || o.isSpectator()) continue;
            if (aimFriends && Config.isFriend(o.getName().getString())) continue;
            double dx = o.getX() - eye.x, dy = o.getY() + o.getBbHeight() * (aimTarget == 1 ? 0.93 : 0.68) - eye.y, dz = o.getZ() - eye.z;
            double h2 = dx * dx + dz * dz;
            if (h2 + dy * dy > r2) continue;
            float ty = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float tp = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(h2)));
            float dyaw = Mth.wrapDegrees(ty - yaw), dpit = tp - pitch;
            double ang = Math.sqrt(dyaw * dyaw + dpit * dpit);
            if (ang < bestAng) { bestAng = ang; bdy = dyaw; bdp = dpit; found = true; }
        }
        if (!found) return;
        float f = 0.04f + 0.035f * aimSpeed, max = 3f + aimSpeed * 1.5f;
        float jit = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.2f;
        p.setYRot(yaw + Mth.clamp(bdy * f, -max, max) + jit);
        if (aimVertical) p.setXRot(Mth.clamp(pitch + Mth.clamp(bdp * f * 0.8f, -max, max), -90f, 90f));
    }

    // ---------------- Auto Totem ----------------
    private static void autoTotem(LocalPlayer p) {
        if (mc.gui.screen() != null || p.isDeadOrDying()) { totemAt = -1; return; }
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) { totemAt = -1; return; }
        if (totemHealth > 0 && p.getHealth() > totemHealth) return;
        if (totemAt < 0) { totemAt = tick + rand(totemDelayMin, totemDelayMax); return; }
        if (tick < totemAt) return;
        totemAt = -1;
        for (int i = 36; i <= 44; i++) if (p.inventoryMenu.slots.get(i).getItem().is(Items.TOTEM_OF_UNDYING)) { swap(p, i, 40); return; }
        for (int i = 9; i <= 35; i++) if (p.inventoryMenu.slots.get(i).getItem().is(Items.TOTEM_OF_UNDYING)) { swap(p, i, 40); return; }
    }

    // ---------------- Auto Armor ----------------
    private static final Item[][] ARMOR = {
            {Items.LEATHER_HELMET, Items.GOLDEN_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET},
            {Items.LEATHER_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE},
            {Items.LEATHER_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS},
            {Items.LEATHER_BOOTS, Items.GOLDEN_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS}};

    /** -1 vacio, 0..5 material, -2 otra cosa (elytra, calabaza...) que no se toca */
    private static int rank(int part, ItemStack s) {
        if (s.isEmpty()) return -1;
        for (int i = 0; i < 6; i++) if (s.is(ARMOR[part][i])) return i;
        return -2;
    }

    private static void autoArmor(LocalPlayer p) {
        if (mc.gui.screen() != null || !steps.isEmpty()) return;
        if (++armorCd < Math.max(2, armorDelay * 3)) return;
        armorCd = 0;
        for (int part = 0; part < 4; part++) {
            int cur = rank(part, p.inventoryMenu.slots.get(5 + part).getItem());
            if (cur == -2) continue;
            int best = cur, src = -1;
            for (int s = 9; s <= 44; s++) {
                int r = rank(part, p.inventoryMenu.slots.get(s).getItem());
                if (r > best) { best = r; src = s; }
            }
            if (src < 0) continue;
            final int armorSlot = 5 + part, fs = src;
            if (src >= 36) { swap(p, armorSlot, src - 36); return; }
            int spare = 0, sel = p.getInventory().getSelectedSlot();
            if (spare == sel) spare = 1;
            final int sp = spare;
            swap(p, fs, sp);
            schedule(1, () -> swap(p, armorSlot, sp));
            schedule(2, () -> swap(p, fs, sp));
            return;
        }
    }

    // ---------------- Auto Gapple ----------------
    private static int findHotbar(LocalPlayer p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private static void gapple(LocalPlayer p) {
        if (gapCd > 0) gapCd--;
        if (gapPhase == 0) {
            if (!on(ModuleManager.AUTO_GAPPLE) || gapCd > 0 || mc.gui.screen() != null || p.isDeadOrDying()) return;
            if (p.getHealth() > gapHealth) return;
            int s = gapEnchanted ? findHotbar(p, Items.ENCHANTED_GOLDEN_APPLE) : findHotbar(p, Items.GOLDEN_APPLE);
            if (s < 0) s = findHotbar(p, gapEnchanted ? Items.GOLDEN_APPLE : Items.ENCHANTED_GOLDEN_APPLE);
            if (s < 0) return;
            gapPrevSlot = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(s);
            gapPhase = 1; gapTimer = 0;
            return;
        }
        gapTimer++;
        if (gapPhase == 1 && gapTimer >= 2) { mc.options.keyUse.setDown(true); gapPhase = 2; gapTimer = 0; return; }
        if (gapPhase == 2) {
            boolean done = (gapTimer > 4 && !p.isUsingItem()) || gapTimer > 60 || !on(ModuleManager.AUTO_GAPPLE) || mc.gui.screen() != null;
            if (done) {
                mc.options.keyUse.setDown(false);
                if (gapRestore && gapPrevSlot >= 0) p.getInventory().setSelectedSlot(gapPrevSlot);
                gapPhase = 0; gapCd = 40; gapPrevSlot = -1;
            }
        }
    }

    // ---------------- Pearl throw (se apaga solo; asignale una tecla) ----------------
    private static Method useItem;
    private static boolean useLooked;

    private static void pearl(LocalPlayer p) {
        if (pearlPhase == 0) {
            if (!on(ModuleManager.PEARL)) return;
            ModuleManager.PEARL.setOn(false);
            if (mc.gui.screen() != null) return;
            int s = findHotbar(p, Items.ENDER_PEARL);
            if (s < 0) return;
            pearlPrev = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(s);
            pearlPhase = 1; pearlTimer = 0;
            return;
        }
        if (++pearlTimer < 2) return;
        if (pearlPhase == 1) {
            try {
                if (!useLooked) {
                    useLooked = true;
                    for (Method m : mc.gameMode.getClass().getMethods())
                        if (m.getName().equals("useItem") && m.getParameterCount() == 2) { useItem = m; break; }
                }
                if (useItem != null) { useItem.invoke(mc.gameMode, p, InteractionHand.MAIN_HAND); p.swing(InteractionHand.MAIN_HAND); }
            } catch (Throwable ignored) {}
            pearlPhase = 2; pearlTimer = 0;
            return;
        }
        if (pearlRestore && pearlPrev >= 0) p.getInventory().setSelectedSlot(pearlPrev);
        pearlPhase = 0; pearlPrev = -1;
    }
}
