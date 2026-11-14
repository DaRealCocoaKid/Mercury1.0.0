package dev.mercury.client.ui;

import com.visionplus.client.VPConfig;
import dev.mercury.client.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/** Capa "Mercury" que se dibuja encima del fondo de las pantallas de Mundos y Multijugador de Minecraft. */
public final class Skin {
    public static long changedAt = System.nanoTime();

    private Skin() {}

    public static void screenChanged() { changedAt = System.nanoTime(); }

    public static float openProgress(long since, float ms) {
        if (!Config.anim) return 1f;
        return Gfx.ease((System.nanoTime() - since) / 1_000_000f / ms);
    }

    public static void overlay(Screen s, GuiGraphicsExtractor g, int w, int h) {
        if (VPConfig.I == null || !VPConfig.I.customTitle) return;
        if (!(s instanceof JoinMultiplayerScreen) && !(s instanceof SelectWorldScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        float p = openProgress(changedAt, 420f);
        // gris traslucido sobre el fondo (vanilla ya desenfoca) + degradado
        g.fillGradient(0, 0, w, h, Gfx.a(0xE0101216, 0.35f + 0.65f * p), Gfx.a(0xD01A1C22, 0.35f + 0.65f * p));
        // estela de movimiento al abrir (desenfoque falso)
        if (p < 1f) {
            for (int k = 1; k <= 3; k++) {
                int off = (int) ((1f - p) * 14 * k);
                g.fill(0, off, w, off + 2, Gfx.a(0xFFFFFFFF, 0.10f * (1f - p)));
            }
        }
        // barra superior con el nombre
        int bh = 24;
        g.fillGradient(0, 0, w, bh, Gfx.a(0xF0202329, p), Gfx.a(0x80202329, p));
        g.fill(0, bh - 1, w, bh, Gfx.a(Gfx.ACC, 0.55f * p));
        String t = "MERCURY CLIENT";
        int tw = mc.font.width(t);
        g.text(mc.font, Gfx.bold(t), (w - tw) / 2, 8 - (int) ((1f - p) * 10), Gfx.a(Gfx.TEXT, p), true);
    }
}
