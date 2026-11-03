package com.znclient.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ViewModelConfigScreen extends ModuleConfigScreen {
    private static final String[] POSITION_KEYS = {"position_x", "position_y", "position_z"};
    private static final String[] ROTATION_KEYS = {"rotation_x", "rotation_y", "rotation_z"};

    ViewModelConfigScreen(Screen parent) {
        super(parent, "viewmodel");
    }

    @Override
    protected boolean hasPreviewPanel() {
        return false;
    }

    @Override
    protected void initOptions() {
        addHandOptions(true, this.contentTop + 14);
        addHandOptions(false, this.contentTop + 444);
        this.addRenderableWidget(ZnClientUi.button(this.panelLeft + this.panelWidth - 142,
                this.footerY(), 128, 22,
                Component.translatable("screen.znclient.mods.restore_defaults"), this.font,
                this::restoreDefaults));
    }

    private void addHandOptions(boolean leftHand, int top) {
        for (int index = 0; index < POSITION_KEYS.length; index++) {
            addOptionWidget(slider(top + index * 50, POSITION_KEYS[index], leftHand));
        }
        for (int index = 0; index < ROTATION_KEYS.length; index++) {
            addOptionWidget(slider(top + 150 + index * 50, ROTATION_KEYS[index], leftHand));
        }
        addOptionWidget(slider(top + 300, "scale", leftHand));
    }

    private net.minecraft.client.gui.components.AbstractWidget slider(
            int y, String setting, boolean leftHand) {
        boolean scale = setting.equals("scale");
        boolean rotation = setting.startsWith("rotation");
        double minimum = scale ? 0.5 : -1.0;
        double maximum = scale ? 1.5 : 1.0;
        double step = 0.01;
        if (rotation) {
            minimum = -180.0;
            maximum = 180.0;
            step = 1.0;
        }
        return ZnClientUi.slider(this.contentLeft, y, this.contentWidth, 26, this.font,
                minimum, maximum, step,
                () -> getValue(setting, leftHand),
                value -> setValue(setting, leftHand, (float) value),
                () -> valueLabel(setting, leftHand), ClientSettings::save);
    }

    private static double getValue(String setting, boolean leftHand) {
        if (leftHand) {
            return switch (setting) {
                case "position_x" -> ClientSettings.viewModelLeftHorizontal;
                case "position_y" -> ClientSettings.viewModelLeftHeight;
                case "position_z" -> ClientSettings.viewModelLeftDepth;
                case "rotation_x" -> ClientSettings.viewModelLeftRotationX;
                case "rotation_y" -> ClientSettings.viewModelLeftRotationY;
                case "rotation_z" -> ClientSettings.viewModelLeftRotationZ;
                case "scale" -> ClientSettings.viewModelLeftScale;
                default -> 0.0;
            };
        }
        return switch (setting) {
            case "position_x" -> ClientSettings.viewModelRightHorizontal;
            case "position_y" -> ClientSettings.viewModelRightHeight;
            case "position_z" -> ClientSettings.viewModelRightDepth;
            case "rotation_x" -> ClientSettings.viewModelRightRotationX;
            case "rotation_y" -> ClientSettings.viewModelRightRotationY;
            case "rotation_z" -> ClientSettings.viewModelRightRotationZ;
            case "scale" -> ClientSettings.viewModelRightScale;
            default -> 0.0;
        };
    }

    private static void setValue(String setting, boolean leftHand, float value) {
        if (leftHand) {
            switch (setting) {
                case "position_x" -> ClientSettings.viewModelLeftHorizontal = value;
                case "position_y" -> ClientSettings.viewModelLeftHeight = value;
                case "position_z" -> ClientSettings.viewModelLeftDepth = value;
                case "rotation_x" -> ClientSettings.viewModelLeftRotationX = value;
                case "rotation_y" -> ClientSettings.viewModelLeftRotationY = value;
                case "rotation_z" -> ClientSettings.viewModelLeftRotationZ = value;
                case "scale" -> ClientSettings.viewModelLeftScale = value;
            }
            return;
        }
        switch (setting) {
            case "position_x" -> ClientSettings.viewModelRightHorizontal = value;
            case "position_y" -> ClientSettings.viewModelRightHeight = value;
            case "position_z" -> ClientSettings.viewModelRightDepth = value;
            case "rotation_x" -> ClientSettings.viewModelRightRotationX = value;
            case "rotation_y" -> ClientSettings.viewModelRightRotationY = value;
            case "rotation_z" -> ClientSettings.viewModelRightRotationZ = value;
            case "scale" -> ClientSettings.viewModelRightScale = value;
        }
    }

    private static Component valueLabel(String setting, boolean leftHand) {
        float value = (float) getValue(setting, leftHand);
        if (setting.equals("scale")) {
            return Component.literal(String.format(java.util.Locale.ROOT, "%.2fx", value));
        }
        if (setting.startsWith("rotation")) {
            return Component.literal(String.format(java.util.Locale.ROOT, "%+.0f°", value));
        }
        return Component.literal(String.format(java.util.Locale.ROOT, "%+.0f%%", value * 100.0f));
    }

    private void restoreDefaults() {
        ClientSettings.viewModelLeftHeight = 0.0f;
        ClientSettings.viewModelLeftHorizontal = 0.0f;
        ClientSettings.viewModelLeftDepth = 0.0f;
        ClientSettings.viewModelLeftRotationX = 0.0f;
        ClientSettings.viewModelLeftRotationY = 0.0f;
        ClientSettings.viewModelLeftRotationZ = 0.0f;
        ClientSettings.viewModelLeftScale = 1.0f;
        ClientSettings.viewModelRightHeight = 0.0f;
        ClientSettings.viewModelRightHorizontal = 0.0f;
        ClientSettings.viewModelRightDepth = 0.0f;
        ClientSettings.viewModelRightRotationX = 0.0f;
        ClientSettings.viewModelRightRotationY = 0.0f;
        ClientSettings.viewModelRightRotationZ = 0.0f;
        ClientSettings.viewModelRightScale = 1.0f;
        ClientSettings.save();
        this.rebuildWidgets();
    }

    @Override
    protected int optionsContentBottom() {
        return this.contentTop + 870;
    }

    @Override
    protected void drawOptions(GuiGraphicsExtractor graphics) {
        drawHandOptions(graphics, true, this.contentTop);
        drawHandOptions(graphics, false, this.contentTop + 430);
        graphics.textWithWordWrap(this.font,
                Component.translatable("screen.znclient.mods.viewmodel.hint"),
                this.contentLeft, this.optionY(this.contentTop + 820), this.contentWidth, 28);
    }

    private void drawHandOptions(GuiGraphicsExtractor graphics, boolean leftHand, int top) {
        graphics.text(this.font, Component.translatable(leftHand
                        ? "screen.znclient.mods.viewmodel.left_hand"
                        : "screen.znclient.mods.viewmodel.right_hand"),
                this.contentLeft, this.optionY(top), ZnClientUi.ACCENT, true);
        String[] keys = {
                "position_x", "position_y", "position_z",
                "rotation_x", "rotation_y", "rotation_z", "scale"
        };
        for (int index = 0; index < keys.length; index++) {
            graphics.text(this.font,
                    Component.translatable("screen.znclient.mods.viewmodel." + keys[index]),
                    this.contentLeft, this.optionY(top + index * 50), ZnClientUi.TEXT);
        }
    }
}
