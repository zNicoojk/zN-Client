package com.znclient.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

public final class ZnClientModClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("znclient", "client")
    );

    private static final KeyMapping OPEN_MENU = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.znclient.open_menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, CATEGORY)
    );
    private static final KeyMapping ZOOM = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.znclient.zoom", InputConstants.Type.KEYBOARD, 6, CATEGORY)
    );
    private static float previousZoomProgress;
    private static float zoomProgress;
    private static boolean zoomToggleActive;

    @Override
    public void onInitializeClient() {
        ClientSettings.load();
        ZOOM.setKey(InputConstants.getKey(ClientSettings.zoomKey));
        KeyMapping.resetMapping();
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("znclient", "info_hud"),
                InfoHudOverlay::extractRenderState
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    client.gui.setScreen(new ZnClientModsScreen(null));
                }
            }

            updateZoom(client);
        });
    }

    static boolean isZoomEnabled() {
        return ClientSettings.zoomEnabled;
    }

    static void toggleZoom() {
        ClientSettings.zoomEnabled = !ClientSettings.zoomEnabled;
        if (!ClientSettings.zoomEnabled) zoomToggleActive = false;
        ClientSettings.save();
    }

    static KeyMapping zoomKeyMapping() {
        return ZOOM;
    }

    static void setZoomKey(InputConstants.Key key) {
        ZOOM.setKey(key);
        ClientSettings.zoomKey = key.getName();
        KeyMapping.resetMapping();
        ClientSettings.save();
        Minecraft.getInstance().options.save();
    }

    static void resetZoomKey() {
        setZoomKey(ZOOM.getDefaultKey());
    }

    static boolean isZoomToggleMode() {
        return ClientSettings.zoomToggleMode;
    }

    static void toggleZoomMode() {
        ClientSettings.zoomToggleMode = !ClientSettings.zoomToggleMode;
        zoomToggleActive = false;
        ClientSettings.save();
    }

    static void setZoomTransitionTicks(int ticks) {
        ClientSettings.zoomTransitionTicks = Math.max(1, Math.min(16, ticks));
        ClientSettings.save();
    }

    static boolean isZoomAnimationEnabled() {
        return ClientSettings.zoomAnimationEnabled;
    }

    static void toggleZoomAnimation() {
        ClientSettings.zoomAnimationEnabled = !ClientSettings.zoomAnimationEnabled;
        ClientSettings.save();
    }

    public static boolean isZoomSlowCameraEnabled() {
        return ClientSettings.zoomSlowCameraEnabled;
    }

    static void toggleZoomSlowCamera() {
        ClientSettings.zoomSlowCameraEnabled = !ClientSettings.zoomSlowCameraEnabled;
        ClientSettings.save();
    }

    static boolean isFpsHudEnabled() {
        return HudModuleSettings.FPS.enabled;
    }

    static void toggleFpsHud() {
        HudModuleSettings.FPS.enabled = !HudModuleSettings.FPS.enabled;
        ClientSettings.save();
    }

    static boolean isPingHudEnabled() {
        return HudModuleSettings.PING.enabled;
    }

    static void togglePingHud() {
        HudModuleSettings.PING.enabled = !HudModuleSettings.PING.enabled;
        ClientSettings.save();
    }

    static boolean isCoordinatesHudEnabled() {
        return HudModuleSettings.COORDINATES.enabled;
    }

    static void toggleCoordinatesHud() {
        HudModuleSettings.COORDINATES.enabled = !HudModuleSettings.COORDINATES.enabled;
        ClientSettings.save();
    }

    static boolean isInfoHudEnabled() {
        return HudModuleSettings.FPS.enabled
                && HudModuleSettings.PING.enabled
                && HudModuleSettings.COORDINATES.enabled;
    }

    static boolean isViewModelEnabled() {
        return ClientSettings.viewModelEnabled;
    }

    static void toggleViewModel() {
        ClientSettings.viewModelEnabled = !ClientSettings.viewModelEnabled;
        ClientSettings.save();
    }

    public static void applyViewModel(PoseStack poseStack) {
        applyViewModel(poseStack, InteractionHand.MAIN_HAND);
    }

    public static void applyViewModel(PoseStack poseStack, InteractionHand hand) {
        if (!ClientSettings.viewModelEnabled) {
            return;
        }
        boolean mainHandIsLeft = Minecraft.getInstance().options.mainHand().get() == HumanoidArm.LEFT;
        boolean isLeft = hand == InteractionHand.MAIN_HAND ? mainHandIsLeft : !mainHandIsLeft;
        float horizontal = isLeft ? ClientSettings.viewModelLeftHorizontal : ClientSettings.viewModelRightHorizontal;
        float height = isLeft ? ClientSettings.viewModelLeftHeight : ClientSettings.viewModelRightHeight;
        float depth = isLeft ? ClientSettings.viewModelLeftDepth : ClientSettings.viewModelRightDepth;
        float scale = isLeft ? ClientSettings.viewModelLeftScale : ClientSettings.viewModelRightScale;
        float rotationX = isLeft ? ClientSettings.viewModelLeftRotationX : ClientSettings.viewModelRightRotationX;
        float rotationY = isLeft ? ClientSettings.viewModelLeftRotationY : ClientSettings.viewModelRightRotationY;
        float rotationZ = isLeft ? ClientSettings.viewModelLeftRotationZ : ClientSettings.viewModelRightRotationZ;
        poseStack.translate(
                horizontal * 0.35f,
                height * 0.35f,
                depth * 0.35f
        );
        poseStack.scale(scale, scale, scale);
        poseStack.rotateDegrees(Axis.XP, rotationX);
        poseStack.rotateDegrees(Axis.YP, rotationY);
        poseStack.rotateDegrees(Axis.ZP, rotationZ);
    }

    static void toggleInfoHud() {
        boolean enable = !isInfoHudEnabled();
        HudModuleSettings.FPS.enabled = enable;
        HudModuleSettings.PING.enabled = enable;
        HudModuleSettings.COORDINATES.enabled = enable;
        ClientSettings.save();
    }

    public static float zoomMagnification() {
        return ClientSettings.zoomMagnification;
    }

    public static float zoomProgress() {
        return zoomProgress;
    }

    public static float zoomProgress(float partialTick) {
        float interpolation = Math.max(0.0f, Math.min(1.0f, partialTick));
        return previousZoomProgress + (zoomProgress - previousZoomProgress) * interpolation;
    }

    static void setZoomMagnification(float magnification, boolean save) {
        ClientSettings.zoomMagnification = Math.max(1.0f, Math.min(8.0f, Math.round(magnification * 10.0f) / 10.0f));
        if (save) ClientSettings.save();
    }

    private static void updateZoom(Minecraft client) {
        previousZoomProgress = zoomProgress;

        while (ZOOM.consumeClick()) {
            if (ClientSettings.zoomToggleMode && ClientSettings.zoomEnabled
                    && client.player != null && client.gui.screen() == null) {
                zoomToggleActive = !zoomToggleActive;
            }
        }
        if (client.player == null || !ClientSettings.zoomEnabled) zoomToggleActive = false;

        boolean shouldZoom = ClientSettings.zoomEnabled
                && ClientSettings.zoomMagnification > 1.0f
                && client.player != null
                && client.gui.screen() == null
                && (ClientSettings.zoomToggleMode ? zoomToggleActive : ZOOM.isDown());

        float target = shouldZoom ? 1.0f : 0.0f;
        if (!ClientSettings.zoomAnimationEnabled) {
            zoomProgress = target;
            previousZoomProgress = target;
        } else {
            float difference = target - zoomProgress;
            if (difference != 0.0f) {
                float step = 1.0f / ClientSettings.zoomTransitionTicks;
                zoomProgress += Math.copySign(Math.min(Math.abs(difference), step), difference);
            }
        }
        zoomProgress = Math.max(0.0f, Math.min(1.0f, zoomProgress));
    }
}
