package com.znclient.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Shared lightweight controls and panel drawing for the zN Client screens. */
public final class ZnClientUi {
    public static final int ACCENT = 0xFF39FF14;
    public static final int ACCENT_DIM = 0xFF2ABE10;
    public static final int GREEN = ACCENT;
    public static final int TEXT = 0xFFF2F4F5;
    public static final int MUTED = 0xFF8B949E;
    public static final int CARD_FILL = 0x59000000;
    public static final int CARD_FILL_HOVER = 0x82252B26;
    public static final int CARD_BORDER = 0x66FFFFFF;
    public static final int CARD_BORDER_HOVER = 0xCC39FF14;
    private static final Identifier CLIENT_LOGO = Identifier.fromNamespaceAndPath(
            "znclient", "textures/gui/znclient.png");
    private static final Map<AbstractWidget, HoverState> BUTTON_HOVERS = new WeakHashMap<>();
    private static final Map<Screen, Long> SCREEN_ENTRANCES = new WeakHashMap<>();

    private ZnClientUi() {}

    public static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        drawGlassPanel(graphics, x, y, width, height, 0x68000000);
        if (width <= 4 || height <= 4) return;
        int border = 0x40FFFFFF;
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x28000000);
        graphics.fill(x, y + 1, x + 1, y + height - 1, 0x30FFFFFF);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, 0x30FFFFFF);
        int sheen = 0x0FFFFFFF;
        graphics.fill(x + 1, y + 1, x + width - 1, y + Math.min(20, height / 4), sheen);
    }

    public static boolean usesCustomButtonStyle(Screen screen) {
        return screen instanceof ZnClientTitleScreen
                || screen instanceof ZnClientModsScreen
                || screen instanceof ModuleConfigScreen;
    }

    public static void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x68000000);
    }

    public static void drawGlassPanel(GuiGraphicsExtractor graphics, int x, int y,
                                      int width, int height, int fill) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, fill & 0xFF000000);
    }

    /** Adds subtle zN Client branding while leaving vanilla menu backgrounds untouched. */
    public static void drawScreenSurface(Screen screen, GuiGraphicsExtractor graphics) {
        if (!(screen instanceof SelectWorldScreen) && !(screen instanceof JoinMultiplayerScreen)) return;
        if (screen.width <= 24 || screen.height <= 24) return;

        graphics.blit(RenderPipelines.GUI_TEXTURED, CLIENT_LOGO, 18, 13,
                0.0f, 0.0f, 16, 16, 1244, 1265, 1244, 1265);
        graphics.text(screen.getFont(), Component.literal("zN CLIENT"), 39, 18, 0xFFD5E5F6);
    }

    public static void resetScreenTransitions() {
        SCREEN_ENTRANCES.clear();
    }

    public static void drawTransitionVeil(Screen screen, GuiGraphicsExtractor graphics) {
        long now = System.nanoTime();
        long started = SCREEN_ENTRANCES.computeIfAbsent(screen, ignored -> now);
        float progress = Math.min(1.0f, (now - started) / 220_000_000.0f);
        int alpha = Math.round(104 * (1.0f - progress));
        if (alpha > 0) {
            graphics.fill(0, 0, screen.width, screen.height, alpha << 24);
        }
    }

    public static void drawButtonSurface(GuiGraphicsExtractor graphics, AbstractWidget widget) {
        drawButtonSurface(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                widget.isActive(), buttonHover(widget));
    }

    public static void drawButtonSurface(GuiGraphicsExtractor graphics, int x, int y,
                                         int width, int height, boolean active, float hover) {
        int border = active ? blend(0x40565B63, 0xFF39FF14, hover) : 0x40505A64;
        int fill = active ? blend(0x50000000, 0x7B15221A, hover) : 0x44000000;
        graphics.fill(x + 2, y, x + width - 2, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        graphics.fill(x + 3, y + 2, x + width - 3, y + height - 2,
                active ? blend(0x40000000, 0x68162218, hover) : 0x44000000);
        if (active && hover > 0.05f) {
            graphics.fill(x + 3, y + 2, x + width - 3, y + 3, blend(0x00000000, 0x9039FF14, hover));
        }
    }

    public static Button button(int x, int y, int width, int height, Component label,
                                net.minecraft.client.gui.Font font, Runnable action) {
        return new ActionButton(x, y, width, height, label, font, action);
    }

    public static Button overlayButton(int x, int y, int width, int height, Component label,
                                       net.minecraft.client.gui.Font font, Runnable action) {
        return new OverlayButton(x, y, width, height, label, font, action);
    }

    public static Button toggle(int x, int y, int width, int height, Component label,
                                net.minecraft.client.gui.Font font, Supplier<Boolean> value,
                                Runnable action) {
        return new ToggleButton(x, y, width, height, label, font, value, action);
    }

    public static AbstractWidget slider(int x, int y, int width, int height,
                                        net.minecraft.client.gui.Font font,
                                        double minimum, double maximum, double step,
                                        DoubleSupplier value, DoubleConsumer setter,
                                        Supplier<Component> valueLabel, Runnable onCommit) {
        return new ValueSlider(x, y, width, height, font, minimum, maximum, step,
                value, setter, valueLabel, onCommit);
    }

    private static final class ActionButton extends Button {
        private final net.minecraft.client.gui.Font font;

        private ActionButton(int x, int y, int width, int height, Component label,
                             net.minecraft.client.gui.Font font, Runnable action) {
            super(x, y, width, height, label, button -> action.run(), DEFAULT_NARRATION);
            this.font = font;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            drawGlassSurface(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
            graphics.centeredText(this.font, this.getMessage(), this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2, TEXT);
        }
    }

    private static final class OverlayButton extends Button {
        private final net.minecraft.client.gui.Font font;

        private OverlayButton(int x, int y, int width, int height, Component label,
                              net.minecraft.client.gui.Font font, Runnable action) {
            super(x, y, width, height, label, button -> action.run(), DEFAULT_NARRATION);
            this.font = font;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            drawGlassSurface(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
            graphics.centeredText(this.font, this.getMessage(), this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2, TEXT);
        }
    }

    private static final class ToggleButton extends Button {
        private final net.minecraft.client.gui.Font font;
        private final Supplier<Boolean> value;

        private ToggleButton(int x, int y, int width, int height, Component label,
                             net.minecraft.client.gui.Font font, Supplier<Boolean> value, Runnable action) {
            super(x, y, width, height, label, button -> action.run(), DEFAULT_NARRATION);
            this.font = font;
            this.value = value;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            drawGlassSurface(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
            graphics.text(this.font, this.getMessage(), this.getX() + 12,
                    this.getY() + (this.getHeight() - 8) / 2, TEXT);
            boolean enabled = this.value.get();
            drawSwitch(graphics, this.getRight() - 46, this.getY() + (this.getHeight() - 18) / 2,
                    36, 18, enabled);
        }
    }

    /** Rounded-feel glass surface used by all shared controls. */
    public static void drawGlassSurface(GuiGraphicsExtractor graphics, int x, int y,
                                        int width, int height) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x + 1, y, x + width - 1, y + height, 0x59000000);
        graphics.fill(x, y + 1, x + 1, y + height - 1, 0x59000000);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, 0x59000000);
        graphics.fill(x + 1, y, x + width - 1, y + 1, 0x2EFFFFFF);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, 0x28000000);
    }

    /** Neon on/off switch drawn with a glowing accent when enabled. */
    public static void drawSwitch(GuiGraphicsExtractor graphics, int x, int y,
                                  int width, int height, boolean enabled) {
        if (width < 10 || height < 6) return;
        int knob = Math.min(height - 4, 12);
        graphics.fill(x + 1, y, x + width - 1, y + height, enabled ? 0xFF1E3320 : 0xFF23272C);
        graphics.fill(x, y + 1, x + 1, y + height - 1, enabled ? 0xFF1E3320 : 0xFF23272C);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1,
                enabled ? 0xFF1E3320 : 0xFF23272C);
        graphics.fill(x + 1, y, x + width - 1, y + 1, enabled ? 0xFF39FF14 : 0x40FFFFFF);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height,
                enabled ? 0xFF1E5B14 : 0x28000000);
        int knobX = enabled ? x + width - knob - 5 : x + 5;
        int knobY = y + (height - knob) / 2;
        graphics.fill(knobX, knobY, knobX + knob, knobY + knob, enabled ? ACCENT : 0xFF9CA3AF);
        graphics.fill(knobX + 2, knobY + knob - 3, knobX + knob - 2, knobY + knob - 2,
                enabled ? 0xFF7FFF5C : 0xFFC4CBD2);
    }

    private static final class ValueSlider extends AbstractWidget {
        private final net.minecraft.client.gui.Font font;
        private final double minimum;
        private final double maximum;
        private final double step;
        private final DoubleSupplier value;
        private final DoubleConsumer setter;
        private final Supplier<Component> valueLabel;
        private final Runnable onCommit;
        private boolean dragging;

        private ValueSlider(int x, int y, int width, int height, net.minecraft.client.gui.Font font,
                            double minimum, double maximum, double step, DoubleSupplier value,
                            DoubleConsumer setter, Supplier<Component> valueLabel, Runnable onCommit) {
            super(x, y, width, height, Component.empty());
            this.font = font;
            this.minimum = minimum;
            this.maximum = maximum;
            this.step = step;
            this.value = value;
            this.setter = setter;
            this.valueLabel = valueLabel;
            this.onCommit = onCommit;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            this.dragging = true;
            this.setFromMouseX(event.x());
        }

        @Override
        protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
            if (this.dragging) this.setFromMouseX(event.x());
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            if (this.dragging) this.onCommit.run();
            this.dragging = false;
        }

        private void setFromMouseX(double mouseX) {
            int trackLeft = this.getX() + 9;
            int trackRight = this.getRight() - 59;
            double progress = Math.max(0.0, Math.min(1.0,
                    (mouseX - trackLeft) / Math.max(1.0, trackRight - trackLeft)));
            double raw = this.minimum + progress * (this.maximum - this.minimum);
            double stepped = this.minimum + Math.round((raw - this.minimum) / this.step) * this.step;
            this.setter.accept(Math.max(this.minimum, Math.min(this.maximum, stepped)));
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            drawGlassSurface(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
            int trackLeft = this.getX() + 10;
            int trackRight = this.getRight() - 62;
            int centerY = this.getY() + this.getHeight() / 2;
            double progress = Math.max(0.0, Math.min(1.0,
                    (this.value.getAsDouble() - this.minimum) / (this.maximum - this.minimum)));
            int thumbX = trackLeft + (int) Math.round(progress * (trackRight - trackLeft));
            graphics.fill(trackLeft, centerY - 3, trackRight, centerY + 3, 0xFF16191D);
            graphics.fill(trackLeft, centerY + 2, trackRight, centerY + 3, 0x28000000);
            graphics.fill(trackLeft, centerY - 2, thumbX, centerY + 2, ACCENT);
            graphics.fill(trackLeft, centerY - 2, thumbX, centerY - 1, 0xFF7FFF5C);
            boolean hot = this.isHoveredOrFocused() || this.dragging;
            graphics.fill(thumbX - 5, centerY - 6, thumbX + 5, centerY + 6,
                    hot ? 0xFFFFFFFF : 0xFFCFD7CE);
            graphics.fill(thumbX - 3, centerY - 4, thumbX + 3, centerY + 4,
                    hot ? ACCENT : 0xFF4A5560);
            graphics.centeredText(this.font, this.valueLabel.get(), this.getRight() - 31,
                    centerY - 4, TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            this.defaultButtonNarrationText(narration);
        }
    }

    private static float buttonHover(AbstractWidget widget) {
        long now = System.nanoTime();
        HoverState state = BUTTON_HOVERS.computeIfAbsent(widget, ignored -> new HoverState(now));
        float elapsed = Math.min(1.0f, Math.max(0.0f, (now - state.lastNanos) / 100_000_000.0f));
        state.lastNanos = now;
        float target = widget.isActive() && widget.isHoveredOrFocused() ? 1.0f : 0.0f;
        state.progress += (target - state.progress) * elapsed;
        return state.progress;
    }

    private static int blend(int from, int to, float progress) {
        int alpha = (int) (((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * progress));
        int red = (int) (((from >>> 16) & 0xFF) + ((((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * progress));
        int green = (int) (((from >>> 8) & 0xFF) + ((((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * progress));
        int blue = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * progress));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static final class HoverState {
        private float progress;
        private long lastNanos;

        private HoverState(long now) {
            this.lastNanos = now;
        }
    }
}
