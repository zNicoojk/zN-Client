package com.znclient.mod;

import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

final class ZoomConfigScreen extends ModuleConfigScreen {
    private @Nullable Button keyButton;
    private @Nullable Button modeButton;
    private boolean waitingForKey;

    ZoomConfigScreen(Screen parent) {
        super(parent, "zoom");
    }

    @Override
    protected boolean hasPreviewPanel() {
        return false;
    }

    @Override
    protected void initOptions() {
        int left = this.contentLeft;
        int width = this.contentWidth;
        int half = Math.max(1, (width - 10) / 2);
        int right = left + half + 10;

        this.addOptionWidget(ZnClientUi.slider(left, this.contentTop + 14, width, 26, this.font,
                1.0, 8.0, 0.1,
                ZnClientModClient::zoomMagnification,
                value -> ZnClientModClient.setZoomMagnification((float) value, false),
                () -> Component.literal(String.format(java.util.Locale.ROOT, "%.1fx",
                        ZnClientModClient.zoomMagnification())),
                () -> ZnClientModClient.setZoomMagnification(ZnClientModClient.zoomMagnification(), true)));
        this.addOptionWidget(ZnClientUi.slider(left, this.contentTop + 64, width, 26, this.font,
                1, 16, 1,
                () -> ClientSettings.zoomTransitionTicks,
                value -> ClientSettings.zoomTransitionTicks = (int) value,
                () -> Component.literal(ClientSettings.zoomTransitionTicks
                        + Component.translatable("screen.znclient.mods.zoom.ticks.suffix").getString()),
                () -> ZnClientModClient.setZoomTransitionTicks(ClientSettings.zoomTransitionTicks)));

        this.keyButton = this.addOptionWidget(ZnClientUi.button(left, this.contentTop + 119,
                half, 26, ZnClientModClient.zoomKeyMapping().getTranslatedKeyMessage(), this.font,
                this::startKeyCapture));
        this.modeButton = this.addOptionWidget(ZnClientUi.button(right, this.contentTop + 119,
                half, 26, this.zoomModeLabel(), this.font, this::toggleZoomMode));
        this.addOptionWidget(ZnClientUi.toggle(left, this.contentTop + 157, width, 28,
                Component.translatable("screen.znclient.mods.zoom.animation.label"), this.font,
                () -> ClientSettings.zoomAnimationEnabled, ZnClientModClient::toggleZoomAnimation));
        this.addOptionWidget(ZnClientUi.toggle(left, this.contentTop + 191, width, 28,
                Component.translatable("screen.znclient.mods.zoom.camera.label"), this.font,
                () -> ClientSettings.zoomSlowCameraEnabled, ZnClientModClient::toggleZoomSlowCamera));
        this.addRenderableWidget(ZnClientUi.button(this.panelLeft + this.panelWidth - 142,
                this.footerY(), 128, 22,
                Component.translatable("screen.znclient.mods.restore_defaults"), this.font,
                this::restoreDefaults));
    }

    private void startKeyCapture() {
        this.waitingForKey = true;
        if (this.keyButton != null) this.keyButton.setMessage(Component.translatable("screen.znclient.mods.zoom.press_key"));
    }

    private void toggleZoomMode() {
        ZnClientModClient.toggleZoomMode();
        if (this.modeButton != null) this.modeButton.setMessage(this.zoomModeLabel());
    }

    private Component zoomModeLabel() {
        return Component.translatable(ZnClientModClient.isZoomToggleMode()
                ? "screen.znclient.mods.zoom.mode.toggle"
                : "screen.znclient.mods.zoom.mode.hold");
    }

    @Override
    protected int optionsContentBottom() {
        return this.contentTop + 252;
    }

    private void restoreDefaults() {
        ClientSettings.zoomEnabled = true;
        ClientSettings.zoomMagnification = 3.0f;
        ClientSettings.zoomAnimationEnabled = true;
        ClientSettings.zoomSlowCameraEnabled = true;
        ClientSettings.zoomToggleMode = false;
        ClientSettings.zoomTransitionTicks = 6;
        ZnClientModClient.resetZoomKey();
        ClientSettings.save();
        this.waitingForKey = false;
        if (this.keyButton != null) this.keyButton.setMessage(ZnClientModClient.zoomKeyMapping().getTranslatedKeyMessage());
        if (this.modeButton != null) this.modeButton.setMessage(this.zoomModeLabel());
        this.rebuildWidgets();
    }

    @Override
    protected void drawOptions(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, Component.translatable("screen.znclient.mods.zoom.strength"),
                this.contentLeft, this.optionY(this.contentTop), ZnClientUi.TEXT);
        graphics.text(this.font, Component.translatable("screen.znclient.mods.zoom.speed"),
                this.contentLeft, this.optionY(this.contentTop + 50), ZnClientUi.TEXT);
        int half = Math.max(1, (this.contentWidth - 10) / 2);
        graphics.text(this.font, Component.translatable("screen.znclient.mods.zoom.key_label"),
                this.contentLeft, this.optionY(this.contentTop + 103), ZnClientUi.MUTED);
        graphics.text(this.font, Component.translatable("screen.znclient.mods.zoom.mode.label"),
                this.contentLeft + half + 10, this.optionY(this.contentTop + 103), ZnClientUi.MUTED);
        graphics.textWithWordWrap(this.font,
                Component.translatable("screen.znclient.mods.zoom.fov_hint"),
                this.contentLeft, this.optionY(this.contentTop + 228), this.contentWidth, 24);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.waitingForKey) return super.keyPressed(event);
        if (event.key() == InputConstants.KEY_ESCAPE) {
            this.waitingForKey = false;
            if (this.keyButton != null) this.keyButton.setMessage(ZnClientModClient.zoomKeyMapping().getTranslatedKeyMessage());
            return true;
        }

        InputConstants.Key key = InputConstants.getKey(event);
        if (key != InputConstants.UNKNOWN && key.getType() == InputConstants.Type.KEYBOARD) {
            ZnClientModClient.setZoomKey(key);
            if (this.keyButton != null) this.keyButton.setMessage(ZnClientModClient.zoomKeyMapping().getTranslatedKeyMessage());
            this.waitingForKey = false;
        }
        return true;
    }
}
