package com.znclient.mod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayBrandingMixin {
    private static final Identifier ZNCLIENT_LOGO = Identifier.fromNamespaceAndPath(
            "znclient", "textures/gui/znclient.png");
    private static final int LOGO_WIDTH = 1244;
    private static final int LOGO_HEIGHT = 1265;

    @Inject(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"
            )
    )
    private void znclient$drawLoadingBrand(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                           float partialTick, CallbackInfo ci) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (width <= 0 || height <= 0) return;

        float mojangLogoHeight = Math.min(width * 0.75f, height) * 0.25f;
        int logoBottom = Math.round(height * 0.5f + mojangLogoHeight * 0.5f);
        int progressTop = Math.round(height * 0.8325f) - 7;
        int availableHeight = progressTop - logoBottom - 12;

        int logoSize = Math.max(12, Math.min(42,
                Math.min(Math.round(width * 0.12f), Math.max(12, availableHeight / 2))));
        int logoTop = logoBottom + 5;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ZNCLIENT_LOGO,
                (width - logoSize) / 2, logoTop, 0.0f, 0.0f,
                logoSize, logoSize, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
        graphics.centeredText(Minecraft.getInstance().font, Component.literal("zN Client"), width / 2,
                logoTop + logoSize + 3, 0xFFF2F4F5);
    }
}
