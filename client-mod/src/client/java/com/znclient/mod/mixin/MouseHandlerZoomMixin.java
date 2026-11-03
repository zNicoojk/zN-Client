package com.znclient.mod.mixin;

import com.znclient.mod.ZnClientModClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerZoomMixin {
    @ModifyArgs(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void znclient$smoothZoomTurn(Args arguments) {
        if (!ZnClientModClient.isZoomSlowCameraEnabled()) return;

        float progress = ZnClientModClient.zoomProgress();
        if (progress <= 0.0f) return;

        float easedProgress = progress * progress * (3.0f - (2.0f * progress));
        double turnScale = 1.0 + ((1.0 / ZnClientModClient.zoomMagnification()) - 1.0) * easedProgress;
        Double yaw = arguments.get(0);
        Double pitch = arguments.get(1);
        if (yaw == null || pitch == null) return;
        arguments.set(0, yaw * turnScale);
        arguments.set(1, pitch * turnScale);
    }
}
