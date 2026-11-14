package dev.mercury.client.mixin;

import dev.mercury.client.module.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class FreelookTurnMixin {
    // con freelook activo el mouse gira la camara y tu personaje se queda mirando al mismo lado
    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void fl$turn(double yRot, double xRot, CallbackInfo ci) {
        if (Freelook.active() && (Object) this == Minecraft.getInstance().player) {
            Freelook.turn(yRot, xRot);
            ci.cancel();
        }
    }
}
