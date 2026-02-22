package dev.dromer.chestsort.command;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.dromer.chestsort.data.ChestSortState;
import dev.dromer.chestsort.data.ContainerSnapshot;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;
import dev.dromer.chestsort.net.payload.FindHighlightsPayload;
import dev.dromer.chestsort.net.payload.OpenPresetUiPayload;
import dev.dromer.chestsort.net.payload.WandSelectionPayload;
import dev.dromer.chestsort.util.ContainerCanonicalizer;
import dev.dromer.chestsort.util.WandSelectionUtil;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public final class ChestSortCommands {
    private ChestSortCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, registryAccess));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(CommandManager.literal("cs")
            .then(CommandManager.literal("help")
                .executes(ctx -> help(ctx.getSource())))
            .then(CommandManager.literal("autosort")
                .then(CommandManager.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest(ChestSortState.AUTOSORT_NEVER);
                        builder.suggest(ChestSortState.AUTOSORT_SELECTED);
                        builder.suggest(ChestSortState.AUTOSORT_ALWAYS);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> autosortMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
            .then(CommandManager.literal("highlights")
                .then(CommandManager.literal("dismiss")
                    .executes(ctx -> highlightsDismiss(ctx.getSource())))
                .then(CommandManager.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest(ChestSortState.HIGHLIGHTS_ON);
                        builder.suggest(ChestSortState.HIGHLIGHTS_OFF);
                        builder.suggest(ChestSortState.HIGHLIGHTS_UNTIL_OPENED);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> highlightsMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
            .then(CommandManager.literal("tags")
                .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                    .executes(ctx -> tags(ctx.getSource(), ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem()))))
            .then(CommandManager.literal("blacklist")
                .then(CommandManager.literal("list")
                    .executes(ctx -> blacklistList(ctx.getSource())))
                .then(CommandManager.literal("clear")
                    .executes(ctx -> blacklistClear(ctx.getSource())))
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                        .executes(ctx -> blacklistAdd(ctx.getSource(), ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem()))))
                .then(CommandManager.literal("addPreset")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("blacklist");
                                builder.suggest("whitelist");
                                builder.suggest("everything");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> blacklistAddPreset(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"),
                                StringArgumentType.getString(ctx, "mode")
                            )))))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                        .executes(ctx -> blacklistRemove(ctx.getSource(), ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem()))))
            )
            .then(CommandManager.literal("scan")
                .executes(ctx -> scan(ctx.getSource())))
            .then(CommandManager.literal("find")
                .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                    .executes(ctx -> find(ctx.getSource(), ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem()))))
            .then(CommandManager.literal("presets")
                .then(CommandManager.literal("list")
                    .executes(ctx -> presetsList(ctx.getSource())))
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .executes(ctx -> presetsAdd(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("duplicate")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .executes(ctx -> presetsDuplicate(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .executes(ctx -> presetsRemove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("rename")
                    .then(CommandManager.argument("old", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .then(CommandManager.argument("new", StringArgumentType.string())
                            .executes(ctx -> presetsRename(ctx.getSource(), StringArgumentType.getString(ctx, "old"), StringArgumentType.getString(ctx, "new"))))))
                .then(CommandManager.literal("edit")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .executes(ctx -> presetsEdit(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("import")
                    .executes(ctx -> presetsOpenUi(ctx.getSource(), OpenPresetUiPayload.MODE_IMPORT, "")))
                .then(CommandManager.literal("export")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .executes(ctx -> presetsOpenUi(ctx.getSource(), OpenPresetUiPayload.MODE_EXPORT, StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("exportSelect")
                    .executes(ctx -> presetsOpenUi(ctx.getSource(), OpenPresetUiPayload.MODE_EXPORT_SELECT, "")))
                .then(CommandManager.literal("exportAll")
                    .executes(ctx -> presetsOpenUi(ctx.getSource(), OpenPresetUiPayload.MODE_EXPORT_ALL, "")))
            )
            .then(CommandManager.literal("wand")
                .then(CommandManager.literal("bind")
                    .executes(ctx -> wandBind(ctx.getSource())))
                .then(CommandManager.literal("unbind")
                    .executes(ctx -> wandUnbind(ctx.getSource())))
                .then(CommandManager.literal("deselect")
                    .executes(ctx -> wandDeselect(ctx.getSource())))
                .then(CommandManager.literal("copy")
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .suggests((ctx, builder) -> suggestLookBlock(ctx.getSource(), builder))
                        .executes(ctx -> wandCopy(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"))))
                    .executes(ctx -> wandCopy(ctx.getSource(), null)))
                .then(CommandManager.literal("paste")
                    .executes(ctx -> wandPaste(ctx.getSource())))
                .then(CommandManager.literal("autosort")
                    .then(CommandManager.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("on");
                            builder.suggest("off");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> wandAutosort(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(CommandManager.literal("clear")
                    .executes(ctx -> wandClear(ctx.getSource())))
                .then(CommandManager.literal("merge")
                    .executes(ctx -> wandMerge(ctx.getSource())))
            )
        );
    }

    private static CompletableFuture<Suggestions> suggestLookBlock(ServerCommandSource source, SuggestionsBuilder builder) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return builder.buildFuture();

        HitResult hit = player.raycast(6.0, 0.0f, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            var pos = bhr.getBlockPos();
            builder.suggest(pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPresetName(ServerCommandSource source, SuggestionsBuilder builder) {
        if (source == null) return builder.buildFuture();
        ChestSortState state = ChestSortState.get(source.getServer());
        return CommandSource.suggestMatching(state.getPresets().keySet(), builder);
    }

    private static ServerWorld getWorldByDimId(MinecraftServer server, String dimId) {
        if (server == null || dimId == null || dimId.isEmpty()) return null;
        for (ServerWorld w : server.getWorlds()) {
            if (w == null) continue;
            String id = w.getRegistryKey().getValue().toString();
            if (dimId.equals(id)) return w;
        }
        return null;
    }

    private static int wandBind(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs wand bind must be run by a player"));
            return 0;
        }

        ItemStack held = player.getMainHandStack();
        if (held == null || held.isEmpty()) {
            source.sendError(Text.literal("[CS] Hold an item in your main hand to bind as the wand"));
            return 0;
        }

        String itemId = String.valueOf(Registries.ITEM.getId(held.getItem()));
        ChestSortState state = ChestSortState.get(source.getServer());
        state.setWandItemId(player.getUuidAsString(), itemId);

        // Sync to client so click interception works immediately.
        String uuid = player.getUuidAsString();
        var p1 = state.getWandPos1(uuid);
        var p2 = state.getWandPos2(uuid);
        ServerPlayNetworking.send(player, new WandSelectionPayload(
            itemId,
            p1 != null,
            p1 == null ? "" : p1.dimensionId(),
            p1 == null ? 0L : p1.posLong(),
            p2 != null,
            p2 == null ? "" : p2.dimensionId(),
            p2 == null ? 0L : p2.posLong(),
            0L,
            0
        ));

        source.sendFeedback(() -> Text.literal("[CS] Wand bound to: ").formatted(Formatting.GOLD)
            .append(Text.literal(itemId).formatted(Formatting.YELLOW)), false);
        return 1;
    }

    private static int wandUnbind(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs wand unbind must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getUuidAsString();

        state.setWandItemId(uuid, "");
        state.clearWandSelection(uuid);
        state.clearWandClipboard(uuid);

        ServerPlayNetworking.send(player, new WandSelectionPayload(
            "",
            false,
            "",
            0L,
            false,
            "",
            0L,
            0L,
            0
        ));

        source.sendFeedback(() -> Text.literal("[CS] Wand unbound").formatted(Formatting.GOLD), false);
        return 1;
    }

    private static int wandDeselect(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs wand deselect must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getUuidAsString();
        String wandItemId = state.getWandItemId(uuid);
        if (wandItemId == null || wandItemId.isEmpty()) {
            source.sendError(Text.literal("[CS] No wand is bound. Use /cs wand bind first."));
            return 0;
        }

        state.clearWandSelection(uuid);

        ServerPlayNetworking.send(player, new WandSelectionPayload(
            wandItemId,
            false,
            "",
            0L,
            false,
            "",
            0L,
            0L,
            0
        ));

        source.sendFeedback(() -> Text.literal("[CS] Wand selection cleared").formatted(Formatting.GOLD), false);
        return 1;
    }

    private static int wandCopy(ServerCommandSource source, net.minecraft.util.math.BlockPos posArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs wand copy must be run by a player"));
            return 0;
        }

        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            source.sendError(Text.literal("[CS] Not in a server world"));
            return 0;
        }
        net.minecraft.util.math.BlockPos pos = posArg;
        if (pos == null) {
            HitResult hit = player.raycast(6.0, 0.0f, false);
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                pos = bhr.getBlockPos();
            }
        }
        if (pos == null) {
            source.sendError(Text.literal("[CS] Look at a container block (or pass a block position)"));
            return 0;
        }

        var be = world.getBlockEntity(pos);
        if (be == null) {
            source.sendError(Text.literal("[CS] No block entity at that position"));
            return 0;
        }

        var canonical = ContainerCanonicalizer.canonicalize(world, pos, be);
        if (canonical.snapshotInventory() == null) {
            source.sendError(Text.literal("[CS] That block is not a supported container"));
            return 0;
        }

        String dimId = world.getRegistryKey().getValue().toString();
        long canonicalPosLong = canonical.posLong();

        ChestSortState state = ChestSortState.get(source.getServer());
        ContainerFilterSpec spec = state.getFilterSpec(dimId, canonicalPosLong);
        state.setWandClipboard(player.getUuidAsString(), spec);

        source.sendFeedback(() -> Text.literal("[CS] Copied filter to wand clipboard").formatted(Formatting.GOLD), false);
        return 1;
    }

    private static ChestSortState.WandPos requireWandPos(ChestSortState state, String uuid, int which, ServerCommandSource source) {
        ChestSortState.WandPos pos = (which == 1) ? state.getWandPos1(uuid) : state.getWandPos2(uuid);
        if (pos == null) {
            source.sendError(Text.literal("[CS] Missing wand pos" + which + ". Set pos1 with left-click and pos2 with right-click using your bound wand."));
        }
        return pos;
    }

    private static int wandPaste(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs wand paste must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getUuidAsString();
        ContainerFilterSpec clip = state.getWandClipboard(uuid);
        if (clip == null) {
            source.sendError(Text.literal("[CS] Wand clipboard is empty. Use /cs wand copy first."));
            return 0;
        }

        ChestSortState.WandPos p1 = requireWandPos(state, uuid, 1, source);
        ChestSortState.WandPos p2 = requireWandPos(state, uuid, 2, source);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendError(Text.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        ServerWorld world = getWorldByDimId(source.getServer(), p1.dimensionId());
        if (world == null) {
            source.sendError(Text.literal("[CS] Could not resolve world for dimension: " + p1.dimensionId()));
            return 0;
        }

        Set<Long> targets = WandSelectionUtil.findLoadedContainerCanonicalPosLongsInBox(world, p1.pos(), p2.pos());
        for (long posLong : targets) {
            state.setFilterSpec(p1.dimensionId(), posLong, clip);
        }

        source.sendFeedback(() -> Text.literal("[CS] Pasted filter to ").formatted(Formatting.GOLD)
            .append(Text.literal(String.valueOf(targets.size())).formatted(Formatting.YELLOW))
            .append(Text.literal(" containers (loaded)").formatted(Formatting.GRAY)), false);
        return 1;
    }

    private static int wandAutosort(ServerCommandSource source, String mode) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs wand autosort must be run by a player"));
            return 0;
        }

        boolean on;
        String m = mode == null ? "" : mode.trim().toLowerCase();
        if ("on".equals(m)) on = true;
        else if ("off".equals(m)) on = false;
        else {
            source.sendError(Text.literal("[CS] Invalid mode. Use: on | off"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getUuidAsString();
        ChestSortState.WandPos p1 = requireWandPos(state, uuid, 1, source);
        ChestSortState.WandPos p2 = requireWandPos(state, uuid, 2, source);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendError(Text.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        ServerWorld world = getWorldByDimId(source.getServer(), p1.dimensionId());
        if (world == null) {
            source.sendError(Text.literal("[CS] Could not resolve world for dimension: " + p1.dimensionId()));
            return 0;
        }

        Set<Long> targets = WandSelectionUtil.findLoadedContainerCanonicalPosLongsInBox(world, p1.pos(), p2.pos());
        for (long posLong : targets) {
            ContainerFilterSpec existing = state.getFilterSpec(p1.dimensionId(), posLong);
            ContainerFilterSpec updated = new ContainerFilterSpec(existing.items(), existing.tags(), existing.presets(), on).normalized();
            state.setFilterSpec(p1.dimensionId(), posLong, updated);
        }

        source.sendFeedback(() -> Text.literal("[CS] Set autosort ").formatted(Formatting.GOLD)
            .append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED))
            .append(Text.literal(" for ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(targets.size())).formatted(Formatting.YELLOW))
            .append(Text.literal(" containers (loaded)").formatted(Formatting.GRAY)), false);
        return 1;
    }

    private static int wandClear(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs wand clear must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getUuidAsString();
        ChestSortState.WandPos p1 = requireWandPos(state, uuid, 1, source);
        ChestSortState.WandPos p2 = requireWandPos(state, uuid, 2, source);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendError(Text.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        ServerWorld world = getWorldByDimId(source.getServer(), p1.dimensionId());
        if (world == null) {
            source.sendError(Text.literal("[CS] Could not resolve world for dimension: " + p1.dimensionId()));
            return 0;
        }

        ContainerFilterSpec cleared = new ContainerFilterSpec(List.of(), List.of(), List.of(), false);
        Set<Long> targets = WandSelectionUtil.findLoadedContainerCanonicalPosLongsInBox(world, p1.pos(), p2.pos());
        for (long posLong : targets) {
            state.setFilterSpec(p1.dimensionId(), posLong, cleared);
        }

        source.sendFeedback(() -> Text.literal("[CS] Cleared filters for ").formatted(Formatting.GOLD)
            .append(Text.literal(String.valueOf(targets.size())).formatted(Formatting.YELLOW))
            .append(Text.literal(" containers (loaded)").formatted(Formatting.GRAY)), false);
        return 1;
    }

    private static int wandMerge(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs wand merge must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getUuidAsString();
        ChestSortState.WandPos p1 = requireWandPos(state, uuid, 1, source);
        ChestSortState.WandPos p2 = requireWandPos(state, uuid, 2, source);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendError(Text.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        ServerWorld world = getWorldByDimId(source.getServer(), p1.dimensionId());
        if (world == null) {
            source.sendError(Text.literal("[CS] Could not resolve world for dimension: " + p1.dimensionId()));
            return 0;
        }

        Set<Long> targets = WandSelectionUtil.findLoadedContainerCanonicalPosLongsInBox(world, p1.pos(), p2.pos());

        LinkedHashSet<String> mergedItems = new LinkedHashSet<>();
        LinkedHashSet<String> mergedPresets = new LinkedHashSet<>();
        LinkedHashMap<String, LinkedHashSet<String>> mergedTags = new LinkedHashMap<>();
        boolean anyAutosort = false;

        for (long posLong : targets) {
            ContainerFilterSpec spec = state.getFilterSpec(p1.dimensionId(), posLong);
            if (spec == null) continue;
            ContainerFilterSpec s = spec.normalized();
            anyAutosort |= s.autosort();
            if (s.items() != null) mergedItems.addAll(s.items());
            if (s.presets() != null) mergedPresets.addAll(s.presets());
            if (s.tags() != null) {
                for (var t : s.tags()) {
                    if (t == null || t.tagId() == null) continue;
                    String tagId = ContainerFilterSpec.normalizeTagId(t.tagId());
                    if (tagId.isEmpty()) continue;
                    LinkedHashSet<String> exc = mergedTags.computeIfAbsent(tagId, k -> new LinkedHashSet<>());
                    if (t.exceptions() != null) exc.addAll(ContainerFilterSpec.normalizeStrings(t.exceptions()));
                }
            }
        }

        ArrayList<dev.dromer.chestsort.filter.TagFilterSpec> tags = new ArrayList<>(mergedTags.size());
        for (var e : mergedTags.entrySet()) {
            tags.add(new dev.dromer.chestsort.filter.TagFilterSpec(e.getKey(), List.copyOf(e.getValue())));
        }

        ContainerFilterSpec merged = new ContainerFilterSpec(List.copyOf(mergedItems), tags, List.copyOf(mergedPresets), anyAutosort).normalized();
        state.setWandClipboard(uuid, merged);

        source.sendFeedback(() -> Text.literal("[CS] Merged ").formatted(Formatting.GOLD)
            .append(Text.literal(String.valueOf(targets.size())).formatted(Formatting.YELLOW))
            .append(Text.literal(" containers into wand clipboard").formatted(Formatting.GRAY)), false);
        return 1;
    }

    private static int help(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("[CS] ").formatted(Formatting.GOLD)
            .append(Text.literal("ChestSort help").formatted(Formatting.YELLOW)), false);
        source.sendFeedback(() -> Text.literal(""), false);

        source.sendFeedback(() -> Text.literal("What this mod does:").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("- Lets you define per-container filter rules (items + tags).").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("- Lets you save reusable rules as presets, then apply them to containers.").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("- Adds a container-side UI to edit rules and a preset editor UI.").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal(""), false);

        source.sendFeedback(() -> Text.literal("Container filter UI (how to use):").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("- Open a chest/barrel, then click the \"Filter\" button.").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("- Left panel shows your current filter (Filter Items / Filter Tags / Filter Presets).").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY)
            .append(Text.literal("Autosort").formatted(Formatting.YELLOW))
            .append(Text.literal(" toggle (left panel) enables automatic sorting for that container (depending on ").formatted(Formatting.GRAY))
            .append(Text.literal("/cs autosort").formatted(Formatting.GREEN))
            .append(Text.literal(" mode).").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("- Right panel shows search results; click the green + to add.").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("- Click a tag on the left to open its exception browser; green + adds exceptions, red x removes.").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal(""), false);

        source.sendFeedback(() -> Text.literal("Search tips:").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("- Type normal text to search items.").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("- Type ").formatted(Formatting.GRAY)
            .append(Text.literal("#something").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal(" to search tags.").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("- Type ").formatted(Formatting.GRAY)
            .append(Text.literal("&name").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal(" to search presets and add them to the container.").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal(""), false);

        source.sendFeedback(() -> Text.literal("Presets (local vs global editing):").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("- Add presets to a container via ").formatted(Formatting.GRAY)
            .append(Text.literal("&search").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal("; applied presets then appear on the left list.").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("- Click a preset on the left to edit it locally for this container.").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("- If local edits are unsaved, the preset name shows an ").formatted(Formatting.GRAY)
            .append(Text.literal("*").formatted(Formatting.YELLOW))
            .append(Text.literal(".").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("- Toggle \"Edit preset\" to edit the global preset (affects all containers).").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal(""), false);

        source.sendFeedback(() -> Text.literal("Import / export presets:").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("- Export creates a ").formatted(Formatting.GRAY)
            .append(Text.literal("cs2|").formatted(Formatting.YELLOW))
            .append(Text.literal(" string you can share.").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("- Import takes a ").formatted(Formatting.GRAY)
            .append(Text.literal("cs2|").formatted(Formatting.YELLOW))
            .append(Text.literal(" string and creates a new preset.").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal(""), false);

        source.sendFeedback(() -> Text.literal("Commands:").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY)
            .append(Text.literal("/cs scan").formatted(Formatting.GREEN))
            .append(Text.literal("  (rescans loaded containers for ").formatted(Formatting.GRAY))
            .append(Text.literal("/cs find").formatted(Formatting.GREEN))
            .append(Text.literal(")").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY)
            .append(Text.literal("/cs find <item>").formatted(Formatting.GREEN))
            .append(Text.literal("  (lists containers that contain an item)").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY).append(Text.literal("/cs presets add <name>").formatted(Formatting.GREEN)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY).append(Text.literal("/cs presets duplicate <name>").formatted(Formatting.GREEN)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY).append(Text.literal("/cs presets remove <name>").formatted(Formatting.GREEN)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY).append(Text.literal("/cs presets rename <old> <new>").formatted(Formatting.GREEN)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY).append(Text.literal("/cs presets edit <name>").formatted(Formatting.GREEN)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY).append(Text.literal("/cs presets list").formatted(Formatting.GREEN)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY).append(Text.literal("/cs presets import").formatted(Formatting.GREEN)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY).append(Text.literal("/cs presets export <name>").formatted(Formatting.GREEN)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY).append(Text.literal("/cs presets exportSelect").formatted(Formatting.GREEN)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY).append(Text.literal("/cs presets exportAll").formatted(Formatting.GREEN)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY)
            .append(Text.literal("/cs autosort ").formatted(Formatting.GREEN))
            .append(Text.literal("<never|selected|always>").formatted(Formatting.YELLOW)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY)
            .append(Text.literal("/cs highlights ").formatted(Formatting.GREEN))
            .append(Text.literal("<on|off|until_opened>").formatted(Formatting.YELLOW)), false);
        source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY)
            .append(Text.literal("/cs highlights dismiss").formatted(Formatting.GREEN))
            .append(Text.literal("  (clears current highlights)").formatted(Formatting.GRAY)), false);
        return 1;
    }

    private static int autosortMode(ServerCommandSource source, String mode) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs autosort must be run by a player"));
            return 0;
        }

        String m = mode == null ? "" : mode.trim().toLowerCase();
        if (!ChestSortState.AUTOSORT_NEVER.equals(m) && !ChestSortState.AUTOSORT_SELECTED.equals(m) && !ChestSortState.AUTOSORT_ALWAYS.equals(m)) {
            source.sendError(Text.literal("[CS] Invalid autosort mode. Use: never | selected | always"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        state.setAutosortMode(player.getUuidAsString(), m);
        source.sendFeedback(() -> Text.literal("[CS] Autosort mode set to: " + m), false);
        return 1;
    }

    private static int highlightsMode(ServerCommandSource source, String mode) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs highlights must be run by a player"));
            return 0;
        }

        String m = mode == null ? "" : mode.trim().toLowerCase();
        if (!ChestSortState.HIGHLIGHTS_ON.equals(m) && !ChestSortState.HIGHLIGHTS_OFF.equals(m) && !ChestSortState.HIGHLIGHTS_UNTIL_OPENED.equals(m)) {
            source.sendError(Text.literal("[CS] Invalid highlights mode. Use: on | off | until_opened"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getUuidAsString();
        state.setHighlightsMode(uuid, m);

        String currentDim = player.getEntityWorld().getRegistryKey().getValue().toString();
        String lastItem = state.getLastFindItem(uuid);
        if (lastItem == null) lastItem = "";

        boolean highlightsOff = ChestSortState.HIGHLIGHTS_OFF.equals(m);
        boolean untilOpened = ChestSortState.HIGHLIGHTS_UNTIL_OPENED.equals(m);

        if (highlightsOff) {
            // Clear any existing in-world/container highlights immediately.
            ServerPlayNetworking.send(player, new FindHighlightsPayload(currentDim, lastItem, List.of()));
            ServerPlayNetworking.send(player, new dev.dromer.chestsort.net.payload.ContainerHighlightPayload("", false));
            state.clearHighlightsOnNextOpen(uuid);
        } else {
            // Restore last-find highlights (if any) immediately.
            if (!lastItem.isEmpty()) {
                List<ContainerSnapshot.Match> matches = state.find(lastItem);
                java.util.ArrayList<Long> highlightPos = new java.util.ArrayList<>();
                for (ContainerSnapshot.Match match : matches) {
                    if (match == null) continue;
                    if (!currentDim.equals(match.dimensionId())) continue;
                    if (match.pos() == null) continue;
                    highlightPos.add(match.pos().asLong());
                    if (highlightPos.size() >= 200) break;
                }
                ServerPlayNetworking.send(player, new FindHighlightsPayload(currentDim, lastItem, highlightPos));
            } else {
                // No last find: ensure world highlights are cleared.
                ServerPlayNetworking.send(player, new FindHighlightsPayload(currentDim, "", List.of()));
            }

            // If a container is currently open, update slot highlight immediately.
            boolean highlightContainer = false;
            if (!lastItem.isEmpty() && player.currentScreenHandler instanceof net.minecraft.screen.GenericContainerScreenHandler handler) {
                net.minecraft.inventory.Inventory inv = handler.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    net.minecraft.item.ItemStack st = inv.getStack(i);
                    if (st == null || st.isEmpty()) continue;
                    String id = String.valueOf(net.minecraft.registry.Registries.ITEM.getId(st.getItem()));
                    if (lastItem.equals(id)) {
                        highlightContainer = true;
                        break;
                    }
                }
            }
            ServerPlayNetworking.send(player, new dev.dromer.chestsort.net.payload.ContainerHighlightPayload(lastItem, highlightContainer));

            if (untilOpened && !lastItem.isEmpty()) {
                state.armClearHighlightsOnNextOpen(uuid);
            } else {
                state.clearHighlightsOnNextOpen(uuid);
            }
        }

        source.sendFeedback(() -> Text.literal("[CS] Highlights mode set to: " + m), false);
        return 1;
    }

    private static int highlightsDismiss(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs highlights dismiss must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getUuidAsString();
        state.setLastFindItem(uuid, "");
        state.clearHighlightsOnNextOpen(uuid);

        String currentDim = player.getEntityWorld().getRegistryKey().getValue().toString();
        // Clear in-world outlines and in-container slot/text overlay.
        ServerPlayNetworking.send(player, new FindHighlightsPayload(currentDim, "", List.of()));
        ServerPlayNetworking.send(player, new dev.dromer.chestsort.net.payload.ContainerHighlightPayload("", false));

        source.sendFeedback(() -> Text.literal("[CS] Highlights dismissed."), false);
        return 1;
    }

    private static int presetsAdd(ServerCommandSource source, String name) {
        MinecraftServer server = source.getServer();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs presets must be run by a player"));
            return 0;
        }

        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            source.sendError(Text.literal("[CS] Preset name cannot be empty"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(server);
        boolean ok = state.addPreset(n, new ContainerFilterSpec(List.of(), List.of(), List.of()));
        if (!ok) {
            source.sendError(Text.literal("[CS] Preset already exists (or invalid name): " + n));
            return 0;
        }

        dev.dromer.chestsort.net.ChestSortNetworking.broadcastPresets(server);
        dev.dromer.chestsort.net.ChestSortNetworking.sendPresetsTo(player);
        ServerPlayNetworking.send(player, new OpenPresetUiPayload(OpenPresetUiPayload.MODE_EDIT, n));
        source.sendFeedback(() -> Text.literal("[CS] Added preset: " + n), false);
        return 1;
    }

    private static int presetsRemove(ServerCommandSource source, String name) {
        MinecraftServer server = source.getServer();
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            source.sendError(Text.literal("[CS] Preset name cannot be empty"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(server);
        boolean removed = state.removePreset(n);
        if (!removed) {
            source.sendError(Text.literal("[CS] Preset not found: " + n));
            return 0;
        }

        dev.dromer.chestsort.net.ChestSortNetworking.broadcastPresets(server);
        source.sendFeedback(() -> Text.literal("[CS] Removed preset: " + n), false);
        return 1;
    }

    private static int presetsDuplicate(ServerCommandSource source, String name) {
        MinecraftServer server = source.getServer();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs presets must be run by a player"));
            return 0;
        }

        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            source.sendError(Text.literal("[CS] Preset name cannot be empty"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(server);
        if (!state.hasPreset(n)) {
            source.sendError(Text.literal("[CS] Preset not found: " + n));
            return 0;
        }

        ContainerFilterSpec spec = state.getPresets().get(n);
        if (spec == null) {
            spec = new ContainerFilterSpec(List.of(), List.of(), List.of());
        }

        String base = n + " Copy";
        String out = base;
        if (state.hasPreset(out)) {
            for (int i = 2; i < 10_000; i++) {
                String candidate = base + " " + i;
                if (!state.hasPreset(candidate)) {
                    out = candidate;
                    break;
                }
            }
            if (state.hasPreset(out)) {
                out = base + " " + System.currentTimeMillis();
            }
        }

        state.setPreset(out, spec);
        dev.dromer.chestsort.net.ChestSortNetworking.broadcastPresets(server);
        dev.dromer.chestsort.net.ChestSortNetworking.sendPresetsTo(player);
        final String outName = out;
        source.sendFeedback(() -> Text.literal("[CS] Duplicated preset: " + n + " -> " + outName), false);
        return 1;
    }

    private static int presetsRename(ServerCommandSource source, String oldName, String newName) {
        MinecraftServer server = source.getServer();
        String o = oldName == null ? "" : oldName.trim();
        String n = newName == null ? "" : newName.trim();
        if (o.isEmpty() || n.isEmpty()) {
            source.sendError(Text.literal("[CS] Preset names cannot be empty"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(server);
        boolean ok = state.renamePreset(o, n);
        if (!ok) {
            source.sendError(Text.literal("[CS] Rename failed (missing old name or new name already exists)"));
            return 0;
        }

        dev.dromer.chestsort.net.ChestSortNetworking.broadcastPresets(server);
        source.sendFeedback(() -> Text.literal("[CS] Renamed preset: " + o + " -> " + n), false);

        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            dev.dromer.chestsort.net.ChestSortNetworking.sendPresetsTo(player);
            ServerPlayNetworking.send(player, new OpenPresetUiPayload(OpenPresetUiPayload.MODE_EDIT, n));
        }
        return 1;
    }

    private static int presetsEdit(ServerCommandSource source, String name) {
        MinecraftServer server = source.getServer();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs presets must be run by a player"));
            return 0;
        }

        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            source.sendError(Text.literal("[CS] Preset name cannot be empty"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(server);
        if (!state.hasPreset(n)) {
            source.sendError(Text.literal("[CS] Preset not found: " + n));
            return 0;
        }

        dev.dromer.chestsort.net.ChestSortNetworking.sendPresetsTo(player);
        ServerPlayNetworking.send(player, new OpenPresetUiPayload(OpenPresetUiPayload.MODE_EDIT, n));
        return 1;
    }

    private static int presetsList(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        ChestSortState state = ChestSortState.get(server);

        java.util.ArrayList<String> names = new java.util.ArrayList<>(state.getPresets().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);

        int total = names.size();
        if (total == 0) {
            source.sendFeedback(() -> Text.literal("[CS] No presets."), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("[CS] Presets (" + total + "):").formatted(Formatting.AQUA), false);

        int limit = 200;
        for (int i = 0; i < names.size() && i < limit; i++) {
            String n = names.get(i);
            if (n == null || n.isEmpty()) continue;
            source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY)
                .append(Text.literal(n).formatted(Formatting.YELLOW)), false);
        }

        if (total > limit) {
            int more = total - limit;
            source.sendFeedback(() -> Text.literal("... and " + more + " more").formatted(Formatting.GRAY), false);
        }

        return total;
    }

    private static int presetsOpenUi(ServerCommandSource source, byte mode, String name) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs presets must be run by a player"));
            return 0;
        }

        if (mode == OpenPresetUiPayload.MODE_EXPORT) {
            MinecraftServer server = source.getServer();
            ChestSortState state = ChestSortState.get(server);
            String n = name == null ? "" : name.trim();
            if (n.isEmpty()) {
                source.sendError(Text.literal("[CS] Preset name cannot be empty"));
                return 0;
            }
            if (!state.hasPreset(n)) {
                source.sendError(Text.literal("[CS] Preset not found: " + n));
                return 0;
            }

            // Ensure the client has the latest preset definitions for exporting.
            dev.dromer.chestsort.net.ChestSortNetworking.sendPresetsTo(player);
        }

        if (mode == OpenPresetUiPayload.MODE_EXPORT_ALL) {
            // Ensure the client has the latest preset definitions for exporting.
            dev.dromer.chestsort.net.ChestSortNetworking.sendPresetsTo(player);
        }

        if (mode == OpenPresetUiPayload.MODE_EXPORT_SELECT) {
            // Ensure the client has the latest preset definitions for exporting.
            dev.dromer.chestsort.net.ChestSortNetworking.sendPresetsTo(player);
        }

        ServerPlayNetworking.send(player, new OpenPresetUiPayload(mode, name == null ? "" : name));
        return 1;
    }

    private static int scan(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        ChestSortState state = ChestSortState.get(server);

        int scanned = state.scanLoadedContainers(server);
        long nowMs = System.currentTimeMillis();
        state.setLastFullScanMs(nowMs);

        source.sendFeedback(() -> Text.literal("[CS] Scanned " + scanned + " containers at " + Instant.ofEpochMilli(nowMs)), false);
        return scanned;
    }

    private static int find(ServerCommandSource source, Item item) {
        MinecraftServer server = source.getServer();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs find must be run by a player"));
            return 0;
        }

        Identifier itemId = Registries.ITEM.getId(item);
        ChestSortState state = ChestSortState.get(server);
        state.setLastFindItem(player.getUuidAsString(), itemId.toString());

        String highlightsMode = state.getHighlightsMode(player.getUuidAsString());
        boolean highlightsOff = ChestSortState.HIGHLIGHTS_OFF.equals(highlightsMode);
        boolean untilOpened = ChestSortState.HIGHLIGHTS_UNTIL_OPENED.equals(highlightsMode);

        List<ContainerSnapshot.Match> matches = state.find(itemId.toString());
        if (matches.isEmpty()) {
            source.sendFeedback(() -> Text.literal("[CS] No containers found containing: " + itemId), false);
            // Clear any previous in-world highlights.
            ServerPlayNetworking.send(player, new FindHighlightsPayload(player.getEntityWorld().getRegistryKey().getValue().toString(), itemId.toString(), List.of()));
            if (highlightsOff) {
                source.sendFeedback(() -> Text.literal("[CS] Highlights are OFF. Use /cs highlights on to enable.").formatted(Formatting.GRAY), false);
            }
            return 0;
        }

        String currentDim = player.getEntityWorld().getRegistryKey().getValue().toString();
        if (highlightsOff) {
            // Ensure old outlines are removed and inform the player.
            ServerPlayNetworking.send(player, new FindHighlightsPayload(currentDim, itemId.toString(), List.of()));
            source.sendFeedback(() -> Text.literal("[CS] Highlights are OFF. Use /cs highlights on to enable.").formatted(Formatting.GRAY), false);
        } else {
            // Send in-world highlights for the player's current dimension only.
            java.util.ArrayList<Long> highlightPos = new java.util.ArrayList<>();
            for (ContainerSnapshot.Match m : matches) {
                if (m == null) continue;
                if (!currentDim.equals(m.dimensionId())) continue;
                if (m.pos() == null) continue;
                highlightPos.add(m.pos().asLong());
                if (highlightPos.size() >= 200) break;
            }
            ServerPlayNetworking.send(player, new FindHighlightsPayload(currentDim, itemId.toString(), highlightPos));

            if (untilOpened) {
                state.armClearHighlightsOnNextOpen(player.getUuidAsString());
            } else {
                state.clearHighlightsOnNextOpen(player.getUuidAsString());
            }
        }

        source.sendFeedback(() -> Text.literal("[CS] Found " + matches.size() + " container(s) containing: " + itemId), false);
        for (ContainerSnapshot.Match match : matches) {
            source.sendFeedback(() -> Text.literal("- " + match.dimensionId() + " " + match.pos().toShortString() + " x" + match.count()), false);
        }

        return matches.size();
    }

    private static int blacklistAdd(ServerCommandSource source, Item item) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs blacklist add must be run by a player"));
            return 0;
        }
        if (item == null) {
            source.sendError(Text.literal("[CS] Missing item"));
            return 0;
        }

        String itemId = String.valueOf(Registries.ITEM.getId(item));
        ChestSortState state = ChestSortState.get(source.getServer());
        boolean changed = state.addItemToBlacklist(player.getUuidAsString(), itemId);
        if (changed) {
            source.sendFeedback(() -> Text.literal("[CS] Blacklisted: ").formatted(Formatting.GOLD)
                .append(Text.literal(itemId).formatted(Formatting.YELLOW)), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("[CS] Already blacklisted: ").formatted(Formatting.GOLD)
            .append(Text.literal(itemId).formatted(Formatting.YELLOW)), false);
        return 0;
    }

    private static int blacklistRemove(ServerCommandSource source, Item item) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs blacklist remove must be run by a player"));
            return 0;
        }
        if (item == null) {
            source.sendError(Text.literal("[CS] Missing item"));
            return 0;
        }

        String itemId = String.valueOf(Registries.ITEM.getId(item));
        ChestSortState state = ChestSortState.get(source.getServer());
        boolean changed = state.removeItemFromBlacklist(player.getUuidAsString(), itemId);
        if (changed) {
            source.sendFeedback(() -> Text.literal("[CS] Un-blacklisted: ").formatted(Formatting.GOLD)
                .append(Text.literal(itemId).formatted(Formatting.YELLOW)), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("[CS] Not blacklisted: ").formatted(Formatting.GOLD)
            .append(Text.literal(itemId).formatted(Formatting.YELLOW)), false);
        return 0;
    }

    private static int blacklistList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs blacklist list must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        java.util.ArrayList<String> items = new java.util.ArrayList<>(state.getItemBlacklist(player.getUuidAsString()));
        items.sort(java.util.Comparator.naturalOrder());

        source.sendFeedback(() -> Text.literal("[CS] ").formatted(Formatting.GOLD)
            .append(Text.literal("Blacklisted items: ").formatted(Formatting.YELLOW))
            .append(Text.literal(String.valueOf(items.size())).formatted(Formatting.AQUA)), false);

        if (items.isEmpty()) {
            source.sendFeedback(() -> Text.literal("- (none)").formatted(Formatting.GRAY), false);
            return 1;
        }

        int shown = 0;
        for (String itemId : items) {
            if (itemId == null || itemId.isEmpty()) continue;
            source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY)
                .append(Text.literal(itemId).formatted(Formatting.WHITE)), false);
            shown++;
            if (shown >= 200) break;
        }
        return shown;
    }

    private static int blacklistClear(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs blacklist clear must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        int removed = state.clearItemBlacklist(player.getUuidAsString());
        source.sendFeedback(() -> Text.literal("[CS] Cleared blacklist: ").formatted(Formatting.GOLD)
            .append(Text.literal(String.valueOf(removed)).formatted(Formatting.AQUA)), false);
        return removed;
    }

    private static int blacklistAddPreset(ServerCommandSource source, String presetName, String modeRaw) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[CS] /cs blacklist addPreset must be run by a player"));
            return 0;
        }

        String name = presetName == null ? "" : presetName.trim();
        if (name.isEmpty()) {
            source.sendError(Text.literal("[CS] Missing preset name"));
            return 0;
        }

        String mode = modeRaw == null ? "" : modeRaw.trim().toLowerCase(Locale.ROOT);
        if (mode.isEmpty()) mode = "everything";

        final String nameFinal = name;
        final String modeFinal = mode;

        ChestSortState state = ChestSortState.get(source.getServer());
        ContainerFilterSpec preset = state.getPreset(name);
        if (preset == null) {
            source.sendError(Text.literal("[CS] Preset not found: " + name));
            return 0;
        }

        ContainerFilterSpec base = preset.normalized();

        ContainerFilterSpec selected;
        switch (mode) {
            case "whitelist" -> selected = new ContainerFilterSpec(base.items(), base.tags(), List.of(), false).normalized();
            case "blacklist" -> {
                // "Blacklist part" of a preset is interpreted as items/tags contributed by its applied presets.
                ContainerFilterSpec appliedOnly = new ContainerFilterSpec(List.of(), List.of(), base.presets(), false).normalized();
                selected = state.resolveWithAppliedPresets(appliedOnly);
            }
            case "everything" -> selected = state.resolveWithAppliedPresets(base);
            default -> {
                source.sendError(Text.literal("[CS] Invalid mode: " + mode + " (use: blacklist|whitelist|everything)"));
                return 0;
            }
        }

        LinkedHashSet<String> itemIds = chestsort$collectItemIdsFromSpec(selected);
        if (itemIds.isEmpty()) {
            source.sendFeedback(() -> Text.literal("[CS] Preset \"" + nameFinal + "\" (" + modeFinal + ") contains no items to add."), false);
            return 1;
        }

        int added = state.addItemsToBlacklist(player.getUuidAsString(), itemIds);
        source.sendFeedback(() -> Text.literal("[CS] Added ").formatted(Formatting.GOLD)
            .append(Text.literal(String.valueOf(added)).formatted(Formatting.AQUA))
            .append(Text.literal(" item(s) from preset \"").formatted(Formatting.GOLD))
            .append(Text.literal(nameFinal).formatted(Formatting.YELLOW))
            .append(Text.literal("\" (" + modeFinal + ") to your blacklist.").formatted(Formatting.GOLD)), false);
        return Math.max(1, added);
    }

    private static LinkedHashSet<String> chestsort$collectItemIdsFromSpec(ContainerFilterSpec spec) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (spec == null) return out;
        ContainerFilterSpec s = spec.normalized();

        if (s.items() != null) {
            for (String raw : s.items()) {
                Identifier id = chestsort$parseItemIdentifier(raw);
                if (id == null) continue;
                out.add(id.toString());
            }
        }

        if (s.tags() != null) {
            for (TagFilterSpec tf : s.tags()) {
                if (tf == null) continue;
                Identifier tagId = chestsort$parseTagIdentifier(tf.tagId());
                if (tagId == null) continue;

                HashSet<String> exc = chestsort$expandExceptionsToItemIds(tf.exceptions());
                TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, tagId);
                for (var entry : Registries.ITEM.iterateEntries(tagKey)) {
                    if (entry == null || entry.value() == null) continue;
                    String itemId = String.valueOf(Registries.ITEM.getId(entry.value()));
                    if (itemId == null || itemId.isEmpty()) continue;
                    if (exc.contains(itemId)) continue;
                    out.add(itemId);
                }
            }
        }

        return out;
    }

    private static HashSet<String> chestsort$expandExceptionsToItemIds(List<String> exceptions) {
        HashSet<String> out = new HashSet<>();
        if (exceptions == null || exceptions.isEmpty()) return out;

        for (String raw : exceptions) {
            if (raw == null) continue;
            String t = raw.trim();
            if (t.isEmpty()) continue;

            if (t.startsWith("#")) {
                Identifier tagId = chestsort$parseTagIdentifier(t);
                if (tagId == null) continue;
                TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, tagId);
                for (var entry : Registries.ITEM.iterateEntries(tagKey)) {
                    if (entry == null || entry.value() == null) continue;
                    String itemId = String.valueOf(Registries.ITEM.getId(entry.value()));
                    if (itemId == null || itemId.isEmpty()) continue;
                    out.add(itemId);
                }
            } else {
                Identifier id = chestsort$parseItemIdentifier(t);
                if (id == null) continue;
                out.add(id.toString());
            }
        }

        return out;
    }

    private static Identifier chestsort$parseItemIdentifier(String itemId) {
        if (itemId == null) return null;
        String t = itemId.trim();
        if (t.isEmpty()) return null;
        Identifier id = Identifier.tryParse(t);
        if (id != null) return id;
        if (t.indexOf(':') < 0) {
            return Identifier.tryParse("minecraft:" + t);
        }
        return null;
    }

    private static Identifier chestsort$parseTagIdentifier(String tagId) {
        if (tagId == null) return null;
        String t = tagId.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("#")) t = t.substring(1);

        Identifier id = Identifier.tryParse(t);
        if (id != null) return id;
        if (t.indexOf(':') < 0) {
            return Identifier.tryParse("minecraft:" + t);
        }
        return null;
    }

    private static int tags(ServerCommandSource source, Item item) {
        if (item == null) {
            source.sendError(Text.literal("[CS] Missing item"));
            return 0;
        }

        Identifier itemId = Registries.ITEM.getId(item);
        java.util.ArrayList<String> tags = new java.util.ArrayList<>();

        try {
            // Modern registry API: items expose their tag memberships via their registry entry.
            for (TagKey<Item> tag : item.getRegistryEntry().streamTags().toList()) {
                if (tag == null || tag.id() == null) continue;
                tags.add("#" + tag.id());
            }
        } catch (Throwable t) {
            // Fallback for mappings/versions where streamTags() may not be available.
            // (We keep this graceful rather than hard-crashing the command.)
            source.sendError(Text.literal("[CS] Failed to read tags for: " + itemId));
            return 0;
        }

        java.util.Collections.sort(tags);

        source.sendFeedback(() -> Text.literal("[CS] ").formatted(Formatting.GOLD)
            .append(Text.literal("Tags for ").formatted(Formatting.YELLOW))
            .append(Text.literal(itemId.toString()).formatted(Formatting.WHITE))
            .append(Text.literal(": ").formatted(Formatting.YELLOW))
            .append(Text.literal(String.valueOf(tags.size())).formatted(Formatting.AQUA)), false);

        if (tags.isEmpty()) {
            source.sendFeedback(() -> Text.literal("- (none)").formatted(Formatting.GRAY), false);
            return 1;
        }

        for (String tag : tags) {
            source.sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY)
                .append(Text.literal(tag).formatted(Formatting.LIGHT_PURPLE)), false);
        }

        return tags.size();
    }
}
