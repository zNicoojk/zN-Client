package com.znclient.mod.mixin;

import com.znclient.mod.ZnClientTitleScreen;
import com.znclient.mod.ZnClientUi;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiTitleScreenMixin {
    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void znclient$beginScreenTransition(Screen requestedScreen, CallbackInfo ci) {
        ZnClientUi.resetScreenTransitions();
    }

    @ModifyVariable(
            method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Screen znclient$useCustomTitleScreen(Screen requestedScreen) {
        return requestedScreen instanceof TitleScreen ? new ZnClientTitleScreen() : requestedScreen;
    }
}
