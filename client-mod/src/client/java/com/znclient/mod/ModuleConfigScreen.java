package com.znclient.mod;

import org.jspecify.annotations.Nullable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

abstract class ModuleConfigScreen extends Screen {
    protected final @Nullable Screen parent;
    protected final ClientModule module;
    protected int panelLeft;
    protected int panelTop;
    protected int panelWidth;
    protected int panelHeight;
    protected int contentLeft;
    protected int contentTop;
    protected int contentWidth;
    protected int previewLeft;
    protected int previewTop;
    protected int previewWidth;
    protected int previewHeight;
    private final List<OptionWidget> optionWidgets = new ArrayList<>();
    private float optionScroll;
    private float targetOptionScroll;
    private int optionViewportBottom;

    protected ModuleConfigScreen(@Nullable Screen parent, String moduleId) {
        super(Component.translatable("screen.znclient.mods.config.title"));
        this.parent = parent;
        this.module = ClientModuleRegistry.get(moduleId);
        if (this.module == null) throw new IllegalArgumentException("Unknown module: " + moduleId);
    }

    @Override
    protected void init() {
        this.optionWidgets.clear();
        this.optionScroll = 0.0f;
        this.targetOptionScroll = 0.0f;
        this.panelWidth = Math.max(1, Math.min(680, this.width - 24));
        this.panelHeight = Math.max(1, Math.min(440, this.height - 20));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.contentLeft = this.panelLeft + 18;
        boolean showPreview = this.hasPreviewPanel();
        if (this.panelWidth >= 560) {
            this.contentTop = this.panelTop + 91;
            if (showPreview) {
                this.previewWidth = Math.min(220, Math.max(100, (this.panelWidth - 60) / 3));
                this.contentWidth = this.panelWidth - 36 - this.previewWidth - 12;
                this.previewLeft = this.contentLeft + this.contentWidth + 12;
                this.previewTop = this.contentTop;
                this.previewHeight = 150;
            } else {
                this.contentWidth = this.panelWidth - 36;
            }
        } else if (!showPreview) {
            this.contentWidth = this.panelWidth - 36;
            this.contentTop = this.panelTop + 88;
        } else {
            this.previewWidth = this.panelWidth - 36;
            this.contentWidth = this.previewWidth;
            this.previewLeft = this.contentLeft;
            this.previewTop = this.panelTop + 88;
            this.previewHeight = 60;
            this.contentTop = this.panelTop + 160;
        }
        this.addRenderableOnly(this::drawScreen);
        this.addRenderableWidget(ZnClientUi.toggle(this.contentLeft, this.panelTop + 48,
                this.contentWidth, 32,
                Component.translatable("screen.znclient.mods.config.enabled").append(this.module.title()),
                this.font, this.module::enabled, this.module::toggle));
        this.addRenderableWidget(ZnClientUi.button(this.panelLeft + 14,
                this.panelTop + this.panelHeight - 33, 104, 22,
                Component.translatable("screen.znclient.mods.back"), this.font, this::goBack));
        this.initOptions();
        this.updateOptionLayout();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the live Minecraft view behind the mod preview and controls.
    }

    protected abstract void initOptions();

    protected abstract void drawOptions(GuiGraphicsExtractor graphics);

    protected boolean hasPreviewPanel() {
        return true;
    }

    protected int optionsContentBottom() {
        return this.contentTop;
    }

    protected final <T extends AbstractWidget> T addOptionWidget(T widget) {
        T registered = this.addRenderableWidget(widget);
        this.optionWidgets.add(new OptionWidget(registered, registered.getY()));
        return registered;
    }

    protected final int optionY(int baseY) {
        return baseY - Math.round(this.optionScroll);
    }

    protected final int footerY() {
        return this.panelTop + this.panelHeight - 33;
    }

