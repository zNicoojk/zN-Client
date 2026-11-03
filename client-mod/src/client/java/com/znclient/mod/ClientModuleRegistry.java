
package com.znclient.mod;

import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Central registry used to populate the mod grid; registering a module adds its card automatically. */
public final class ClientModuleRegistry {

    private static final List<ClientModule> MODULES = new ArrayList<>();
    private static long revision;

    static {
        register(new ClientModule(
                "zoom",
                "screen.znclient.mods.card.zoom",
                "screen.znclient.mods.zoom.description",
                ClientModule.Icon.ZOOM,
                ZnClientModClient::isZoomEnabled,
                ZnClientModClient::toggleZoom,
                ZoomConfigScreen::new
        ));

        register(new ClientModule(
                "fps",
                "screen.znclient.mods.card.fps",
                "screen.znclient.mods.card.fps.description",
                ClientModule.Icon.FPS,
                ZnClientModClient::isFpsHudEnabled,
                ZnClientModClient::toggleFpsHud,
                parent -> new HudModuleConfigScreen(parent, "fps")
        ));

        register(new ClientModule(
                "ping",
                "screen.znclient.mods.card.ping",
                "screen.znclient.mods.card.ping.description",
                ClientModule.Icon.PING,
                ZnClientModClient::isPingHudEnabled,
                ZnClientModClient::togglePingHud,
                parent -> new HudModuleConfigScreen(parent, "ping")
        ));

        register(new ClientModule(
                "coordinates",
                "screen.znclient.mods.card.coordinates",
                "screen.znclient.mods.card.coordinates.description",
                ClientModule.Icon.COORDINATES,
                ZnClientModClient::isCoordinatesHudEnabled,
                ZnClientModClient::toggleCoordinatesHud,
                parent -> new HudModuleConfigScreen(parent, "coordinates")
        ));

        register(new ClientModule(
                "viewmodel",
                "screen.znclient.mods.card.viewmodel",
                "screen.znclient.mods.card.viewmodel.description",
                ClientModule.Icon.VIEWMODEL,
                ZnClientModClient::isViewModelEnabled,
                ZnClientModClient::toggleViewModel,
                ViewModelConfigScreen::new
        ));
    }

    private ClientModuleRegistry() {}

    public static synchronized void register(ClientModule module) {
        Objects.requireNonNull(module, "module");

        if (MODULES.stream().anyMatch(existing ->
                existing.id().equals(module.id()))) {
            throw new IllegalArgumentException(
                    "A zN Client module is already registered with id "
                            + module.id()
            );
        }

        MODULES.add(module);
        revision++;
    }

    public static synchronized List<ClientModule> modules() {
        return List.copyOf(MODULES);
    }

    public static synchronized @Nullable ClientModule get(String id) {
        Objects.requireNonNull(id, "id");

        return MODULES.stream()
                .filter(module -> module.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static synchronized long revision() {
        return revision;
    }

    public static Screen createSettings(
            String id,
            @Nullable Screen parent
    ) {
        ClientModule module = get(id);

        if (module == null) {
            throw new IllegalArgumentException(
                    "Unknown zN Client module: " + id
            );
        }

        return module.createSettingsScreen(parent);
    }
}
