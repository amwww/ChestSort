package dev.dromer.chestsort.client;

import dev.dromer.chestsort.net.payload.ContainerContextPayload;
import dev.dromer.chestsort.net.payload.ContainerContextV2Payload;
import dev.dromer.chestsort.net.payload.ContainerContextV3Payload;
import dev.dromer.chestsort.net.payload.ContainerHighlightPayload;
import dev.dromer.chestsort.net.payload.FindHighlightsPayload;
import dev.dromer.chestsort.net.payload.LockedSlotsSyncPayload;
import dev.dromer.chestsort.net.payload.OpenPresetUiPayload;
import dev.dromer.chestsort.net.payload.PresetSyncPayload;
import dev.dromer.chestsort.net.payload.PresetSyncV2Payload;
import dev.dromer.chestsort.net.payload.SortResultPayload;
import dev.dromer.chestsort.net.payload.WandSelectionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ChestsortClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ContainerHighlightPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientHighlightState.set(payload.itemId(), payload.highlight()));
        });

        ClientPlayNetworking.registerGlobalReceiver(FindHighlightsPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientFindHighlightState.set(payload.dimensionId(), payload.itemId(), payload.posLongs()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ContainerContextPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientContainerContext.set(payload.dimensionId(), payload.posLong(), payload.containerType(), payload.filterItems()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ContainerContextV2Payload.ID, (payload, context) -> {
            context.client().execute(() -> ClientContainerContext.set(payload.dimensionId(), payload.posLong(), payload.containerType(), payload.filter()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ContainerContextV3Payload.ID, (payload, context) -> {
            context.client().execute(() -> ClientContainerContext.set(
                payload.dimensionId(),
                payload.posLong(),
                payload.containerType(),
                payload.whitelist(),
                payload.blacklist(),
                payload.whitelistPriority()
            ));
        });

        ClientPlayNetworking.registerGlobalReceiver(PresetSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientPresetRegistry.setFromSync(payload.names(), payload.specs()));
        });

        ClientPlayNetworking.registerGlobalReceiver(PresetSyncV2Payload.ID, (payload, context) -> {
            context.client().execute(() -> ClientPresetRegistry.setFromSyncV2(payload.names(), payload.whitelists(), payload.blacklists()));
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenPresetUiPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                var client = context.client();
                byte mode = payload.mode();
                String name = payload.name();

                boolean inHandledContainer = (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>)
                    && ClientContainerContext.isChestOrBarrel();

                if (mode == OpenPresetUiPayload.MODE_IMPORT) {
                    client.setScreen(new dev.dromer.chestsort.client.gui.PresetTransferScreen(
                        dev.dromer.chestsort.client.gui.PresetTransferScreen.Mode.IMPORT,
                        ""
                    ));
                    return;
                }

                if (mode == OpenPresetUiPayload.MODE_EXPORT) {
                    client.setScreen(new dev.dromer.chestsort.client.gui.PresetTransferScreen(
                        dev.dromer.chestsort.client.gui.PresetTransferScreen.Mode.EXPORT,
                        name
                    ));
                    return;
                }

                if (mode == OpenPresetUiPayload.MODE_EXPORT_ALL) {
                    client.setScreen(new dev.dromer.chestsort.client.gui.PresetTransferScreen(
                        dev.dromer.chestsort.client.gui.PresetTransferScreen.Mode.EXPORT_ALL,
                        ""
                    ));
                    return;
                }

                if (mode == OpenPresetUiPayload.MODE_EXPORT_SELECT) {
                    client.setScreen(dev.dromer.chestsort.client.gui.PresetListTransferScreen.forExportSelect());
                    return;
                }

                if (mode == OpenPresetUiPayload.MODE_EDIT && !inHandledContainer) {
                    client.setScreen(new dev.dromer.chestsort.client.gui.PresetEditorScreen(name));
                    return;
                }

                ClientPresetRegistry.requestOpen(mode, name);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SortResultPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientSortNotificationState.set(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(WandSelectionPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientWandSelectionState.set(
                payload.wandItemId(),
                payload.hasPos1(),
                payload.pos1DimensionId(),
                payload.pos1Long(),
                payload.hasPos2(),
                payload.pos2DimensionId(),
                payload.pos2Long(),
                payload.blockCount(),
                payload.containerCount()
            ));
        });

        ClientPlayNetworking.registerGlobalReceiver(LockedSlotsSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientLockedSlotsState.setFromSync(payload.lockedSlots()));
        });
    }
}
