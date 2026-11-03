package com.znclient.mod;

import org.jspecify.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;

import java.util.Objects;

/** Measures, draws, selects and repositions the client HUD elements. */
final class HudLayoutManager {
    private static final int HEIGHT = 20;
    private static final int BACKGROUND = 0xFF000000;
    private static final int[] COLORS = {
            0xFF39FF14, 0xFFF5F5F5, 0xFF38BDF8, 0xFFF59E0B, 0xFFF87171, 0xFFC084FC
    };
    private static final Entry[] ENTRIES = HudModuleSettings.ALL.stream()
            .map(Entry::new)
            .toArray(Entry[]::new);

    private static @Nullable Entry hovered;
    private static @Nullable Entry dragCandidate;
    private static double dragOffsetX;
    private static double dragOffsetY;
    private static double dragStartX;
    private static double dragStartY;
    private static boolean dragging;
    private static boolean snappedLeft;
    private static boolean snappedRight;
    private static boolean snappedTop;
    private static boolean snappedBottom;

    private HudLayoutManager() {}

    static void renderGame(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() != null) return;
        render(graphics, client, graphics.guiWidth(), graphics.guiHeight(), -1, -1, false);
    }

    static void renderEditor(GuiGraphicsExtractor graphics, int width, int height, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        render(graphics, client, width, height, mouseX, mouseY, true);
    }

    private static void render(GuiGraphicsExtractor graphics, Minecraft client, int width, int height,
                               int mouseX, int mouseY, boolean editing) {
        hovered = null;
        for (Entry entry : ENTRIES) {
            HudModuleSettings settings = entry.settings;
            entry.text = settings.enabled ? textFor(settings, client) : "";
            if (entry.text.isEmpty()) {
                entry.visible = false;
                continue;
            }

            entry.scale = Math.max(0.5f, Math.min(2.0f, settings.scale));
            entry.contentWidth = Math.max(16, textWidth(client.font, entry.text) + 16);
            entry.contentHeight = contentHeight(client.font, entry.text);
            entry.width = Math.max(1, Math.round(entry.contentWidth * entry.scale));
            entry.height = Math.max(1, Math.round(entry.contentHeight * entry.scale));
            int maxLeft = Math.max(0, width - entry.width);
            int maxTop = Math.max(0, height - entry.height);
            entry.left = settings.left(maxLeft, entry.width);
            entry.top = settings.top(maxTop);
            entry.visible = true;

            graphics.pose().pushMatrix();
            graphics.pose().translate(entry.left, entry.top);
            graphics.pose().scale(entry.scale, entry.scale);
            drawContents(graphics, client, entry);
            graphics.pose().popMatrix();

            if (editing && contains(entry, mouseX, mouseY)) hovered = entry;
        }

        if (editing) drawEditorDecoration(graphics, width, height, mouseX, mouseY);
    }

    private static void drawContents(GuiGraphicsExtractor graphics, Minecraft client, Entry entry) {
        drawLabel(graphics, client.font, entry.settings, entry.text,
                entry.contentWidth, entry.contentHeight);
    }

    static void drawPreview(GuiGraphicsExtractor graphics, Font font, HudModuleSettings settings,
                            int x, int y, int maxWidth, int maxHeight) {
        String text = previewText(settings, Minecraft.getInstance());
        if (text.isEmpty() || maxWidth <= 0 || maxHeight <= 0) return;

        int contentWidth = Math.max(16, textWidth(font, text) + 16);
        int contentHeight = contentHeight(font, text);
        float scale = Math.min(settings.scale,
                Math.min(maxWidth / (float) contentWidth, maxHeight / (float) contentHeight));
        if (!Float.isFinite(scale) || scale <= 0.0f) return;
        int renderedWidth = Math.round(contentWidth * scale);
        int renderedHeight = Math.round(contentHeight * scale);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + (maxWidth - renderedWidth) / 2.0f,
                y + (maxHeight - renderedHeight) / 2.0f);
        graphics.pose().scale(scale, scale);
        drawLabel(graphics, font, settings, text, contentWidth, contentHeight);
        graphics.pose().popMatrix();
    }

    private static void drawLabel(GuiGraphicsExtractor graphics, Font font, HudModuleSettings settings,
                                  String text, int contentWidth, int contentHeight) {
        int accent = COLORS[Math.max(0, Math.min(COLORS.length - 1, settings.color))];
        if (settings.background) {
            int alpha = Math.round(settings.opacity * 2.55f);
            graphics.fill(0, 0, contentWidth, contentHeight, (BACKGROUND & 0x00FFFFFF) | (alpha << 24));
        }
        String[] lines = text.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            graphics.text(font, lines[index], 6, 6 + index * font.lineHeight, accent, true);
        }
    }

    private static int textWidth(Font font, String text) {
        int width = 0;
        for (String line : text.split("\\n", -1)) {
            width = Math.max(width, font.width(Objects.requireNonNull(line)));
        }
        return width;
    }

    private static int contentHeight(Font font, String text) {
        int lineCount = text.split("\\n", -1).length;
        return HEIGHT + Math.max(0, lineCount - 1) * font.lineHeight;
    }

    private static String previewText(HudModuleSettings settings, Minecraft client) {
        return switch (settings.id) {
            case "fps" -> fpsText(settings, client.getFps());
            case "ping" -> {
                int currentPing = ping(client);
                yield pingText(settings, currentPing < 0 ? 42 : currentPing);
            }
            case "coordinates" -> client.player != null
                    ? coordinates(client)
                    : coordinatePreview(settings);
            default -> "";
        };
    }

    private static String coordinatePreview(HudModuleSettings settings) {
        if (settings.coordinateFormat == 0 && settings.showX && settings.showY && settings.showZ) {
            return "XYZ 128, 64, -32";
        }
        StringBuilder result = new StringBuilder(36);
        String separator = settings.coordinateFormat == 2 ? "\n" : "  ";
        appendCoordinate(result, settings.showX, settings.coordinateFormat == 0 ? "X" : "X:", 128, separator);
        appendCoordinate(result, settings.showY, settings.coordinateFormat == 0 ? "Y" : "Y:", 64, separator);
        appendCoordinate(result, settings.showZ, settings.coordinateFormat == 0 ? "Z" : "Z:", -32, separator);
        return result.toString().trim();
    }

    private static void drawEditorDecoration(GuiGraphicsExtractor graphics, int width, int height,
                                             int mouseX, int mouseY) {
        Entry selected = dragging && dragCandidate != null ? dragCandidate : hovered;
        if (selected != null && selected.visible) {
            int color = dragging ? 0xFF39FF14 : 0xD839FF14;
            graphics.outline(selected.left - 2, selected.top - 2,
                    selected.width + 4, selected.height + 4, color);
        }
        if (!ClientSettings.hudEditorGuides || !dragging || dragCandidate == null) return;
        int guide = 0xA039FF14;
        if (snappedLeft || snappedRight) {
            int x = snappedRight ? width - 1 : 0;
            graphics.fill(x, 0, x + 1, height, guide);
        }
        if (snappedTop || snappedBottom) {
            int y = snappedBottom ? height - 1 : 0;
            graphics.fill(0, y, width, y + 1, guide);
        }
    }

    static boolean beginDrag(double mouseX, double mouseY) {
        Entry entry = hitTest(mouseX, mouseY);
        if (entry == null) return false;
        dragCandidate = entry;
        dragOffsetX = mouseX - entry.left;
        dragOffsetY = mouseY - entry.top;
        dragStartX = mouseX;
        dragStartY = mouseY;
        dragging = false;
        clearSnapState();
        return true;
    }

    static boolean drag(double mouseX, double mouseY, int width, int height) {
        Entry entry = dragCandidate;
        if (entry == null) return false;
        if (!dragging && Math.hypot(mouseX - dragStartX, mouseY - dragStartY) < 2.0) return true;
        dragging = true;
        int maxLeft = Math.max(0, width - entry.width);
        int maxTop = Math.max(0, height - entry.height);
        int left = clamp((int) Math.round(mouseX - dragOffsetX), 0, maxLeft);
        int top = clamp((int) Math.round(mouseY - dragOffsetY), 0, maxTop);
        clearSnapState();
        if (ClientSettings.hudEditorSnap) {
            if (left <= 8) { left = 0; snappedLeft = true; }
            else if (maxLeft - left <= 8) { left = maxLeft; snappedRight = true; }
            if (top <= 8) { top = 0; snappedTop = true; }
            else if (maxTop - top <= 8) { top = maxTop; snappedBottom = true; }
        }
        entry.left = left;
        entry.top = top;
        entry.settings.moveTo(left, top, maxLeft, maxTop);
        return true;
    }

    static boolean endDrag() {
        if (dragCandidate == null) return false;
        boolean moved = dragging;
        dragCandidate = null;
        dragging = false;
        clearSnapState();
        if (moved) ClientSettings.save();
        return moved;
    }

    static boolean isDragging() {
        return dragging;
    }

    private static @Nullable Entry hitTest(double mouseX, double mouseY) {
        // Reverse render order so overlapping HUD items select the one visually on top.
        for (int index = ENTRIES.length - 1; index >= 0; index--) {
            Entry entry = ENTRIES[index];
            if (entry.visible && contains(entry, mouseX, mouseY)) return entry;
        }
        return null;
    }

    private static boolean contains(Entry entry, double x, double y) {
        return x >= entry.left - 2 && x < entry.left + entry.width + 2
                && y >= entry.top - 2 && y < entry.top + entry.height + 2;
    }

    private static String textFor(HudModuleSettings settings, Minecraft client) {
        return switch (settings.id) {
            case "fps" -> fpsText(settings, client.getFps());
            case "ping" -> pingText(settings, ping(client));
            case "coordinates" -> coordinates(client);
            default -> "";
        };
    }

    private static String fpsText(HudModuleSettings settings, int fps) {
        return switch (settings.format) {
            case 1 -> fps + " FPS";
            case 2 -> Integer.toString(fps);
            default -> "FPS " + fps;
        };
    }

    private static String pingText(HudModuleSettings settings, int ping) {
        if (ping < 0) return settings.format == 2 ? "Ping: N/A" : "PING N/A";
        return switch (settings.format) {
            case 1 -> ping + " ms";
            case 2 -> "Ping: " + ping + " ms";
            default -> "PING " + ping + " ms";
        };
    }

    private static int ping(Minecraft client) {
        var player = client.player;
        var connection = client.getConnection();
        if (player == null || connection == null) return -1;
        PlayerInfo info = connection.getPlayerInfo(player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    private static String coordinates(Minecraft client) {
        if (client.player == null) return "";
        BlockPos position = client.player.blockPosition();
        HudModuleSettings settings = HudModuleSettings.COORDINATES;
        if (settings.coordinateFormat == 0 && settings.showX && settings.showY && settings.showZ) {
            return "XYZ " + position.getX() + ", " + position.getY() + ", " + position.getZ();
        }
        StringBuilder result = new StringBuilder(36);
        String separator = settings.coordinateFormat == 2 ? "\n" : "  ";
        appendCoordinate(result, settings.showX, settings.coordinateFormat == 0 ? "X" : "X:", position.getX(), separator);
        appendCoordinate(result, settings.showY, settings.coordinateFormat == 0 ? "Y" : "Y:", position.getY(), separator);
        appendCoordinate(result, settings.showZ, settings.coordinateFormat == 0 ? "Z" : "Z:", position.getZ(), separator);
        return result.toString().trim();
    }

    private static void appendCoordinate(StringBuilder result, boolean enabled, String label, int value,
                                         String separator) {
        if (!enabled) return;
        if (!result.isEmpty()) result.append(separator);
        result.append(label).append(' ').append(value);
    }

    private static void clearSnapState() {
        snappedLeft = false;
        snappedRight = false;
        snappedTop = false;
        snappedBottom = false;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Entry {
        private final HudModuleSettings settings;
        private String text = "";
        private float scale = 1.0f;
        private int contentWidth;
        private int contentHeight;
        private int left;
        private int top;
        private int width;
        private int height;
        private boolean visible;

        private Entry(HudModuleSettings settings) {
            this.settings = settings;
        }
    }
}
