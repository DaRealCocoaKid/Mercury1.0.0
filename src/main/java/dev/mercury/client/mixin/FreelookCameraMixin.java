package dev.mercury.client.mixin;

import dev.mercury.client.module.Freelook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class FreelookCameraMixin {
    // la camara usa el angulo de freelook en vez del del jugador
    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float fl$yaw(float v) { return Freelook.active() ? Freelook.yaw : v; }

    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float fl$pitch(float v) { return Freelook.active() ? Freelook.pitch : v; }

    // sin colision: la camara no se acerca al personaje cuando hay bloques detras
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true, require = 0)
    private void fl$zoom(float max, CallbackInfoReturnable<Float> cir) {
        if (Freelook.active() && Freelook.noClip) cir.setReturnValue((float) Freelook.distance);
    }
}
