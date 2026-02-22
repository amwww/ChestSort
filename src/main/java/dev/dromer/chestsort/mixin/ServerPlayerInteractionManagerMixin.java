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
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {

    @Inject(
        method = "interactBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chestsort$onInteractBlock(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        // Wand selection: right-click sets pos2.
        ChestSortState state = ChestSortState.get(serverWorld.getServer());
        String uuid = player == null ? "" : player.getUuidAsString();
        String wandItemId = state.getWandItemId(uuid);
        if (wandItemId != null && !wandItemId.isEmpty() && stack != null && !stack.isEmpty()) {
            String heldId = String.valueOf(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()));
            if (wandItemId.equals(heldId)) {
                BlockPos clickPos = hitResult.getBlockPos();
                String dimId = serverWorld.getRegistryKey().getValue().toString();
                state.setWandPos2(uuid, dimId, clickPos.asLong());
                player.sendMessage(net.minecraft.text.Text.literal("[CS] ").formatted(net.minecraft.util.Formatting.GOLD)
                    .append(net.minecraft.text.Text.literal("Wand pos2 set to ").formatted(net.minecraft.util.Formatting.GRAY))
                    .append(net.minecraft.text.Text.literal(clickPos.getX() + " " + clickPos.getY() + " " + clickPos.getZ()).formatted(net.minecraft.util.Formatting.YELLOW)), false);
                cir.setReturnValue(ActionResult.SUCCESS);
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
        Inventory inv = canonical.snapshotInventory();
        long canonicalPosLong = canonical.posLong();

        state = ChestSortState.get(serverWorld.getServer());

        // If this is a double chest, migrate any legacy per-half filters into the canonical key.
        // (Helps users upgrading from older versions where each half had a separate whitelist.)
        if ("chest".equals(containerType) && be instanceof ChestBlockEntity) {
            long clickedPosLong = pos.asLong();
            if (clickedPosLong != canonicalPosLong) {
                String dimId = serverWorld.getRegistryKey().getValue().toString();

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
        String dimId = serverWorld.getRegistryKey().getValue().toString();
        var spec = state.getFilterSpec(dimId, canonicalPosLong);
        var blacklist = state.getBlacklistSpec(dimId, canonicalPosLong);
        boolean whitelistPriority = state.whitelistPriority(dimId, canonicalPosLong);
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

        String playerUuid = player.getUuidAsString();
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
        method = "interactBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
        at = @At("RETURN")
    )
    private void chestsort$autosortAfterOpen(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (!(world instanceof ServerWorld serverWorld)) return;

        ActionResult result = cir.getReturnValue();
        if (result == null || result == ActionResult.PASS) return;

        if (player == null) return;
        if (!(player.currentScreenHandler instanceof net.minecraft.screen.GenericContainerScreenHandler handler)) return;

        BlockPos pos = hitResult.getBlockPos();
        BlockEntity be = serverWorld.getBlockEntity(pos);
        if (be == null) return;

        var canonical = ContainerCanonicalizer.canonicalize(serverWorld, pos, be);
        if (canonical.snapshotInventory() == null) return;

        long canonicalPosLong = canonical.posLong();
        String dimId = serverWorld.getRegistryKey().getValue().toString();

        ChestSortState state = ChestSortState.get(serverWorld.getServer());
        String mode = state.getAutosortMode(player.getUuidAsString());
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

        Inventory containerInv = handler.getInventory();
        ChestSortNetworking.SortOperationResult result2 = ChestSortNetworking.sortMatchingIntoDetailed(
            state,
            player.getUuidAsString(),
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
            player.currentScreenHandler.sendContentUpdates();
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
