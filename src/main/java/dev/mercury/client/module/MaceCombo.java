package dev.mercury.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Mace Combo: vas volando con elytra hacia un jugador y cuando estas a rango:
 *  1) te pone la pechera (hotbar: cambio directo; inventario: abre la E, la coloca y cierra),
 *  2) pasa tu slot al mazo de la hotbar,
 *  3) golpea con el mazo apenas llegas al alcance.
 * Despues, si sales volando (lanzado hacia arriba), se equipa la elytra sola (y opcionalmente empieza a planear).
 */
public final class MaceCombo {
    private static final Minecraft mc = Minecraft.getInstance();

    public static int range = 8;          // bloques a los que empieza el cambio de armadura
    public static boolean reEquip = true; // volver a la elytra al salir volando
    public static boolean autoGlide = true;
    public static boolean useInventory = true; // si no esta en la hotbar, abrir la E

    private enum Phase { IDLE, ARMOR, MACE, HIT, AFTER }
    private static final class Step { int at; Runnable r; }

    private static Phase phase = Phase.IDLE;
    private static final List<Step> steps = new ArrayList<>();
    private static Player target;
    private static int tick, phaseTicks, cooldown, glideTries;

    private MaceCombo() {}

    public static boolean on() { return ModuleManager.MACE_COMBO != null && ModuleManager.MACE_COMBO.isOn(); }

    private static void reset() {
        phase = Phase.IDLE; steps.clear(); target = null; phaseTicks = 0; glideTries = 0;
    }

    private static void schedule(int delay, Runnable r) {
        Step s = new Step();
        s.at = tick + delay;
        s.r = r;
        steps.add(s);
    }

    // ---------------- items ----------------
    private static boolean isChestplate(ItemStack s) {
        return s.is(Items.NETHERITE_CHESTPLATE) || s.is(Items.DIAMOND_CHESTPLATE) || s.is(Items.IRON_CHESTPLATE)
                || s.is(Items.GOLDEN_CHESTPLATE) || s.is(Items.CHAINMAIL_CHESTPLATE) || s.is(Items.LEATHER_CHESTPLATE);
    }

    private static boolean isElytra(ItemStack s) { return s.is(Items.ELYTRA); }

    private static int chestPriority(ItemStack s) {
        if (s.is(Items.NETHERITE_CHESTPLATE)) return 6;
        if (s.is(Items.DIAMOND_CHESTPLATE)) return 5;
        if (s.is(Items.IRON_CHESTPLATE)) return 4;
        if (s.is(Items.CHAINMAIL_CHESTPLATE)) return 3;
        if (s.is(Items.GOLDEN_CHESTPLATE)) return 2;
        return 1;
    }

