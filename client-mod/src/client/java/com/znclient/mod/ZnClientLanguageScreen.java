package com.znclient.mod;

import org.jspecify.annotations.Nullable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ZnClientLanguageScreen extends Screen {
    private final @Nullable Screen parent;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    ZnClientLanguageScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.znclient.mods.language"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.max(1, Math.min(420, this.width - 32));
        this.panelHeight = Math.min(160, Math.max(120, this.height - 24));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.addRenderableOnly(this::drawPanel);

        int gap = 10;
        int buttonWidth = Math.max(1, (this.panelWidth - 36 - gap) / 2);
        int buttonTop = this.panelTop + 58;
        int buttonLeft = this.panelLeft + 18;
        this.addRenderableWidget(ZnClientUi.overlayButton(buttonLeft, buttonTop, buttonWidth, 32,
                this.languageLabel("es_es", "screen.znclient.mods.language.spanish"), this.font,
                () -> this.selectLanguage("es_es")));
        this.addRenderableWidget(ZnClientUi.overlayButton(buttonLeft + buttonWidth + gap, buttonTop,
                buttonWidth, 32, this.languageLabel("en_us", "screen.znclient.mods.language.english"),
                this.font, () -> this.selectLanguage("en_us")));
        this.addRenderableWidget(ZnClientUi.overlayButton(this.panelLeft + 18,
                this.panelTop + this.panelHeight - 38, 100, 24,
                Component.translatable("screen.znclient.mods.back"), this.font,
                () -> this.minecraft.gui.setScreen(this.parent)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the Minecraft title screen behind the language choices.
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x20000000);
        ZnClientUi.drawPanel(graphics, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight);
        graphics.centeredText(this.font, Component.translatable("screen.znclient.mods.language"),
                this.width / 2, this.panelTop + 24, ZnClientUi.TEXT);
    }

    private Component languageLabel(String languageCode, String translationKey) {
        Component label = Component.translatable(translationKey);
        if (this.minecraft.options.languageCode.equals(languageCode)) {
            return Component.literal("\u2713 ").append(label);
        }
        return label;
    }

    private void selectLanguage(String languageCode) {
        if (this.minecraft.options.languageCode.equals(languageCode)) {
            this.minecraft.gui.setScreen(this.parent);
            return;
        }
        this.minecraft.options.languageCode = languageCode;
        var languageManager = this.minecraft.getLanguageManager();
        languageManager.setSelected(languageCode);
        this.minecraft.options.save();
        languageManager.onResourceManagerReload(this.minecraft.getResourceManager());
        this.minecraft.gui.setScreen(this.parent);
    }
}
