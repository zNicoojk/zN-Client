package com.znclient.mod;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

final class ClientSettings {
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("znclient.properties");

    static boolean zoomEnabled = true;
    static float zoomMagnification = 3.0f;
    static boolean zoomAnimationEnabled = true;
    static boolean zoomSlowCameraEnabled = true;
    static boolean zoomToggleMode;
    static int zoomTransitionTicks = 6;
    static String zoomKey = "key.keyboard.c";
    static boolean hudEditorGuides = true;
    static boolean hudEditorSnap;
    static boolean viewModelEnabled = true;
    static float viewModelHeight;
    static float viewModelHorizontal;
    static float viewModelDepth;
    static float viewModelScale = 1.0f;
    static float viewModelLeftHeight;
    static float viewModelLeftHorizontal;
    static float viewModelLeftDepth;
    static float viewModelLeftScale = 1.0f;
    static float viewModelLeftRotationX;
    static float viewModelLeftRotationY;
    static float viewModelLeftRotationZ;
    static float viewModelRightHeight;
    static float viewModelRightHorizontal;
    static float viewModelRightDepth;
    static float viewModelRightScale = 1.0f;
    static float viewModelRightRotationX;
    static float viewModelRightRotationY;
    static float viewModelRightRotationZ;

    private static final List<HudModuleSettings> HUD_MODULES = HudModuleSettings.ALL;

    private ClientSettings() {}

    static void load() {
        if (!Files.exists(CONFIG_FILE)) return;

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("[zN Client] Could not read settings: " + error.getMessage());
            return;
        }

        zoomEnabled = booleanValue(properties, "zoom.enabled", true);
        zoomMagnification = floatValue(properties, "zoom.magnification", 3.0f, 1.0f, 8.0f);
        zoomAnimationEnabled = booleanValue(properties, "zoom.animation", true);
        zoomSlowCameraEnabled = booleanValue(properties, "zoom.slow_camera", true);
        zoomToggleMode = booleanValue(properties, "zoom.toggle_mode", false);
        zoomTransitionTicks = intValue(properties, "zoom.transition_ticks", 6, 1, 16);
        zoomKey = properties.getProperty("zoom.key", zoomKey).trim();
        if (zoomKey.isEmpty()) zoomKey = "key.keyboard.c";