    private static int findMace(LocalPlayer p) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(Items.MACE)) return i;
        return -1;
    }

    private static void swap(LocalPlayer p, int slotId, int button) {
        if (mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slotId, button, ContainerInput.SWAP, p);
    }

    /**
     * Pone en el pecho lo que pida `want`. Hotbar -> cambio directo (sin abrir nada).
     * Inventario interno -> abre la E, coloca y cierra (parece un jugador normal).
     * @return true si ya estaba puesto o se puso en marcha
     */
    private static boolean equipChest(Predicate<ItemStack> want, java.util.function.ToIntFunction<ItemStack> score) {
        LocalPlayer p = mc.player;
        if (p == null) return false;
        if (want.test(p.getItemBySlot(EquipmentSlot.CHEST))) return true;

        int hb = -1, best = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (want.test(s) && score.applyAsInt(s) > best) { best = score.applyAsInt(s); hb = i; }
        }
        if (hb >= 0) { swap(p, 6, hb); return true; }

        if (!useInventory || mc.gui.screen() != null) return false;
        int inner = -1;
        best = -1;
        for (int s = 9; s <= 35; s++) {
            ItemStack it = p.inventoryMenu.slots.get(s).getItem();
            if (want.test(it) && score.applyAsInt(it) > best) { best = score.applyAsInt(it); inner = s; }
        }
        if (inner < 0) return false;

        int mace = findMace(p);
        int spare = 0;
        for (int i = 0; i < 9; i++) if (i != mace && i != p.getInventory().getSelectedSlot()) { spare = i; break; }
        final int fi = inner, fs = spare;
        mc.gui.setScreen(new InventoryScreen(p));
        schedule(2, () -> swap(p, fi, fs));   // pechera -> hotbar (slot libre)
        schedule(3, () -> swap(p, 6, fs));    // hotbar -> pecho
        schedule(4, () -> swap(p, fi, fs));   // lo que tenias puesto vuelve al inventario
        schedule(6, () -> { if (mc.gui.screen() instanceof InventoryScreen) mc.gui.setScreen(null); });
        return true;
    }

    private static void msg(String s) {
        if (mc.gui != null) mc.gui.hud.setOverlayMessage(Component.literal("Mace Combo: " + s), false);
    }

    private static void startGlide(LocalPlayer p) {
        try {
            Class<?> pk = Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket");
            Class<?> act = Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket$Action");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object a = Enum.valueOf((Class) act, "START_FALL_FLYING");
            Object pkt = pk.getConstructor(net.minecraft.world.entity.Entity.class, act).newInstance(p, a);
            Object conn = p.connection;
            for (var m : conn.getClass().getMethods())
                if (m.getName().equals("send") && m.getParameterCount() == 1) { m.invoke(conn, pkt); return; }
        } catch (Throwable ignored) {}
    }

    // ---------------- tick ----------------
    private static Player nearest(LocalPlayer p, double max) {
        Player best = null;
        double bd = max * max;
        for (Player o : mc.level.players()) {
            if (o == p || !o.isAlive() || o.isSpectator() || dev.mercury.client.Config.isFriend(o.getName().getString())) continue;
            double d = o.distanceToSqr(p);
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    public static void tick() {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) { reset(); return; }
        tick++;
        if (cooldown > 0) cooldown--;

        // acciones programadas (inventario)
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            if (tick >= s.at) { steps.remove(i--); s.r.run(); }
        }
        if (!on()) { if (phase != Phase.IDLE || !steps.isEmpty()) reset(); return; }
        if (!steps.isEmpty()) return; // esperando a que termine el cambio

        phaseTicks++;
        boolean elytraOn = isElytra(p.getItemBySlot(EquipmentSlot.CHEST));

        switch (phase) {
            case IDLE -> {
                if (p.isFallFlying() && mc.gui.screen() == null) {
                    Player t = nearest(p, range);
                    if (t != null) { target = t; phase = Phase.ARMOR; phaseTicks = 0; }
                } else if (reEquip && cooldown == 0 && !elytraOn && !p.onGround() && mc.gui.screen() == null
                        && p.getDeltaMovement().y > 0.5) {
                    // saliste volando: elytra puesta
                    if (equipChest(MaceCombo::isElytra, s -> 1)) { cooldown = 60; glideTries = autoGlide ? 40 : 0; }
                }
                if (glideTries > 0) glideStep(p);
            }
            case ARMOR -> {
                if (target == null || !target.isAlive()) { phase = Phase.IDLE; return; }
                if (isElytra(p.getItemBySlot(EquipmentSlot.CHEST))) {
                    if (!equipChest(MaceCombo::isChestplate, MaceCombo::chestPriority)) msg("no tienes pechera");
                }
                phase = Phase.MACE;
                phaseTicks = 0;
            }
            case MACE -> {
                if (target == null || !target.isAlive()) { phase = Phase.IDLE; return; }
                int m = findMace(p);
                if (m < 0) { msg("no hay mazo en la hotbar"); phase = Phase.IDLE; return; }
                if (p.getInventory().getSelectedSlot() != m) p.getInventory().setSelectedSlot(m);
                phase = Phase.HIT;
                phaseTicks = 0;
            }
            case HIT -> {
                if (target == null || !target.isAlive() || phaseTicks > 80 || mc.gui.screen() != null) {
                    phase = Phase.IDLE;
                    return;
                }
                Vec3 eye = p.getEyePosition();
                if (target.getBoundingBox().distanceToSqr(eye) <= 2.9 * 2.9) {
                    mc.gameMode.attack(p, target);
                    p.swing(InteractionHand.MAIN_HAND);
                    phase = Phase.AFTER;
                    phaseTicks = 0;
                }
            }
            case AFTER -> {
                if (reEquip && cooldown == 0 && !elytraOn && !p.onGround() && p.getDeltaMovement().y > 0.5
                        && mc.gui.screen() == null) {
                    if (equipChest(MaceCombo::isElytra, s -> 1)) { cooldown = 60; glideTries = autoGlide ? 40 : 0; }
                }
                if (glideTries > 0) glideStep(p);
                if ((p.onGround() && phaseTicks > 10) || phaseTicks > 120) { phase = Phase.IDLE; target = null; }
            }
        }
    }

    private static void glideStep(LocalPlayer p) {
        glideTries--;
        if (isElytra(p.getItemBySlot(EquipmentSlot.CHEST)) && !p.onGround() && !p.isFallFlying()
                && p.getDeltaMovement().y < -0.05 && glideTries % 3 == 0) {
            startGlide(p);
        }
    }
}
