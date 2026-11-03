package com.znclient.mod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Fabric HUD hook; layout and drawing live in HudLayoutManager. */
final class InfoHudOverlay {
    private InfoHudOverlay() {}

    static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        HudLayoutManager.renderGame(graphics);
    }
}
