package dev.dromer.chestsort.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.dromer.chestsort.data.ChestSortState;
import dev.dromer.chestsort.net.ChestSortNetworking;
import dev.dromer.chestsort.net.payload.ContainerContextPayload;
import dev.dromer.chestsort.net.payload.ContainerContextV2Payload;
import dev.dromer.chestsort.net.payload.ContainerContextV3Payload;
import dev.dromer.chestsort.net.payload.ContainerHighlightPayload;
import dev.dromer.chestsort.net.payload.FindHighlightsPayload;
import dev.dromer.chestsort.net.payload.SortResultPayload;
import dev.dromer.chestsort.util.ContainerCanonicalizer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerInteractionManagerMixin {

    @Inject(
        method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chestsort$onInteractBlock(ServerPlayer player, Level world, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }

        // Wand selection: right-click sets pos2.
        ChestSortState state = ChestSortState.get(serverWorld.getServer());
        String uuid = player == null ? "" : player.getStringUUID();
        String wandItemId = state.getWandItemId(uuid);
        if (wandItemId != null && !wandItemId.isEmpty() && stack != null && !stack.isEmpty()) {
            String heldId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (wandItemId.equals(heldId)) {
                BlockPos clickPos = hitResult.getBlockPos();
                String dimId = serverWorld.dimension().identifier().toString();
                state.setWandPos2(uuid, dimId, clickPos.asLong());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[CS] ").withStyle(net.minecraft.ChatFormatting.GOLD)
                    .append(net.minecraft.network.chat.Component.literal("Wand pos2 set to ").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(net.minecraft.network.chat.Component.literal(clickPos.getX() + " " + clickPos.getY() + " " + clickPos.getZ()).withStyle(net.minecraft.ChatFormatting.YELLOW)), false);
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockEntity be = serverWorld.getBlockEntity(pos);
        if (be == null) {
            return;
        }

        var canonical = ContainerCanonicalizer.canonicalize(serverWorld, pos, be);
        if (canonical.snapshotInventory() == null) return;
        String containerType = canonical.containerType();
        Container inv = canonical.snapshotInventory();
        long canonicalPosLong = canonical.posLong();

        state = ChestSortState.get(serverWorld.getServer());
        state.setOpenContainer(player.getStringUUID(), serverWorld.dimension().identifier().toString(), canonicalPosLong);

        // If this is a double chest, migrate any legacy per-half filters into the canonical key.
        // (Helps users upgrading from older versions where each half had a separate whitelist.)
        if ("chest".equals(containerType) && be instanceof ChestBlockEntity) {
            long clickedPosLong = pos.asLong();
            if (clickedPosLong != canonicalPosLong) {
                String dimId = serverWorld.dimension().identifier().toString();

                var a = state.getFilterSpec(dimId, canonicalPosLong);
                var b = state.getFilterSpec(dimId, clickedPosLong);
                var mergedItems = new java.util.LinkedHashSet<String>();
                mergedItems.addAll(a.items());
                mergedItems.addAll(b.items());

                var mergedPresets = new java.util.LinkedHashSet<String>();
                mergedPresets.addAll(a.presets());
                mergedPresets.addAll(b.presets());

                var mergedTags = new java.util.LinkedHashMap<String, java.util.LinkedHashSet<String>>();
                java.util.function.Consumer<dev.dromer.chestsort.filter.TagFilterSpec> addTag = (t) -> {
                    if (t == null || t.tagId() == null) return;
                    String tagId = dev.dromer.chestsort.filter.ContainerFilterSpec.normalizeTagId(t.tagId());
                    if (tagId.isEmpty()) return;
                    var set = mergedTags.computeIfAbsent(tagId, k -> new java.util.LinkedHashSet<>());
                    if (t.exceptions() != null) {
                        for (String exc : t.exceptions()) {
                            if (exc != null && !exc.trim().isEmpty()) set.add(exc.trim());
                        }
                    }
                };

                for (var t : a.tags()) addTag.accept(t);
                for (var t : b.tags()) addTag.accept(t);

                java.util.ArrayList<dev.dromer.chestsort.filter.TagFilterSpec> tags = new java.util.ArrayList<>(mergedTags.size());
                for (var e : mergedTags.entrySet()) {
                    tags.add(new dev.dromer.chestsort.filter.TagFilterSpec(e.getKey(), java.util.List.copyOf(e.getValue())));
                }

                var mergedSpec = new dev.dromer.chestsort.filter.ContainerFilterSpec(
                    java.util.List.copyOf(mergedItems),
                    tags,
                    java.util.List.copyOf(mergedPresets)
                ).normalized();

                if (!mergedSpec.isEmpty()) {
                    state.setFilterSpec(dimId, canonicalPosLong, mergedSpec);
                    state.setFilterSpec(dimId, clickedPosLong, new dev.dromer.chestsort.filter.ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of()));
                }
            }
        }

        state.updateFromBlockEntity(serverWorld, () -> canonicalPosLong, inv, containerType);

        // Update client-side context (used for filter editing + sort button).
        String dimId = serverWorld.dimension().identifier().toString();
        var spec = state.getFilterSpec(dimId, canonicalPosLong);
        var blacklist = state.getBlacklistSpec(dimId, canonicalPosLong);
        boolean whitelistPriority = state.whitelistPriority(dimId, canonicalPosLong);
        System.err.println("[ChestSort] Sending ContainerContext dim=" + dimId + " pos=" + canonicalPosLong + " type=" + containerType);
        // Keep legacy payload for older clients (items only).
        ServerPlayNetworking.send(player, new ContainerContextPayload(dimId, canonicalPosLong, containerType, spec.items()));
        // v2 payload for newer clients (items + tags).
        ServerPlayNetworking.send(player, new ContainerContextV2Payload(dimId, canonicalPosLong, containerType, spec));
        // v3 payload includes container blacklist + priority.
        ServerPlayNetworking.send(player, new ContainerContextV3Payload(dimId, canonicalPosLong, containerType, spec, blacklist, whitelistPriority));

        // Presets sync for the filter UI.
        ChestSortNetworking.sendPresetsTo(player);

        // Locked player slots for "protected slots" feature.
        ChestSortNetworking.sendLockedSlotsTo(player);

        String playerUuid = player.getStringUUID();
        String highlightsMode = state.getHighlightsMode(playerUuid);
        boolean highlightsOff = ChestSortState.HIGHLIGHTS_OFF.equals(highlightsMode);

        String lastItem = state.getLastFindItem(playerUuid);
        boolean highlight = false;
        if (highlightsOff) {
            // Make sure any previous in-container highlights are removed.
            ServerPlayNetworking.send(player, new ContainerHighlightPayload("", false));
        } else {
            highlight = !lastItem.isEmpty() && state.containerContains(serverWorld, canonicalPosLong, lastItem);
            ServerPlayNetworking.send(player, new ContainerHighlightPayload(lastItem, highlight));
        }

        // One-shot highlight mode: clear only when a highlighted container is opened.
        if (!highlightsOff && highlight && state.shouldClearHighlightsOnNextOpen(playerUuid)) {
            // Clear world outlines.
            ServerPlayNetworking.send(player, new FindHighlightsPayload(dimId, lastItem == null ? "" : lastItem, java.util.List.of()));
            // Stop future container-open highlights.
            state.setLastFindItem(playerUuid, "");
            state.clearHighlightsOnNextOpen(playerUuid);
        }
    }

    @Inject(
        method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
        at = @At("RETURN")
    )
    private void chestsort$autosortAfterOpen(ServerPlayer player, Level world, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(world instanceof ServerLevel serverWorld)) return;

        InteractionResult result = cir.getReturnValue();
        if (result == null || result == InteractionResult.PASS) return;

        if (player == null) return;
        if (!(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu handler)) return;

        BlockPos pos = hitResult.getBlockPos();
        BlockEntity be = serverWorld.getBlockEntity(pos);
        if (be == null) return;

        var canonical = ContainerCanonicalizer.canonicalize(serverWorld, pos, be);
        if (canonical.snapshotInventory() == null) return;

        long canonicalPosLong = canonical.posLong();
        String dimId = serverWorld.dimension().identifier().toString();

        ChestSortState state = ChestSortState.get(serverWorld.getServer());
        String mode = state.getAutosortMode(player.getStringUUID());
        if (ChestSortState.AUTOSORT_NEVER.equals(mode)) return;

        var filter = state.getFilterSpec(dimId, canonicalPosLong);
        if (filter == null) return;

        boolean shouldTrigger;
        if (ChestSortState.AUTOSORT_ALWAYS.equals(mode)) {
            shouldTrigger = true;
        } else {
            // selected
            shouldTrigger = filter.autosort();
        }
        if (!shouldTrigger) return;

        var effective = state.resolveWithAppliedPresets(filter);
        if (effective == null || effective.isEmpty()) return;

        var blacklist = state.getBlacklistSpec(dimId, canonicalPosLong);
        var effectiveBlacklist = blacklist == null ? new dev.dromer.chestsort.filter.ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of()) : state.resolveBlacklistWithAppliedPresets(blacklist);
        boolean whitelistPriority = state.whitelistPriority(dimId, canonicalPosLong);

        Container containerInv = handler.getContainer();
        ChestSortNetworking.SortOperationResult result2 = ChestSortNetworking.sortMatchingIntoDetailed(
            state,
            player.getStringUUID(),
            dimId,
            canonicalPosLong,
            player.getInventory(),
            containerInv,
            filter,
            effective,
            blacklist,
            effectiveBlacklist,
            whitelistPriority
        );
        int moved = result2 == null ? 0 : result2.movedTotal();
        if (moved > 0) {
            player.containerMenu.sendAllDataToRemote();
            state.updateFromBlockEntity(serverWorld, () -> canonicalPosLong, containerInv, canonical.containerType());
        }

        ServerPlayNetworking.send(player, new SortResultPayload(
            dimId,
            canonicalPosLong,
            SortResultPayload.KIND_AUTOSORT,
            mode,
            filter.autosort(),
            result2 == null ? 0L : result2.undoId(),
            moved,
            result2 == null ? java.util.List.of() : result2.lines()
        ));
    }
}
