package dev.mercury.client.mixin;

import dev.mercury.client.ui.Skin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow public int width;
    @Shadow public int height;

    // Tema Mercury en Mundos / Multijugador. require = 0: si el metodo cambia de nombre no rompe el juego, solo no se aplica.
    @Inject(method = "extractBackground", at = @At("TAIL"), require = 0)
    private void mc$skin(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        Skin.overlay((Screen) (Object) this, g, width, height);
    }
}
