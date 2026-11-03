package com.znclient.mod;

import java.util.List;
import java.util.Properties;

/** Persisted visual options shared by HUD modules; position values are screen-relative. */
final class HudModuleSettings {
    static final HudModuleSettings FPS = new HudModuleSettings("fps", 0.02f, 0.02f, 12, 12);
    static final HudModuleSettings PING = new HudModuleSettings("ping", 0.02f, 0.02f, 12, 36);
    static final HudModuleSettings COORDINATES = new HudModuleSettings("coordinates", 0.02f, 0.02f, 12, 60);
    static final List<HudModuleSettings> ALL = List.of(FPS, PING, COORDINATES);

    final String id;
    final float defaultX;
    final float defaultY;
    final int defaultLeft;
    final int defaultTop;
    boolean enabled = true;
    boolean positionSaved;
    float x;
    float y;
    float scale = 1.0f;
    int color;
    boolean background = true;
    int opacity = 100;
    int format;
    boolean showX = true;
    boolean showY = true;
    boolean showZ = true;
    int coordinateFormat;
    int legacyCorner;

    private HudModuleSettings(String id, float defaultX, float defaultY, int defaultLeft, int defaultTop) {
        this.id = id;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.defaultLeft = defaultLeft;
        this.defaultTop = defaultTop;
        this.x = defaultX;
        this.y = defaultY;
    }

    void load(Properties properties, boolean enabledFallback) {
        String prefix = "hud." + this.id + ".";
        this.enabled = booleanValue(properties, prefix + "enabled", enabledFallback);
        this.positionSaved = properties.containsKey(prefix + "x") && properties.containsKey(prefix + "y");
        this.x = floatValue(properties, prefix + "x", this.defaultX, 0.0f, 1.0f);
        this.y = floatValue(properties, prefix + "y", this.defaultY, 0.0f, 1.0f);
        this.scale = floatValue(properties, prefix + "scale", 1.0f, 0.5f, 2.0f);
        this.color = intValue(properties, prefix + "color", 0, 0, 5);
        int legacyStyle = intValue(properties, prefix + "style", 0, 0, 2);
        this.background = booleanValue(properties, prefix + "background", legacyStyle != 1);
        this.opacity = intValue(properties, prefix + "opacity", 100, 0, 100);
        this.format = intValue(properties, prefix + "format", 0, 0, 2);
        if (this.id.equals("coordinates")) {
            this.showX = booleanValue(properties, prefix + "show_x", true);
            this.showY = booleanValue(properties, prefix + "show_y", true);
            this.showZ = booleanValue(properties, prefix + "show_z", true);
            this.coordinateFormat = intValue(properties, prefix + "coordinate_format", 2, 0, 2);
        }
        if (this.id.equals("fps")) this.legacyCorner = intValue(properties, prefix + "position", 0, 0, 3);
    }

    void save(Properties properties) {
        String prefix = "hud." + this.id + ".";
        properties.setProperty(prefix + "enabled", Boolean.toString(this.enabled));
        if (this.positionSaved) {
            properties.setProperty(prefix + "x", Float.toString(this.x));
            properties.setProperty(prefix + "y", Float.toString(this.y));
        } else {
            properties.remove(prefix + "x");
            properties.remove(prefix + "y");
        }
        properties.setProperty(prefix + "scale", Float.toString(this.scale));
        properties.setProperty(prefix + "color", Integer.toString(this.color));
        properties.remove(prefix + "style");
        properties.setProperty(prefix + "background", Boolean.toString(this.background));
        properties.setProperty(prefix + "opacity", Integer.toString(this.opacity));
        properties.setProperty(prefix + "format", Integer.toString(this.format));
        if (this.id.equals("coordinates")) {
            properties.setProperty(prefix + "show_x", Boolean.toString(this.showX));
            properties.setProperty(prefix + "show_y", Boolean.toString(this.showY));
            properties.setProperty(prefix + "show_z", Boolean.toString(this.showZ));
            properties.setProperty(prefix + "coordinate_format", Integer.toString(this.coordinateFormat));
        }
        if (this.id.equals("fps")) {
            properties.setProperty(prefix + "position", Integer.toString(this.legacyCorner));
        }
    }

    void restoreDefaults() {
        this.enabled = true;
        this.positionSaved = false;
        this.x = this.defaultX;
        this.y = this.defaultY;
        this.scale = 1.0f;
        this.color = 0;
        this.background = true;
        this.opacity = 100;
        this.format = 0;
        this.showX = true;
        this.showY = true;
        this.showZ = true;
        this.coordinateFormat = 2;
        this.legacyCorner = 0;
    }

    int left(int maxLeft, int width) {
        if (this.positionSaved) return clamp(Math.round(this.x * maxLeft), 0, maxLeft);
        if (this.id.equals("fps") && (this.legacyCorner == 1 || this.legacyCorner == 3)) return maxLeft;
        return clamp(this.defaultLeft, 0, maxLeft);
    }

    int top(int maxTop) {
        if (this.positionSaved) return clamp(Math.round(this.y * maxTop), 0, maxTop);
        if (this.id.equals("fps") && (this.legacyCorner == 2 || this.legacyCorner == 3)) return maxTop;
        return clamp(this.defaultTop, 0, maxTop);
    }

    void moveTo(int left, int top, int maxLeft, int maxTop) {
        this.x = maxLeft <= 0 ? 0.0f : clamp(left, 0, maxLeft) / (float) maxLeft;
        this.y = maxTop <= 0 ? 0.0f : clamp(top, 0, maxTop) / (float) maxTop;
        this.positionSaved = true;
    }

    private static boolean booleanValue(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static float floatValue(Properties properties, String key, float fallback, float minimum, float maximum) {
        try {
            float value = Float.parseFloat(properties.getProperty(key, Float.toString(fallback)));
            return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int intValue(Properties properties, String key, int fallback, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
