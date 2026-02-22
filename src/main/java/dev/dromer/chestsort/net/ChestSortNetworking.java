package dev.dromer.chestsort.net;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.dromer.chestsort.data.ChestSortState;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;
import dev.dromer.chestsort.net.payload.ContainerContextPayload;
import dev.dromer.chestsort.net.payload.ContainerContextV2Payload;
import dev.dromer.chestsort.net.payload.ContainerContextV3Payload;
import dev.dromer.chestsort.net.payload.ContainerHighlightPayload;
import dev.dromer.chestsort.net.payload.FindHighlightsPayload;
import dev.dromer.chestsort.net.payload.ImportPresetListPayload;
import dev.dromer.chestsort.net.payload.ImportPresetPayload;
import dev.dromer.chestsort.net.payload.LockedSlotsSyncPayload;
import dev.dromer.chestsort.net.payload.OpenPresetUiPayload;
import dev.dromer.chestsort.net.payload.OrganizeRequestPayload;
import dev.dromer.chestsort.net.payload.PresetSyncPayload;
import dev.dromer.chestsort.net.payload.PresetSyncV2Payload;
import dev.dromer.chestsort.net.payload.SetContainerFiltersPayload;
import dev.dromer.chestsort.net.payload.SetFilterPayload;
import dev.dromer.chestsort.net.payload.SetFilterV2Payload;
import dev.dromer.chestsort.net.payload.SetPresetPayload;
import dev.dromer.chestsort.net.payload.SetPresetV2Payload;
import dev.dromer.chestsort.net.payload.SortRequestPayload;
import dev.dromer.chestsort.net.payload.SortResultPayload;
import dev.dromer.chestsort.net.payload.ToggleLockedSlotPayload;
import dev.dromer.chestsort.net.payload.UndoSortPayload;
import dev.dromer.chestsort.net.payload.WandSelectPayload;
import dev.dromer.chestsort.net.payload.WandSelectionPayload;
import dev.dromer.chestsort.util.ContainerCanonicalizer;
import dev.dromer.chestsort.util.Cs2StringCodec;
import dev.dromer.chestsort.util.WandSelectionUtil;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class ChestSortNetworking {
    private ChestSortNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(ContainerHighlightPayload.ID, ContainerHighlightPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FindHighlightsPayload.ID, FindHighlightsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ContainerContextPayload.ID, ContainerContextPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ContainerContextV2Payload.ID, ContainerContextV2Payload.CODEC);
        PayloadTypeRegistry.playS2C().register(ContainerContextV3Payload.ID, ContainerContextV3Payload.CODEC);
        PayloadTypeRegistry.playS2C().register(PresetSyncPayload.ID, PresetSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PresetSyncV2Payload.ID, PresetSyncV2Payload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenPresetUiPayload.ID, OpenPresetUiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SortResultPayload.ID, SortResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WandSelectionPayload.ID, WandSelectionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LockedSlotsSyncPayload.ID, LockedSlotsSyncPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(SetFilterPayload.ID, SetFilterPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetFilterV2Payload.ID, SetFilterV2Payload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetContainerFiltersPayload.ID, SetContainerFiltersPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SortRequestPayload.ID, SortRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OrganizeRequestPayload.ID, OrganizeRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UndoSortPayload.ID, UndoSortPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetPresetPayload.ID, SetPresetPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetPresetV2Payload.ID, SetPresetV2Payload.CODEC);
        PayloadTypeRegistry.playC2S().register(ImportPresetPayload.ID, ImportPresetPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ImportPresetListPayload.ID, ImportPresetListPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WandSelectPayload.ID, WandSelectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ToggleLockedSlotPayload.ID, ToggleLockedSlotPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ToggleLockedSlotPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                int idx = payload.playerInventoryIndex();
                if (idx < 0 || idx >= player.getInventory().size()) return;

                ChestSortState state = ChestSortState.get(context.server());
                state.toggleLockedSlot(player.getUuidAsString(), idx);
                sendLockedSlotsTo(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(WandSelectPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                ChestSortState state = ChestSortState.get(context.server());
                String uuid = player.getUuidAsString();
                String wandItemId = state.getWandItemId(uuid);
                if (wandItemId == null || wandItemId.isEmpty()) return;

                String dimId = payload.dimensionId() == null ? "" : payload.dimensionId().trim();
                if (dimId.isEmpty()) return;

                byte which = payload.which();
                if (which == 1) {
                    state.setWandPos1(uuid, dimId, payload.posLong());
                } else if (which == 2) {
                    state.setWandPos2(uuid, dimId, payload.posLong());
                } else {
                    return;
                }

                var p1 = state.getWandPos1(uuid);
                var p2 = state.getWandPos2(uuid);
                long blockCount = 0L;
                int containerCount = 0;

                if (p1 != null && p2 != null && p1.dimensionId().equals(p2.dimensionId())) {
                    Identifier dimIdentifier = Identifier.tryParse(p1.dimensionId());
                    ServerWorld world = dimIdentifier == null ? null : context.server().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimIdentifier));
                    if (world != null) {
                        blockCount = WandSelectionUtil.volume(p1.pos(), p2.pos());
                        containerCount = WandSelectionUtil.findLoadedContainerCanonicalPosLongsInBox(world, p1.pos(), p2.pos()).size();
                    }
                }

                // Actionbar feedback
                String label = which == 1 ? "pos1" : "pos2";
                var setPos = which == 1 ? state.getWandPos1(uuid) : state.getWandPos2(uuid);
                if (setPos != null) {
                    BlockPos bp = setPos.pos();
                    net.minecraft.text.MutableText msg = net.minecraft.text.Text.literal("[CS] ").formatted(net.minecraft.util.Formatting.GOLD)
                        .append(net.minecraft.text.Text.literal("Wand " + label + ": ").formatted(net.minecraft.util.Formatting.GRAY))
                        .append(net.minecraft.text.Text.literal(bp.getX() + " " + bp.getY() + " " + bp.getZ()).formatted(net.minecraft.util.Formatting.YELLOW));
                    if (blockCount > 0L) {
                        msg = msg.append(net.minecraft.text.Text.literal("  Area: ").formatted(net.minecraft.util.Formatting.GRAY))
                            .append(net.minecraft.text.Text.literal(String.valueOf(blockCount)).formatted(net.minecraft.util.Formatting.YELLOW))
                            .append(net.minecraft.text.Text.literal(" blocks, ").formatted(net.minecraft.util.Formatting.GRAY))
                            .append(net.minecraft.text.Text.literal(String.valueOf(containerCount)).formatted(net.minecraft.util.Formatting.YELLOW))
                            .append(net.minecraft.text.Text.literal(" containers").formatted(net.minecraft.util.Formatting.GRAY));
                    }
                    player.sendMessage(msg, true);
                }

                ServerPlayNetworking.send(player, new WandSelectionPayload(
                    wandItemId,
                    p1 != null,
                    p1 == null ? "" : p1.dimensionId(),
                    p1 == null ? 0L : p1.posLong(),
                    p2 != null,
                    p2 == null ? "" : p2.dimensionId(),
                    p2 == null ? 0L : p2.posLong(),
                    blockCount,
                    containerCount
                ));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetFilterPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ChestSortState state = ChestSortState.get(context.server());

                Identifier dimIdentifier = Identifier.tryParse(payload.dimensionId());
                ServerWorld world = dimIdentifier == null ? null : context.server().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimIdentifier));
                if (world == null) {
                    state.setFilter(payload.dimensionId(), payload.posLong(), payload.filterItems());
                    return;
                }

                BlockPos pos = BlockPos.fromLong(payload.posLong());
                BlockEntity be = world.getBlockEntity(pos);
                if (be == null) {
                    state.setFilter(payload.dimensionId(), payload.posLong(), payload.filterItems());
                    return;
                }

                long canonicalPosLong = ContainerCanonicalizer.canonicalize(world, pos, be).posLong();
                state.setFilter(payload.dimensionId(), canonicalPosLong, payload.filterItems());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetFilterV2Payload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ChestSortState state = ChestSortState.get(context.server());

                Identifier dimIdentifier = Identifier.tryParse(payload.dimensionId());
                ServerWorld world = dimIdentifier == null ? null : context.server().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimIdentifier));
                if (world == null) {
                    state.setFilterSpec(payload.dimensionId(), payload.posLong(), payload.filter());
                    return;
                }

                BlockPos pos = BlockPos.fromLong(payload.posLong());
                BlockEntity be = world.getBlockEntity(pos);
                if (be == null) {
                    state.setFilterSpec(payload.dimensionId(), payload.posLong(), payload.filter());
                    return;
                }

                long canonicalPosLong = ContainerCanonicalizer.canonicalize(world, pos, be).posLong();
                state.setFilterSpec(payload.dimensionId(), canonicalPosLong, payload.filter());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetContainerFiltersPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ChestSortState state = ChestSortState.get(context.server());

                Identifier dimIdentifier = Identifier.tryParse(payload.dimensionId());
                ServerWorld world = dimIdentifier == null ? null : context.server().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimIdentifier));
                if (world == null) {
                    state.setFilterSpec(payload.dimensionId(), payload.posLong(), payload.whitelist());
                    state.setBlacklistSpec(payload.dimensionId(), payload.posLong(), payload.blacklist());
                    state.setWhitelistPriority(payload.dimensionId(), payload.posLong(), payload.whitelistPriority());
                    return;
                }

                BlockPos pos = BlockPos.fromLong(payload.posLong());
                BlockEntity be = world.getBlockEntity(pos);
                if (be == null) {
                    state.setFilterSpec(payload.dimensionId(), payload.posLong(), payload.whitelist());
                    state.setBlacklistSpec(payload.dimensionId(), payload.posLong(), payload.blacklist());
                    state.setWhitelistPriority(payload.dimensionId(), payload.posLong(), payload.whitelistPriority());
                    return;
                }

                long canonicalPosLong = ContainerCanonicalizer.canonicalize(world, pos, be).posLong();
                state.setFilterSpec(payload.dimensionId(), canonicalPosLong, payload.whitelist());
                state.setBlacklistSpec(payload.dimensionId(), canonicalPosLong, payload.blacklist());
                state.setWhitelistPriority(payload.dimensionId(), canonicalPosLong, payload.whitelistPriority());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SortRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                Identifier dimIdentifier = Identifier.tryParse(payload.dimensionId());
                if (dimIdentifier == null) return;

                // Only allow sorting in the player's current world.
                ServerWorld world = player.getEntityWorld();
                if (!world.getRegistryKey().getValue().equals(dimIdentifier)) return;

                BlockPos pos = BlockPos.fromLong(payload.posLong());
                if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

                BlockEntity be = world.getBlockEntity(pos);
                if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) return;

                long canonicalPosLong = ContainerCanonicalizer.canonicalize(world, pos, be).posLong();

                // Sort into the currently opened container inventory.
                if (!(player.currentScreenHandler instanceof net.minecraft.screen.GenericContainerScreenHandler handler)) return;
                Inventory containerInv = handler.getInventory();

                ChestSortState state = ChestSortState.get(context.server());
                ContainerFilterSpec filter = state.getFilterSpec(payload.dimensionId(), canonicalPosLong);
                if (filter == null || filter.isEmpty()) {
                    // Still report that nothing was moved.
                    ServerPlayNetworking.send(player, new SortResultPayload(
                        payload.dimensionId(),
                        canonicalPosLong,
                        SortResultPayload.KIND_SORT,
                        state.getAutosortMode(player.getUuidAsString()),
                        false,
                        0L,
                        0,
                        List.of()
                    ));
                    return;
                }

                ContainerFilterSpec effective = state.resolveWithAppliedPresets(filter);
                if (effective == null || effective.isEmpty()) {
                    ServerPlayNetworking.send(player, new SortResultPayload(
                        payload.dimensionId(),
                        canonicalPosLong,
                        SortResultPayload.KIND_SORT,
                        state.getAutosortMode(player.getUuidAsString()),
                        filter.autosort(),
                        0L,
                        0,
                        List.of()
                    ));
                    return;
                }

                ContainerFilterSpec blacklist = state.getBlacklistSpec(payload.dimensionId(), canonicalPosLong);
                ContainerFilterSpec effectiveBlacklist = blacklist == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : state.resolveBlacklistWithAppliedPresets(blacklist);
                boolean whitelistPriority = state.whitelistPriority(payload.dimensionId(), canonicalPosLong);

                SortOperationResult result = sortMatchingIntoDetailed(
                    state,
                    player.getUuidAsString(),
                    payload.dimensionId(),
                    canonicalPosLong,
                    player.getInventory(),
                    containerInv,
                    filter,
                    effective,
                    blacklist,
                    effectiveBlacklist,
                    whitelistPriority
                );
                if (result.movedTotal > 0) {
                    player.currentScreenHandler.sendContentUpdates();
                    // Update snapshot for this container after sorting.
                    state.updateFromBlockEntity(world, () -> canonicalPosLong, containerInv, be instanceof ChestBlockEntity ? "chest" : "barrel");
                }

                ServerPlayNetworking.send(player, new SortResultPayload(
                    payload.dimensionId(),
                    canonicalPosLong,
                    SortResultPayload.KIND_SORT,
                    state.getAutosortMode(player.getUuidAsString()),
                    filter.autosort(),
                    result.undoId,
                    result.movedTotal,
                    result.lines
                ));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(OrganizeRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                Identifier dimIdentifier = Identifier.tryParse(payload.dimensionId());
                if (dimIdentifier == null) return;

                // Only allow organizing in the player's current world.
                ServerWorld world = player.getEntityWorld();
                if (!world.getRegistryKey().getValue().equals(dimIdentifier)) return;

                BlockPos pos = BlockPos.fromLong(payload.posLong());
                if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

                BlockEntity be = world.getBlockEntity(pos);
                if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) return;

                long canonicalPosLong = ContainerCanonicalizer.canonicalize(world, pos, be).posLong();

                if (!(player.currentScreenHandler instanceof net.minecraft.screen.GenericContainerScreenHandler handler)) return;
                Inventory containerInv = handler.getInventory();

                ChestSortState state = ChestSortState.get(context.server());

                List<ItemStack> playerSnap = snapshot(player.getInventory());

                List<ItemStack> before = snapshot(containerInv);
                organizeContainer(containerInv);
                List<ItemStack> after = snapshot(containerInv);

                int changedSlots = countChangedSlots(before, after);

                long undoId = 0L;
                List<SortResultPayload.SortLine> lines = summarizeContainer(after);

                if (changedSlots > 0) {
                    undoId = state.nextUndoId(player.getUuidAsString());
                    // Reuse the undo mechanism: snapshot player/container before and after.
                    state.setLastSortUndo(player.getUuidAsString(), new ChestSortState.SortUndoTransaction(
                        payload.dimensionId(),
                        canonicalPosLong,
                        undoId,
                        changedSlots,
                        lines,
                        playerSnap,
                        before,
                        playerSnap,
                        after
                    ));

                    player.currentScreenHandler.sendContentUpdates();
                    state.updateFromBlockEntity(world, () -> canonicalPosLong, containerInv, be instanceof ChestBlockEntity ? "chest" : "barrel");
                }

                ServerPlayNetworking.send(player, new SortResultPayload(
                    payload.dimensionId(),
                    canonicalPosLong,
                    SortResultPayload.KIND_ORGANIZE,
                    state.getAutosortMode(player.getUuidAsString()),
                    false,
                    undoId,
                    changedSlots,
                    lines
                ));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UndoSortPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                Identifier dimIdentifier = Identifier.tryParse(payload.dimensionId());
                if (dimIdentifier == null) return;

                ServerWorld world = player.getEntityWorld();
                if (!world.getRegistryKey().getValue().equals(dimIdentifier)) return;

                BlockPos pos = BlockPos.fromLong(payload.posLong());
                if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

                BlockEntity be = world.getBlockEntity(pos);
                if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) return;

                long canonicalPosLong = ContainerCanonicalizer.canonicalize(world, pos, be).posLong();

                if (!(player.currentScreenHandler instanceof net.minecraft.screen.GenericContainerScreenHandler handler)) return;
                Inventory containerInv = handler.getInventory();

                ChestSortState state = ChestSortState.get(context.server());
                ChestSortState.SortUndoTransaction tx = state.getLastSortUndo(player.getUuidAsString());
                if (tx == null) return;
                if (tx.undoId() != payload.undoId()) return;
                if (!payload.dimensionId().equals(tx.dimensionId())) return;
                if (tx.posLong() != canonicalPosLong) return;

                restoreSnapshot(player.getInventory(), tx.playerBefore());
                restoreSnapshot(containerInv, tx.containerBefore());
                player.currentScreenHandler.sendContentUpdates();
                state.clearLastSortUndo(player.getUuidAsString());
                state.updateFromBlockEntity(world, () -> canonicalPosLong, containerInv, be instanceof ChestBlockEntity ? "chest" : "barrel");

                ServerPlayNetworking.send(player, new SortResultPayload(
                    payload.dimensionId(),
                    canonicalPosLong,
                    SortResultPayload.KIND_UNDO,
                    state.getAutosortMode(player.getUuidAsString()),
                    false,
                    0L,
                    tx.movedTotal(),
                    tx.lines()
                ));
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(SetPresetPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                String name = payload.name() == null ? "" : payload.name().trim();
                if (name.isEmpty()) return;

                ChestSortState state = ChestSortState.get(context.server());
                state.setPreset(name, payload.spec());
                broadcastPresets(context.server());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetPresetV2Payload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                String name = payload.name() == null ? "" : payload.name().trim();
                if (name.isEmpty()) return;

                ChestSortState state = ChestSortState.get(context.server());
                state.setPreset(name, payload.whitelist(), payload.blacklist());
                broadcastPresets(context.server());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ImportPresetPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                ChestSortState state = ChestSortState.get(context.server());

                // Allow importing either a single named preset or a presetList containing multiple presets.
                try {
                    var list = Cs2StringCodec.decodePresetList(payload.data());
                    var whitelists = list == null ? null : list.whitelists();
                    var blacklists = list == null ? null : list.blacklists();
                    if (whitelists == null || whitelists.isEmpty()) {
                        player.sendMessage(net.minecraft.text.Text.literal("[CS] Invalid preset import: empty presetList").formatted(net.minecraft.util.Formatting.RED), false);
                        return;
                    }

                    int imported = 0;
                    String firstImportedName = "";

                    for (var e : whitelists.entrySet()) {
                        if (e == null) continue;
                        String desired = e.getKey() == null ? "" : e.getKey().trim();
                        if (desired.isEmpty()) continue;

                        String actual = chestsort$uniquePresetName(state, desired);
                        ContainerFilterSpec wl = e.getValue();
                        ContainerFilterSpec bl = (blacklists == null) ? null : blacklists.get(desired);
                        if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) continue;
                        state.setPreset(actual,
                            wl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl,
                            bl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : bl);
                        if (imported == 0) firstImportedName = actual;
                        imported++;
                    }

                    if (imported <= 0) {
                        player.sendMessage(net.minecraft.text.Text.literal("[CS] Invalid preset import: empty presetList").formatted(net.minecraft.util.Formatting.RED), false);
                        return;
                    }

                    broadcastPresets(context.server());
                    sendPresetsTo(player);

                    player.sendMessage(net.minecraft.text.Text.literal("[CS] Imported " + imported + " preset" + (imported == 1 ? "" : "s") + ".").formatted(net.minecraft.util.Formatting.GREEN), false);
                    if (!firstImportedName.isEmpty()) {
                        ServerPlayNetworking.send(player, new OpenPresetUiPayload(OpenPresetUiPayload.MODE_EDIT, firstImportedName));
                    }
                    return;
                } catch (IllegalArgumentException ignoredNotList) {
                    // Not a presetList, fall through to single preset import.
                }

                Cs2StringCodec.DecodedPresetImport decoded;
                try {
                    decoded = Cs2StringCodec.decodePresetImport(payload.data());
                } catch (IllegalArgumentException e) {
                    player.sendMessage(net.minecraft.text.Text.literal("[CS] Invalid preset import: " + e.getMessage()).formatted(net.minecraft.util.Formatting.RED), false);
                    return;
                }

                String name = decoded.name() == null ? "" : decoded.name().trim();
                if (name.isEmpty()) {
                    player.sendMessage(net.minecraft.text.Text.literal("[CS] Invalid preset import: missing preset name").formatted(net.minecraft.util.Formatting.RED), false);
                    return;
                }

                String actual = chestsort$uniquePresetName(state, name);
                ContainerFilterSpec wl = decoded.whitelist();
                ContainerFilterSpec bl = decoded.blacklist();
                if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) {
                    player.sendMessage(net.minecraft.text.Text.literal("[CS] Invalid preset import: empty preset").formatted(net.minecraft.util.Formatting.RED), false);
                    return;
                }

                state.setPreset(actual,
                    wl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl,
                    bl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : bl);
                broadcastPresets(context.server());

                sendPresetsTo(player);
                player.sendMessage(net.minecraft.text.Text.literal("[CS] Imported preset: " + actual).formatted(net.minecraft.util.Formatting.GREEN), false);
                ServerPlayNetworking.send(player, new OpenPresetUiPayload(OpenPresetUiPayload.MODE_EDIT, actual));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ImportPresetListPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                ChestSortState state = ChestSortState.get(context.server());
                List<String> names = payload.names() == null ? List.of() : payload.names();
                List<ContainerFilterSpec> specs = payload.specs() == null ? List.of() : payload.specs();
                int count = Math.min(names.size(), specs.size());
                if (count <= 0) {
                    player.sendMessage(net.minecraft.text.Text.literal("[CS] Invalid preset import: nothing selected").formatted(net.minecraft.util.Formatting.RED), false);
                    return;
                }

                int imported = 0;
                String firstImportedName = "";
                for (int i = 0; i < count; i++) {
                    String desired = names.get(i) == null ? "" : names.get(i).trim();
                    if (desired.isEmpty()) continue;
                    ContainerFilterSpec spec = specs.get(i);
                    if (spec == null || spec.isEmpty()) continue;

                    String actual = chestsort$uniquePresetName(state, desired);
                    state.setPreset(actual, spec);
                    if (imported == 0) firstImportedName = actual;
                    imported++;
                }

                if (imported <= 0) {
                    player.sendMessage(net.minecraft.text.Text.literal("[CS] Invalid preset import: nothing selected").formatted(net.minecraft.util.Formatting.RED), false);
                    return;
                }

                broadcastPresets(context.server());
                sendPresetsTo(player);

                player.sendMessage(net.minecraft.text.Text.literal("[CS] Imported " + imported + " preset" + (imported == 1 ? "" : "s") + ".").formatted(net.minecraft.util.Formatting.GREEN), false);
                if (!firstImportedName.isEmpty()) {
                    ServerPlayNetworking.send(player, new OpenPresetUiPayload(OpenPresetUiPayload.MODE_EDIT, firstImportedName));
                }
            });
        });
    }

    private static String chestsort$uniquePresetName(ChestSortState state, String desiredName) {
        String base = desiredName == null ? "" : desiredName.trim();
        if (base.isEmpty()) return base;
        if (state == null) return base;
        if (!state.hasPreset(base)) return base;

        for (int i = 2; i < 10_000; i++) {
            String n = base + " " + i;
            if (!state.hasPreset(n)) return n;
        }
        return base + " " + System.currentTimeMillis();
    }

    public static SortOperationResult sortMatchingIntoDetailed(
        ChestSortState state,
        String playerUuid,
        String dimId,
        long posLong,
        net.minecraft.entity.player.PlayerInventory playerInv,
        Inventory containerInv,
        ContainerFilterSpec baseWhitelist,
        ContainerFilterSpec effectiveWhitelist,
        ContainerFilterSpec baseBlacklist,
        ContainerFilterSpec effectiveBlacklist,
        boolean whitelistPriority
    ) {
        List<ItemStack> playerBefore = snapshot(playerInv);
        List<ItemStack> containerBefore = snapshot(containerInv);

        SortDetailAccumulator detail = new SortDetailAccumulator();
        int moved = sortMatchingInto(playerInv, containerInv, effectiveWhitelist, effectiveBlacklist, whitelistPriority, detail, baseWhitelist, state, playerUuid);

        List<ItemStack> playerAfter = snapshot(playerInv);
        List<ItemStack> containerAfter = snapshot(containerInv);

        List<SortResultPayload.SortLine> lines = detail.toLines();

        long undoId = 0L;
        if (moved > 0 && state != null && playerUuid != null && !playerUuid.isEmpty()) {
            undoId = state.nextUndoId(playerUuid);
            state.setLastSortUndo(playerUuid, new ChestSortState.SortUndoTransaction(
                dimId == null ? "" : dimId,
                posLong,
                undoId,
                moved,
                lines,
                playerBefore,
                containerBefore,
                playerAfter,
                containerAfter
            ));
        }

        return new SortOperationResult(undoId, moved, lines);
    }

    public record SortOperationResult(long undoId, int movedTotal, List<SortResultPayload.SortLine> lines) {
    }

    private static int sortMatchingInto(
        net.minecraft.entity.player.PlayerInventory playerInv,
        Inventory containerInv,
        ContainerFilterSpec whitelist,
        ContainerFilterSpec blacklist,
        boolean whitelistPriority,
        SortDetailAccumulator detail,
        ContainerFilterSpec baseWhitelist,
        ChestSortState state,
        String playerUuid
    ) {
        ContainerFilterSpec w = whitelist == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : whitelist;
        ContainerFilterSpec b = blacklist == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : blacklist;

        Set<String> allowedItemIds = new HashSet<>(w.items() == null ? List.of() : w.items());
        List<TagFilterRuntime> tagFilters = compileTagFilters(w.tags());

        Set<String> blockedItemIds = new HashSet<>(b.items() == null ? List.of() : b.items());
        List<TagFilterRuntime> blacklistTagFilters = compileTagFilters(b.tags());

        int moved = 0;

        for (int i = 0; i < playerInv.size(); i++) {
            if (state != null && playerUuid != null && !playerUuid.isEmpty() && state.isSlotLocked(playerUuid, i)) {
                continue;
            }
            ItemStack stack = playerInv.getStack(i);
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

            if (state != null && playerUuid != null && !playerUuid.isEmpty() && state.isItemBlacklisted(playerUuid, itemId)) {
                continue;
            }

            boolean allowed = allowedItemIds.contains(itemId);
            if (!allowed) {
                for (TagFilterRuntime tf : tagFilters) {
                    if (tf.matches(stack, itemId)) {
                        allowed = true;
                        break;
                    }
                }
            }

            if (!allowed) continue;

            boolean blocked = blockedItemIds.contains(itemId);
            if (!blocked) {
                for (TagFilterRuntime tf : blacklistTagFilters) {
                    if (tf.matches(stack, itemId)) {
                        blocked = true;
                        break;
                    }
                }
            }

            if (blocked && !whitelistPriority) continue;

            int before = stack.getCount();
            ItemStack remainder = insertInto(containerInv, stack);
            playerInv.setStack(i, remainder);
            int movedHere = (before - remainder.getCount());
            moved += movedHere;

            if (detail != null && movedHere > 0) {
                detail.addMoved(itemId, movedHere, explainReasons(stack, itemId, baseWhitelist, state));
            }
        }

        return moved;
    }

    private static List<String> explainReasons(ItemStack stack, String itemId, ContainerFilterSpec baseFilter, ChestSortState state) {
        ArrayList<String> out = new ArrayList<>();
        if (baseFilter == null) {
            out.add("Matched filter");
            return out;
        }

        ContainerFilterSpec base = baseFilter.normalized();
        if (base.items() != null && base.items().contains(itemId)) {
            out.add("Matched item filter");
        }
        List<TagFilterRuntime> baseTags = compileTagFilters(base.tags());
        for (TagFilterRuntime tf : baseTags) {
            if (tf.matches(stack, itemId)) {
                out.add("Matched tag filter #" + tf.tagKey().id());
            }
        }

        if (state != null && base.presets() != null) {
            for (String presetName : base.presets()) {
                if (presetName == null || presetName.isEmpty()) continue;
                ContainerFilterSpec preset = state.getPreset(presetName);
                if (preset == null) continue;
                ContainerFilterSpec p = preset.normalized();
                if (p.items() != null && p.items().contains(itemId)) {
                    out.add("Matched preset \"" + presetName + "\" containing item filter");
                }
                List<TagFilterRuntime> presetTags = compileTagFilters(p.tags());
                for (TagFilterRuntime tf : presetTags) {
                    if (tf.matches(stack, itemId)) {
                        out.add("Matched preset \"" + presetName + "\" containing tag filter #" + tf.tagKey().id());
                    }
                }
            }
        }

        if (out.isEmpty()) {
            out.add("Matched filter");
        }
        return out;
    }

    private static List<ItemStack> snapshot(Inventory inv) {
        ArrayList<ItemStack> out = new ArrayList<>(inv.size());
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            out.add(s == null ? ItemStack.EMPTY : s.copy());
        }
        return out;
    }

    private static void restoreSnapshot(Inventory inv, List<ItemStack> snap) {
        if (snap == null) return;
        int n = Math.min(inv.size(), snap.size());
        for (int i = 0; i < n; i++) {
            ItemStack s = snap.get(i);
            inv.setStack(i, s == null ? ItemStack.EMPTY : s.copy());
        }
        inv.markDirty();
    }

    private static final class SortDetailAccumulator {
        private final java.util.LinkedHashMap<String, Integer> movedByItem = new java.util.LinkedHashMap<>();
        private final java.util.HashMap<String, List<String>> reasonsByItem = new java.util.HashMap<>();

        void addMoved(String itemId, int count, List<String> reasons) {
            if (itemId == null || itemId.isEmpty()) return;
            movedByItem.merge(itemId, count, Integer::sum);
            if (reasons != null && !reasons.isEmpty()) {
                reasonsByItem.putIfAbsent(itemId, reasons);
            }
        }

        List<SortResultPayload.SortLine> toLines() {
            ArrayList<SortResultPayload.SortLine> out = new ArrayList<>(movedByItem.size());
            for (var e : movedByItem.entrySet()) {
                String itemId = e.getKey();
                int count = e.getValue();
                List<String> reasons = reasonsByItem.getOrDefault(itemId, List.of());
                out.add(new SortResultPayload.SortLine(itemId, count, reasons));
            }
            // Sort biggest-first to make the notification more useful.
            out.sort((a, b) -> Integer.compare(b.count(), a.count()));
            return out;
        }
    }

    private static int countChangedSlots(List<ItemStack> before, List<ItemStack> after) {
        if (before == null || after == null) return 0;
        int n = Math.min(before.size(), after.size());
        int changed = 0;
        for (int i = 0; i < n; i++) {
            ItemStack a = before.get(i);
            ItemStack b = after.get(i);
            if (a == null) a = ItemStack.EMPTY;
            if (b == null) b = ItemStack.EMPTY;
            if (!ItemStack.areItemsAndComponentsEqual(a, b) || a.getCount() != b.getCount()) {
                changed++;
            }
        }
        return changed;
    }

    private static void organizeContainer(Inventory containerInv) {
        if (containerInv == null) return;

        ArrayList<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < containerInv.size(); i++) {
            ItemStack s = containerInv.getStack(i);
            if (s == null || s.isEmpty()) continue;
            stacks.add(s.copy());
        }

        // Group by combine-compatibility. Container sizes are small enough for O(n^2).
        ArrayList<ItemStack> protos = new ArrayList<>();
        ArrayList<Integer> totals = new ArrayList<>();

        for (ItemStack s : stacks) {
            boolean merged = false;
            for (int g = 0; g < protos.size(); g++) {
                if (ItemStack.areItemsAndComponentsEqual(protos.get(g), s)) {
                    totals.set(g, totals.get(g) + s.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                ItemStack p = s.copy();
                p.setCount(1);
                protos.add(p);
                totals.add(s.getCount());
            }
        }

        ArrayList<ItemStack> packed = new ArrayList<>();
        for (int i = 0; i < protos.size(); i++) {
            ItemStack proto = protos.get(i);
            int total = totals.get(i);
            int max = Math.min(proto.getMaxCount(), containerInv.getMaxCountPerStack());
            int remaining = total;
            while (remaining > 0) {
                int take = Math.min(max, remaining);
                ItemStack out = proto.copy();
                out.setCount(take);
                packed.add(out);
                remaining -= take;
            }
        }

        // Write back sequentially.
        int slot = 0;
        for (; slot < containerInv.size() && slot < packed.size(); slot++) {
            containerInv.setStack(slot, packed.get(slot));
        }
        for (; slot < containerInv.size(); slot++) {
            containerInv.setStack(slot, ItemStack.EMPTY);
        }
        containerInv.markDirty();
    }

    private static List<SortResultPayload.SortLine> summarizeContainer(List<ItemStack> after) {
        if (after == null || after.isEmpty()) return List.of();

        java.util.LinkedHashMap<String, Integer> totals = new java.util.LinkedHashMap<>();
        for (ItemStack s : after) {
            if (s == null || s.isEmpty()) continue;
            String id = Registries.ITEM.getId(s.getItem()).toString();
            totals.merge(id, s.getCount(), Integer::sum);
        }

        ArrayList<SortResultPayload.SortLine> out = new ArrayList<>(totals.size());
        for (var e : totals.entrySet()) {
            out.add(new SortResultPayload.SortLine(e.getKey(), e.getValue(), List.of("Grouped in container")));
        }
        out.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return out;
    }

    public static void sendPresetsTo(net.minecraft.server.network.ServerPlayerEntity player) {
        if (player == null) return;
        ChestSortState state = ChestSortState.get(((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getServer());

        Map<String, ContainerFilterSpec> presets = state.getPresets();
        Map<String, ContainerFilterSpec> presetBlacklists = state.getPresetBlacklists();
        ArrayList<String> names = new ArrayList<>(presets.size());
        ArrayList<ContainerFilterSpec> specs = new ArrayList<>(presets.size());
        ArrayList<ContainerFilterSpec> blSpecs = new ArrayList<>(presets.size());
        for (var e : presets.entrySet()) {
            String name = e.getKey();
            if (name == null || name.trim().isEmpty()) continue;
            names.add(name);
            specs.add(e.getValue() == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : e.getValue());

            ContainerFilterSpec bl = presetBlacklists.get(name);
            blSpecs.add(bl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : bl);
        }

        // Prefer v2 (whitelist+blacklist), but keep v1 for compatibility.
        ServerPlayNetworking.send(player, new PresetSyncV2Payload(names, specs, blSpecs));
        ServerPlayNetworking.send(player, new PresetSyncPayload(names, specs));
    }

    public static void sendLockedSlotsTo(net.minecraft.server.network.ServerPlayerEntity player) {
        if (player == null) return;
        ChestSortState state = ChestSortState.get(((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getServer());
        List<Integer> locked = state.getLockedSlots(player.getUuidAsString());
        ServerPlayNetworking.send(player, new LockedSlotsSyncPayload(locked));
    }

    public static void broadcastPresets(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (var player : server.getPlayerManager().getPlayerList()) {
            sendPresetsTo(player);
        }
    }

    public static int sortMatchingInto(net.minecraft.entity.player.PlayerInventory playerInv, Inventory containerInv, ContainerFilterSpec filter) {
        Set<String> allowedItemIds = new HashSet<>(filter.items() == null ? List.of() : filter.items());

        List<TagFilterRuntime> tagFilters = compileTagFilters(filter.tags());

        int moved = 0;

        for (int i = 0; i < playerInv.size(); i++) {
            ItemStack stack = playerInv.getStack(i);
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

            boolean allowed = allowedItemIds.contains(itemId);
            if (!allowed) {
                for (TagFilterRuntime tf : tagFilters) {
                    if (tf.matches(stack, itemId)) {
                        allowed = true;
                        break;
                    }
                }
            }

            if (!allowed) continue;

            int before = stack.getCount();
            ItemStack remainder = insertInto(containerInv, stack);
            playerInv.setStack(i, remainder);
            moved += (before - remainder.getCount());
        }

        return moved;
    }

    private static List<TagFilterRuntime> compileTagFilters(List<TagFilterSpec> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        ArrayList<TagFilterRuntime> out = new ArrayList<>(tags.size());
        for (TagFilterSpec tag : tags) {
            if (tag == null) continue;
            String raw = tag.tagId();
            if (raw == null) continue;
            String idStr = raw.startsWith("#") ? raw.substring(1) : raw;
            Identifier id = Identifier.tryParse(idStr);
            if (id == null) continue;
            TagKey<net.minecraft.item.Item> key = TagKey.of(RegistryKeys.ITEM, id);

            Set<String> exceptions = new HashSet<>();
            if (tag.exceptions() != null) {
                for (String exc : tag.exceptions()) {
                    if (exc == null) continue;
                    String t = exc.trim();
                    if (!t.isEmpty()) exceptions.add(t);
                }
            }
            out.add(new TagFilterRuntime(key, exceptions));
        }
        return out;
    }

    private record TagFilterRuntime(TagKey<net.minecraft.item.Item> tagKey, Set<String> exceptions) {
        boolean matches(ItemStack stack, String itemId) {
            if (exceptions != null && exceptions.contains(itemId)) return false;
            return stack.isIn(tagKey);
        }
    }

    private static ItemStack insertInto(Inventory container, ItemStack stack) {
        if (stack.isEmpty()) return stack;
        ItemStack remaining = stack.copy();

        // First pass: merge into existing stacks.
        for (int i = 0; i < container.size(); i++) {
            ItemStack existing = container.getStack(i);
            if (existing.isEmpty()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(existing, remaining)) continue;

            int max = Math.min(existing.getMaxCount(), container.getMaxCountPerStack());
            int canAdd = max - existing.getCount();
            if (canAdd <= 0) continue;

            int toMove = Math.min(canAdd, remaining.getCount());
            existing.increment(toMove);
            remaining.decrement(toMove);
            container.setStack(i, existing);
            if (remaining.isEmpty()) return ItemStack.EMPTY;
        }

        // Second pass: fill empty slots.
        for (int i = 0; i < container.size(); i++) {
            ItemStack existing = container.getStack(i);
            if (!existing.isEmpty()) continue;

            int max = Math.min(remaining.getMaxCount(), container.getMaxCountPerStack());
            int toMove = Math.min(max, remaining.getCount());

            ItemStack placed = remaining.copy();
            placed.setCount(toMove);
            container.setStack(i, placed);
            remaining.decrement(toMove);
            if (remaining.isEmpty()) return ItemStack.EMPTY;
        }

        return remaining;
    }
}
