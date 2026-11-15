package dev.mercury.client.module;

import dev.mercury.client.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat Zylow: auto-ataque cuando el crosshair apunta a un jugador.
 * Respeta el cooldown del arma y usa timing humanizado.
 *
 * Port 1:1 de combat-zylow 1.0.0. Los valores son fijos a proposito:
 * no hay ajustes de ticks ni de nada, solo se activa/desactiva con la keybind.
 */
public final class AutoHit {
    // ---- constantes fijas (iguales al jar original) ----
    private static final int FIRST_PERFECT_HITS = 3;     // primeros golpes siempre perfectos
    private static final double CHANCE_DELAYED = 0.18;   // prob. de golpear con retraso
    private static final double CHANCE_EARLY = 0.05;     // prob. de golpear antes de cargar al 100%
    private static final float EARLY_MIN = 0.7f;
    private static final float EARLY_MAX = 0.95f;
    private static final int DELAY_MAX_TICKS = 3;
    private static final int COMBO_RESET_TICKS = 20;
    private static final boolean SHOW_MESSAGE = true;

    /** Alcance maximo para golpear (bloques). Mas corto que el alcance vanilla (3.0) para que el servidor no rechace el golpe. */
    public static final String[] REACH = {"2.2", "2.4", "2.6", "2.8", "3.0"};
    public static int reachIdx = 1;
    private static double lastTx, lastTz;
    private static Player lastTarget;

    private static final class Plan {
        float minStrength = 1.0f;
        int extraDelay = 0;
    }

    private static final Minecraft mc = Minecraft.getInstance();

    private static int comboHits, lostTicks;
    private static boolean hadTarget;
    private static Plan plan;

    private AutoHit() {}

    public static boolean isEnabled() { return ModuleManager.COMBAT != null && ModuleManager.COMBAT.isOn(); }

    // ---------------- Tick ----------------
    public static void tick() {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            resetAll();
            return;
        }

        if (!isEnabled()) { resetAll(); return; }
        if (mc.gui.screen() != null || p.isUsingItem() || !p.isAlive()) {
            plan = null;
            return;
        }

        Player target = lookedAtPlayer(p);
        if (target == null) {
            plan = null;
            hadTarget = false;
            if (++lostTicks > COMBO_RESET_TICKS) comboHits = 0;
            return;
        }
        lostTicks = 0;

        // Si el objetivo esta en el borde del alcance, el servidor (con ping) rechaza el golpe pero el cliente ya aplico
        // el frenazo del ataque -> el servidor te corrige la posicion y sientes que te quedas trabado. Solo se golpea dentro de rango seguro.
        double reach = Double.parseDouble(REACH[Math.floorMod(reachIdx, REACH.length)]);
        // persiguiendo: si el objetivo se aleja, en el servidor ya esta mas lejos que lo que ves (ping) -> margen extra
        if (target == lastTarget) {
            double mx = target.getX() - lastTx, mz = target.getZ() - lastTz;
            double ax = target.getX() - p.getX(), az = target.getZ() - p.getZ();
            double len = Math.sqrt(ax * ax + az * az);
            if (len > 0.01 && (mx * ax + mz * az) / len > 0.03) reach -= 0.3;
        }
        lastTarget = target; lastTx = target.getX(); lastTz = target.getZ();
        if (target.getBoundingBox().distanceToSqr(p.getEyePosition()) > reach * reach) {
            plan = null;
            return;
        }

        if (plan == null) plan = newPlan(!hadTarget);
        hadTarget = true;

        float strength = p.getAttackStrengthScale(0.5f);
        if (strength < plan.minStrength) return;

        if (plan.extraDelay > 0) {
            plan.extraDelay--;
            return;
        }

        mc.gameMode.attack(p, target);
        p.swing(InteractionHand.MAIN_HAND);
        comboHits++;
        plan = null;
    }

    private static Player lookedAtPlayer(LocalPlayer self) {
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e instanceof Player pl && pl != self && !Config.isFriend(pl.getName().getString()) && pl.isAlive() && !pl.isSpectator()) return pl;
        }
        return null;
    }

    private static Plan newPlan(boolean firstOfTarget) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Plan pl = new Plan();
        if (comboHits < FIRST_PERFECT_HITS) return pl;

        double roll = r.nextDouble();
        if (roll < CHANCE_EARLY) {
            pl.minStrength = EARLY_MIN + r.nextFloat() * (EARLY_MAX - EARLY_MIN);
        } else if (roll < CHANCE_EARLY + CHANCE_DELAYED) {
            pl.extraDelay = 1 + r.nextInt(DELAY_MAX_TICKS);
        } else if (firstOfTarget && r.nextDouble() < 0.3) {
            pl.extraDelay = 1;
        }
        return pl;
    }

    private static void resetAll() {
        plan = null;
        comboHits = 0;
        lostTicks = 0;
        hadTarget = false;
        lastTarget = null;
    }
}
