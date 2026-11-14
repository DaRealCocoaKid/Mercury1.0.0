package dev.mercury.client.ui;

import dev.mercury.client.Config;
import dev.mercury.client.module.Module;
import dev.mercury.client.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** HUD limpio: marca arriba a la izquierda y lista de modulos activos a la derecha. */
public final class Hud {
    private Hud() {}

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("mercuryclient", "hud"),
                (g, dt) -> draw(g));
    }

    private static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.hud || mc.player == null) return;
        var font = mc.font;

        String brand = "MERCURY";
        int bw = font.width(brand) + 12;
        Gfx.rrect(g, 4, 4, 4 + bw, 18, 4, 0x90181A20);
        g.fill(4, 8, 6, 14, Gfx.ACC);
        g.text(font, Gfx.bold(brand), 10, 8, Gfx.TEXT, true);

        List<Module> on = new ArrayList<>();
        for (Module m : ModuleManager.ALL) { if (m == ModuleManager.FREELOOK ? dev.mercury.client.module.Freelook.active() : m.isOn()) on.add(m); }
        on.sort(Comparator.comparingInt((Module m) -> font.width(m.name)).reversed());

        int W = g.guiWidth(), y = 4;
        for (Module m : on) {
            int tw = font.width(m.name);
            int x2 = W - 4, x1 = x2 - tw - 12;
            Gfx.rrect(g, x1, y, x2, y + 13, 3, 0x88181A20);
            g.fill(x2 - 2, y + 2, x2, y + 11, Gfx.ACC);
            g.text(font, m.name, x1 + 5, y + 3, Gfx.TEXT, true);
            y += 15;
        }
    }
}
