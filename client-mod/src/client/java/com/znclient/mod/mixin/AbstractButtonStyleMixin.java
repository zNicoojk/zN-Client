package com.znclient.mod.mixin;

import com.znclient.mod.ModuleCardWidget;
import com.znclient.mod.ZnClientTitleScreen;
import com.znclient.mod.ZnClientUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonStyleMixin {
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void znclient$removeVanillaButtonFrame(CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (ZnClientUi.usesCustomButtonStyle(screen) || (Object) this instanceof ModuleCardWidget) {
            ci.cancel();
        }
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void znclient$drawGlassButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                          float delta, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().gui.screen();
        AbstractWidget widget = (AbstractWidget) (Object) this;
        if (screen instanceof ZnClientTitleScreen || widget instanceof ModuleCardWidget) return;
        if (!ZnClientUi.usesCustomButtonStyle(screen)) return;
        ZnClientUi.drawButtonSurface(graphics, widget);
    }
}