        String legacyInfoHud = properties.getProperty("hud.info.enabled", "true");
        HudModuleSettings.FPS.load(properties, Boolean.parseBoolean(legacyInfoHud));
        HudModuleSettings.PING.load(properties, Boolean.parseBoolean(legacyInfoHud));
        HudModuleSettings.COORDINATES.load(properties, Boolean.parseBoolean(legacyInfoHud));
        hudEditorGuides = booleanValue(properties, "hud.editor.guides", true);
        hudEditorSnap = booleanValue(properties, "hud.editor.snap", false);
        viewModelEnabled = booleanValue(properties, "viewmodel.enabled", true);
        viewModelHeight = floatValue(properties, "viewmodel.height", 0.0f, -1.0f, 1.0f);
        viewModelHorizontal = floatValue(properties, "viewmodel.horizontal", 0.0f, -1.0f, 1.0f);
        viewModelDepth = floatValue(properties, "viewmodel.depth", 0.0f, -1.0f, 1.0f);
        viewModelScale = floatValue(properties, "viewmodel.scale", 1.0f, 0.5f, 1.5f);
        viewModelLeftHeight = floatValue(properties, "viewmodel.left.height", viewModelHeight, -1.0f, 1.0f);
        viewModelLeftHorizontal = floatValue(properties, "viewmodel.left.horizontal", viewModelHorizontal, -1.0f, 1.0f);
        viewModelLeftDepth = floatValue(properties, "viewmodel.left.depth", viewModelDepth, -1.0f, 1.0f);
        viewModelLeftScale = floatValue(properties, "viewmodel.left.scale", viewModelScale, 0.5f, 1.5f);
        viewModelLeftRotationX = floatValue(properties, "viewmodel.left.rotation_x", 0.0f, -180.0f, 180.0f);
        viewModelLeftRotationY = floatValue(properties, "viewmodel.left.rotation_y", 0.0f, -180.0f, 180.0f);
        viewModelLeftRotationZ = floatValue(properties, "viewmodel.left.rotation_z", 0.0f, -180.0f, 180.0f);
        viewModelRightHeight = floatValue(properties, "viewmodel.right.height", viewModelHeight, -1.0f, 1.0f);
        viewModelRightHorizontal = floatValue(properties, "viewmodel.right.horizontal", viewModelHorizontal, -1.0f, 1.0f);
        viewModelRightDepth = floatValue(properties, "viewmodel.right.depth", viewModelDepth, -1.0f, 1.0f);
        viewModelRightScale = floatValue(properties, "viewmodel.right.scale", viewModelScale, 0.5f, 1.5f);
        viewModelRightRotationX = floatValue(properties, "viewmodel.right.rotation_x", 0.0f, -180.0f, 180.0f);
        viewModelRightRotationY = floatValue(properties, "viewmodel.right.rotation_y", 0.0f, -180.0f, 180.0f);
        viewModelRightRotationZ = floatValue(properties, "viewmodel.right.rotation_z", 0.0f, -180.0f, 180.0f);
    }

    static void save() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                properties.load(reader);
            } catch (IOException | IllegalArgumentException error) {
                System.err.println("[zN Client] Settings were not saved to protect existing preferences: "
                        + error.getMessage());
                return;
            }
        }

        properties.setProperty("zoom.enabled", Boolean.toString(zoomEnabled));
        properties.setProperty("zoom.magnification", Float.toString(zoomMagnification));
        properties.setProperty("zoom.animation", Boolean.toString(zoomAnimationEnabled));
        properties.setProperty("zoom.slow_camera", Boolean.toString(zoomSlowCameraEnabled));
        properties.setProperty("zoom.toggle_mode", Boolean.toString(zoomToggleMode));
        properties.setProperty("zoom.transition_ticks", Integer.toString(zoomTransitionTicks));
        properties.setProperty("zoom.key", zoomKey);
        for (HudModuleSettings module : HUD_MODULES) module.save(properties);
        properties.setProperty("hud.editor.guides", Boolean.toString(hudEditorGuides));
        properties.setProperty("hud.editor.snap", Boolean.toString(hudEditorSnap));
        properties.setProperty("viewmodel.enabled", Boolean.toString(viewModelEnabled));
        properties.setProperty("viewmodel.height", Float.toString(viewModelHeight));
        properties.setProperty("viewmodel.horizontal", Float.toString(viewModelHorizontal));
        properties.setProperty("viewmodel.depth", Float.toString(viewModelDepth));
        properties.setProperty("viewmodel.scale", Float.toString(viewModelScale));
        properties.setProperty("viewmodel.left.height", Float.toString(viewModelLeftHeight));
        properties.setProperty("viewmodel.left.horizontal", Float.toString(viewModelLeftHorizontal));
        properties.setProperty("viewmodel.left.depth", Float.toString(viewModelLeftDepth));
        properties.setProperty("viewmodel.left.scale", Float.toString(viewModelLeftScale));
        properties.setProperty("viewmodel.left.rotation_x", Float.toString(viewModelLeftRotationX));
        properties.setProperty("viewmodel.left.rotation_y", Float.toString(viewModelLeftRotationY));
        properties.setProperty("viewmodel.left.rotation_z", Float.toString(viewModelLeftRotationZ));
        properties.setProperty("viewmodel.right.height", Float.toString(viewModelRightHeight));
        properties.setProperty("viewmodel.right.horizontal", Float.toString(viewModelRightHorizontal));
        properties.setProperty("viewmodel.right.depth", Float.toString(viewModelRightDepth));
        properties.setProperty("viewmodel.right.scale", Float.toString(viewModelRightScale));
        properties.setProperty("viewmodel.right.rotation_x", Float.toString(viewModelRightRotationX));
        properties.setProperty("viewmodel.right.rotation_y", Float.toString(viewModelRightRotationY));
        properties.setProperty("viewmodel.right.rotation_z", Float.toString(viewModelRightRotationZ));

        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                properties.store(writer, "zN Client mod settings");
            }
        } catch (IOException error) {
            System.err.println("[zN Client] Could not save settings: " + error.getMessage());
        }
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
}
