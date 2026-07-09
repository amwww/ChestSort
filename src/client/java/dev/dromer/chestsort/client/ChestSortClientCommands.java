package dev.dromer.chestsort.client;

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

import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Client-only reimplementation of the /cs command tree. This mod never relies on any
 * server-side support, so every command here reads/writes purely client-local state.
 */
public final class ChestSortClientCommands {
    private ChestSortClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(ChestSortClientCommands::register);
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, net.minecraft.commands.CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommands.literal("cs")
            .then(ClientCommands.literal("help")
                .executes(ctx -> help(ctx.getSource())))
            .then(ClientCommands.literal("autosort")
                .then(ClientCommands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest(ClientFilterSettings.AUTOSORT_NEVER);
                        builder.suggest(ClientFilterSettings.AUTOSORT_SELECTED);
                        builder.suggest(ClientFilterSettings.AUTOSORT_ALWAYS);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> autosortMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
            .then(ClientCommands.literal("highlights")
                .then(ClientCommands.literal("dismiss")
                    .executes(ctx -> highlightsDismiss(ctx.getSource())))
                .then(ClientCommands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest(ClientFilterSettings.HIGHLIGHTS_ON);
                        builder.suggest(ClientFilterSettings.HIGHLIGHTS_OFF);
                        builder.suggest(ClientFilterSettings.HIGHLIGHTS_UNTIL_OPENED);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> highlightsMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
            .then(ClientCommands.literal("tags")
                .then(ClientCommands.argument("item", ItemArgument.item(registryAccess))
                    .executes(ctx -> tags(ctx.getSource(), ItemArgument.getItem(ctx, "item").item().value()))))
            .then(ClientCommands.literal("blacklist")
                .then(ClientCommands.literal("list")
                    .executes(ctx -> blacklistList(ctx.getSource())))
                .then(ClientCommands.literal("clear")
                    .executes(ctx -> blacklistClear(ctx.getSource())))
                .then(ClientCommands.literal("mode")
                    .executes(ctx -> blacklistMode(ctx.getSource(), ""))
                    .then(ClientCommands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("preventSort");
                            builder.suggest("strictPreventSort");
                            builder.suggest("preventEntry");
                            builder.suggest("strictPreventEntry");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> blacklistMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(ClientCommands.literal("add")
                    .then(ClientCommands.argument("item", ItemArgument.item(registryAccess))
                        .executes(ctx -> blacklistAdd(ctx.getSource(), ItemArgument.getItem(ctx, "item").item().value()))))
                .then(ClientCommands.literal("addPreset")
                    .then(ClientCommands.argument("name", StringArgumentType.string())
                        .suggests(ChestSortClientCommands::suggestPresetName)
                        .then(ClientCommands.argument("mode", StringArgumentType.word())
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
                .then(ClientCommands.literal("remove")
                    .then(ClientCommands.argument("item", ItemArgument.item(registryAccess))
                        .executes(ctx -> blacklistRemove(ctx.getSource(), ItemArgument.getItem(ctx, "item").item().value()))))
            )
            .then(ClientCommands.literal("scan")
                .executes(ctx -> scan(ctx.getSource())))
            .then(ClientCommands.literal("find")
                .then(ClientCommands.argument("item", ItemArgument.item(registryAccess))
                    .executes(ctx -> find(ctx.getSource(), ItemArgument.getItem(ctx, "item").item().value()))))
            .then(ClientCommands.literal("presets")
                .then(ClientCommands.literal("list")
                    .executes(ctx -> presetsList(ctx.getSource())))
                .then(ClientCommands.literal("add")
                    .then(ClientCommands.argument("name", StringArgumentType.string())
                        .executes(ctx -> presetsAdd(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommands.literal("create")
                    .executes(ctx -> presetsCreate(ctx.getSource())))
                .then(ClientCommands.literal("duplicate")
                    .then(ClientCommands.argument("name", StringArgumentType.string())
                        .suggests(ChestSortClientCommands::suggestPresetName)
                        .executes(ctx -> presetsDuplicate(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommands.literal("remove")
                    .then(ClientCommands.argument("name", StringArgumentType.string())
                        .suggests(ChestSortClientCommands::suggestPresetName)
                        .executes(ctx -> presetsRemove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommands.literal("rename")
                    .then(ClientCommands.argument("old", StringArgumentType.string())
                        .suggests(ChestSortClientCommands::suggestPresetName)
                        .then(ClientCommands.argument("new", StringArgumentType.string())
                            .executes(ctx -> presetsRename(ctx.getSource(), StringArgumentType.getString(ctx, "old"), StringArgumentType.getString(ctx, "new"))))))
                .then(ClientCommands.literal("edit")
                    .then(ClientCommands.argument("name", StringArgumentType.string())
                        .suggests(ChestSortClientCommands::suggestPresetName)
                        .executes(ctx -> presetsEdit(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommands.literal("import")
                    .executes(ctx -> presetsImport(ctx.getSource())))
                .then(ClientCommands.literal("export")
                    .then(ClientCommands.argument("name", StringArgumentType.string())
                        .suggests(ChestSortClientCommands::suggestPresetName)
                        .executes(ctx -> presetsExport(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommands.literal("exportSelect")
                    .executes(ctx -> presetsExportSelect(ctx.getSource())))
                .then(ClientCommands.literal("exportAll")
                    .executes(ctx -> presetsExportAll(ctx.getSource())))
            )
            .then(ClientCommands.literal("wand")
                .then(ClientCommands.literal("bind")
                    .executes(ctx -> wandBind(ctx.getSource())))
                .then(ClientCommands.literal("unbind")
                    .executes(ctx -> wandUnbind(ctx.getSource())))
                .then(ClientCommands.literal("deselect")
                    .executes(ctx -> wandDeselect(ctx.getSource())))
                .then(ClientCommands.literal("copy")
                    .executes(ctx -> wandCopy(ctx.getSource())))
                .then(ClientCommands.literal("paste")
                    .executes(ctx -> wandPaste(ctx.getSource())))
                .then(ClientCommands.literal("autosort")
                    .then(ClientCommands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("on");
                            builder.suggest("off");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> wandAutosort(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(ClientCommands.literal("clear")
                    .executes(ctx -> wandClear(ctx.getSource())))
                .then(ClientCommands.literal("merge")
                    .executes(ctx -> wandMerge(ctx.getSource())))
            )
        );
    }

    // ---- Suggestion helpers ----

    private static CompletableFuture<Suggestions> suggestPresetName(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ClientPresetRegistry.namesSorted(), builder);
    }

    // ---- box scan (client-side equivalent of WandSelectionUtil) ----

    /** Loaded-chunk-only scan of container positions in a box, deduped for double chests. */
    private static Set<Long> findLoadedContainersInBox(ClientLevel level, BlockPos a, BlockPos b) {
        if (level == null || a == null || b == null) return Set.of();

        LinkedHashSet<Long> out = new LinkedHashSet<>();
        long volume = WandVolume.volume(a, b);
        if (volume > 262_144L) {
            // Safety cap so a huge accidental selection doesn't hang the client.
            return Set.of();
        }

        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;
            var c = ClientContainerCanonicalizer.canonicalize(level, pos.immutable(), be);
            if (c == null) continue;
            out.add(c.posLong());
        }
        return Set.copyOf(out);
    }

    private static final class WandVolume {
        static long volume(BlockPos a, BlockPos b) {
            long dx = (long) Math.abs(a.getX() - b.getX()) + 1L;
            long dy = (long) Math.abs(a.getY() - b.getY()) + 1L;
            long dz = (long) Math.abs(a.getZ() - b.getZ()) + 1L;
            return dx * dy * dz;
        }
    }

    // ---- autosort / highlights ----

    private static int autosortMode(FabricClientCommandSource source, String mode) {
        String m = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (!ClientFilterSettings.AUTOSORT_NEVER.equals(m) && !ClientFilterSettings.AUTOSORT_SELECTED.equals(m) && !ClientFilterSettings.AUTOSORT_ALWAYS.equals(m)) {
            source.sendError(Component.literal("[CS] Invalid autosort mode. Use: never | selected | always"));
            return 0;
        }
        ClientFilterSettings.setAutosortMode(m);
        source.sendFeedback(Component.literal("[CS] Autosort mode set to: " + m));
        return 1;
    }

    private static int highlightsMode(FabricClientCommandSource source, String mode) {
        String m = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (!ClientFilterSettings.HIGHLIGHTS_ON.equals(m) && !ClientFilterSettings.HIGHLIGHTS_OFF.equals(m) && !ClientFilterSettings.HIGHLIGHTS_UNTIL_OPENED.equals(m)) {
            source.sendError(Component.literal("[CS] Invalid highlights mode. Use: on | off | until_opened"));
            return 0;
        }
        ClientFilterSettings.setHighlightsMode(m);

        String lastItem = ClientFilterSettings.lastFindItem();
        boolean highlightsOff = ClientFilterSettings.HIGHLIGHTS_OFF.equals(m);
        boolean untilOpened = ClientFilterSettings.HIGHLIGHTS_UNTIL_OPENED.equals(m);

        var client = Minecraft.getInstance();
        String currentDim = client.level == null ? "" : client.level.dimension().identifier().toString();

        if (highlightsOff) {
            ClientFindHighlightState.clear();
            ClientHighlightState.clear();
            ClientFilterSettings.clearHighlightsOnNextOpenFlag();
        } else {
            if (!lastItem.isEmpty()) {
                var matches = ClientContainerFindIndex.find(lastItem);
                List<Long> highlightPos = new ArrayList<>();
                for (var match : matches) {
                    if (!currentDim.equals(match.dimensionId())) continue;
                    highlightPos.add(match.pos().asLong());
                    if (highlightPos.size() >= 200) break;
                }
                ClientFindHighlightState.set(currentDim, lastItem, highlightPos);
            } else {
                ClientFindHighlightState.clear();
            }

            if (untilOpened && !lastItem.isEmpty()) {
                ClientFilterSettings.armClearHighlightsOnNextOpen();
            } else {
                ClientFilterSettings.clearHighlightsOnNextOpenFlag();
            }
        }

        source.sendFeedback(Component.literal("[CS] Highlights mode set to: " + m));
        return 1;
    }

    private static int highlightsDismiss(FabricClientCommandSource source) {
        ClientFilterSettings.setLastFindItem("");
        ClientFilterSettings.clearHighlightsOnNextOpenFlag();
        ClientFindHighlightState.clear();
        ClientHighlightState.clear();
        source.sendFeedback(Component.literal("[CS] Highlights dismissed."));
        return 1;
    }

    // ---- presets ----

    private static int presetsAdd(FabricClientCommandSource source, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            source.sendError(Component.literal("[CS] Preset name cannot be empty"));
            return 0;
        }
        if (ClientPresetRegistry.get(n) != null) {
            source.sendError(Component.literal("[CS] Preset already exists: " + n));
            return 0;
        }
        ClientPresetRegistry.putLocal(n, new ContainerFilterSpec(List.of(), List.of(), List.of()));
        ClientPendingScreen.open(new dev.dromer.chestsort.client.gui.PresetEditorScreen(n));
        source.sendFeedback(Component.literal("[CS] Added preset: " + n));
        return 1;
    }

    /** Opens a GUI to create a named preset from a pasted item/tag list (avoids chat text limits). */
    private static int presetsCreate(FabricClientCommandSource source) {
        ClientPendingScreen.open(new dev.dromer.chestsort.client.gui.PresetCreateScreen());
        return 1;
    }

    private static int presetsRemove(FabricClientCommandSource source, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || ClientPresetRegistry.get(n) == null) {
            source.sendError(Component.literal("[CS] Preset not found: " + n));
            return 0;
        }
        ClientPresetRegistry.removeLocal(n);
        source.sendFeedback(Component.literal("[CS] Removed preset: " + n));
        return 1;
    }

    private static int presetsDuplicate(FabricClientCommandSource source, String name) {
        String n = name == null ? "" : name.trim();
        ContainerFilterSpec spec = ClientPresetRegistry.get(n);
        if (n.isEmpty() || spec == null) {
            source.sendError(Component.literal("[CS] Preset not found: " + n));
            return 0;
        }
        ContainerFilterSpec blacklist = ClientPresetRegistry.getBlacklist(n);

        String base = n + " Copy";
        String out = base;
        if (ClientPresetRegistry.get(out) != null) {
            for (int i = 2; i < 10_000; i++) {
                String candidate = base + " " + i;
                if (ClientPresetRegistry.get(candidate) == null) {
                    out = candidate;
                    break;
                }
            }
            if (ClientPresetRegistry.get(out) != null) {
                out = base + " " + System.currentTimeMillis();
            }
        }

        ClientPresetRegistry.putLocal(out, spec);
        if (blacklist != null) ClientPresetRegistry.putLocalBlacklist(out, blacklist);
        source.sendFeedback(Component.literal("[CS] Duplicated preset: " + n + " -> " + out));
        return 1;
    }

    private static int presetsRename(FabricClientCommandSource source, String oldName, String newName) {
        String o = oldName == null ? "" : oldName.trim();
        String n = newName == null ? "" : newName.trim();
        ContainerFilterSpec spec = ClientPresetRegistry.get(o);
        if (o.isEmpty() || n.isEmpty() || spec == null || ClientPresetRegistry.get(n) != null) {
            source.sendError(Component.literal("[CS] Rename failed (missing old name or new name already exists)"));
            return 0;
        }

        ContainerFilterSpec blacklist = ClientPresetRegistry.getBlacklist(o);
        ClientPresetRegistry.removeLocal(o);
        ClientPresetRegistry.putLocal(n, spec);
        if (blacklist != null) ClientPresetRegistry.putLocalBlacklist(n, blacklist);

        source.sendFeedback(Component.literal("[CS] Renamed preset: " + o + " -> " + n));
        ClientPendingScreen.open(new dev.dromer.chestsort.client.gui.PresetEditorScreen(n));
        return 1;
    }

    private static int presetsEdit(FabricClientCommandSource source, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || ClientPresetRegistry.get(n) == null) {
            source.sendError(Component.literal("[CS] Preset not found: " + n));
            return 0;
        }
        ClientPendingScreen.open(new dev.dromer.chestsort.client.gui.PresetEditorScreen(n));
        return 1;
    }

    private static int presetsList(FabricClientCommandSource source) {
        List<String> names = ClientPresetRegistry.namesSorted();
        if (names.isEmpty()) {
            source.sendFeedback(Component.literal("[CS] No presets."));
            return 0;
        }

        source.sendFeedback(Component.literal("[CS] Presets (" + names.size() + "):").withStyle(ChatFormatting.AQUA));
        int limit = 200;
        for (int i = 0; i < names.size() && i < limit; i++) {
            source.sendFeedback(Component.literal("- ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(names.get(i)).withStyle(ChatFormatting.YELLOW)));
        }
        if (names.size() > limit) {
            source.sendFeedback(Component.literal("... and " + (names.size() - limit) + " more").withStyle(ChatFormatting.GRAY));
        }
        return names.size();
    }

    private static int presetsImport(FabricClientCommandSource source) {
        ClientPendingScreen.open(new dev.dromer.chestsort.client.gui.PresetTransferScreen(
            dev.dromer.chestsort.client.gui.PresetTransferScreen.Mode.IMPORT, ""
        ));
        return 1;
    }

    private static int presetsExport(FabricClientCommandSource source, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || ClientPresetRegistry.get(n) == null) {
            source.sendError(Component.literal("[CS] Preset not found: " + n));
            return 0;
        }
        ClientPendingScreen.open(new dev.dromer.chestsort.client.gui.PresetTransferScreen(
            dev.dromer.chestsort.client.gui.PresetTransferScreen.Mode.EXPORT, n
        ));
        return 1;
    }

    private static int presetsExportSelect(FabricClientCommandSource source) {
        ClientPendingScreen.open(dev.dromer.chestsort.client.gui.PresetListTransferScreen.forExportSelect());
        return 1;
    }

    private static int presetsExportAll(FabricClientCommandSource source) {
        ClientPendingScreen.open(new dev.dromer.chestsort.client.gui.PresetTransferScreen(
            dev.dromer.chestsort.client.gui.PresetTransferScreen.Mode.EXPORT_ALL, ""
        ));
        return 1;
    }

    // ---- find / scan ----

    private static int scan(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("[CS] Known containers (opened this/previous sessions): " + ClientContainerFindIndex.size()));
        source.sendFeedback(Component.literal("[CS] This mod is client-only: only containers you've personally opened are indexed.").withStyle(ChatFormatting.GRAY));
        return ClientContainerFindIndex.size();
    }

    private static int find(FabricClientCommandSource source, Item item) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        ClientFilterSettings.setLastFindItem(itemId.toString());

        String mode = ClientFilterSettings.highlightsMode();
        boolean highlightsOff = ClientFilterSettings.HIGHLIGHTS_OFF.equals(mode);
        boolean untilOpened = ClientFilterSettings.HIGHLIGHTS_UNTIL_OPENED.equals(mode);

        var matches = ClientContainerFindIndex.find(itemId.toString());
        var client = Minecraft.getInstance();
        String currentDim = client.level == null ? "" : client.level.dimension().identifier().toString();

        if (matches.isEmpty()) {
            source.sendFeedback(Component.literal("[CS] No known containers containing: " + itemId));
            ClientFindHighlightState.clear();
            if (highlightsOff) {
                source.sendFeedback(Component.literal("[CS] Highlights are OFF. Use /cs highlights on to enable.").withStyle(ChatFormatting.GRAY));
            }
            return 0;
        }

        if (highlightsOff) {
            ClientFindHighlightState.clear();
            source.sendFeedback(Component.literal("[CS] Highlights are OFF. Use /cs highlights on to enable.").withStyle(ChatFormatting.GRAY));
        } else {
            List<Long> highlightPos = new ArrayList<>();
            for (var m : matches) {
                if (!currentDim.equals(m.dimensionId())) continue;
                highlightPos.add(m.pos().asLong());
                if (highlightPos.size() >= 200) break;
            }
            ClientFindHighlightState.set(currentDim, itemId.toString(), highlightPos);

            if (untilOpened) {
                ClientFilterSettings.armClearHighlightsOnNextOpen();
            } else {
                ClientFilterSettings.clearHighlightsOnNextOpenFlag();
            }
        }

        source.sendFeedback(Component.literal("[CS] Found " + matches.size() + " known container(s) containing: " + itemId));
        for (var m : matches) {
            source.sendFeedback(Component.literal("- " + m.dimensionId() + " " + m.pos().toShortString() + " x" + m.count()));
        }
        return matches.size();
    }

    // ---- blacklist ----

    private static int blacklistAdd(FabricClientCommandSource source, Item item) {
        if (item == null) {
            source.sendError(Component.literal("[CS] Missing item"));
            return 0;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        boolean changed = ClientFilterSettings.addToBlacklist(itemId);
        if (changed) {
            source.sendFeedback(Component.literal("[CS] Blacklisted: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(itemId).withStyle(ChatFormatting.YELLOW)));
            return 1;
        }
        source.sendFeedback(Component.literal("[CS] Already blacklisted: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(itemId).withStyle(ChatFormatting.YELLOW)));
        return 0;
    }

    private static int blacklistRemove(FabricClientCommandSource source, Item item) {
        if (item == null) {
            source.sendError(Component.literal("[CS] Missing item"));
            return 0;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        boolean changed = ClientFilterSettings.removeFromBlacklist(itemId);
        if (changed) {
            source.sendFeedback(Component.literal("[CS] Un-blacklisted: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(itemId).withStyle(ChatFormatting.YELLOW)));
            return 1;
        }
        source.sendFeedback(Component.literal("[CS] Not blacklisted: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(itemId).withStyle(ChatFormatting.YELLOW)));
        return 0;
    }

    private static int blacklistList(FabricClientCommandSource source) {
        List<String> items = new ArrayList<>(ClientFilterSettings.blacklistItems());
        items.sort(java.util.Comparator.naturalOrder());

        source.sendFeedback(Component.literal("[CS] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Blacklisted items: ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(String.valueOf(items.size())).withStyle(ChatFormatting.AQUA)));

        if (items.isEmpty()) {
            source.sendFeedback(Component.literal("- (none)").withStyle(ChatFormatting.GRAY));
            return 1;
        }

        int shown = 0;
        for (String itemId : items) {
            if (itemId == null || itemId.isEmpty()) continue;
            source.sendFeedback(Component.literal("- ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(itemId).withStyle(ChatFormatting.WHITE)));
            shown++;
            if (shown >= 200) break;
        }
        return shown;
    }

    private static int blacklistClear(FabricClientCommandSource source) {
        int removed = ClientFilterSettings.clearBlacklist();
        source.sendFeedback(Component.literal("[CS] Cleared blacklist: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(String.valueOf(removed)).withStyle(ChatFormatting.AQUA)));
        return removed;
    }

    private static int blacklistMode(FabricClientCommandSource source, String modeRaw) {
        if (modeRaw == null || modeRaw.trim().isEmpty()) {
            String current = ClientFilterSettings.blacklistMode();
            source.sendFeedback(Component.literal("[CS] Blacklist mode is: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(current).withStyle(ChatFormatting.YELLOW)));
            return 1;
        }

        String m = modeRaw.trim().toLowerCase(Locale.ROOT).replace("_", "");
        if (!ClientFilterSettings.BLACKLIST_MODE_PREVENT_SORT.equals(m)
            && !ClientFilterSettings.BLACKLIST_MODE_STRICT_PREVENT_SORT.equals(m)
            && !ClientFilterSettings.BLACKLIST_MODE_PREVENT_ENTRY.equals(m)
            && !ClientFilterSettings.BLACKLIST_MODE_STRICT_PREVENT_ENTRY.equals(m)) {
            source.sendError(Component.literal("[CS] Invalid blacklist mode. Use: preventSort | strictPreventSort | preventEntry | strictPreventEntry"));
            return 0;
        }

        ClientFilterSettings.setBlacklistMode(m);
        source.sendFeedback(Component.literal("[CS] Blacklist mode set to: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(m).withStyle(ChatFormatting.YELLOW)));
        return 1;
    }

    private static int blacklistAddPreset(FabricClientCommandSource source, String presetName, String modeRaw) {
        String name = presetName == null ? "" : presetName.trim();
        if (name.isEmpty()) {
            source.sendError(Component.literal("[CS] Missing preset name"));
            return 0;
        }

        String mode = modeRaw == null ? "" : modeRaw.trim().toLowerCase(Locale.ROOT);
        if (mode.isEmpty()) mode = "everything";

        ContainerFilterSpec preset = ClientPresetRegistry.get(name);
        if (preset == null) {
            source.sendError(Component.literal("[CS] Preset not found: " + name));
            return 0;
        }
        ContainerFilterSpec base = preset.normalized();

        ContainerFilterSpec selected;
        switch (mode) {
            case "whitelist" -> selected = new ContainerFilterSpec(base.items(), base.tags(), List.of(), false).normalized();
            case "blacklist" -> {
                ContainerFilterSpec appliedOnly = new ContainerFilterSpec(List.of(), List.of(), base.presets(), false).normalized();
                selected = ClientFilterResolver.resolveWithAppliedPresets(appliedOnly);
            }
            case "everything" -> selected = ClientFilterResolver.resolveWithAppliedPresets(base);
            default -> {
                source.sendError(Component.literal("[CS] Invalid mode: " + mode + " (use: blacklist|whitelist|everything)"));
                return 0;
            }
        }

        LinkedHashSet<String> itemIds = collectItemIdsFromSpec(selected);
        if (itemIds.isEmpty()) {
            source.sendFeedback(Component.literal("[CS] Preset \"" + name + "\" (" + mode + ") contains no items to add."));
            return 1;
        }

        int added = ClientFilterSettings.addAllToBlacklist(itemIds);
        source.sendFeedback(Component.literal("[CS] Added ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(String.valueOf(added)).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" item(s) from preset \"").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("\" (" + mode + ") to your blacklist.").withStyle(ChatFormatting.GOLD)));
        return Math.max(1, added);
    }

    private static LinkedHashSet<String> collectItemIdsFromSpec(ContainerFilterSpec spec) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (spec == null) return out;
        ContainerFilterSpec s = spec.normalized();

        if (s.items() != null) {
            for (String raw : s.items()) {
                Identifier id = parseItemIdentifier(raw);
                if (id == null) continue;
                out.add(id.toString());
            }
        }

        if (s.tags() != null) {
            for (TagFilterSpec tf : s.tags()) {
                if (tf == null) continue;
                Identifier tagId = parseTagIdentifier(tf.tagId());
                if (tagId == null) continue;

                HashSet<String> exc = expandExceptionsToItemIds(tf.exceptions());
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                for (var entry : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                    if (entry == null || entry.value() == null) continue;
                    String itemId = BuiltInRegistries.ITEM.getKey(entry.value()).toString();
                    if (itemId == null || itemId.isEmpty() || exc.contains(itemId)) continue;
                    out.add(itemId);
                }
            }
        }
        return out;
    }

    private static HashSet<String> expandExceptionsToItemIds(List<String> exceptions) {
        HashSet<String> out = new HashSet<>();
        if (exceptions == null || exceptions.isEmpty()) return out;
        for (String raw : exceptions) {
            if (raw == null) continue;
            String t = raw.trim();
            if (t.isEmpty()) continue;

            if (t.startsWith("#")) {
                Identifier tagId = parseTagIdentifier(t);
                if (tagId == null) continue;
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                for (var entry : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                    if (entry == null || entry.value() == null) continue;
                    String itemId = BuiltInRegistries.ITEM.getKey(entry.value()).toString();
                    if (itemId != null && !itemId.isEmpty()) out.add(itemId);
                }
            } else {
                Identifier id = parseItemIdentifier(t);
                if (id != null) out.add(id.toString());
            }
        }
        return out;
    }

    private static Identifier parseItemIdentifier(String itemId) {
        if (itemId == null) return null;
        String t = itemId.trim();
        if (t.isEmpty()) return null;
        Identifier id = Identifier.tryParse(t);
        if (id != null) return id;
        if (t.indexOf(':') < 0) return Identifier.tryParse("minecraft:" + t);
        return null;
    }

    private static Identifier parseTagIdentifier(String tagId) {
        if (tagId == null) return null;
        String t = tagId.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("#")) t = t.substring(1);
        Identifier id = Identifier.tryParse(t);
        if (id != null) return id;
        if (t.indexOf(':') < 0) return Identifier.tryParse("minecraft:" + t);
        return null;
    }

    private static int tags(FabricClientCommandSource source, Item item) {
        if (item == null) {
            source.sendError(Component.literal("[CS] Missing item"));
            return 0;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        ArrayList<String> tags = new ArrayList<>();
        try {
            for (TagKey<Item> tag : item.builtInRegistryHolder().tags().toList()) {
                if (tag == null || tag.location() == null) continue;
                tags.add("#" + tag.location());
            }
        } catch (Throwable t) {
            source.sendError(Component.literal("[CS] Failed to read tags for: " + itemId));
            return 0;
        }
        java.util.Collections.sort(tags);

        source.sendFeedback(Component.literal("[CS] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Tags for ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(itemId.toString()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(": ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(String.valueOf(tags.size())).withStyle(ChatFormatting.AQUA)));

        if (tags.isEmpty()) {
            source.sendFeedback(Component.literal("- (none)").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        for (String tag : tags) {
            source.sendFeedback(Component.literal("- ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(tag).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
        return tags.size();
    }

    // ---- wand ----

    private static int wandBind(FabricClientCommandSource source) {
        var player = source.getPlayer();
        if (player == null) return 0;
        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            source.sendError(Component.literal("[CS] Hold an item in your main hand to bind as the wand"));
            return 0;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        ClientWandState.bind(itemId);
        source.sendFeedback(Component.literal("[CS] Wand bound to: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(itemId).withStyle(ChatFormatting.YELLOW)));
        return 1;
    }

    private static int wandUnbind(FabricClientCommandSource source) {
        ClientWandState.unbind();
        source.sendFeedback(Component.literal("[CS] Wand unbound").withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int wandDeselect(FabricClientCommandSource source) {
        if (!ClientWandState.isBound()) {
            source.sendError(Component.literal("[CS] No wand is bound. Use /cs wand bind first."));
            return 0;
        }
        ClientWandState.clearSelection();
        source.sendFeedback(Component.literal("[CS] Wand selection cleared").withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int wandCopy(FabricClientCommandSource source) {
        var player = source.getPlayer();
        var level = source.getLevel();
        if (player == null || level == null) return 0;

        BlockPos pos = null;
        HitResult hit = player.pick(6.0, 0.0f, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            pos = bhr.getBlockPos();
        }
        if (pos == null) {
            source.sendError(Component.literal("[CS] Look at a container block"));
            return 0;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            source.sendError(Component.literal("[CS] No block entity at that position"));
            return 0;
        }
        var canonical = ClientContainerCanonicalizer.canonicalize(level, pos.immutable(), be);
        if (canonical == null) {
            source.sendError(Component.literal("[CS] That block is not a supported container"));
            return 0;
        }

        String dimId = level.dimension().identifier().toString();
        var entry = ClientContainerFilterStorage.get(dimId, canonical.posLong());
        ContainerFilterSpec spec = entry == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : entry.whitelist();
        ClientWandState.setClipboard(spec);

        source.sendFeedback(Component.literal("[CS] Copied filter to wand clipboard").withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static ClientWandState.Pos requirePos(FabricClientCommandSource source, int which) {
        ClientWandState.Pos pos = which == 1 ? ClientWandState.pos1() : ClientWandState.pos2();
        if (pos == null) {
            source.sendError(Component.literal("[CS] Missing wand pos" + which + ". Set pos1 with left-click and pos2 with right-click using your bound wand."));
        }
        return pos;
    }

    private static int wandPaste(FabricClientCommandSource source) {
        ContainerFilterSpec clip = ClientWandState.clipboard();
        if (clip == null) {
            source.sendError(Component.literal("[CS] Wand clipboard is empty. Use /cs wand copy first."));
            return 0;
        }
        ClientWandState.Pos p1 = requirePos(source, 1);
        ClientWandState.Pos p2 = requirePos(source, 2);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendError(Component.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        var level = source.getLevel();
        Set<Long> targets = findLoadedContainersInBox(level, p1.pos(), p2.pos());
        for (long posLong : targets) {
            var existing = ClientContainerFilterStorage.get(p1.dimensionId(), posLong);
            var blacklist = existing == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : existing.blacklist();
            boolean priority = existing == null || existing.whitelistPriority();
            ClientContainerFilterStorage.put(p1.dimensionId(), posLong, clip, blacklist, priority);
        }

        source.sendFeedback(Component.literal("[CS] Pasted filter to ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(String.valueOf(targets.size())).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" containers (loaded)").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int wandAutosort(FabricClientCommandSource source, String mode) {
        boolean on;
        String m = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if ("on".equals(m)) on = true;
        else if ("off".equals(m)) on = false;
        else {
            source.sendError(Component.literal("[CS] Invalid mode. Use: on | off"));
            return 0;
        }

        ClientWandState.Pos p1 = requirePos(source, 1);
        ClientWandState.Pos p2 = requirePos(source, 2);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendError(Component.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        var level = source.getLevel();
        Set<Long> targets = findLoadedContainersInBox(level, p1.pos(), p2.pos());
        for (long posLong : targets) {
            ClientAutosortState.setEnabled(p1.dimensionId(), posLong, on);
        }

        source.sendFeedback(Component.literal("[CS] Set autosort ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED))
            .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.valueOf(targets.size())).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" containers (loaded)").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int wandClear(FabricClientCommandSource source) {
        ClientWandState.Pos p1 = requirePos(source, 1);
        ClientWandState.Pos p2 = requirePos(source, 2);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendError(Component.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        var level = source.getLevel();
        ContainerFilterSpec cleared = new ContainerFilterSpec(List.of(), List.of(), List.of(), false);
        Set<Long> targets = findLoadedContainersInBox(level, p1.pos(), p2.pos());
        for (long posLong : targets) {
            ClientContainerFilterStorage.put(p1.dimensionId(), posLong, cleared, cleared, true);
        }

        source.sendFeedback(Component.literal("[CS] Cleared filters for ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(String.valueOf(targets.size())).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" containers (loaded)").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int wandMerge(FabricClientCommandSource source) {
        ClientWandState.Pos p1 = requirePos(source, 1);
        ClientWandState.Pos p2 = requirePos(source, 2);
        if (p1 == null || p2 == null) return 0;
        if (!p1.dimensionId().equals(p2.dimensionId())) {
            source.sendError(Component.literal("[CS] pos1 and pos2 must be in the same dimension"));
            return 0;
        }

        var level = source.getLevel();
        Set<Long> targets = findLoadedContainersInBox(level, p1.pos(), p2.pos());

        LinkedHashSet<String> mergedItems = new LinkedHashSet<>();
        LinkedHashSet<String> mergedPresets = new LinkedHashSet<>();
        LinkedHashMap<String, LinkedHashSet<String>> mergedTags = new LinkedHashMap<>();
        boolean anyAutosort = false;

        for (long posLong : targets) {
            var entry = ClientContainerFilterStorage.get(p1.dimensionId(), posLong);
            if (entry == null) continue;
            ContainerFilterSpec s = entry.whitelist().normalized();
            anyAutosort |= ClientAutosortState.isEnabled(p1.dimensionId(), posLong);
            if (s.items() != null) mergedItems.addAll(s.items());
            if (s.presets() != null) mergedPresets.addAll(s.presets());
            if (s.tags() != null) {
                for (var t : s.tags()) {
                    if (t == null || t.tagId() == null) continue;
                    String tagId = ContainerFilterSpec.normalizeTagId(t.tagId());
                    if (tagId.isEmpty()) continue;
                    var set = mergedTags.computeIfAbsent(tagId, k -> new LinkedHashSet<>());
                    if (t.exceptions() != null) set.addAll(ContainerFilterSpec.normalizeStrings(t.exceptions()));
                }
            }
        }

        ArrayList<TagFilterSpec> tags = new ArrayList<>(mergedTags.size());
        for (var e : mergedTags.entrySet()) {
            tags.add(new TagFilterSpec(e.getKey(), List.copyOf(e.getValue())));
        }

        ContainerFilterSpec merged = new ContainerFilterSpec(List.copyOf(mergedItems), tags, List.copyOf(mergedPresets), anyAutosort).normalized();
        ClientWandState.setClipboard(merged);

        source.sendFeedback(Component.literal("[CS] Merged ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(String.valueOf(targets.size())).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" containers into wand clipboard").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    // ---- help ----

    private static int help(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("[CS] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("ChestSort help (client-only mode)").withStyle(ChatFormatting.YELLOW)));
        source.sendFeedback(Component.literal("- This mod never requires anything installed on the server.").withStyle(ChatFormatting.GRAY));
        source.sendFeedback(Component.literal("- Open a chest/barrel, then click the \"Filter\" button to edit its rules.").withStyle(ChatFormatting.GRAY));
        source.sendFeedback(Component.literal("Commands:").withStyle(ChatFormatting.AQUA));
        source.sendFeedback(Component.literal("- /cs find <item>  (lists known containers containing an item)").withStyle(ChatFormatting.GREEN));
        source.sendFeedback(Component.literal("- /cs presets add|create|duplicate|remove|rename|edit|list|import|export|exportSelect|exportAll").withStyle(ChatFormatting.GREEN));
        source.sendFeedback(Component.literal("- In any search field, type @presetName (or &presetName) to add/reference a preset.").withStyle(ChatFormatting.GRAY));
        source.sendFeedback(Component.literal("- /cs autosort <never|selected|always>").withStyle(ChatFormatting.GREEN));
        source.sendFeedback(Component.literal("- /cs highlights <on|off|until_opened>").withStyle(ChatFormatting.GREEN));
        source.sendFeedback(Component.literal("- /cs highlights dismiss").withStyle(ChatFormatting.GREEN));
        source.sendFeedback(Component.literal("- /cs blacklist <list|add|remove|clear|mode|addPreset>").withStyle(ChatFormatting.GREEN));
        source.sendFeedback(Component.literal("- /cs wand <bind|unbind|deselect|copy|paste|autosort|clear|merge>").withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
