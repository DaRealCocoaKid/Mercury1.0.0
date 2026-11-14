package dev.mercury.client.module;

import dev.mercury.client.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

/**
 * Safe Anchor: al colocar un respawn anchor cerca de un enemigo hace la tecnica completa:
 *  1) carga el anchor con glowstone,
 *  2) coloca un bloque de escudo entre tu y el anchor (glowstone u obsidiana),
 *  3) cambia a totem (o a un slot que no sea glowstone),
 *  4) detona el anchor.
 * Los clicks se mandan directo al servidor (no hace falta mirar el bloque ni mover la camara).
 * No actua en el Nether (ahi el anchor solo fija el spawn).
 */
public final class SafeAnchor {
    private static final Minecraft mc = Minecraft.getInstance();

    public static int min = 1, max = 3, range = 8, shieldMode = 0;
    public static boolean totem = true, detonate = true, shield = true;
    public static final String[] SHIELDS = {"Glowstone", "Obsidiana"};

    private enum St { IDLE, CHARGE, SHIELD, SWITCH, BOOM }

    private static St st = St.IDLE;
    private static BlockPos anchor;
    private static int tick, at, waited;
    private static Method useItemOn;
    private static boolean looked;

    private SafeAnchor() {}

    public static boolean on() { return ModuleManager.SAFE_ANCHOR != null && ModuleManager.SAFE_ANCHOR.isOn(); }

    private static void reset() { st = St.IDLE; anchor = null; waited = 0; }

    private static int next() { return tick + Math.max(1, Pvp.rand(min, Math.max(min, max))); }

    /** Llamado al usar un respawn anchor en la mano (antes de que se coloque). */
    public static void start(Player p, Level l, BlockHitResult hit) {
        if (st != St.IDLE || l.dimension() == Level.NETHER) return;
        BlockPos a = hit.getBlockPos().relative(hit.getDirection());
        if (!enemyNear(a)) return;
        if (findHotbar(p, Items.GLOWSTONE) < 0) return;
        anchor = a;
        st = St.CHARGE;
        waited = 0;
        at = next() + 1; // un tick extra para que el servidor confirme el anchor
    }

    private static boolean enemyNear(BlockPos a) {
        double x = a.getX() + 0.5, y = a.getY() + 0.5, z = a.getZ() + 0.5;
        for (Player o : mc.level.players()) {
            if (o == mc.player || !o.isAlive() || o.isSpectator() || Config.isFriend(o.getName().getString())) continue;
            if (o.distanceToSqr(x, y, z) <= (double) range * range) return true;
        }
        return false;
    }

    private static int findHotbar(Player p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private static void select(LocalPlayer p, int slot) {
        if (slot >= 0) p.getInventory().setSelectedSlot(slot);
    }

    /** click derecho directo sobre una cara de un bloque (sin rotar la camara). */
    private static void click(LocalPlayer p, BlockPos pos, Direction face) {
        if (mc.gameMode == null) return;
        try {
            if (!looked) {
                looked = true;
                for (Method m : mc.gameMode.getClass().getMethods()) {
                    if (m.getName().equals("useItemOn") && m.getParameterCount() == 3) { useItemOn = m; break; }
                }
            }
            if (useItemOn == null) return;
            Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            useItemOn.invoke(mc.gameMode, p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
            p.swing(InteractionHand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }

    private static boolean isAnchor() { return mc.level.getBlockState(anchor).is(Blocks.RESPAWN_ANCHOR); }

    /** slot para detonar: totem si hay, si no cualquier cosa que no sea glowstone/anchor/perla. */
    private static int detonateSlot(LocalPlayer p) {
        if (totem) { int t = findHotbar(p, Items.TOTEM_OF_UNDYING); if (t >= 0) return t; }
        int cur = p.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            var s = p.getInventory().getItem(i);
            if (i == cur || s.isEmpty() || s.is(Items.GLOWSTONE) || s.is(Items.RESPAWN_ANCHOR) || s.is(Items.ENDER_PEARL)) continue;
            return i;
        }
        return -1;
    }

    public static void tick() {
        tick++;
        if (st == St.IDLE) return;
        LocalPlayer p = mc.player;
        if (!on() || p == null || mc.level == null || mc.gui.screen() != null || anchor == null) { reset(); return; }
        if (p.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5) > 36) { reset(); return; }
        if (tick < at) return;

        switch (st) {
            case CHARGE -> {
                if (!isAnchor()) { if (++waited > 12) reset(); else at = tick + 1; return; }
                int g = findHotbar(p, Items.GLOWSTONE);
                if (g < 0) { reset(); return; }
                select(p, g);
                click(p, anchor, Direction.UP);
                st = St.SHIELD;
                at = next();
            }
            case SHIELD -> {
                if (shield) placeShield(p);
                st = St.SWITCH;
                at = next();
            }
            case SWITCH -> {
                if (!detonate) { reset(); return; }
                int s = detonateSlot(p);
                if (s >= 0) select(p, s);
                st = St.BOOM;
                at = next();
            }
            case BOOM -> {
                if (isAnchor()) click(p, anchor, Direction.UP);
                reset();
            }
            default -> reset();
        }
    }

    /** Bloque de escudo pegado al anchor, del lado de tu cuerpo. Se apoya en el bloque de abajo (sin agacharte). */
    private static void placeShield(LocalPlayer p) {
        int slot = -1;
        if (shieldMode == 1) slot = findHotbar(p, Items.OBSIDIAN);
        if (slot < 0) slot = findHotbar(p, Items.GLOWSTONE);
        if (slot < 0) return;

        double dx = p.getX() - (anchor.getX() + 0.5), dz = p.getZ() - (anchor.getZ() + 0.5);
        Direction toward = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos s = anchor.relative(toward);
        BlockPos body = p.blockPosition();
        if (s.equals(body) || s.equals(body.above())) return;
        if (!mc.level.getBlockState(s).isAir()) return;
        BlockPos support = s.below();
        if (mc.level.getBlockState(support).isAir()) return;
        select(p, slot);
        click(p, support, Direction.UP);
    }
}
