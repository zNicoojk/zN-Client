package com.znclient.mod.mixin;

import com.znclient.mod.ZnClientModClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraZoomMixin {
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
    private void znclient$applySmoothZoom(float partialTick, CallbackInfoReturnable<Float> callback) {
        float progress = ZnClientModClient.zoomProgress(partialTick);
        if (progress <= 0.0f) return;

        float easedProgress = progress * progress * (3.0f - (2.0f * progress));
        float magnification = ZnClientModClient.zoomMagnification();
        float zoomFactor = 1.0f + ((1.0f / magnification) - 1.0f) * easedProgress;
        Float originalFov = callback.getReturnValue();
        if (originalFov == null) return;
        callback.setReturnValue(originalFov * zoomFactor);
    }
}
