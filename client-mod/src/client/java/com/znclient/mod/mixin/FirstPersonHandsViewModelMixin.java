package com.znclient.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.znclient.mod.ZnClientModClient;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsViewModelMixin {
    @Inject(
            method = "submitArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    shift = At.Shift.AFTER
            )
    )
    private void znclient$applyViewModel(
            PlayerRenderState playerRenderState,
            FirstPersonHandsAndItemsRenderState handRenderState,
            float equippedProgress,
            float swingProgress,
            InteractionHand hand,
            float tickProgress,
            net.minecraft.world.item.ItemStack itemStack,
            float itemSwapProgress,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int light,
            CallbackInfo callbackInfo
    ) {
        ZnClientModClient.applyViewModel(poseStack, hand);
    }

}
