package dev.mercury.client.module;

import dev.mercury.client.fx.Cam;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;

/** Hitbox de end crystals: aristas suaves con brillo (mismo estilo que el ESP de cofres/jugadores). */
public final class HitboxOverlay {
    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("mercuryclient", "crystal_hitbox"),
                (g, dt) -> draw(g, dt.getGameTimeDeltaPartialTick(false)));
    }

    private static void draw(GuiGraphicsExtractor g, float pt) {
        var p = mc.player;
        if (!ModuleManager.CRYSTAL_HITBOX.isOn() || p == null || mc.level == null) return;
        try {
            Cam.update(mc, g, pt);
            if (!Cam.ok) return;
            int color = dev.mercury.client.Config.HB_COLORS[Math.floorMod(Pvp.hitboxColor, dev.mercury.client.Config.HB_COLORS.length)] & 0xFFFFFF;
            int n = 0;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof EndCrystal) || e.distanceToSqr(p) > 48 * 48) continue;
                AABB bb = e.getBoundingBox();
                Cam.box(g, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, color, 1f, 1, n++ < 10);
                Cam.halo(g, (bb.minX + bb.maxX) / 2, (bb.minY + bb.maxY) / 2, (bb.minZ + bb.maxZ) / 2, 1.8, color, 0.2f);
            }
        } catch (Throwable ignored) {}
    }
}
