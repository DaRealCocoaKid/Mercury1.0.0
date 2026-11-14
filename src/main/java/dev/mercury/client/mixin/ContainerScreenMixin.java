package dev.mercury.client.mixin;

import dev.mercury.client.module.Pvp;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    // Fast Totem (1 click): un click sobre un totem lo manda directo a la offhand.
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void mc$slotClicked(Slot slot, int slotId, int button, ContainerInput type, CallbackInfo ci) {
        if (Pvp.interceptClick(slot, button)) ci.cancel();
    }
}
