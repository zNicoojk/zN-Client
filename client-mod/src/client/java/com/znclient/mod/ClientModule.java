
package com.znclient.mod;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** A registered, independently configurable client module. */
public final class ClientModule {
    public enum Icon { ZOOM, FPS, PING, COORDINATES, VIEWMODEL }

    private final @NonNull String id;
    private final @NonNull String titleKey;
    private final @NonNull String descriptionKey;
    private final @NonNull Icon icon;
    private final @NonNull BooleanSupplier enabled;
    private final @NonNull Runnable toggle;
    private final @NonNull Function<@Nullable Screen, Screen> settingsScreen;

    public ClientModule(
            @NonNull String id,
            @NonNull String titleKey,
            @NonNull String descriptionKey,
            @NonNull Icon icon,
            @NonNull BooleanSupplier enabled,
            @NonNull Runnable toggle,
            @NonNull Function<@Nullable Screen, Screen> settingsScreen
    ) {
        this.id = Objects.requireNonNull(id);
        this.titleKey = Objects.requireNonNull(titleKey);
        this.descriptionKey = Objects.requireNonNull(descriptionKey);
        this.icon = Objects.requireNonNull(icon);
        this.enabled = Objects.requireNonNull(enabled);
        this.toggle = Objects.requireNonNull(toggle);
        this.settingsScreen = Objects.requireNonNull(settingsScreen);
    }

    public @NonNull String id() {
        return this.id;
    }

    public @NonNull Icon icon() {
        return this.icon;
    }

    public @NonNull Component title() {
        return Component.translatable(this.titleKey);
    }

    public @NonNull Component description() {
        return Component.translatable(this.descriptionKey);
    }

    public boolean enabled() {
        return this.enabled.getAsBoolean();
    }

    public void toggle() {
        this.toggle.run();
    }

    public @NonNull Screen createSettingsScreen(@Nullable Screen parent) {
        return Objects.requireNonNull(this.settingsScreen.apply(parent));
    }
}
