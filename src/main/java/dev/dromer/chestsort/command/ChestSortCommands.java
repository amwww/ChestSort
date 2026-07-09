package dev.dromer.chestsort.command;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class ChestSortCommands {
    private ChestSortCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, registryAccess));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(Commands.literal("cs")
            .then(Commands.literal("help")
                .executes(ctx -> help(ctx.getSource())))
            .then(Commands.literal("autosort")
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest(ChestSortState.AUTOSORT_NEVER);
                        builder.suggest(ChestSortState.AUTOSORT_SELECTED);
                        builder.suggest(ChestSortState.AUTOSORT_ALWAYS);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> autosortMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
            .then(Commands.literal("highlights")
                .then(Commands.literal("dismiss")
                    .executes(ctx -> highlightsDismiss(ctx.getSource())))
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest(ChestSortState.HIGHLIGHTS_ON);
                        builder.suggest(ChestSortState.HIGHLIGHTS_OFF);
                        builder.suggest(ChestSortState.HIGHLIGHTS_UNTIL_OPENED);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> highlightsMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
            .then(Commands.literal("tags")
                .then(Commands.argument("item", ItemArgument.item(registryAccess))
                    .executes(ctx -> tags(ctx.getSource(), ItemArgument.getItem(ctx, "item").item().value()))))
            .then(Commands.literal("blacklist")
                .then(Commands.literal("list")
                    .executes(ctx -> blacklistList(ctx.getSource())))
                .then(Commands.literal("clear")
                    .executes(ctx -> blacklistClear(ctx.getSource())))
                .then(Commands.literal("mode")
                    .executes(ctx -> blacklistMode(ctx.getSource(), ""))
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("preventSort");
                            builder.suggest("strictPreventSort");
                            builder.suggest("preventEntry");
                            builder.suggest("strictPreventEntry");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> blacklistMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("add")
                    .then(Commands.argument("item", ItemArgument.item(registryAccess))
                        .executes(ctx -> blacklistAdd(ctx.getSource(), ItemArgument.getItem(ctx, "item").item().value()))))
                .then(Commands.literal("addPreset")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .then(Commands.argument("mode", StringArgumentType.word())
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
                .then(Commands.literal("remove")
                    .then(Commands.argument("item", ItemArgument.item(registryAccess))
                        .executes(ctx -> blacklistRemove(ctx.getSource(), ItemArgument.getItem(ctx, "item").item().value()))))
            )
            .then(Commands.literal("scan")
                .executes(ctx -> scan(ctx.getSource())))
            .then(Commands.literal("find")
                .then(Commands.argument("item", ItemArgument.item(registryAccess))
                    .executes(ctx -> find(ctx.getSource(), ItemArgument.getItem(ctx, "item").item().value()))))
            .then(Commands.literal("presets")
                .then(Commands.literal("list")
                    .executes(ctx -> presetsList(ctx.getSource())))
                .then(Commands.literal("add")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> presetsAdd(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("duplicate")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .executes(ctx -> presetsDuplicate(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .executes(ctx -> presetsRemove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("rename")
                    .then(Commands.argument("old", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .then(Commands.argument("new", StringArgumentType.string())
                            .executes(ctx -> presetsRename(ctx.getSource(), StringArgumentType.getString(ctx, "old"), StringArgumentType.getString(ctx, "new"))))))
                .then(Commands.literal("edit")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .executes(ctx -> presetsEdit(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("import")
                    .executes(ctx -> presetsOpenUi(ctx.getSource(), OpenPresetUiPayload.MODE_IMPORT, "")))
                .then(Commands.literal("export")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestPresetName(ctx.getSource(), builder))
                        .executes(ctx -> presetsOpenUi(ctx.getSource(), OpenPresetUiPayload.MODE_EXPORT, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("exportSelect")
                    .executes(ctx -> presetsOpenUi(ctx.getSource(), OpenPresetUiPayload.MODE_EXPORT_SELECT, "")))
                .then(Commands.literal("exportAll")
                    .executes(ctx -> presetsOpenUi(ctx.getSource(), OpenPresetUiPayload.MODE_EXPORT_ALL, "")))
            )
            .then(Commands.literal("wand")
                .then(Commands.literal("bind")
                    .executes(ctx -> wandBind(ctx.getSource())))
                .then(Commands.literal("unbind")
                    .executes(ctx -> wandUnbind(ctx.getSource())))
                .then(Commands.literal("deselect")
                    .executes(ctx -> wandDeselect(ctx.getSource())))
                .then(Commands.literal("copy")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .suggests((ctx, builder) -> suggestLookBlock(ctx.getSource(), builder))
                        .executes(ctx -> wandCopy(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"))))
                    .executes(ctx -> wandCopy(ctx.getSource(), null)))
                .then(Commands.literal("paste")
                    .executes(ctx -> wandPaste(ctx.getSource())))
                .then(Commands.literal("autosort")
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("on");
                            builder.suggest("off");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> wandAutosort(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("clear")
                    .executes(ctx -> wandClear(ctx.getSource())))
                .then(Commands.literal("merge")
                    .executes(ctx -> wandMerge(ctx.getSource())))
            )
        );
    }

    private static CompletableFuture<Suggestions> suggestLookBlock(CommandSourceStack source, SuggestionsBuilder builder) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return builder.buildFuture();

        HitResult hit = player.pick(6.0, 0.0f, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            var pos = bhr.getBlockPos();
            builder.suggest(pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPresetName(CommandSourceStack source, SuggestionsBuilder builder) {
        if (source == null) return builder.buildFuture();
        ChestSortState state = ChestSortState.get(source.getServer());
        return SharedSuggestionProvider.suggest(state.getPresets().keySet(), builder);
    }

    private static net.minecraft.server.level.ServerLevel getWorldByDimId(MinecraftServer server, String dimId) {
        if (server == null || dimId == null || dimId.isEmpty()) return null;
        for (net.minecraft.server.level.ServerLevel w : server.getAllLevels()) {
            if (w == null) continue;
            String id = w.dimension().identifier().toString();
            if (dimId.equals(id)) return w;
        }
        return null;
    }

    private static int wandBind(CommandSourceStack source) {
        net.minecraft.server.level.ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs wand bind must be run by a player"));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            source.sendFailure(Component.literal("[CS] Hold an item in your main hand to bind as the wand"));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        ChestSortState state = ChestSortState.get(source.getServer());
        state.setWandItemId(player.getStringUUID(), itemId);

        // Sync to client so click interception works immediately.
        String uuid = player.getStringUUID();
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

        source.sendSuccess(() -> Component.literal("[CS] Wand bound to: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(itemId).withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    private static int wandUnbind(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs wand unbind must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getStringUUID();

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

        source.sendSuccess(() -> Component.literal("[CS] Wand unbound").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int wandDeselect(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs wand deselect must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getStringUUID();
        String wandItemId = state.getWandItemId(uuid);
        if (wandItemId == null || wandItemId.isEmpty()) {
            source.sendFailure(Component.literal("[CS] No wand is bound. Use /cs wand bind first."));
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

        source.sendSuccess(() -> Component.literal("[CS] Wand selection cleared").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int wandCopy(CommandSourceStack source, net.minecraft.core.BlockPos posArg) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs wand copy must be run by a player"));
            return 0;
        }

        if (!(player.level() instanceof ServerLevel world)) {
            source.sendFailure(Component.literal("[CS] Not in a server world"));
            return 0;
        }
        net.minecraft.core.BlockPos pos = posArg;
        if (pos == null) {
            HitResult hit = player.pick(6.0, 0.0f, false);
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                pos = bhr.getBlockPos();
            }
        }
        if (pos == null) {
            source.sendFailure(Component.literal("[CS] Look at a container block (or pass a block position)"));
            return 0;
        }

        var be = world.getBlockEntity(pos);
        if (be == null) {
            source.sendFailure(Component.literal("[CS] No block entity at that position"));
            return 0;
        }

        var canonical = ContainerCanonicalizer.canonicalize(world, pos, be);
        if (canonical.snapshotInventory() == null) {
            source.sendFailure(Component.literal("[CS] That block is not a supported container"));
            return 0;
        }

        String dimId = world.dimension().identifier().toString();
        long canonicalPosLong = canonical.posLong();

        ChestSortState state = ChestSortState.get(source.getServer());
        ContainerFilterSpec spec = state.getFilterSpec(dimId, canonicalPosLong);
        state.setWandClipboard(player.getStringUUID(), spec);

        source.sendSuccess(() -> Component.literal("[CS] Copied filter to wand clipboard").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static ChestSortState.WandPos requireWandPos(ChestSortState state, String uuid, int which, CommandSourceStack source) {
        ChestSortState.WandPos pos = (which == 1) ? state.getWandPos1(uuid) : state.getWandPos2(uuid);
        if (pos == null) {
            source.sendFailure(Component.literal("[CS] Missing wand pos" + which + ". Set pos1 with left-click and pos2 with right-click using your bound wand."));
        }
        return pos;
    }

    private static int wandPaste(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs wand paste must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getStringUUID();
        ContainerFilterSpec clip = state.getWandClipboard(uuid);
        if (clip == null) {
            source.sendFailure(Component.literal("[CS] Wand clipboard is empty. Use /cs wand copy first."));
            return 0;
        }

        ChestSortState.WandPos p1 = requireWandPos(state, uuid, 1, source);
        ChestSortState.WandPos p2 = requireWandPos(state, uuid, 2, source);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendFailure(Component.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        ServerLevel world = getWorldByDimId(source.getServer(), p1.dimensionId());
        if (world == null) {
            source.sendFailure(Component.literal("[CS] Could not resolve world for dimension: " + p1.dimensionId()));
            return 0;
        }

        Set<Long> targets = WandSelectionUtil.findLoadedContainerCanonicalPosLongsInBox(world, p1.pos(), p2.pos());
        for (long posLong : targets) {
            state.setFilterSpec(p1.dimensionId(), posLong, clip);
        }

        source.sendSuccess(() -> Component.literal("[CS] Pasted filter to ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(String.valueOf(targets.size())).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" containers (loaded)").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int wandAutosort(CommandSourceStack source, String mode) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs wand autosort must be run by a player"));
            return 0;
        }

        boolean on;
        String m = mode == null ? "" : mode.trim().toLowerCase();
        if ("on".equals(m)) on = true;
        else if ("off".equals(m)) on = false;
        else {
            source.sendFailure(Component.literal("[CS] Invalid mode. Use: on | off"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getStringUUID();
        ChestSortState.WandPos p1 = requireWandPos(state, uuid, 1, source);
        ChestSortState.WandPos p2 = requireWandPos(state, uuid, 2, source);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendFailure(Component.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        ServerLevel world = getWorldByDimId(source.getServer(), p1.dimensionId());
        if (world == null) {
            source.sendFailure(Component.literal("[CS] Could not resolve world for dimension: " + p1.dimensionId()));
            return 0;
        }

        Set<Long> targets = WandSelectionUtil.findLoadedContainerCanonicalPosLongsInBox(world, p1.pos(), p2.pos());
        for (long posLong : targets) {
            ContainerFilterSpec existing = state.getFilterSpec(p1.dimensionId(), posLong);
            ContainerFilterSpec updated = new ContainerFilterSpec(existing.items(), existing.tags(), existing.presets(), on).normalized();
            state.setFilterSpec(p1.dimensionId(), posLong, updated);
        }

        source.sendSuccess(() -> Component.literal("[CS] Set autosort ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED))
            .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.valueOf(targets.size())).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" containers (loaded)").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int wandClear(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs wand clear must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getStringUUID();
        ChestSortState.WandPos p1 = requireWandPos(state, uuid, 1, source);
        ChestSortState.WandPos p2 = requireWandPos(state, uuid, 2, source);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendFailure(Component.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        ServerLevel world = getWorldByDimId(source.getServer(), p1.dimensionId());
        if (world == null) {
            source.sendFailure(Component.literal("[CS] Could not resolve world for dimension: " + p1.dimensionId()));
            return 0;
        }

        ContainerFilterSpec cleared = new ContainerFilterSpec(List.of(), List.of(), List.of(), false);
        Set<Long> targets = WandSelectionUtil.findLoadedContainerCanonicalPosLongsInBox(world, p1.pos(), p2.pos());
        for (long posLong : targets) {
            state.setFilterSpec(p1.dimensionId(), posLong, cleared);
        }

        source.sendSuccess(() -> Component.literal("[CS] Cleared filters for ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(String.valueOf(targets.size())).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" containers (loaded)").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int wandMerge(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs wand merge must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getStringUUID();
        ChestSortState.WandPos p1 = requireWandPos(state, uuid, 1, source);
        ChestSortState.WandPos p2 = requireWandPos(state, uuid, 2, source);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendFailure(Component.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        ServerLevel world = getWorldByDimId(source.getServer(), p1.dimensionId());
        if (world == null) {
            source.sendFailure(Component.literal("[CS] Could not resolve world for dimension: " + p1.dimensionId()));
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

        source.sendSuccess(() -> Component.literal("[CS] Merged ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(String.valueOf(targets.size())).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" containers into wand clipboard").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[CS] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("ChestSort help").withStyle(ChatFormatting.YELLOW)), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("What this mod does:").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("- Lets you define per-container filter rules (items + tags).").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("- Lets you save reusable rules as presets, then apply them to containers.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("- Adds a container-side UI to edit rules and a preset editor UI.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("Container filter UI (how to use):").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("- Open a chest/barrel, then click the \"Filter\" button.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("- Left panel shows your current filter (Filter Items / Filter Tags / Filter Presets).").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("Autosort").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" toggle (left panel) enables automatic sorting for that container (depending on ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("/cs autosort").withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" mode).").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("- Right panel shows search results; click the green + to add.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("- Click a tag on the left to open its exception browser; green + adds exceptions, red x removes.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("Search tips:").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("- Type normal text to search items.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("- Type ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("#something").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(" to search tags.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("- Type ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("&name").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(" to search presets and add them to the container.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("Presets (local vs global editing):").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("- Add presets to a container via ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("&search").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal("; applied presets then appear on the left list.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("- Click a preset on the left to edit it locally for this container.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("- If local edits are unsaved, the preset name shows an ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("*").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(".").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("- Toggle \"Edit preset\" to edit the global preset (affects all containers).").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("Import / export presets:").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("- Export creates a ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("cs2|").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" string you can share.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("- Import takes a ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("cs2|").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" string and creates a new preset.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("Commands:").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("/cs scan").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("  (rescans loaded containers for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("/cs find").withStyle(ChatFormatting.GREEN))
            .append(Component.literal(")").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("/cs find <item>").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("  (lists containers that contain an item)").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY).append(Component.literal("/cs presets add <name>").withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY).append(Component.literal("/cs presets duplicate <name>").withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY).append(Component.literal("/cs presets remove <name>").withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY).append(Component.literal("/cs presets rename <old> <new>").withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY).append(Component.literal("/cs presets edit <name>").withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY).append(Component.literal("/cs presets list").withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY).append(Component.literal("/cs presets import").withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY).append(Component.literal("/cs presets export <name>").withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY).append(Component.literal("/cs presets exportSelect").withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY).append(Component.literal("/cs presets exportAll").withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("/cs autosort ").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("<never|selected|always>").withStyle(ChatFormatting.YELLOW)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("/cs highlights ").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("<on|off|until_opened>").withStyle(ChatFormatting.YELLOW)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("/cs highlights dismiss").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("  (clears current highlights)").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("/cs blacklist ").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("<list|add|remove|clear>").withStyle(ChatFormatting.YELLOW)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("/cs blacklist addPreset ").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("<name> ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("<blacklist|whitelist|everything>").withStyle(ChatFormatting.YELLOW)), false);
        source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("/cs blacklist mode ").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("<preventSort|strictPreventSort|preventEntry|strictPreventEntry>").withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    private static int autosortMode(CommandSourceStack source, String mode) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs autosort must be run by a player"));
            return 0;
        }

        String m = mode == null ? "" : mode.trim().toLowerCase();
        if (!ChestSortState.AUTOSORT_NEVER.equals(m) && !ChestSortState.AUTOSORT_SELECTED.equals(m) && !ChestSortState.AUTOSORT_ALWAYS.equals(m)) {
            source.sendFailure(Component.literal("[CS] Invalid autosort mode. Use: never | selected | always"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        state.setAutosortMode(player.getStringUUID(), m);
        source.sendSuccess(() -> Component.literal("[CS] Autosort mode set to: " + m), false);
        return 1;
    }

    private static int highlightsMode(CommandSourceStack source, String mode) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs highlights must be run by a player"));
            return 0;
        }

        String m = mode == null ? "" : mode.trim().toLowerCase();
        if (!ChestSortState.HIGHLIGHTS_ON.equals(m) && !ChestSortState.HIGHLIGHTS_OFF.equals(m) && !ChestSortState.HIGHLIGHTS_UNTIL_OPENED.equals(m)) {
            source.sendFailure(Component.literal("[CS] Invalid highlights mode. Use: on | off | until_opened"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getStringUUID();
        state.setHighlightsMode(uuid, m);

        String currentDim = player.level().dimension().identifier().toString();
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
            if (!lastItem.isEmpty() && player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu handler) {
                net.minecraft.world.Container inv = handler.getContainer();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack st = inv.getItem(i);
                    if (st == null || st.isEmpty()) continue;
                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
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

        source.sendSuccess(() -> Component.literal("[CS] Highlights mode set to: " + m), false);
        return 1;
    }

    private static int highlightsDismiss(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs highlights dismiss must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        String uuid = player.getStringUUID();
        state.setLastFindItem(uuid, "");
        state.clearHighlightsOnNextOpen(uuid);

        String currentDim = player.level().dimension().identifier().toString();
        // Clear in-world outlines and in-container slot/text overlay.
        ServerPlayNetworking.send(player, new FindHighlightsPayload(currentDim, "", List.of()));
        ServerPlayNetworking.send(player, new dev.dromer.chestsort.net.payload.ContainerHighlightPayload("", false));

        source.sendSuccess(() -> Component.literal("[CS] Highlights dismissed."), false);
        return 1;
    }

    private static int presetsAdd(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs presets must be run by a player"));
            return 0;
        }

        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            source.sendFailure(Component.literal("[CS] Preset name cannot be empty"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(server);
        boolean ok = state.addPreset(n, new ContainerFilterSpec(List.of(), List.of(), List.of()));
        if (!ok) {
            source.sendFailure(Component.literal("[CS] Preset already exists (or invalid name): " + n));
            return 0;
        }

        dev.dromer.chestsort.net.ChestSortNetworking.broadcastPresets(server);
        dev.dromer.chestsort.net.ChestSortNetworking.sendPresetsTo(player);
        ServerPlayNetworking.send(player, new OpenPresetUiPayload(OpenPresetUiPayload.MODE_EDIT, n));
        source.sendSuccess(() -> Component.literal("[CS] Added preset: " + n), false);
        return 1;
    }

    private static int presetsRemove(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            source.sendFailure(Component.literal("[CS] Preset name cannot be empty"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(server);
        boolean removed = state.removePreset(n);
        if (!removed) {
            source.sendFailure(Component.literal("[CS] Preset not found: " + n));
            return 0;
        }

        dev.dromer.chestsort.net.ChestSortNetworking.broadcastPresets(server);
        source.sendSuccess(() -> Component.literal("[CS] Removed preset: " + n), false);
        return 1;
    }

    private static int presetsDuplicate(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs presets must be run by a player"));
            return 0;
        }

        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            source.sendFailure(Component.literal("[CS] Preset name cannot be empty"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(server);
        if (!state.hasPreset(n)) {
            source.sendFailure(Component.literal("[CS] Preset not found: " + n));
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
        source.sendSuccess(() -> Component.literal("[CS] Duplicated preset: " + n + " -> " + outName), false);
        return 1;
    }

    private static int presetsRename(CommandSourceStack source, String oldName, String newName) {
        MinecraftServer server = source.getServer();
        String o = oldName == null ? "" : oldName.trim();
        String n = newName == null ? "" : newName.trim();
        if (o.isEmpty() || n.isEmpty()) {
            source.sendFailure(Component.literal("[CS] Preset names cannot be empty"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(server);
        boolean ok = state.renamePreset(o, n);
        if (!ok) {
            source.sendFailure(Component.literal("[CS] Rename failed (missing old name or new name already exists)"));
            return 0;
        }

        dev.dromer.chestsort.net.ChestSortNetworking.broadcastPresets(server);
        source.sendSuccess(() -> Component.literal("[CS] Renamed preset: " + o + " -> " + n), false);

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            dev.dromer.chestsort.net.ChestSortNetworking.sendPresetsTo(player);
            ServerPlayNetworking.send(player, new OpenPresetUiPayload(OpenPresetUiPayload.MODE_EDIT, n));
        }
        return 1;
    }

    private static int presetsEdit(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs presets must be run by a player"));
            return 0;
        }

        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            source.sendFailure(Component.literal("[CS] Preset name cannot be empty"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(server);
        if (!state.hasPreset(n)) {
            source.sendFailure(Component.literal("[CS] Preset not found: " + n));
            return 0;
        }

        dev.dromer.chestsort.net.ChestSortNetworking.sendPresetsTo(player);
        ServerPlayNetworking.send(player, new OpenPresetUiPayload(OpenPresetUiPayload.MODE_EDIT, n));
        return 1;
    }

    private static int presetsList(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ChestSortState state = ChestSortState.get(server);

        java.util.ArrayList<String> names = new java.util.ArrayList<>(state.getPresets().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);

        int total = names.size();
        if (total == 0) {
            source.sendSuccess(() -> Component.literal("[CS] No presets."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[CS] Presets (" + total + "):").withStyle(ChatFormatting.AQUA), false);

        int limit = 200;
        for (int i = 0; i < names.size() && i < limit; i++) {
            String n = names.get(i);
            if (n == null || n.isEmpty()) continue;
            source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(n).withStyle(ChatFormatting.YELLOW)), false);
        }

        if (total > limit) {
            int more = total - limit;
            source.sendSuccess(() -> Component.literal("... and " + more + " more").withStyle(ChatFormatting.GRAY), false);
        }

        return total;
    }

    private static int presetsOpenUi(CommandSourceStack source, byte mode, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs presets must be run by a player"));
            return 0;
        }

        if (mode == OpenPresetUiPayload.MODE_EXPORT) {
            MinecraftServer server = source.getServer();
            ChestSortState state = ChestSortState.get(server);
            String n = name == null ? "" : name.trim();
            if (n.isEmpty()) {
                source.sendFailure(Component.literal("[CS] Preset name cannot be empty"));
                return 0;
            }
            if (!state.hasPreset(n)) {
                source.sendFailure(Component.literal("[CS] Preset not found: " + n));
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

    private static int scan(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ChestSortState state = ChestSortState.get(server);

        int scanned = state.scanLoadedContainers(server);
        long nowMs = System.currentTimeMillis();
        state.setLastFullScanMs(nowMs);

        source.sendSuccess(() -> Component.literal("[CS] Scanned " + scanned + " containers at " + Instant.ofEpochMilli(nowMs)), false);
        return scanned;
    }

    private static int find(CommandSourceStack source, Item item) {
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs find must be run by a player"));
            return 0;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        ChestSortState state = ChestSortState.get(server);
        state.setLastFindItem(player.getStringUUID(), itemId.toString());

        String highlightsMode = state.getHighlightsMode(player.getStringUUID());
        boolean highlightsOff = ChestSortState.HIGHLIGHTS_OFF.equals(highlightsMode);
        boolean untilOpened = ChestSortState.HIGHLIGHTS_UNTIL_OPENED.equals(highlightsMode);

        List<ContainerSnapshot.Match> matches = state.find(itemId.toString());
        if (matches.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[CS] No containers found containing: " + itemId), false);
            // Clear any previous in-world highlights.
            ServerPlayNetworking.send(player, new FindHighlightsPayload(player.level().dimension().identifier().toString(), itemId.toString(), List.of()));
            if (highlightsOff) {
                source.sendSuccess(() -> Component.literal("[CS] Highlights are OFF. Use /cs highlights on to enable.").withStyle(ChatFormatting.GRAY), false);
            }
            return 0;
        }

        String currentDim = player.level().dimension().identifier().toString();
        if (highlightsOff) {
            // Ensure old outlines are removed and inform the player.
            ServerPlayNetworking.send(player, new FindHighlightsPayload(currentDim, itemId.toString(), List.of()));
            source.sendSuccess(() -> Component.literal("[CS] Highlights are OFF. Use /cs highlights on to enable.").withStyle(ChatFormatting.GRAY), false);
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
                state.armClearHighlightsOnNextOpen(player.getStringUUID());
            } else {
                state.clearHighlightsOnNextOpen(player.getStringUUID());
            }
        }

        source.sendSuccess(() -> Component.literal("[CS] Found " + matches.size() + " container(s) containing: " + itemId), false);
        for (ContainerSnapshot.Match match : matches) {
            source.sendSuccess(() -> Component.literal("- " + match.dimensionId() + " " + match.pos().toShortString() + " x" + match.count()), false);
        }

        return matches.size();
    }

    private static int blacklistAdd(CommandSourceStack source, Item item) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs blacklist add must be run by a player"));
            return 0;
        }
        if (item == null) {
            source.sendFailure(Component.literal("[CS] Missing item"));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        ChestSortState state = ChestSortState.get(source.getServer());
        boolean changed = state.addItemToBlacklist(player.getStringUUID(), itemId);
        if (changed) {
            source.sendSuccess(() -> Component.literal("[CS] Blacklisted: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(itemId).withStyle(ChatFormatting.YELLOW)), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("[CS] Already blacklisted: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(itemId).withStyle(ChatFormatting.YELLOW)), false);
        return 0;
    }

    private static int blacklistRemove(CommandSourceStack source, Item item) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs blacklist remove must be run by a player"));
            return 0;
        }
        if (item == null) {
            source.sendFailure(Component.literal("[CS] Missing item"));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        ChestSortState state = ChestSortState.get(source.getServer());
        boolean changed = state.removeItemFromBlacklist(player.getStringUUID(), itemId);
        if (changed) {
            source.sendSuccess(() -> Component.literal("[CS] Un-blacklisted: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(itemId).withStyle(ChatFormatting.YELLOW)), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("[CS] Not blacklisted: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(itemId).withStyle(ChatFormatting.YELLOW)), false);
        return 0;
    }

    private static int blacklistList(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs blacklist list must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        java.util.ArrayList<String> items = new java.util.ArrayList<>(state.getItemBlacklist(player.getStringUUID()));
        items.sort(java.util.Comparator.naturalOrder());

        source.sendSuccess(() -> Component.literal("[CS] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Blacklisted items: ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(String.valueOf(items.size())).withStyle(ChatFormatting.AQUA)), false);

        if (items.isEmpty()) {
            source.sendSuccess(() -> Component.literal("- (none)").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        int shown = 0;
        for (String itemId : items) {
            if (itemId == null || itemId.isEmpty()) continue;
            source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(itemId).withStyle(ChatFormatting.WHITE)), false);
            shown++;
            if (shown >= 200) break;
        }
        return shown;
    }

    private static int blacklistClear(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs blacklist clear must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        int removed = state.clearItemBlacklist(player.getStringUUID());
        source.sendSuccess(() -> Component.literal("[CS] Cleared blacklist: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(String.valueOf(removed)).withStyle(ChatFormatting.AQUA)), false);
        return removed;
    }

    private static int blacklistMode(CommandSourceStack source, String modeRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs blacklist mode must be run by a player"));
            return 0;
        }

        ChestSortState state = ChestSortState.get(source.getServer());
        if (modeRaw == null || modeRaw.trim().isEmpty()) {
            String current = state.getBlacklistMode(player.getStringUUID());
            source.sendSuccess(() -> Component.literal("[CS] Blacklist mode is: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(current).withStyle(ChatFormatting.YELLOW)), false);
            return 1;
        }

        String m = modeRaw.trim().toLowerCase(Locale.ROOT);
        // Allow friendlier camelCase inputs.
        m = m.replace("_", "");

        if (!ChestSortState.BLACKLIST_MODE_PREVENT_SORT.equals(m)
            && !ChestSortState.BLACKLIST_MODE_STRICT_PREVENT_SORT.equals(m)
            && !ChestSortState.BLACKLIST_MODE_PREVENT_ENTRY.equals(m)
            && !ChestSortState.BLACKLIST_MODE_STRICT_PREVENT_ENTRY.equals(m)) {
            source.sendFailure(Component.literal("[CS] Invalid blacklist mode. Use: preventSort | strictPreventSort | preventEntry | strictPreventEntry"));
            return 0;
        }

        final String mFinal = m;
        state.setBlacklistMode(player.getStringUUID(), mFinal);
        source.sendSuccess(() -> Component.literal("[CS] Blacklist mode set to: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(mFinal).withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    private static int blacklistAddPreset(CommandSourceStack source, String presetName, String modeRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[CS] /cs blacklist addPreset must be run by a player"));
            return 0;
        }

        String name = presetName == null ? "" : presetName.trim();
        if (name.isEmpty()) {
            source.sendFailure(Component.literal("[CS] Missing preset name"));
            return 0;
        }

        String mode = modeRaw == null ? "" : modeRaw.trim().toLowerCase(Locale.ROOT);
        if (mode.isEmpty()) mode = "everything";

        final String nameFinal = name;
        final String modeFinal = mode;

        ChestSortState state = ChestSortState.get(source.getServer());
        ContainerFilterSpec preset = state.getPreset(name);
        if (preset == null) {
            source.sendFailure(Component.literal("[CS] Preset not found: " + name));
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
                source.sendFailure(Component.literal("[CS] Invalid mode: " + mode + " (use: blacklist|whitelist|everything)"));
                return 0;
            }
        }

        LinkedHashSet<String> itemIds = chestsort$collectItemIdsFromSpec(selected);
        if (itemIds.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[CS] Preset \"" + nameFinal + "\" (" + modeFinal + ") contains no items to add."), false);
            return 1;
        }

        int added = state.addItemsToBlacklist(player.getStringUUID(), itemIds);
        source.sendSuccess(() -> Component.literal("[CS] Added ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(String.valueOf(added)).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" item(s) from preset \"").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(nameFinal).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("\" (" + modeFinal + ") to your blacklist.").withStyle(ChatFormatting.GOLD)), false);
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
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                for (var entry : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                    if (entry == null || entry.value() == null) continue;
                    String itemId = BuiltInRegistries.ITEM.getKey(entry.value()).toString();
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
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                for (var entry : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                    if (entry == null || entry.value() == null) continue;
                    String itemId = BuiltInRegistries.ITEM.getKey(entry.value()).toString();
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

    private static int tags(CommandSourceStack source, Item item) {
        if (item == null) {
            source.sendFailure(Component.literal("[CS] Missing item"));
            return 0;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        java.util.ArrayList<String> tags = new java.util.ArrayList<>();

        try {
            // Modern registry API: items expose their tag memberships via their registry entry.
            for (TagKey<Item> tag : item.builtInRegistryHolder().tags().toList()) {
                if (tag == null || tag.location() == null) continue;
                tags.add("#" + tag.location());
            }
        } catch (Throwable t) {
            // Fallback for mappings/versions where streamTags() may not be available.
            // (We keep this graceful rather than hard-crashing the command.)
            source.sendFailure(Component.literal("[CS] Failed to read tags for: " + itemId));
            return 0;
        }

        java.util.Collections.sort(tags);

        source.sendSuccess(() -> Component.literal("[CS] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Tags for ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(itemId.toString()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(": ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(String.valueOf(tags.size())).withStyle(ChatFormatting.AQUA)), false);

        if (tags.isEmpty()) {
            source.sendSuccess(() -> Component.literal("- (none)").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        for (String tag : tags) {
            source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(tag).withStyle(ChatFormatting.LIGHT_PURPLE)), false);
        }

        return tags.size();
    }
}
