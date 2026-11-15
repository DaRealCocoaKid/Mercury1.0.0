package dev.mercury.client;

import com.visionplus.client.ChunkFinder;
import com.visionplus.client.EspRenderer;
import com.visionplus.client.Freecam;
import com.visionplus.client.PlayerWatch;
import com.visionplus.client.Scanner;
import com.visionplus.client.VPConfig;
import dev.mercury.client.module.HitboxOverlay;
import dev.mercury.client.module.ModuleManager;
import dev.mercury.client.module.Pvp;
import dev.mercury.client.ui.Hud;
import dev.mercury.client.ui.MainMenu;
import dev.mercury.client.ui.MercuryMenu;
import dev.mercury.client.ui.Skin;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;

public class MercuryClient implements ClientModInitializer {
    private static boolean wasIn, menuKeyWas = true;
    private static int joinDelay = -1;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();   // carga VPConfig y crea los modulos
        Config.load();          // aplica teclas/valores guardados
        VPConfig.I.enabled = true;
        if (Config.firstRun) VPConfig.I.customTitle = true; // primera vez: menu Mercury activado

        EspRenderer.register();
        HitboxOverlay.register();
        Hud.register();
        dev.mercury.client.fx.Fx.register();

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            Pvp.onUse(player, level, hand, hit);
            return InteractionResult.PASS;
        });

        ClientTickEvents.START_CLIENT_TICK.register(Freecam::startTick);
        ClientTickEvents.START_CLIENT_TICK.register(dev.mercury.client.module.MoveTools::startTick);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ModuleManager.tick(mc);
            Scanner.tick(mc);
            ChunkFinder.tick(mc);
            PlayerWatch.tick(mc);

            // tecla del menu dentro del juego
            boolean inGame = mc.player != null && mc.gui.screen() == null;
            if (inGame) {
                boolean down = GLFW.glfwGetKey(mc.getWindow().handle(), Config.menuKey) == GLFW.GLFW_PRESS;
                if (down && !menuKeyWas) mc.gui.setScreen(new MercuryMenu(null));
                menuKeyWas = down;
            } else {
                menuKeyWas = true;
            }

            // al entrar a un servidor se abre el menu (si esta activado)
            boolean in = mc.player != null && mc.level != null;
            if (in && !wasIn) joinDelay = 40;
            if (!in) joinDelay = -1;
            wasIn = in;
            if (joinDelay > 0 && --joinDelay == 0) {
                if (Config.openOnJoin && mc.getSingleplayerServer() == null && mc.gui.screen() == null)
                    mc.gui.setScreen(new MercuryMenu(null));
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            Skin.screenChanged();
            if (screen instanceof TitleScreen && VPConfig.I.customTitle)
                client.execute(() -> client.gui.setScreen(new MainMenu()));
        });
    }
}
