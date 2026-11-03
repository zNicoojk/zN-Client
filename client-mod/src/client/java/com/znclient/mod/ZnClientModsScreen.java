package com.znclient.mod;

import org.jspecify.annotations.Nullable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ZnClientModsScreen extends Screen {
    private static final int LIST_WIDTH = 850;
    private static final int LIST_HEIGHT = 520;
    private static final int TILE_MIN_WIDTH = 230;
    private static final int TILE_HEIGHT = 84;
    private static final int GRID_GAP = 10;

    private enum Page { HOME, MODULES }

    private final @Nullable Screen parent;
    private Page page = Page.HOME;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int homeButtonLeft;
    private int homeButtonTop;
    private int homeButtonWidth;
    private int homeButtonHeight;
    private boolean capturedHudPress;
    private boolean capturedPressOnModsButton;
    private @Nullable EditBox searchField;
    private String searchQuery = "";
    private @Nullable String cachedQuery;
    private long cachedRegistryRevision = -1;
    private List<ClientModule> cachedModules = List.of();
    private final List<ModuleCardWidget> cardWidgets = new ArrayList<>();
    private final List<String> cardModuleIds = new ArrayList<>();
    private float scrollPosition;
    private float targetScrollPosition;
    private int scrollbarDragOffset = -1;

    public ZnClientModsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.znclient.mods.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.cardWidgets.clear();
        this.cardModuleIds.clear();
        this.scrollbarDragOffset = -1;
        this.capturedHudPress = false;
        if (this.page == Page.HOME) {
            this.panelWidth = this.width;
            this.panelHeight = this.height;
            this.panelLeft = 0;
            this.panelTop = 0;
            this.addRenderableOnly(this::drawHome);
            int logoHeight = this.homeLogoHeight();
            int groupHeight = logoHeight + 18 + 36;
            int groupTop = (this.height - groupHeight) / 2;
            this.homeButtonWidth = Math.max(1, Math.min(176, this.width - 32));
            this.homeButtonHeight = 36;
            this.homeButtonLeft = (this.width - this.homeButtonWidth) / 2;
            this.homeButtonTop = groupTop + logoHeight + 18;
            int languageButtonWidth = Math.min(40, this.homeButtonWidth);
            int languageLeft = Math.min(this.width - languageButtonWidth - 16,
                    this.homeButtonLeft + this.homeButtonWidth + 10);
            this.addRenderableWidget(ZnClientUi.overlayButton(this.homeButtonLeft,
                    this.homeButtonTop, this.homeButtonWidth, this.homeButtonHeight,
                    Component.translatable("screen.znclient.mods.home.mods"), this.font,
                    () -> this.changePage(Page.MODULES)));
            this.addRenderableWidget(new LanguageIconButton(
                    languageLeft, this.homeButtonTop,
                    languageButtonWidth, this.homeButtonHeight, this.font,
                    () -> this.minecraft.options.languageCode,
                    () -> this.minecraft.gui.setScreen(new ZnClientLanguageScreen(this))));
            return;
        }

        this.panelWidth = Math.max(1, Math.min(LIST_WIDTH, this.width - 24));
        this.panelHeight = Math.max(1, Math.min(LIST_HEIGHT, this.height - 20));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.addRenderableOnly(this::drawModuleList);
        this.addRenderableWidget(ZnClientUi.button(this.panelLeft + this.panelWidth - 98,
                this.panelTop + 10, 84, 22,
                Component.translatable("screen.znclient.mods.home"), this.font,
                () -> this.changePage(Page.HOME)));

        int searchWidth = Math.max(1, this.panelWidth - 32);
        this.searchField = new EditBox(this.font, this.panelLeft + 16, this.panelTop + 42,
                searchWidth, 22, Component.translatable("screen.znclient.mods.search"));
        this.searchField.setBordered(false);
        this.searchField.setTextColor(ZnClientUi.TEXT);
        this.searchField.setTextColorUneditable(ZnClientUi.MUTED);
        this.searchField.setMaxLength(64);
        this.searchField.setValue(this.searchQuery);
        this.searchField.setHint(Component.translatable("screen.znclient.mods.search_hint"));
        this.searchField.setResponder(value -> {
            this.searchQuery = value;
            this.cachedQuery = null;
            this.scrollPosition = 0.0f;
            this.targetScrollPosition = 0.0f;
            this.syncCardWidgets(this.filteredModules());
        });
        this.addRenderableWidget(this.searchField);
        this.syncCardWidgets(this.filteredModules());
    }

    private void changePage(Page page) {
        this.page = page;
        this.rebuildWidgets();
    }

    private void drawHome(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xB00C0E12, 0xB8141A16);
        HudLayoutManager.renderEditor(graphics, this.width, this.height, mouseX, mouseY);
        int logoWidth = this.homeLogoWidth();
        int logoHeight = this.homeLogoHeight();
        int logoLeft = (this.width - logoWidth) / 2;
        int groupHeight = logoHeight + 18 + this.homeButtonHeight;
        int logoTop = Math.max(12, (this.height - groupHeight) / 2);
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("znclient", "textures/gui/znclient.png"),
                logoLeft, logoTop, 0.0f, 0.0f, logoWidth, logoHeight,
                1244, 1265, 1244, 1265);
    }

    private int homeLogoWidth() {
        int maxHeight = Math.max(28, this.height - 92);
        int maxWidth = Math.round(maxHeight * 1244.0f / 1265.0f);
        return Math.max(28, Math.min(82, Math.min(this.width - 36, maxWidth)));
    }

    private int homeLogoHeight() {
        return Math.max(1, Math.min(this.height - 54,
                Math.round(this.homeLogoWidth() * 1265.0f / 1244.0f)));
    }

    private static final class LanguageIconButton extends Button {
        private final net.minecraft.client.gui.Font font;
        private final java.util.function.Supplier<String> language;

        private LanguageIconButton(int x, int y, int width, int height,
                                   net.minecraft.client.gui.Font font,
                                   java.util.function.Supplier<String> language, Runnable action) {
            super(x, y, width, height, Component.translatable("screen.znclient.mods.language"),
                    button -> action.run(), DEFAULT_NARRATION);
            this.font = font;
            this.language = language;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            String languageCode = this.language.get();
            String shortCode = languageCode.equals("es_es") ? "ES" : "EN";
            int centerX = this.getX() + this.getWidth() / 2;
            int globeTop = this.getY() + 3;
            graphics.fill(centerX - 3, globeTop, centerX + 3, globeTop + 1, ZnClientUi.TEXT);
            graphics.fill(centerX - 4, globeTop + 1, centerX + 4, globeTop + 7, ZnClientUi.TEXT);
            graphics.fill(centerX - 3, globeTop + 7, centerX + 3, globeTop + 8, ZnClientUi.TEXT);
            graphics.fill(centerX - 2, globeTop + 1, centerX + 2, globeTop + 2, 0xFF20242A);
            graphics.fill(centerX - 3, globeTop + 2, centerX + 3, globeTop + 3, 0xFF20242A);
            graphics.fill(centerX - 3, globeTop + 4, centerX + 3, globeTop + 5, 0xFF20242A);
            graphics.fill(centerX - 2, globeTop + 6, centerX + 2, globeTop + 7, 0xFF20242A);
            graphics.fill(centerX - 1, globeTop + 1, centerX + 1, globeTop + 7, ZnClientUi.TEXT);
            graphics.centeredText(this.font, Component.literal(shortCode),
                    centerX, this.getY() + 19, ZnClientUi.TEXT);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the live Minecraft view behind the transparent mod browser.
    }

    private void drawModuleList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xE0101216, 0xD81A1E24);
        ZnClientUi.drawPanel(graphics, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight);
        int headerLeft = this.panelLeft + 16;
        graphics.text(this.font, Component.translatable("screen.znclient.mods.heading"),
                headerLeft, this.panelTop + 12, ZnClientUi.TEXT, true);
        List<ClientModule> modules = this.filteredModules();
        graphics.text(this.font, Component.literal(modules.size()
                        + Component.translatable("screen.znclient.mods.count.suffix").getString()),
                headerLeft, this.panelTop + 27, ZnClientUi.ACCENT);
        int logoSize = 18;
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("znclient", "textures/gui/znclient.png"),
                this.panelLeft + this.panelWidth - logoSize - 114, this.panelTop + 10,
                0.0f, 0.0f, logoSize, logoSize, 1244, 1265, 1244, 1265);

        int searchTop = this.panelTop + 40;
        ZnClientUi.drawGlassSurface(graphics, this.panelLeft + 14, searchTop,
                this.panelWidth - 28, 26);
        graphics.text(this.font, Component.literal("⌕"), this.panelLeft + 22,
                searchTop + 9, ZnClientUi.MUTED);

        GridLayout layout = this.gridLayout();
        float maxScroll = this.maxScroll(layout, modules.size());
        if (this.targetScrollPosition > maxScroll) this.targetScrollPosition = maxScroll;
        if (this.scrollPosition > maxScroll) this.scrollPosition = maxScroll;
        this.syncCardWidgets(modules);
        this.positionCardWidgets(layout, modules);

        graphics.enableScissor(layout.left, layout.top, layout.right, layout.bottom);
        for (int index = 0; index < modules.size(); index++) {
            ClientModule module = modules.get(index);
            int row = index / layout.columns;
            int column = index % layout.columns;
            int left = layout.left + column * (layout.tileWidth + GRID_GAP);
            int top = Math.round(layout.top + (row - this.scrollPosition) * (TILE_HEIGHT + GRID_GAP));
            if (top + TILE_HEIGHT < layout.top || top > layout.bottom) continue;
            this.drawModuleCard(graphics, module, left, top, layout.tileWidth, mouseX, mouseY);
        }
        graphics.disableScissor();

        if (modules.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("screen.znclient.mods.no_results"),
                    this.width / 2, layout.top + 24, ZnClientUi.MUTED);
        }
        this.drawScrollbar(graphics, layout, modules.size());
        graphics.centeredText(this.font, Component.translatable("screen.znclient.mods.list_hint"),
                this.width / 2, this.panelTop + this.panelHeight - 14, ZnClientUi.MUTED);
    }

    private void drawModuleCard(GuiGraphicsExtractor graphics, ClientModule module,
                                int left, int top, int width, int mouseX, int mouseY) {
        boolean hovered = mouseX >= left && mouseX < left + width
                && mouseY >= top && mouseY < top + TILE_HEIGHT;
        int accent = module.enabled() ? ZnClientUi.ACCENT : ZnClientUi.MUTED;

        graphics.fill(left + 1, top + 1, left + width - 1, top + TILE_HEIGHT - 1,
                hovered ? ZnClientUi.CARD_FILL_HOVER : ZnClientUi.CARD_FILL);
        graphics.fill(left + 1, top, left + width - 1, top + 1,
                hovered ? ZnClientUi.CARD_BORDER_HOVER : ZnClientUi.CARD_BORDER);
        graphics.fill(left + 1, top + TILE_HEIGHT - 1, left + width - 1, top + TILE_HEIGHT,
                hovered ? 0x8039FF14 : 0x28000000);
        graphics.fill(left, top + 1, left + 1, top + TILE_HEIGHT - 1,
                hovered ? ZnClientUi.CARD_BORDER_HOVER : ZnClientUi.CARD_BORDER);
        graphics.fill(left + width - 1, top + 1, left + width, top + TILE_HEIGHT - 1,
                hovered ? ZnClientUi.CARD_BORDER_HOVER : ZnClientUi.CARD_BORDER);
        graphics.fill(left + 1, top + 2, left + width - 1, top + 6,
                hovered ? 0x2039FF14 : 0x12FFFFFF);
        graphics.fill(left + 2, top + 1, left + 4, top + TILE_HEIGHT - 1,
                module.enabled() ? accent : 0x30000000);

        int iconBox = 30;
        graphics.fill(left + 14, top + 24, left + 14 + iconBox, top + 24 + iconBox,
                module.enabled() ? 0x3339FF14 : 0x22FFFFFF);
        this.drawIcon(graphics, module.icon(), left + 14 + 4, top + 24 + 4,
                module.enabled() ? ZnClientUi.ACCENT : 0xFF9CA3AF);
        graphics.text(this.font, module.title(), left + 52, top + 14, ZnClientUi.TEXT, true);
        graphics.textWithWordWrap(this.font, module.description(), left + 52, top + 32,
                Math.max(1, width - 118), 31);

        int switchX = left + width - 47;
        int switchY = top + 12;
        ZnClientUi.drawSwitch(graphics, switchX, switchY, 35, 17, module.enabled());
        graphics.text(this.font, Component.literal("›"), left + width - 16,
                top + TILE_HEIGHT - 17, module.enabled() ? ZnClientUi.ACCENT : ZnClientUi.MUTED);
    }

    private void drawIcon(GuiGraphicsExtractor graphics, ClientModule.Icon icon,
                          int x, int y, int color) {
        switch (icon) {
            case ZOOM -> {
                graphics.fill(x + 3, y + 1, x + 15, y + 3, color);
                graphics.fill(x + 1, y + 3, x + 3, y + 13, color);
                graphics.fill(x + 15, y + 3, x + 17, y + 13, color);
                graphics.fill(x + 3, y + 13, x + 15, y + 15, color);
                graphics.fill(x + 13, y + 14, x + 16, y + 17, color);
                graphics.fill(x + 16, y + 17, x + 22, y + 19, color);
            }
            case FPS -> {
                graphics.fill(x + 2, y + 11, x + 6, y + 19, color);
                graphics.fill(x + 9, y + 7, x + 13, y + 19, color);
                graphics.fill(x + 16, y + 2, x + 20, y + 19, color);
                graphics.fill(x, y + 19, x + 22, y + 21, color);
            }
            case PING -> {
                graphics.fill(x + 9, y + 15, x + 13, y + 21, color);
                graphics.fill(x + 5, y + 11, x + 17, y + 13, color);
                graphics.fill(x + 3, y + 7, x + 19, y + 9, color);
                graphics.fill(x + 1, y + 3, x + 21, y + 5, color);
            }
            case COORDINATES -> {
                graphics.fill(x + 9, y + 1, x + 13, y + 21, color);
                graphics.fill(x + 1, y + 9, x + 21, y + 13, color);
                graphics.fill(x + 5, y + 5, x + 8, y + 8, color);
                graphics.fill(x + 14, y + 14, x + 17, y + 17, color);
            }
            case VIEWMODEL -> {
                graphics.fill(x + 5, y + 2, x + 9, y + 20, color);
                graphics.fill(x + 13, y + 2, x + 17, y + 20, color);
                graphics.fill(x + 3, y + 5, x + 19, y + 8, color);
                graphics.fill(x + 3, y + 14, x + 19, y + 17, color);
            }
        }
    }

    private GridLayout gridLayout() {
        int left = this.panelLeft + 14;
        int right = this.panelLeft + this.panelWidth - 14;
        int top = this.panelTop + 74;
        int bottom = this.panelTop + this.panelHeight - 31;
        int innerWidth = Math.max(1, right - left);
        int columns = Math.max(1, Math.min(4,
                (innerWidth + GRID_GAP) / (TILE_MIN_WIDTH + GRID_GAP)));
        int tileWidth = Math.max(1, (innerWidth - GRID_GAP * (columns - 1)) / columns);
        return new GridLayout(left, top, right, Math.max(top + 1, bottom), columns, tileWidth);
    }

    private float maxScroll(GridLayout layout, int moduleCount) {
        int totalRows = (moduleCount + layout.columns - 1) / layout.columns;
        float visibleRows = (layout.bottom - layout.top + GRID_GAP) / (float) (TILE_HEIGHT + GRID_GAP);
        return Math.max(0.0f, totalRows - visibleRows);
    }

    private List<ClientModule> filteredModules() {
        String query = this.searchQuery.strip().toLowerCase(Locale.ROOT);
        long revision = ClientModuleRegistry.revision();
        if (revision != this.cachedRegistryRevision || !query.equals(this.cachedQuery)) {
            List<ClientModule> modules = ClientModuleRegistry.modules();
            this.cachedModules = query.isEmpty() ? modules : modules.stream()
                    .filter(module -> module.id().toLowerCase(Locale.ROOT).contains(query)
                            || module.title().getString().toLowerCase(Locale.ROOT).contains(query)
                            || module.description().getString().toLowerCase(Locale.ROOT).contains(query))
                    .toList();
            this.cachedQuery = query;
            this.cachedRegistryRevision = revision;
        }
        return this.cachedModules;
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, GridLayout layout, int moduleCount) {
        float maxScroll = this.maxScroll(layout, moduleCount);
        if (maxScroll <= 0.0f) return;
        int x = layout.right - 3;
        int height = layout.bottom - layout.top;
        graphics.fill(x, layout.top, x + 2, layout.bottom, 0xFF343A42);
        int thumbHeight = Math.max(18, Math.round(height / (maxScroll + 1.0f)));
        int travel = Math.max(0, height - thumbHeight);
        int thumbTop = layout.top + Math.round(travel * (this.scrollPosition / maxScroll));
        graphics.fill(x - 1, thumbTop, x + 3, thumbTop + thumbHeight,
                0xAA777F88);
    }

    private boolean isInsideGrid(double x, double y) {
        GridLayout layout = this.gridLayout();
        return x >= layout.left && x < layout.right && y >= layout.top && y < layout.bottom;
    }

    private void syncCardWidgets(List<ClientModule> modules) {
        List<String> moduleIds = modules.stream().map(ClientModule::id).toList();
        if (this.cardModuleIds.equals(moduleIds)) return;

        for (ModuleCardWidget widget : this.cardWidgets) this.removeWidget(widget);
        this.cardWidgets.clear();
        this.cardModuleIds.clear();
        for (ClientModule module : modules) {
            ModuleCardWidget widget = new ModuleCardWidget(module, 0, 0, 1, TILE_HEIGHT,
                    () -> this.minecraft.gui.setScreen(module.createSettingsScreen(this)));
            this.cardWidgets.add(this.addRenderableWidget(widget));
            this.cardModuleIds.add(module.id());
        }
    }

    private void positionCardWidgets(GridLayout layout, List<ClientModule> modules) {
        for (int index = 0; index < this.cardWidgets.size(); index++) {
            ModuleCardWidget widget = this.cardWidgets.get(index);
            int row = index / layout.columns;
            int column = index % layout.columns;
            int left = layout.left + column * (layout.tileWidth + GRID_GAP);
            int top = Math.round(layout.top + (row - this.scrollPosition) * (TILE_HEIGHT + GRID_GAP));
            widget.setRectangle(left, top, layout.tileWidth, TILE_HEIGHT);
            widget.setClip(layout.top, layout.bottom);
        }
    }

    private boolean isInsideScrollbar(double x, double y, GridLayout layout, int moduleCount) {
        return this.maxScroll(layout, moduleCount) > 0.0f
                && x >= layout.right - 7 && x < layout.right + 5
                && y >= layout.top && y < layout.bottom;
    }

    private int scrollbarThumbHeight(GridLayout layout, int moduleCount) {
        float maxScroll = this.maxScroll(layout, moduleCount);
        int height = layout.bottom - layout.top;
        return Math.min(height, Math.max(18, Math.round(height / (maxScroll + 1.0f))));
    }

    private int scrollbarThumbTop(GridLayout layout, int moduleCount) {
        float maxScroll = this.maxScroll(layout, moduleCount);
        int thumbHeight = this.scrollbarThumbHeight(layout, moduleCount);
        int travel = Math.max(0, layout.bottom - layout.top - thumbHeight);
        return layout.top + Math.round(travel * (this.scrollPosition / maxScroll));
    }

    private void setScrollFromScrollbar(double mouseY, GridLayout layout, int moduleCount) {
        float maxScroll = this.maxScroll(layout, moduleCount);
        int thumbHeight = this.scrollbarThumbHeight(layout, moduleCount);
        int travel = Math.max(1, layout.bottom - layout.top - thumbHeight);
        int thumbTop = (int) Math.round(mouseY - this.scrollbarDragOffset);
        double progress = Math.max(0.0, Math.min(1.0,
                (thumbTop - layout.top) / (double) travel));
        this.targetScrollPosition = (float) (progress * maxScroll);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.page == Page.HOME && event.buttonInfo().button() == 1) {
            boolean onModsButton = event.x() >= this.homeButtonLeft
                    && event.x() < this.homeButtonLeft + this.homeButtonWidth
                    && event.y() >= this.homeButtonTop
                    && event.y() < this.homeButtonTop + this.homeButtonHeight;
            if (HudLayoutManager.beginDrag(event.x(), event.y())) {
                this.capturedHudPress = true;
                this.capturedPressOnModsButton = onModsButton;
                return true;
            }
        }
        if (this.page == Page.MODULES && event.buttonInfo().button() == 1) {
            GridLayout layout = this.gridLayout();
            List<ClientModule> modules = this.filteredModules();
            if (this.isInsideScrollbar(event.x(), event.y(), layout, modules.size())) {
                int thumbTop = this.scrollbarThumbTop(layout, modules.size());
                int thumbHeight = this.scrollbarThumbHeight(layout, modules.size());
                this.scrollbarDragOffset = event.y() >= thumbTop && event.y() < thumbTop + thumbHeight
                        ? (int) event.y() - thumbTop
                        : thumbHeight / 2;
                this.setScrollFromScrollbar(event.y(), layout, modules.size());
                return true;
            }

            for (int index = 0; index < modules.size(); index++) {
                int row = index / layout.columns;
                int column = index % layout.columns;
                int left = layout.left + column * (layout.tileWidth + GRID_GAP);
                int top = Math.round(layout.top + (row - this.scrollPosition) * (TILE_HEIGHT + GRID_GAP));
                if (event.x() < left || event.x() >= left + layout.tileWidth
                        || event.y() < Math.max(top, layout.top)
                        || event.y() >= Math.min(top + TILE_HEIGHT, layout.bottom)) {
                    continue;
                }

                ClientModule module = modules.get(index);
                int switchX = left + layout.tileWidth - 47;
                int switchY = top + 12;
                if (event.x() >= switchX && event.x() < switchX + 35
                        && event.y() >= switchY && event.y() < switchY + 17) {
                    module.toggle();
                } else {
                    this.minecraft.gui.setScreen(module.createSettingsScreen(this));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.page == Page.HOME && this.capturedHudPress && event.buttonInfo().button() == 1) {
            HudLayoutManager.drag(event.x(), event.y(), this.width, this.height);
            return true;
        }
        if (this.page == Page.MODULES && this.scrollbarDragOffset >= 0
                && event.buttonInfo().button() == 1) {
            GridLayout layout = this.gridLayout();
            this.setScrollFromScrollbar(event.y(), layout, this.filteredModules().size());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.page == Page.HOME && this.capturedHudPress) {
            boolean movedHud = HudLayoutManager.endDrag();
            boolean clickMods = this.capturedPressOnModsButton && !movedHud;
            this.capturedHudPress = false;
            this.capturedPressOnModsButton = false;
            if (clickMods) this.changePage(Page.MODULES);
            return true;
        }
        if (this.scrollbarDragOffset >= 0) {
            this.scrollbarDragOffset = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (this.page == Page.MODULES && this.isInsideGrid(mouseX, mouseY)) {
            GridLayout layout = this.gridLayout();
            this.targetScrollPosition = Math.max(0.0f, Math.min(this.maxScroll(layout,
                    this.filteredModules().size()), this.targetScrollPosition - (float) verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void tick() {
        super.tick();
        this.scrollPosition += (this.targetScrollPosition - this.scrollPosition) * 0.34f;
        if (Math.abs(this.targetScrollPosition - this.scrollPosition) < 0.01f) {
            this.scrollPosition = this.targetScrollPosition;
        }
        if (this.page == Page.MODULES) {
            GridLayout layout = this.gridLayout();
            this.positionCardWidgets(layout, this.filteredModules());
        }
    }

    @Override
    public void onClose() {
        if (this.capturedHudPress) {
            HudLayoutManager.endDrag();
            this.capturedHudPress = false;
        }
        if (this.page == Page.MODULES) {
            this.changePage(Page.HOME);
        } else {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record GridLayout(int left, int top, int right, int bottom,
                              int columns, int tileWidth) {}
}
