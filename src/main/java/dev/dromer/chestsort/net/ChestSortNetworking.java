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
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ChestSortNetworking {
    private ChestSortNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(ContainerHighlightPayload.ID, ContainerHighlightPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FindHighlightsPayload.ID, FindHighlightsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ContainerContextPayload.ID, ContainerContextPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ContainerContextV2Payload.ID, ContainerContextV2Payload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ContainerContextV3Payload.ID, ContainerContextV3Payload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PresetSyncPayload.ID, PresetSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PresetSyncV2Payload.ID, PresetSyncV2Payload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenPresetUiPayload.ID, OpenPresetUiPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SortResultPayload.ID, SortResultPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WandSelectionPayload.ID, WandSelectionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LockedSlotsSyncPayload.ID, LockedSlotsSyncPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(SetFilterPayload.ID, SetFilterPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetFilterV2Payload.ID, SetFilterV2Payload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetContainerFiltersPayload.ID, SetContainerFiltersPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SortRequestPayload.ID, SortRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OrganizeRequestPayload.ID, OrganizeRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UndoSortPayload.ID, UndoSortPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetPresetPayload.ID, SetPresetPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetPresetV2Payload.ID, SetPresetV2Payload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ImportPresetPayload.ID, ImportPresetPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ImportPresetListPayload.ID, ImportPresetListPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WandSelectPayload.ID, WandSelectPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ToggleLockedSlotPayload.ID, ToggleLockedSlotPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ToggleLockedSlotPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
            var player = context.player();
                if (player == null) return;

                int idx = payload.playerInventoryIndex();
                if (idx < 0 || idx >= player.getInventory().getContainerSize()) {
                    System.err.println("[ChestSort] ToggleLockedSlotPayload idx " + idx + " out of range (size=" + player.getInventory().getContainerSize() + ")");
                    return;
                }

                ChestSortState state = ChestSortState.get(context.server());
                state.toggleLockedSlot(player.getStringUUID(), idx);
                System.err.println("[ChestSort] Toggled idx " + idx + ", now locked=" + state.getLockedSlots(player.getStringUUID()));
                sendLockedSlotsTo(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(WandSelectPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                ChestSortState state = ChestSortState.get(context.server());
                String uuid = player.getStringUUID();
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
                    ServerLevel world = dimIdentifier == null ? null : context.server().getLevel(ResourceKey.create(Registries.DIMENSION, dimIdentifier));
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
                    MutableComponent msg = Component.literal("[CS] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Wand " + label + ": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(bp.getX() + " " + bp.getY() + " " + bp.getZ()).withStyle(ChatFormatting.YELLOW));
                    if (blockCount > 0L) {
                        msg = msg.append(Component.literal("  Area: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(String.valueOf(blockCount)).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" blocks, ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(String.valueOf(containerCount)).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" containers").withStyle(ChatFormatting.GRAY));
                    }
                    player.sendOverlayMessage(msg);
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
                ServerLevel world = dimIdentifier == null ? null : context.server().getLevel(ResourceKey.create(Registries.DIMENSION, dimIdentifier));
                if (world == null) {
                    state.setFilter(payload.dimensionId(), payload.posLong(), payload.filterItems());
                    return;
                }

                BlockPos pos = BlockPos.of(payload.posLong());
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
                ServerLevel world = dimIdentifier == null ? null : context.server().getLevel(ResourceKey.create(Registries.DIMENSION, dimIdentifier));
                if (world == null) {
                    state.setFilterSpec(payload.dimensionId(), payload.posLong(), payload.filter());
                    return;
                }

                BlockPos pos = BlockPos.of(payload.posLong());
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
                ServerLevel world = dimIdentifier == null ? null : context.server().getLevel(ResourceKey.create(Registries.DIMENSION, dimIdentifier));
                if (world == null) {
                    state.setFilterSpec(payload.dimensionId(), payload.posLong(), payload.whitelist());
                    state.setBlacklistSpec(payload.dimensionId(), payload.posLong(), payload.blacklist());
                    state.setWhitelistPriority(payload.dimensionId(), payload.posLong(), payload.whitelistPriority());
                    return;
                }

                BlockPos pos = BlockPos.of(payload.posLong());
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
                ServerLevel world = player.level();
                if (!world.dimension().identifier().equals(dimIdentifier)) return;

                BlockPos pos = BlockPos.of(payload.posLong());
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

                BlockEntity be = world.getBlockEntity(pos);
                if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) return;

                long canonicalPosLong = ContainerCanonicalizer.canonicalize(world, pos, be).posLong();

                // Sort into the currently opened container inventory.
                if (!(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu handler)) return;
                Container containerInv = handler.getContainer();

                ChestSortState state = ChestSortState.get(context.server());
                ContainerFilterSpec filter = state.getFilterSpec(payload.dimensionId(), canonicalPosLong);
                if (filter == null || filter.isEmpty()) {
                    // Still report that nothing was moved.
                    ServerPlayNetworking.send(player, new SortResultPayload(
                        payload.dimensionId(),
                        canonicalPosLong,
                        SortResultPayload.KIND_SORT,
                        state.getAutosortMode(player.getStringUUID()),
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
                        state.getAutosortMode(player.getStringUUID()),
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
                    player.getStringUUID(),
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
                    player.containerMenu.sendAllDataToRemote();
                    // Update snapshot for this container after sorting.
                    state.updateFromBlockEntity(world, () -> canonicalPosLong, containerInv, be instanceof ChestBlockEntity ? "chest" : "barrel");
                }

                ServerPlayNetworking.send(player, new SortResultPayload(
                    payload.dimensionId(),
                    canonicalPosLong,
                    SortResultPayload.KIND_SORT,
                    state.getAutosortMode(player.getStringUUID()),
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
                ServerLevel world = player.level();
                if (!world.dimension().identifier().equals(dimIdentifier)) return;

                BlockPos pos = BlockPos.of(payload.posLong());
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

                BlockEntity be = world.getBlockEntity(pos);
                if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) return;

                long canonicalPosLong = ContainerCanonicalizer.canonicalize(world, pos, be).posLong();

                if (!(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu handler)) return;
                Container containerInv = handler.getContainer();

                ChestSortState state = ChestSortState.get(context.server());

                List<ItemStack> playerSnap = snapshot(player.getInventory());

                List<ItemStack> before = snapshot(containerInv);
                organizeContainer(containerInv);
                List<ItemStack> after = snapshot(containerInv);

                int changedSlots = countChangedSlots(before, after);

                long undoId = 0L;
                List<SortResultPayload.SortLine> lines = summarizeContainer(after);

                if (changedSlots > 0) {
                    undoId = state.nextUndoId(player.getStringUUID());
                    // Reuse the undo mechanism: snapshot player/container before and after.
                    state.setLastSortUndo(player.getStringUUID(), new ChestSortState.SortUndoTransaction(
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

                    player.containerMenu.sendAllDataToRemote();
                    state.updateFromBlockEntity(world, () -> canonicalPosLong, containerInv, be instanceof ChestBlockEntity ? "chest" : "barrel");
                }

                ServerPlayNetworking.send(player, new SortResultPayload(
                    payload.dimensionId(),
                    canonicalPosLong,
                    SortResultPayload.KIND_ORGANIZE,
                    state.getAutosortMode(player.getStringUUID()),
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

                ServerLevel world = player.level();
                if (!world.dimension().identifier().equals(dimIdentifier)) return;

                BlockPos pos = BlockPos.of(payload.posLong());
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

                BlockEntity be = world.getBlockEntity(pos);
                if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) return;

                long canonicalPosLong = ContainerCanonicalizer.canonicalize(world, pos, be).posLong();

                if (!(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu handler)) return;
                Container containerInv = handler.getContainer();

                ChestSortState state = ChestSortState.get(context.server());
                ChestSortState.SortUndoTransaction tx = state.getLastSortUndo(player.getStringUUID());
                if (tx == null) return;
                if (tx.undoId() != payload.undoId()) return;
                if (!payload.dimensionId().equals(tx.dimensionId())) return;
                if (tx.posLong() != canonicalPosLong) return;

                restoreSnapshot(player.getInventory(), tx.playerBefore());
                restoreSnapshot(containerInv, tx.containerBefore());
                player.containerMenu.sendAllDataToRemote();
                state.clearLastSortUndo(player.getStringUUID());
                state.updateFromBlockEntity(world, () -> canonicalPosLong, containerInv, be instanceof ChestBlockEntity ? "chest" : "barrel");

                ServerPlayNetworking.send(player, new SortResultPayload(
                    payload.dimensionId(),
                    canonicalPosLong,
                    SortResultPayload.KIND_UNDO,
                    state.getAutosortMode(player.getStringUUID()),
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
                        player.sendSystemMessage(Component.literal("[CS] Invalid preset import: empty presetList").withStyle(ChatFormatting.RED));
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
                        player.sendSystemMessage(Component.literal("[CS] Invalid preset import: empty presetList").withStyle(ChatFormatting.RED));
                        return;
                    }

                    broadcastPresets(context.server());
                    sendPresetsTo(player);

                    player.sendSystemMessage(Component.literal("[CS] Imported " + imported + " preset" + (imported == 1 ? "" : "s") + ".").withStyle(ChatFormatting.GREEN));
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
                    player.sendSystemMessage(Component.literal("[CS] Invalid preset import: " + e.getMessage()).withStyle(ChatFormatting.RED));
                    return;
                }

                String name = decoded.name() == null ? "" : decoded.name().trim();
                if (name.isEmpty()) {
                    player.sendSystemMessage(Component.literal("[CS] Invalid preset import: missing preset name").withStyle(ChatFormatting.RED));
                    return;
                }

                String actual = chestsort$uniquePresetName(state, name);
                ContainerFilterSpec wl = decoded.whitelist();
                ContainerFilterSpec bl = decoded.blacklist();
                if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) {
                    player.sendSystemMessage(Component.literal("[CS] Invalid preset import: empty preset").withStyle(ChatFormatting.RED));
                    return;
                }

                state.setPreset(actual,
                    wl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl,
                    bl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : bl);
                broadcastPresets(context.server());

                sendPresetsTo(player);
                player.sendSystemMessage(Component.literal("[CS] Imported preset: " + actual).withStyle(ChatFormatting.GREEN));
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
                    player.sendSystemMessage(Component.literal("[CS] Invalid preset import: nothing selected").withStyle(ChatFormatting.RED));
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
                    player.sendSystemMessage(Component.literal("[CS] Invalid preset import: nothing selected").withStyle(ChatFormatting.RED));
                    return;
                }

                broadcastPresets(context.server());
                sendPresetsTo(player);

                player.sendSystemMessage(Component.literal("[CS] Imported " + imported + " preset" + (imported == 1 ? "" : "s") + ".").withStyle(ChatFormatting.GREEN));
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
        Inventory playerInv,
        Container containerInv,
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
        Inventory playerInv,
        Container containerInv,
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

        for (int i = 0; i < playerInv.getContainerSize(); i++) {
            if (state != null && playerUuid != null && !playerUuid.isEmpty() && state.isSlotLocked(playerUuid, i)) {
                continue;
            }
            ItemStack stack = playerInv.getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            String blMode = state != null && playerUuid != null && !playerUuid.isEmpty()
                ? state.getBlacklistMode(playerUuid)
                : ChestSortState.BLACKLIST_MODE_PREVENT_SORT;
            boolean preventSort = ChestSortState.blacklistModePreventsSort(blMode);
            boolean strictSort = ChestSortState.BLACKLIST_MODE_STRICT_PREVENT_SORT.equals(blMode);
            boolean globallyBlacklisted = preventSort && state != null && playerUuid != null && !playerUuid.isEmpty() && state.isItemBlacklisted(playerUuid, itemId);

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

            if (globallyBlacklisted && (strictSort || !whitelistPriority)) continue;

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
            playerInv.setItem(i, remainder);
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
                out.add("Matched tag filter #" + tf.tagKey().location());
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
                        out.add("Matched preset \"" + presetName + "\" containing tag filter #" + tf.tagKey().location());
                    }
                }
            }
        }

        if (out.isEmpty()) {
            out.add("Matched filter");
        }
        return out;
    }

    private static List<ItemStack> snapshot(Container inv) {
        ArrayList<ItemStack> out = new ArrayList<>(inv.getContainerSize());
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            out.add(s == null ? ItemStack.EMPTY : s.copy());
        }
        return out;
    }

    private static void restoreSnapshot(Container inv, List<ItemStack> snap) {
        if (snap == null) return;
        int n = Math.min(inv.getContainerSize(), snap.size());
        for (int i = 0; i < n; i++) {
            ItemStack s = snap.get(i);
            inv.setItem(i, s == null ? ItemStack.EMPTY : s.copy());
        }
        inv.setChanged();
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
            if (!ItemStack.matches(a, b) || a.getCount() != b.getCount()) {
                changed++;
            }
        }
        return changed;
    }

    private static void organizeContainer(Container containerInv) {
        if (containerInv == null) return;

        ArrayList<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < containerInv.getContainerSize(); i++) {
            ItemStack s = containerInv.getItem(i);
            if (s == null || s.isEmpty()) continue;
            stacks.add(s.copy());
        }

        // Group by combine-compatibility. Container sizes are small enough for O(n^2).
        ArrayList<ItemStack> protos = new ArrayList<>();
        ArrayList<Integer> totals = new ArrayList<>();

        for (ItemStack s : stacks) {
            boolean merged = false;
            for (int g = 0; g < protos.size(); g++) {
                if (ItemStack.matches(protos.get(g), s)) {
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
            int max = Math.min(proto.getMaxStackSize(), containerInv.getMaxStackSize());
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
        for (; slot < containerInv.getContainerSize() && slot < packed.size(); slot++) {
            containerInv.setItem(slot, packed.get(slot));
        }
        for (; slot < containerInv.getContainerSize(); slot++) {
            containerInv.setItem(slot, ItemStack.EMPTY);
        }
        containerInv.setChanged();
    }

    private static List<SortResultPayload.SortLine> summarizeContainer(List<ItemStack> after) {
        if (after == null || after.isEmpty()) return List.of();

        java.util.LinkedHashMap<String, Integer> totals = new java.util.LinkedHashMap<>();
        for (ItemStack s : after) {
            if (s == null || s.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            totals.merge(id, s.getCount(), Integer::sum);
        }

        ArrayList<SortResultPayload.SortLine> out = new ArrayList<>(totals.size());
        for (var e : totals.entrySet()) {
            out.add(new SortResultPayload.SortLine(e.getKey(), e.getValue(), List.of("Grouped in container")));
        }
        out.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return out;
    }

    public static void sendPresetsTo(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        ChestSortState state = ChestSortState.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer());

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

    public static void sendLockedSlotsTo(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        ChestSortState state = ChestSortState.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
        List<Integer> locked = state.getLockedSlots(player.getStringUUID());
        ServerPlayNetworking.send(player, new LockedSlotsSyncPayload(locked));
    }

    public static void broadcastPresets(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (var player : server.getPlayerList().getPlayers()) {
            sendPresetsTo(player);
        }
    }

    public static int sortMatchingInto(Inventory playerInv, Container containerInv, ContainerFilterSpec filter) {
        Set<String> allowedItemIds = new HashSet<>(filter.items() == null ? List.of() : filter.items());

        List<TagFilterRuntime> tagFilters = compileTagFilters(filter.tags());

        int moved = 0;

        for (int i = 0; i < playerInv.getContainerSize(); i++) {
            ItemStack stack = playerInv.getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

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
            playerInv.setItem(i, remainder);
            moved += (before - remainder.getCount());
        }

        return moved;
    }

    public static List<TagFilterRuntime> compileTagFilters(List<TagFilterSpec> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        ArrayList<TagFilterRuntime> out = new ArrayList<>(tags.size());
        for (TagFilterSpec tag : tags) {
            if (tag == null) continue;
            String raw = tag.tagId();
            if (raw == null) continue;
            String idStr = raw.startsWith("#") ? raw.substring(1) : raw;
            Identifier id = Identifier.tryParse(idStr);
            if (id == null) continue;
            TagKey<net.minecraft.world.item.Item> key = TagKey.create(Registries.ITEM, id);

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

    public record TagFilterRuntime(TagKey<net.minecraft.world.item.Item> tagKey, Set<String> exceptions) {
        public boolean matches(ItemStack stack, String itemId) {
            if (exceptions != null && exceptions.contains(itemId)) return false;
            return stack.is(tagKey);
        }
    }

    private static ItemStack insertInto(Container container, ItemStack stack) {
        if (stack.isEmpty()) return stack;
        ItemStack remaining = stack.copy();

        // First pass: merge into existing stacks.
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack existing = container.getItem(i);
            if (existing.isEmpty()) continue;
            if (!ItemStack.matches(existing, remaining)) continue;

            int max = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            int canAdd = max - existing.getCount();
            if (canAdd <= 0) continue;

            int toMove = Math.min(canAdd, remaining.getCount());
            existing.grow(toMove);
            remaining.shrink(toMove);
            container.setItem(i, existing);
            if (remaining.isEmpty()) return ItemStack.EMPTY;
        }

        // Second pass: fill empty slots.
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack existing = container.getItem(i);
            if (!existing.isEmpty()) continue;

            int max = Math.min(remaining.getMaxStackSize(), container.getMaxStackSize());
            int toMove = Math.min(max, remaining.getCount());

            ItemStack placed = remaining.copy();
            placed.setCount(toMove);
            container.setItem(i, placed);
            remaining.shrink(toMove);
            if (remaining.isEmpty()) return ItemStack.EMPTY;
        }

        return remaining;
    }
}