    protected final void drawScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.updateOptionLayout();
        graphics.fill(0, 0, this.width, this.height, 0x20000000);
        ZnClientUi.drawPanel(graphics, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight);
        graphics.text(this.font, Component.translatable("screen.znclient.mods.config.brand"),
                this.panelLeft + 18, this.panelTop + 12, ZnClientUi.MUTED, true);
        graphics.text(this.font, this.module.title(), this.panelLeft + 18, this.panelTop + 27,
                ZnClientUi.TEXT, true);
        graphics.text(this.font, this.module.description(), this.panelLeft + 18,
                this.panelTop + 38, ZnClientUi.MUTED);
        if (this.hasPreviewPanel()) this.drawPreviewPanel(graphics);
        graphics.enableScissor(this.contentLeft, this.contentTop,
                this.panelLeft + this.panelWidth - 18, this.optionViewportBottom);
        this.drawOptions(graphics);
        graphics.disableScissor();
        float maxScroll = Math.max(0.0f, this.optionsContentBottom() - this.optionViewportBottom);
        if (maxScroll > 0.0f) this.drawOptionScrollbar(graphics, maxScroll);
        graphics.centeredText(this.font, Component.translatable(maxScroll > 0.0f
                        ? "screen.znclient.mods.config.scroll_hint"
                        : "screen.znclient.mods.close_hint"),
                this.width / 2, this.panelTop + this.panelHeight - 14, ZnClientUi.MUTED);
    }

    private void drawPreviewPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(this.previewLeft, this.previewTop,
                this.previewLeft + this.previewWidth, this.previewTop + this.previewHeight, 0x50000000);
        graphics.text(this.font, Component.translatable("screen.znclient.mods.config.preview"),
                this.previewLeft + 8, this.previewTop + 7, ZnClientUi.MUTED);
        int contentX = this.previewLeft + 8;
        int contentY = this.previewTop + 24;
        int contentWidth = Math.max(1, this.previewWidth - 16);
        int contentHeight = Math.max(1, this.previewHeight - 29);
        this.drawPreviewContent(graphics, contentX, contentY, contentWidth, contentHeight);
    }

    protected void drawPreviewContent(GuiGraphicsExtractor graphics, int x, int y,
                                      int width, int height) {
        graphics.centeredText(this.font, this.module.title(), x + width / 2,
                y + Math.max(0, (height - this.font.lineHeight) / 2), ZnClientUi.ACCENT);
    }

    private void drawOptionScrollbar(GuiGraphicsExtractor graphics, float maxScroll) {
        int x = this.panelLeft + this.panelWidth - 8;
        int height = this.optionViewportBottom - this.contentTop;
        int contentHeight = this.optionsContentBottom() - this.contentTop;
        int thumbHeight = Math.max(18, Math.round(height * (height / (float) contentHeight)));
        int travel = Math.max(0, height - thumbHeight);
        int thumbTop = this.contentTop + Math.round(travel * (this.optionScroll / maxScroll));
        graphics.fill(x, this.contentTop, x + 2, this.optionViewportBottom, 0xFF343A42);
        graphics.fill(x - 1, thumbTop, x + 3, thumbTop + thumbHeight, 0xAA777F88);
    }

    private void updateOptionLayout() {
        this.optionViewportBottom = Math.max(this.contentTop + 1, this.footerY() - 8);
        float maxScroll = Math.max(0.0f, this.optionsContentBottom() - this.optionViewportBottom);
        this.targetOptionScroll = Math.max(0.0f, Math.min(maxScroll, this.targetOptionScroll));
        this.optionScroll = Math.max(0.0f, Math.min(maxScroll, this.optionScroll));
        int viewportTop = this.contentTop;
        for (OptionWidget option : this.optionWidgets) {
            int y = option.baseY - Math.round(this.optionScroll);
            option.widget.setY(y);
            boolean fullyVisible = y >= viewportTop
                    && y + option.widget.getHeight() <= this.optionViewportBottom;
            option.widget.visible = fullyVisible;
            option.widget.active = fullyVisible;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (mouseX >= this.contentLeft && mouseX < this.contentLeft + this.contentWidth
                && mouseY >= this.contentTop && mouseY < this.optionViewportBottom) {
            float maxScroll = Math.max(0.0f, this.optionsContentBottom() - this.optionViewportBottom);
            if (maxScroll > 0.0f) {
                this.targetOptionScroll = Math.max(0.0f, Math.min(maxScroll,
                        this.targetOptionScroll - (float) verticalAmount * 30.0f));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void tick() {
        super.tick();
        this.optionScroll += (this.targetOptionScroll - this.optionScroll) * 0.34f;
        if (Math.abs(this.targetOptionScroll - this.optionScroll) < 0.01f) {
            this.optionScroll = this.targetOptionScroll;
        }
    }

    protected final void goBack() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.goBack();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record OptionWidget(AbstractWidget widget, int baseY) {}
}
