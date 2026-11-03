package com.znclient.mod;

import org.jspecify.annotations.Nullable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class HudModuleConfigScreen extends ModuleConfigScreen {
    private static final String[] COLOR_KEYS = {
            "screen.znclient.mods.fps.color.green",
            "screen.znclient.mods.fps.color.white",
            "screen.znclient.mods.fps.color.blue",
            "screen.znclient.mods.fps.color.amber",
            "screen.znclient.mods.fps.color.red",
            "screen.znclient.mods.fps.color.purple"
    };
    private final HudModuleSettings settings;
    private @Nullable Button colorButton;
    private @Nullable Button formatButton;

    HudModuleConfigScreen(Screen parent, String moduleId) {
        super(parent, moduleId);
        this.settings = switch (moduleId) {
            case "fps" -> HudModuleSettings.FPS;
            case "ping" -> HudModuleSettings.PING;
            case "coordinates" -> HudModuleSettings.COORDINATES;
            default -> throw new IllegalArgumentException("Not a HUD module: " + moduleId);
        };
    }

    @Override
    protected void initOptions() {
        int row = this.contentTop + 16;

        this.colorButton = this.addOptionWidget(ZnClientUi.button(this.contentLeft, row, this.contentWidth, 27,
                this.colorLabel(), this.font, this::cycleColor));
        this.addOptionWidget(ZnClientUi.slider(this.contentLeft, this.contentTop + 56,
                this.contentWidth, 26, this.font, 0.5, 2.0, 0.05,
                () -> this.settings.scale,
                value -> this.settings.scale = (float) value,
                () -> Component.literal(String.format(java.util.Locale.ROOT, "%.2fx", this.settings.scale)),
                ClientSettings::save));
        this.addOptionWidget(ZnClientUi.toggle(this.contentLeft, this.contentTop + 88,
                this.contentWidth, 28, Component.translatable("screen.znclient.mods.hud.background"),
                this.font, () -> this.settings.background, this::toggleBackground));
        this.addOptionWidget(ZnClientUi.slider(this.contentLeft, this.contentTop + 130,
                this.contentWidth, 26, this.font, 0, 100, 1,
                () -> this.settings.opacity,
                value -> this.settings.opacity = (int) value,
                () -> Component.literal(this.settings.opacity + "%"), ClientSettings::save));
        this.formatButton = this.addOptionWidget(ZnClientUi.button(this.contentLeft,
                this.contentTop + 169, this.contentWidth, 28, this.formatLabel(), this.font,
                this::cycleFormat));

        int nextRow = this.contentTop + 204;
        if (this.settings == HudModuleSettings.COORDINATES) {
            int axisGap = 6;
            int axisWidth = Math.max(1, (this.contentWidth - axisGap * 2) / 3);
            this.addOptionWidget(ZnClientUi.toggle(this.contentLeft, nextRow, axisWidth, 27,
                    Component.translatable("screen.znclient.mods.coordinates.axis.x"), this.font,
                    () -> this.settings.showX, () -> this.toggleAxis('x')));
            this.addOptionWidget(ZnClientUi.toggle(this.contentLeft + axisWidth + axisGap, nextRow,
                    axisWidth, 27, Component.translatable("screen.znclient.mods.coordinates.axis.y"),
                    this.font, () -> this.settings.showY, () -> this.toggleAxis('y')));
            this.addOptionWidget(ZnClientUi.toggle(this.contentLeft + (axisWidth + axisGap) * 2, nextRow,
                    axisWidth, 27, Component.translatable("screen.znclient.mods.coordinates.axis.z"),
                    this.font, () -> this.settings.showZ, () -> this.toggleAxis('z')));
        }
        this.addRenderableWidget(ZnClientUi.button(this.panelLeft + this.panelWidth - 142,
                this.footerY(), 128, 22,
                Component.translatable("screen.znclient.mods.restore_defaults"), this.font,
                this::restoreDefaults));
    }

    private void cycleColor() {
        this.settings.color = (this.settings.color + 1) % COLOR_KEYS.length;
        ClientSettings.save();
        if (this.colorButton != null) this.colorButton.setMessage(this.colorLabel());
    }

    private void cycleFormat() {
        if (this.settings == HudModuleSettings.COORDINATES) {
            this.settings.coordinateFormat = (this.settings.coordinateFormat + 1) % 3;
        } else {
            this.settings.format = (this.settings.format + 1) % 3;
        }
        ClientSettings.save();
        if (this.formatButton != null) this.formatButton.setMessage(this.formatLabel());
    }

    private void toggleBackground() {
        this.settings.background = !this.settings.background;
        ClientSettings.save();
    }

    private void toggleAxis(char axis) {
        if (axis == 'x') this.settings.showX = !this.settings.showX;
        else if (axis == 'y') this.settings.showY = !this.settings.showY;
        else this.settings.showZ = !this.settings.showZ;
        ClientSettings.save();
    }

    private void restoreDefaults() {
        this.settings.restoreDefaults();
        ClientSettings.save();
        this.rebuildWidgets();
    }

    private Component colorLabel() {
        return Component.translatable("screen.znclient.mods.hud.color")
                .append(Component.translatable(COLOR_KEYS[this.settings.color]));
    }

    private Component formatLabel() {
        String formatKey;
        if (this.settings == HudModuleSettings.FPS) {
            formatKey = "screen.znclient.mods.hud.format.fps." + this.settings.format;
        } else if (this.settings == HudModuleSettings.PING) {
            formatKey = "screen.znclient.mods.hud.format.ping." + this.settings.format;
        } else {
            formatKey = "screen.znclient.mods.hud.format.coordinates." + this.settings.coordinateFormat;
        }
        return Component.translatable("screen.znclient.mods.hud.format")
                .append(Component.translatable(formatKey));
    }

    @Override
    protected void drawPreviewContent(GuiGraphicsExtractor graphics, int x, int y,
                                      int width, int height) {
        HudLayoutManager.drawPreview(graphics, this.font, this.settings, x, y, width, height);
    }

    @Override
    protected int optionsContentBottom() {
        return this.contentTop + (this.settings == HudModuleSettings.COORDINATES ? 312 : 276);
    }

    @Override
    protected void drawOptions(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, Component.translatable("screen.znclient.mods.hud.color"),
                this.contentLeft, this.optionY(this.contentTop + 4), ZnClientUi.TEXT);
        graphics.text(this.font, Component.translatable("screen.znclient.mods.hud.scale"),
                this.contentLeft, this.optionY(this.contentTop + 45), ZnClientUi.TEXT);
        graphics.text(this.font, Component.translatable("screen.znclient.mods.hud.opacity"),
                this.contentLeft, this.optionY(this.contentTop + 119), ZnClientUi.TEXT);
    }
}
