package com.znclient.mod;

import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** zN Client title menu. Vanilla screens keep their original behavior and parent navigation. */
public final class ZnClientTitleScreen extends Screen {
    private static final Identifier CLIENT_LOGO = Identifier.fromNamespaceAndPath(
            "znclient", "textures/gui/znclient.png");
    private static final int LOGO_WIDTH = 1244;
    private static final int LOGO_HEIGHT = 1265;
    private static final float MENU_SCALE = 0.88f;

    private int logoTop;
    private int logoWidth;
    private int logoHeight;
    private int buttonWidth;
    private int buttonHeight;
    private int buttonGap;
    private int buttonTop;
    private int compactButtonGap;

    public ZnClientTitleScreen() {
        super(Component.translatable("screen.znclient.title"));
    }

    @Override
    protected void init() {
        int baseButtonWidth = Math.min(360, this.width - 32);
        int baseButtonHeight = Math.min(38, Math.round(this.height * 0.05f));
        int baseButtonGap = Math.max(4, Math.min(8, Math.round(this.height * 0.012f)));
        this.buttonWidth = Math.max(1, Math.round(baseButtonWidth * MENU_SCALE));
        this.buttonHeight = Math.max(12, Math.round(baseButtonHeight * MENU_SCALE));
        this.buttonGap = Math.max(2, Math.round(baseButtonGap * MENU_SCALE));
        this.compactButtonGap = Math.max(3, Math.round(this.buttonWidth * 0.05f));

        float logoAspect = LOGO_WIDTH / (float) LOGO_HEIGHT;
        this.logoTop = Math.max(8, Math.round(this.height * 0.025f));

        int menuHeight = this.buttonHeight * 5 + this.buttonGap * 4;
        int bottomMargin = Math.max(12, Math.round(this.height * 0.06f));
        int maxLogoHeight = Math.max(28, this.height - menuHeight - bottomMargin - this.logoTop - 18);
        this.logoWidth = Math.max(1, Math.round(Math.min(680.0f,
                Math.min(180.0f, Math.min(this.width * 0.22f, maxLogoHeight * logoAspect)))));
        this.logoHeight = Math.round(this.logoWidth / logoAspect);
        int preferredTop = this.logoTop + this.logoHeight + 12;
        int firstAllowedTop = this.logoTop + this.logoHeight + 12;
        int lastAllowedTop = this.height - menuHeight - bottomMargin;
        this.buttonTop = Math.max(4, Math.min(Math.max(preferredTop, firstAllowedTop), lastAllowedTop));

        this.addRenderableOnly((graphics, mouseX, mouseY, delta) -> this.drawLogo(graphics));
        this.addMenuButton((this.width - this.buttonWidth) / 2, this.buttonTop,
                this.buttonWidth, "screen.znclient.title.singleplayer",
                () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this)));
        this.addMenuButton((this.width - this.buttonWidth) / 2,
                this.buttonTop + this.buttonHeight + this.buttonGap,
                this.buttonWidth, "screen.znclient.title.multiplayer",
                () -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this)));
        this.addMenuButton((this.width - this.buttonWidth) / 2,
                this.buttonTop + (this.buttonHeight + this.buttonGap) * 2,
                this.buttonWidth, "screen.znclient.title.realms",
                () -> this.minecraft.gui.setScreen(new RealmsMainScreen(this)));
        int compactWidth = (this.buttonWidth - this.compactButtonGap) / 2;
        int compactTop = this.buttonTop + (this.buttonHeight + this.buttonGap) * 3;
        this.addMenuButton((this.width - this.buttonWidth) / 2, compactTop,
                compactWidth, "screen.znclient.title.settings",
                () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options)));
        this.addMenuButton((this.width - this.buttonWidth) / 2 + compactWidth + this.compactButtonGap,
                compactTop, compactWidth, "screen.znclient.title.language",
                () -> this.minecraft.gui.setScreen(new ZnClientLanguageScreen(this)));
        this.addMenuButton((this.width - this.buttonWidth) / 2,
                this.buttonTop + (this.buttonHeight + this.buttonGap) * 4,
                this.buttonWidth, "screen.znclient.title.quit", this.minecraft::stop);
    }

    private void addMenuButton(int left, int top, int width, String label, Runnable action) {
        this.addRenderableWidget(new MenuActionButton(left, top, width, this.buttonHeight,
                Component.translatable(label), this.font, action));
    }

    private void drawLogo(GuiGraphicsExtractor graphics) {
        int glowWidth = Math.min(this.width - 60, Math.round(this.logoWidth * 1.4f));
        int glowLeft = (this.width - glowWidth) / 2;
        int glowTop = this.logoTop + this.logoHeight + 2;
        int glowHeight = Math.max(4, Math.round(this.logoHeight * 0.025f));
        graphics.fill(glowLeft, glowTop, glowLeft + glowWidth, glowTop + glowHeight, 0x2839FF14);
        graphics.fill(glowLeft + 14, glowTop + 1, glowLeft + glowWidth - 14,
                glowTop + glowHeight - 1, 0x5439FF14);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CLIENT_LOGO,
                (this.width - this.logoWidth) / 2, this.logoTop, 0.0f, 0.0f,
                this.logoWidth, this.logoHeight, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class MenuActionButton extends Button {
        private final net.minecraft.client.gui.Font font;
        private float hoverProgress;
        private long lastRenderNanos;

        private MenuActionButton(int x, int y, int width, int height, Component label,
                                 net.minecraft.client.gui.Font font, Runnable action) {
            super(x, y, width, height, label, button -> action.run(), DEFAULT_NARRATION);
            this.font = font;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            long now = System.nanoTime();
            float elapsed = this.lastRenderNanos == 0L
                    ? 1.0f : Math.min(1.0f, (now - this.lastRenderNanos) / 100_000_000.0f);
            this.lastRenderNanos = now;
            float target = this.isActive() && this.isHoveredOrFocused() ? 1.0f : 0.0f;
            this.hoverProgress += (target - this.hoverProgress) * Math.min(1.0f, elapsed * 8.0f);

            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            int border = blend(0xFF101316, 0xFF39FF14, this.hoverProgress);
            int topEdge = blend(0xFF687079, 0xFF9BFF7A, this.hoverProgress);
            int face = blend(0xE52A2E33, 0xE51E3A20, this.hoverProgress);

            graphics.fill(x + 2, y + 2, x + width - 2, y + height + 2, 0x70000000);
            graphics.fill(x + 1, y, x + width - 1, y + height, border);
            graphics.fill(x + 2, y + 1, x + width - 2, y + height - 1, face);
            graphics.fill(x + 4, y + 2, x + width - 4, y + 3, topEdge);
            graphics.fill(x + 3, y + height - 3, x + width - 3, y + height - 2, 0xAA090B0D);
            graphics.centeredText(this.font, this.getMessage(), x + width / 2,
                    y + (height - 8) / 2, 0xFFF1F2F3);
        }

        private static int blend(int from, int to, float progress) {
            int alpha = channel(from, to, progress, 24);
            int red = channel(from, to, progress, 16);
            int green = channel(from, to, progress, 8);
            int blue = channel(from, to, progress, 0);
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        private static int channel(int from, int to, float progress, int shift) {
            int start = (from >>> shift) & 0xFF;
            int end = (to >>> shift) & 0xFF;
            return Math.round(start + (end - start) * progress);
        }
    }
}
