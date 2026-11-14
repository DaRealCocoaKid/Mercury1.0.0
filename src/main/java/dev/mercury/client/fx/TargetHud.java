package dev.mercury.client.fx;

import dev.mercury.client.module.ModuleManager;
import dev.mercury.client.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/** Panelito junto a la mira con nombre, vida y distancia del ultimo jugador que miraste. */
public final class TargetHud {
    private static Player target;
    private static long seen;
    private static float shownHp = -1;

    private TargetHud() {}

    public static boolean on() { return ModuleManager.TARGET_HUD != null && ModuleManager.TARGET_HUD.isOn(); }

    public static void draw(Minecraft mc, GuiGraphicsExtractor g) {
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        long now = Fx.now();
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof Player pl && pl != mc.player) { target = pl; seen = now; }
        if (target == null || !target.isAlive() || now - seen > 3000) { target = null; shownHp = -1; return; }

        float max = Math.max(1f, target.getMaxHealth()), hp = Math.max(0f, target.getHealth());
        if (shownHp < 0) shownHp = hp;
        shownHp += (hp - shownHp) * 0.15f;
        String name = target.getName().getString();
        String info = String.format("%.1f  |  %dm", hp, Math.round(Math.sqrt(target.distanceToSqr(mc.player))));
        int w = Math.max(96, mc.font.width(name) + 16), h = 30;
        int x = g.guiWidth() / 2 + 18, y = g.guiHeight() / 2 + 14;
        float fade = Math.min(1f, (3000 - (now - seen)) / 400f);
        Gfx.rrect(g, x, y, x + w, y + h, 5, Gfx.a(0xC0101218, fade));
        g.text(mc.font, name, x + 6, y + 5, Col.argb(0xFFFFFF, fade), true);
        g.text(mc.font, info, x + 6, y + 16, Col.argb(0xA0A8B8, fade), false);
        float f = Math.max(0f, Math.min(1f, shownHp / max));
        int bw = w - 12;
        Gfx.rrect(g, x + 6, y + h - 6, x + 6 + bw, y + h - 3, 1, Gfx.a(0x40FFFFFF, fade));
        if (f > 0.02f) Gfx.rrect(g, x + 6, y + h - 6, x + 6 + (int) (bw * f), y + h - 3, 1, Col.argb(Col.lerp(0xFF3030, 0x40FF60, f), fade));
    }
}
