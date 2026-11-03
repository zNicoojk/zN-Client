package com.znclient.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;

/** Receives card clicks through Minecraft's normal widget event dispatch. */
public final class ModuleCardWidget extends Button {
    private static final int SWITCH_WIDTH = 35;
    private static final int SWITCH_HEIGHT = 17;
    private static final int SWITCH_RIGHT_INSET = 12;
    private static final int SWITCH_TOP_INSET = 12;

    private final ClientModule module;
    private final Runnable openSettings;
    private int clipTop;
    private int clipBottom;

    ModuleCardWidget(ClientModule module, int x, int y, int width, int height,
                     Runnable openSettings) {
        super(x, y, width, height, module.title(), button -> {}, DEFAULT_NARRATION);
        this.module = module;
        this.openSettings = openSettings;
        this.clipTop = y;
        this.clipBottom = y + height;
    }

    void setClip(int top, int bottom) {
        this.clipTop = top;
        this.clipBottom = bottom;
        boolean inViewport = this.getBottom() > top && this.getY() < bottom;
        this.visible = inViewport;
        this.active = inViewport;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.visible && this.active
                && super.isMouseOver(mouseX, mouseY)
                && mouseY >= this.clipTop && mouseY < this.clipBottom;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int switchX = this.getRight() - SWITCH_RIGHT_INSET - SWITCH_WIDTH;
        int switchY = this.getY() + SWITCH_TOP_INSET;
        boolean onSwitch = event.x() >= switchX && event.x() < switchX + SWITCH_WIDTH
                && event.y() >= switchY && event.y() < switchY + SWITCH_HEIGHT;
        if (onSwitch) {
            this.module.toggle();
        } else {
            this.openSettings.run();
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The screen draws the card inside its clipped grid; this button owns input only.
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }
}
