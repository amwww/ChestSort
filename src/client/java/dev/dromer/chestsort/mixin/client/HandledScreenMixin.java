package dev.dromer.chestsort.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dromer.chestsort.client.ClientContainerContext;
import dev.dromer.chestsort.client.ClientContainerFilterStorage;
import dev.dromer.chestsort.client.ClientHighlightState;
import dev.dromer.chestsort.client.ClientLastInteractedBlock;
import dev.dromer.chestsort.client.ClientLockedSlotsState;
import dev.dromer.chestsort.client.ClientNetworkingUtil;
import dev.dromer.chestsort.client.ClientOnlyOrganizer;
import dev.dromer.chestsort.client.ClientOnlySorter;
import dev.dromer.chestsort.client.ClientPresetRegistry;
import dev.dromer.chestsort.client.ClientSortNotificationState;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;
import dev.dromer.chestsort.net.payload.ImportPresetPayload;
import dev.dromer.chestsort.net.payload.OpenPresetUiPayload;
import dev.dromer.chestsort.net.payload.OrganizeRequestPayload;
import dev.dromer.chestsort.net.payload.SetContainerFiltersPayload;
import dev.dromer.chestsort.net.payload.SetFilterPayload;
import dev.dromer.chestsort.net.payload.SetFilterV2Payload;
import dev.dromer.chestsort.net.payload.SetPresetPayload;
import dev.dromer.chestsort.net.payload.SetPresetV2Payload;
import dev.dromer.chestsort.net.payload.SortRequestPayload;
import dev.dromer.chestsort.net.payload.SortResultPayload;
import dev.dromer.chestsort.net.payload.ToggleLockedSlotPayload;
import dev.dromer.chestsort.net.payload.UndoSortPayload;
import dev.dromer.chestsort.util.Cs2StringCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;


@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Unique
    private static final int CHESTSORT_PANEL_GAP = 6;

    @Unique
    private static final int CHESTSORT_PANEL_W = 140;

    @Unique
    private static final int CHESTSORT_ROW_H = 18;

    @Unique
    private static final int CHESTSORT_HEADER_H = 24;

    @Unique
    private static final int CHESTSORT_PAD_BOTTOM = 6;

    @Unique
    private static final int CHESTSORT_ACTION_W = 14;

    @Unique
    private static final int CHESTSORT_ACTION_PAD = 6;

    @Unique
    private int chestsort$leftPanelX;

    @Unique
    private int chestsort$leftPanelY;

    @Unique
    private int chestsort$leftPanelW;

    @Unique
    private int chestsort$rightPanelX;

    @Unique
    private int chestsort$rightPanelY;

    @Unique
    private int chestsort$rightPanelW;

    @Unique
    private int chestsort$leftRowsShown = 9;

    @Unique
    private int chestsort$resultsRowsShown = 10;

    @Unique
    private int chestsort$resultsPanelY;

    @Unique
    private int chestsort$resultsRowsY;

    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Shadow
    protected int backgroundWidth;

    @Shadow
    protected net.minecraft.screen.ScreenHandler handler;

    @Unique
    private boolean chestsort$filterMode = false;

    @Unique
    private ButtonWidget chestsort$filterButton;

    @Unique
    private ButtonWidget chestsort$whitelistTabButton;

    @Unique
    private ButtonWidget chestsort$blacklistTabButton;

    @Unique
    private ButtonWidget chestsort$importOpenButton;

    @Unique
    private ButtonWidget chestsort$exportOpenButton;

    @Unique
    private ButtonWidget chestsort$sortButton;

    @Unique
    private ButtonWidget chestsort$organizeButton;

    @Unique
    private ButtonWidget chestsort$autosortButton;

    @Unique
    private ButtonWidget chestsort$lockSlotButton;

    @Unique
    private boolean chestsort$lockSlotsMode = false;

    @Unique
    private ButtonWidget chestsort$undoButton;

    @Unique
    private TextFieldWidget chestsort$searchField;

    @Unique
    private java.util.List<Item> chestsort$allItems;

    @Unique
    private java.util.List<Item> chestsort$searchResultItems = java.util.List.of();

    @Unique
    private java.util.List<String> chestsort$searchResultTagIds = java.util.List.of();

    @Unique
    private java.util.List<String> chestsort$searchResultPresetNames = java.util.List.of();

    @Unique
    private java.util.List<String> chestsort$searchResultSubtitles = java.util.List.of();

    @Unique
    private int chestsort$leftScroll = 0;

    @Unique
    private int chestsort$resultsScroll = 0;

    @Unique
    private int chestsort$selectedLeftRowIndex = -1;

    @Unique
    private int chestsort$selectedResultIndex = -1;

    @Unique
    private java.util.List<String> chestsort$editingFilterItems = java.util.List.of();

    @Unique
    private java.util.List<TagFilterSpec> chestsort$editingFilterTags = java.util.List.of();

    @Unique
    private java.util.List<String> chestsort$editingFilterPresets = java.util.List.of();

    @Unique
    private java.util.List<String> chestsort$editingWhitelistItems = java.util.List.of();

    @Unique
    private java.util.List<TagFilterSpec> chestsort$editingWhitelistTags = java.util.List.of();

    @Unique
    private java.util.List<String> chestsort$editingWhitelistPresets = java.util.List.of();

    @Unique
    private java.util.List<String> chestsort$editingBlacklistItems = java.util.List.of();

    @Unique
    private java.util.List<TagFilterSpec> chestsort$editingBlacklistTags = java.util.List.of();

    @Unique
    private java.util.List<String> chestsort$editingBlacklistPresets = java.util.List.of();

    @Unique
    private boolean chestsort$editingWhitelistPriority = true;

    @Unique
    private static final int CHESTSORT_TAB_WHITELIST = 0;

    @Unique
    private static final int CHESTSORT_TAB_BLACKLIST = 1;

    @Unique
    private int chestsort$filterTab = CHESTSORT_TAB_WHITELIST;

    @Unique
    private boolean chestsort$editingAutosort = false;

    @Unique
    private boolean chestsort$itemsExpanded = true;

    @Unique
    private boolean chestsort$tagsExpanded = true;

    @Unique
    private boolean chestsort$presetsExpanded = true;

    @Unique
    private boolean chestsort$tagBrowserMode = false;

    @Unique
    private String chestsort$tagBrowserTagId = "";

    @Unique
    private java.util.List<Item> chestsort$tagBrowserItems = java.util.List.of();

    @Unique
    private final java.util.Map<String, java.util.List<Item>> chestsort$tagItemsCache = new java.util.HashMap<>();

    @Unique
    private java.util.List<String> chestsort$allItemTagIds = null;

    @Unique
    private boolean chestsort$tagIdScanStarted = false;

    @Unique
    private long chestsort$tagIdScanLastAttemptMs = 0L;

    @Unique
    private boolean chestsort$filterDirty = false;

    @Unique
    private boolean chestsort$importPopupOpen = false;

    @Unique
    private boolean chestsort$exportPopupOpen = false;

    @Unique
    private boolean chestsort$presetImportPopupOpen = false;

    @Unique
    private boolean chestsort$presetExportPopupOpen = false;

    @Unique
    private TextFieldWidget chestsort$importField;

    @Unique
    private ButtonWidget chestsort$importConfirmButton;

    @Unique
    private ButtonWidget chestsort$importCancelButton;

    @Unique
    private TextFieldWidget chestsort$exportField;

    @Unique
    private ButtonWidget chestsort$exportCloseButton;

    @Unique
    private TextFieldWidget chestsort$presetImportField;

    @Unique
    private ButtonWidget chestsort$presetImportConfirmButton;

    @Unique
    private ButtonWidget chestsort$presetImportCancelButton;

    @Unique
    private TextFieldWidget chestsort$presetExportField;

    @Unique
    private ButtonWidget chestsort$presetExportCloseButton;

    @Unique
    private String chestsort$presetExportName = "";

    @Unique
    private String chestsort$importError = "";

    @Unique
    private String chestsort$exportError = "";

    @Unique
    private String chestsort$presetImportError = "";

    @Unique
    private String chestsort$presetExportError = "";

    @Unique
    private String chestsort$editingPresetName = "";

    @Unique
    private boolean chestsort$editPresetMode = false;

    @Unique
    private ButtonWidget chestsort$editPresetButton;

    @Unique
    private static final String CHESTSORT_IMPORT_PREFIX = "cs2:";

    @Unique
    private static final int CHESTSORT_LEFT_KIND_ITEMS_HEADER = 0;

    @Unique
    private static final int CHESTSORT_LEFT_KIND_ITEM = 1;

    @Unique
    private static final int CHESTSORT_LEFT_KIND_TAGS_HEADER = 2;

    @Unique
    private static final int CHESTSORT_LEFT_KIND_TAG = 3;

    @Unique
    private static final int CHESTSORT_LEFT_KIND_PRESETS_HEADER = 4;

    @Unique
    private static final int CHESTSORT_LEFT_KIND_PRESET = 5;

    @Unique
    private static final int CHESTSORT_LEFT_KIND_AUTOSORT = 6;

    @Unique
    private static final int CHESTSORT_LEFT_KIND_PRIORITY = 7;

    @Unique
    private static int chestsort$leftRowEncode(int kind, int index) {
        return (kind << 24) | (index & 0x00FFFFFF);
    }

    @Unique
    private static int chestsort$leftRowKind(int encoded) {
        return (encoded >>> 24) & 0xFF;
    }

    @Unique
    private static int chestsort$leftRowIndex(int encoded) {
        int idx = encoded & 0x00FFFFFF;
        // sign-extend 24-bit
        if ((idx & 0x00800000) != 0) idx |= 0xFF000000;
        return idx;
    }

    @Unique
    private void chestsort$ensureState() {
        if (chestsort$editingFilterItems == null) {
            chestsort$editingFilterItems = java.util.List.of();
        }
        if (chestsort$editingFilterTags == null) {
            chestsort$editingFilterTags = java.util.List.of();
        }
        if (chestsort$editingFilterPresets == null) {
            chestsort$editingFilterPresets = java.util.List.of();
        }

        if (chestsort$editingWhitelistItems == null) chestsort$editingWhitelistItems = java.util.List.of();
        if (chestsort$editingWhitelistTags == null) chestsort$editingWhitelistTags = java.util.List.of();
        if (chestsort$editingWhitelistPresets == null) chestsort$editingWhitelistPresets = java.util.List.of();

        if (chestsort$editingBlacklistItems == null) chestsort$editingBlacklistItems = java.util.List.of();
        if (chestsort$editingBlacklistTags == null) chestsort$editingBlacklistTags = java.util.List.of();
        if (chestsort$editingBlacklistPresets == null) chestsort$editingBlacklistPresets = java.util.List.of();

        if (chestsort$searchResultItems == null) {
            chestsort$searchResultItems = java.util.List.of();
        }
        if (chestsort$searchResultTagIds == null) {
            chestsort$searchResultTagIds = java.util.List.of();
        }
        if (chestsort$searchResultPresetNames == null) {
            chestsort$searchResultPresetNames = java.util.List.of();
        }
        if (chestsort$searchResultSubtitles == null) {
            chestsort$searchResultSubtitles = java.util.List.of();
        }
        if (chestsort$tagBrowserItems == null) {
            chestsort$tagBrowserItems = java.util.List.of();
        }
    }

    @Unique
    private void chestsort$syncTabStorageFromEditingLists() {
        if (chestsort$filterTab == CHESTSORT_TAB_BLACKLIST) {
            chestsort$editingBlacklistItems = chestsort$editingFilterItems;
            chestsort$editingBlacklistTags = chestsort$editingFilterTags;
            chestsort$editingBlacklistPresets = chestsort$editingFilterPresets;
        } else {
            chestsort$editingWhitelistItems = chestsort$editingFilterItems;
            chestsort$editingWhitelistTags = chestsort$editingFilterTags;
            chestsort$editingWhitelistPresets = chestsort$editingFilterPresets;
        }
    }

    @Unique
    private void chestsort$applyActiveTabToEditingLists() {
        if (chestsort$filterTab == CHESTSORT_TAB_BLACKLIST) {
            chestsort$editingFilterItems = chestsort$editingBlacklistItems;
            chestsort$editingFilterTags = chestsort$editingBlacklistTags;
            chestsort$editingFilterPresets = chestsort$editingBlacklistPresets;
        } else {
            chestsort$editingFilterItems = chestsort$editingWhitelistItems;
            chestsort$editingFilterTags = chestsort$editingWhitelistTags;
            chestsort$editingFilterPresets = chestsort$editingWhitelistPresets;
        }

        if (!(chestsort$editingFilterItems instanceof java.util.ArrayList)) {
            chestsort$editingFilterItems = new java.util.ArrayList<>(chestsort$editingFilterItems == null ? java.util.List.of() : chestsort$editingFilterItems);
        }
        if (!(chestsort$editingFilterTags instanceof java.util.ArrayList)) {
            chestsort$editingFilterTags = new java.util.ArrayList<>(chestsort$editingFilterTags == null ? java.util.List.of() : chestsort$editingFilterTags);
        }
        if (!(chestsort$editingFilterPresets instanceof java.util.ArrayList)) {
            chestsort$editingFilterPresets = new java.util.ArrayList<>(chestsort$editingFilterPresets == null ? java.util.List.of() : chestsort$editingFilterPresets);
        }
    }

    @Unique
    private void chestsort$switchFilterTab(int tab) {
        if (tab != CHESTSORT_TAB_WHITELIST && tab != CHESTSORT_TAB_BLACKLIST) return;
        if (chestsort$filterTab == tab) return;

        chestsort$syncTabStorageFromEditingLists();
        chestsort$filterTab = tab;
        chestsort$applyActiveTabToEditingLists();

        chestsort$tagBrowserMode = false;
        chestsort$tagBrowserTagId = "";
        chestsort$tagBrowserItems = java.util.List.of();

        chestsort$leftScroll = 0;
        chestsort$resultsScroll = 0;
        chestsort$selectedLeftRowIndex = -1;
        chestsort$selectedResultIndex = -1;

        if (chestsort$searchField != null) {
            chestsort$searchField.setText("");
            chestsort$updateSearchResults();
        }
        chestsort$clampScroll();
    }

    @Unique
    private static String chestsort$lc(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    @Unique
    private void chestsort$ensureAllItems() {
        if (chestsort$allItems != null) {
            return;
        }
        chestsort$allItems = new java.util.ArrayList<>();
        for (Item item : Registries.ITEM) {
            chestsort$allItems.add(item);
        }
    }

    @Unique
    private void chestsort$updateSearchResults() {
        chestsort$ensureState();
        chestsort$ensureAllItems();

        String query = chestsort$searchField == null ? "" : chestsort$lc(chestsort$searchField.getText()).trim();
        if (query.isEmpty()) {
            if (chestsort$tagBrowserMode) {
                java.util.Set<String> exc = chestsort$getExceptionSetForTag(chestsort$tagBrowserTagId);
                java.util.ArrayList<Item> items = new java.util.ArrayList<>(16);
                java.util.ArrayList<String> tagIds = new java.util.ArrayList<>(16);
                java.util.ArrayList<String> subtitles = new java.util.ArrayList<>(16);
                for (Item item : chestsort$tagBrowserItems) {
                    if (item != null) {
                        String itemId = String.valueOf(Registries.ITEM.getId(item));
                        if (!itemId.isEmpty() && exc.contains(itemId)) continue;
                    }
                    items.add(item);
                    tagIds.add("");
                    subtitles.add(chestsort$formatResultSubtitle(item));
                    if (items.size() >= 200) break;
                }
                chestsort$searchResultItems = items;
                chestsort$searchResultTagIds = tagIds;
                chestsort$searchResultPresetNames = java.util.List.of();
                chestsort$searchResultSubtitles = subtitles;
                chestsort$resultsScroll = 0;
                chestsort$selectedResultIndex = items.isEmpty() ? -1 : 0;
                chestsort$clampScroll();
                return;
            }

            chestsort$searchResultItems = java.util.List.of();
            chestsort$searchResultTagIds = java.util.List.of();
            chestsort$searchResultPresetNames = java.util.List.of();
            chestsort$searchResultSubtitles = java.util.List.of();
            chestsort$resultsScroll = 0;
            chestsort$selectedResultIndex = -1;
            return;
        }

        // If the query matches entries already in the filter, move them to the top so the user can see
        // they are already added (and keep them out of the search results).
        if (!chestsort$tagBrowserMode && !query.startsWith("&")) {
            if (query.startsWith("#")) {
                chestsort$bumpMatchingFilterTagsToTop(query);
            } else {
                chestsort$bumpMatchingFilterItemsToTop(query);
            }
        }

        java.util.ArrayList<Item> resultItems = new java.util.ArrayList<>(16);
        java.util.ArrayList<String> resultTagIds = new java.util.ArrayList<>(16);
        java.util.ArrayList<String> subtitles = new java.util.ArrayList<>(16);

        // Preset search: prefix '&' filters preset names.
        if (!chestsort$tagBrowserMode && query.startsWith("&")) {
            String q = query.substring(1).trim();
            java.util.ArrayList<Item> items = new java.util.ArrayList<>(16);
            java.util.ArrayList<String> tagIds = new java.util.ArrayList<>(16);
            java.util.ArrayList<String> presetNames = new java.util.ArrayList<>(16);
            java.util.ArrayList<String> presetSubtitles = new java.util.ArrayList<>(16);

            java.util.HashSet<String> alreadyApplied = new java.util.HashSet<>();
            if (chestsort$editingFilterPresets != null) {
                for (String p : chestsort$editingFilterPresets) {
                    if (p == null) continue;
                    String t = p.trim();
                    if (!t.isEmpty()) alreadyApplied.add(t);
                }
            }

            for (String name : ClientPresetRegistry.namesSorted()) {
                if (name == null) continue;
                if (alreadyApplied.contains(name.trim())) continue;
                String lcName = chestsort$lc(name);
                if (q.isEmpty() || lcName.startsWith(q) || lcName.contains(q)) {
                    items.add(null);
                    tagIds.add("");
                    presetNames.add(name);
                    ContainerFilterSpec spec = ClientPresetRegistry.get(name);
                    int itemCount = spec == null || spec.items() == null ? 0 : spec.items().size();
                    int tagCount = spec == null || spec.tags() == null ? 0 : spec.tags().size();
                    presetSubtitles.add("items: " + itemCount + " tags: " + tagCount);
                    if (items.size() >= 200) break;
                }
            }

            chestsort$searchResultItems = items;
            chestsort$searchResultTagIds = tagIds;
            chestsort$searchResultPresetNames = presetNames;
            chestsort$searchResultSubtitles = presetSubtitles;
            chestsort$resultsScroll = 0;
            chestsort$selectedResultIndex = items.isEmpty() ? -1 : 0;
            chestsort$clampScroll();
            return;
        }

        // Tag browser mode: by default search within tag items.
        if (chestsort$tagBrowserMode) {
            if (query.startsWith("#")) {
                chestsort$appendMatchingTagResults(query, resultItems, resultTagIds, subtitles);
            } else {
                java.util.Set<String> exc = chestsort$getExceptionSetForTag(chestsort$tagBrowserTagId);
                for (Item item : chestsort$tagBrowserItems) {
                    var id = Registries.ITEM.getId(item);
                    String idStr = id == null ? "" : id.toString();
                    String nameStr = chestsort$lc(Text.translatable(item.getTranslationKey()).getString());
                    if (chestsort$lc(idStr).contains(query) || nameStr.contains(query)) {
                        if (!idStr.isEmpty() && exc.contains(idStr)) continue;
                        resultItems.add(item);
                        resultTagIds.add("");
                        subtitles.add(chestsort$formatResultSubtitle(item));
                        if (resultItems.size() >= 200) break;
                    }
                }
            }
        } else {
            // Normal mode: if the user starts with '#', treat it as a tag entry to add.
            if (query.startsWith("#")) {
                chestsort$appendMatchingTagResults(query, resultItems, resultTagIds, subtitles);
            } else {
                java.util.HashSet<String> already = new java.util.HashSet<>(chestsort$editingFilterItems == null ? java.util.List.of() : chestsort$editingFilterItems);
                for (Item item : chestsort$allItems) {
                    var id = Registries.ITEM.getId(item);
                    String idStr = id == null ? "" : id.toString();
                    String nameStr = chestsort$lc(Text.translatable(item.getTranslationKey()).getString());

                    // Keep already-added entries out of the results.
                    if (!idStr.isEmpty() && already.contains(idStr)) continue;

                    if (chestsort$lc(idStr).contains(query) || nameStr.contains(query)) {
                        resultItems.add(item);
                        resultTagIds.add("");
                        subtitles.add(chestsort$formatResultSubtitle(item));
                        if (resultItems.size() >= 200) {
                            break;
                        }
                    }
                }
            }
        }

        chestsort$searchResultItems = resultItems;
        chestsort$searchResultTagIds = resultTagIds;
        chestsort$searchResultPresetNames = java.util.List.of();
        chestsort$searchResultSubtitles = subtitles;
        chestsort$resultsScroll = 0;
        chestsort$selectedResultIndex = resultItems.isEmpty() ? -1 : 0;
        chestsort$clampScroll();
    }

    @Unique
    private void chestsort$clampScroll() {
        chestsort$ensureState();
        // Clamp scroll offsets to list sizes / visible rows.
        int maxLeft = Math.max(0, chestsort$getLeftRows().size() - Math.max(1, chestsort$leftRowsShown));
        chestsort$leftScroll = Math.max(0, Math.min(chestsort$leftScroll, maxLeft));

        int maxResults = Math.max(0, chestsort$getSearchResultsSize() - Math.max(1, chestsort$resultsRowsShown));
        chestsort$resultsScroll = Math.max(0, Math.min(chestsort$resultsScroll, maxResults));

        // Keep selection visible.
        if (chestsort$selectedLeftRowIndex >= 0 && chestsort$leftRowsShown > 0) {
            if (chestsort$selectedLeftRowIndex < chestsort$leftScroll) {
                chestsort$leftScroll = chestsort$selectedLeftRowIndex;
            } else if (chestsort$selectedLeftRowIndex >= chestsort$leftScroll + chestsort$leftRowsShown) {
                chestsort$leftScroll = chestsort$selectedLeftRowIndex - chestsort$leftRowsShown + 1;
            }
            maxLeft = Math.max(0, chestsort$getLeftRows().size() - Math.max(1, chestsort$leftRowsShown));
            chestsort$leftScroll = Math.max(0, Math.min(chestsort$leftScroll, maxLeft));
        }

        if (chestsort$selectedResultIndex >= 0 && chestsort$resultsRowsShown > 0) {
            if (chestsort$selectedResultIndex < chestsort$resultsScroll) {
                chestsort$resultsScroll = chestsort$selectedResultIndex;
            } else if (chestsort$selectedResultIndex >= chestsort$resultsScroll + chestsort$resultsRowsShown) {
                chestsort$resultsScroll = chestsort$selectedResultIndex - chestsort$resultsRowsShown + 1;
            }
            maxResults = Math.max(0, chestsort$getSearchResultsSize() - chestsort$resultsRowsShown);
            chestsort$resultsScroll = Math.max(0, Math.min(chestsort$resultsScroll, maxResults));
        }
    }

    @Unique
    private int chestsort$getSearchResultsSize() {
        return chestsort$searchResultItems == null ? 0 : chestsort$searchResultItems.size();
    }

    @Unique
    private static String chestsort$formatResultSubtitle(Item item) {
        String itemId = String.valueOf(Registries.ITEM.getId(item));
        if (item instanceof net.minecraft.item.BlockItem blockItem) {
            String blockId = String.valueOf(Registries.BLOCK.getId(blockItem.getBlock()));
            if (!blockId.isEmpty() && !blockId.equals(itemId)) {
                return itemId + " | " + blockId;
            }
        }
        return itemId;
    }

    @Unique
    private void chestsort$updateLayout() {
        chestsort$ensureState();
        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        int availableH = Math.max(0, screenH - (CHESTSORT_PANEL_GAP * 2));

        // Keep panels OUTSIDE the handled screen when possible.
        // If there isn't enough room (small window or large GUI scale), shrink the panels to fit.
        int availableLeft = x - (CHESTSORT_PANEL_GAP * 2);
        chestsort$leftPanelW = Math.min(CHESTSORT_PANEL_W, Math.max(0, availableLeft));
        chestsort$leftPanelX = x - chestsort$leftPanelW - CHESTSORT_PANEL_GAP;

        int availableRight = screenW - (x + backgroundWidth) - (CHESTSORT_PANEL_GAP * 2);
        chestsort$rightPanelW = Math.min(CHESTSORT_PANEL_W, Math.max(0, availableRight));
        chestsort$rightPanelX = x + backgroundWidth + CHESTSORT_PANEL_GAP;

        // Compute vertical layout for filter mode so buttons/results never go off-screen.
        // Left panel is a scrollable list; in filter mode it may have a tab header.
        int leftHeaderH = chestsort$filterMode ? CHESTSORT_HEADER_H : 0;
        chestsort$leftRowsShown = Math.min(12, Math.max(0, (availableH - leftHeaderH - CHESTSORT_PAD_BOTTOM) / CHESTSORT_ROW_H));
        int leftPanelH = (chestsort$leftRowsShown * CHESTSORT_ROW_H) + CHESTSORT_PAD_BOTTOM + leftHeaderH;

        int rightPanelH;
        if (chestsort$filterMode) {
            int fixedNoRows = 24 /*search field (20) + 4px gap*/
                + ((chestsort$editingPresetName != null && !chestsort$editingPresetName.isEmpty()) ? 24 : 0) /*edit preset toggle*/
                + CHESTSORT_HEADER_H + CHESTSORT_PAD_BOTTOM
                ;
            chestsort$resultsRowsShown = Math.min(10, Math.max(0, (availableH - fixedNoRows) / CHESTSORT_ROW_H));
            rightPanelH = 24 + ((chestsort$editingPresetName != null && !chestsort$editingPresetName.isEmpty()) ? 24 : 0) + CHESTSORT_HEADER_H + (chestsort$resultsRowsShown * CHESTSORT_ROW_H) + CHESTSORT_PAD_BOTTOM;

            chestsort$resultsPanelY = 0;
            chestsort$resultsRowsY = 0;
        } else {
            // Filter/Sort buttons only.
            chestsort$resultsRowsShown = 0;
            // Filter + Import/Export + Sort + Autosort (or import/export popup).
            // Filter + Import/Export + Sort + Organize + Autosort
            // + Lock Slot
            rightPanelH = 140;
            chestsort$resultsPanelY = 0;
            chestsort$resultsRowsY = 0;
        }

        int leftYPreferred = y + CHESTSORT_PANEL_GAP;
        int leftYMax = screenH - leftPanelH - CHESTSORT_PANEL_GAP;
        chestsort$leftPanelY = Math.max(CHESTSORT_PANEL_GAP, Math.min(leftYPreferred, leftYMax));

        int rightYPreferred = y + CHESTSORT_PANEL_GAP;
        int rightYMax = screenH - rightPanelH - CHESTSORT_PANEL_GAP;
        chestsort$rightPanelY = Math.max(CHESTSORT_PANEL_GAP, Math.min(rightYPreferred, rightYMax));

        if (chestsort$filterMode) {
            int resultsPanelY = chestsort$rightPanelY + 24 + ((chestsort$editingPresetName != null && !chestsort$editingPresetName.isEmpty()) ? 24 : 0);
            chestsort$resultsPanelY = resultsPanelY;
            chestsort$resultsRowsY = resultsPanelY + 18;
        }

        chestsort$clampScroll();
    }

    @Unique
    private void chestsort$applyWidgetLayout() {
        chestsort$updateLayout();

        int rightX = chestsort$rightPanelX;
        int rightY = chestsort$rightPanelY;
        int rightW = chestsort$rightPanelW;

        if (chestsort$filterButton != null) {
            chestsort$filterButton.setX(rightX);
            chestsort$filterButton.setY(rightY);
            chestsort$filterButton.setWidth(rightW);
        }
        if (chestsort$importOpenButton != null) {
            int w = Math.max(0, (rightW - 18) / 2);
            chestsort$importOpenButton.setX(rightX + 6);
            chestsort$importOpenButton.setY(rightY + 24);
            chestsort$importOpenButton.setWidth(w);
        }
        if (chestsort$exportOpenButton != null) {
            int w = Math.max(0, (rightW - 18) / 2);
            chestsort$exportOpenButton.setX(rightX + 12 + w);
            chestsort$exportOpenButton.setY(rightY + 24);
            chestsort$exportOpenButton.setWidth(w);
        }
        if (chestsort$sortButton != null) {
            chestsort$sortButton.setX(rightX);
            chestsort$sortButton.setY(rightY + 48);
            chestsort$sortButton.setWidth(rightW);
        }
        if (chestsort$organizeButton != null) {
            chestsort$organizeButton.setX(rightX);
            chestsort$organizeButton.setY(rightY + 72);
            chestsort$organizeButton.setWidth(rightW);
        }
        if (chestsort$autosortButton != null) {
            chestsort$autosortButton.setX(rightX);
            chestsort$autosortButton.setY(rightY + 96);
            chestsort$autosortButton.setWidth(rightW);
        }
        if (chestsort$lockSlotButton != null) {
            chestsort$lockSlotButton.setX(rightX);
            chestsort$lockSlotButton.setY(rightY + 120);
            chestsort$lockSlotButton.setWidth(rightW);
        }
        if (chestsort$searchField != null) {
            chestsort$searchField.setX(rightX);
            chestsort$searchField.setY(rightY);
            chestsort$searchField.setWidth(rightW);
        }

        int leftX = chestsort$leftPanelX;
        int leftY = chestsort$leftPanelY;
        int leftW = chestsort$leftPanelW;
        if (chestsort$whitelistTabButton != null) {
            int w = Math.max(0, (leftW - 18) / 2);
            chestsort$whitelistTabButton.setX(leftX + 6);
            chestsort$whitelistTabButton.setY(leftY + 2);
            chestsort$whitelistTabButton.setWidth(w);
        }
        if (chestsort$blacklistTabButton != null) {
            int w = Math.max(0, (leftW - 18) / 2);
            chestsort$blacklistTabButton.setX(leftX + 12 + w);
            chestsort$blacklistTabButton.setY(leftY + 2);
            chestsort$blacklistTabButton.setWidth(w);
        }

        if (chestsort$editPresetButton != null) {
            chestsort$editPresetButton.setX(rightX);
            chestsort$editPresetButton.setY(rightY + 24);
            chestsort$editPresetButton.setWidth(rightW);
        }

        if (chestsort$importField != null) {
            chestsort$importField.setX(rightX + 6);
            chestsort$importField.setY(rightY + 22);
            chestsort$importField.setWidth(Math.max(0, rightW - 12));
        }
        if (chestsort$importConfirmButton != null) {
            chestsort$importConfirmButton.setX(rightX + 6);
            chestsort$importConfirmButton.setY(rightY + 46);
            chestsort$importConfirmButton.setWidth(Math.max(0, (rightW - 18) / 2));
        }
        if (chestsort$importCancelButton != null) {
            int w = Math.max(0, (rightW - 18) / 2);
            chestsort$importCancelButton.setX(rightX + 12 + w);
            chestsort$importCancelButton.setY(rightY + 46);
            chestsort$importCancelButton.setWidth(w);
        }

        if (chestsort$exportField != null) {
            chestsort$exportField.setX(rightX + 6);
            chestsort$exportField.setY(rightY + 22);
            chestsort$exportField.setWidth(Math.max(0, rightW - 12));
        }
        if (chestsort$exportCloseButton != null) {
            chestsort$exportCloseButton.setX(rightX + 6);
            chestsort$exportCloseButton.setY(rightY + 46);
            chestsort$exportCloseButton.setWidth(Math.max(0, rightW - 12));
        }

        if (chestsort$presetImportField != null) {
            chestsort$presetImportField.setX(rightX + 6);
            chestsort$presetImportField.setY(rightY + 22);
            chestsort$presetImportField.setWidth(Math.max(0, rightW - 12));
        }
        if (chestsort$presetImportConfirmButton != null) {
            chestsort$presetImportConfirmButton.setX(rightX + 6);
            chestsort$presetImportConfirmButton.setY(rightY + 46);
            chestsort$presetImportConfirmButton.setWidth(Math.max(0, (rightW - 18) / 2));
        }
        if (chestsort$presetImportCancelButton != null) {
            int w = Math.max(0, (rightW - 18) / 2);
            chestsort$presetImportCancelButton.setX(rightX + 12 + w);
            chestsort$presetImportCancelButton.setY(rightY + 46);
            chestsort$presetImportCancelButton.setWidth(w);
        }

        if (chestsort$presetExportField != null) {
            chestsort$presetExportField.setX(rightX + 6);
            chestsort$presetExportField.setY(rightY + 22);
            chestsort$presetExportField.setWidth(Math.max(0, rightW - 12));
        }
        if (chestsort$presetExportCloseButton != null) {
            chestsort$presetExportCloseButton.setX(rightX + 6);
            chestsort$presetExportCloseButton.setY(rightY + 46);
            chestsort$presetExportCloseButton.setWidth(Math.max(0, rightW - 12));
        }
    }

    @Unique
    private void chestsort$saveEditingFilter() {
        if (!chestsort$isTargetContainer()) return;

        if (!chestsort$filterDirty) {
            return;
        }

        String dimId = ClientContainerContext.dimensionId();
        long posLong = ClientContainerContext.posLong();
        boolean hasServerContext = dimId != null && !dimId.isEmpty();

        // Keep tab storage in sync in case list instances were replaced.
        chestsort$syncTabStorageFromEditingLists();

        // Preset editor target.
        if (chestsort$editingPresetName != null && !chestsort$editingPresetName.isEmpty() && chestsort$editPresetMode) {
            String presetName = chestsort$editingPresetName.trim();
            if (!presetName.isEmpty()) {
                java.util.List<String> wlItems = ContainerFilterSpec.normalizeStrings(chestsort$editingWhitelistItems);
                java.util.List<TagFilterSpec> wlTags = (chestsort$editingWhitelistTags == null ? java.util.List.of() : chestsort$editingWhitelistTags);
                ContainerFilterSpec whitelistForPreset = new ContainerFilterSpec(wlItems, wlTags, java.util.List.of(), false).normalized();

                java.util.List<String> blItems = ContainerFilterSpec.normalizeStrings(chestsort$editingBlacklistItems);
                java.util.List<TagFilterSpec> blTags = (chestsort$editingBlacklistTags == null ? java.util.List.of() : chestsort$editingBlacklistTags);
                ContainerFilterSpec blacklistForPreset = new ContainerFilterSpec(blItems, blTags, java.util.List.of(), false).normalized();

                ClientNetworkingUtil.sendSafe(new SetPresetV2Payload(presetName, whitelistForPreset, blacklistForPreset));
                ClientPresetRegistry.putLocal(presetName, whitelistForPreset);
                ClientPresetRegistry.putLocalBlacklist(presetName, blacklistForPreset);

                chestsort$editingWhitelistItems = new java.util.ArrayList<>(whitelistForPreset.items());
                chestsort$editingWhitelistTags = new java.util.ArrayList<>(whitelistForPreset.tags());
                chestsort$editingWhitelistPresets = new java.util.ArrayList<>();

                chestsort$editingBlacklistItems = new java.util.ArrayList<>(blacklistForPreset.items());
                chestsort$editingBlacklistTags = new java.util.ArrayList<>(blacklistForPreset.tags());
                chestsort$editingBlacklistPresets = new java.util.ArrayList<>();

                chestsort$applyActiveTabToEditingLists();
            }
            chestsort$filterDirty = false;
            return;
        }

        java.util.List<String> wlItems = ContainerFilterSpec.normalizeStrings(chestsort$editingWhitelistItems);
        java.util.List<TagFilterSpec> wlTags = (chestsort$editingWhitelistTags == null ? java.util.List.of() : chestsort$editingWhitelistTags);
        java.util.List<String> wlPresets = ContainerFilterSpec.normalizeStrings(chestsort$editingWhitelistPresets == null ? java.util.List.of() : chestsort$editingWhitelistPresets);
        ContainerFilterSpec whitelistForContainer = new ContainerFilterSpec(wlItems, wlTags, wlPresets, chestsort$editingAutosort).normalized();

        java.util.List<String> blItems = ContainerFilterSpec.normalizeStrings(chestsort$editingBlacklistItems);
        java.util.List<TagFilterSpec> blTags = (chestsort$editingBlacklistTags == null ? java.util.List.of() : chestsort$editingBlacklistTags);
        java.util.List<String> blPresets = ContainerFilterSpec.normalizeStrings(chestsort$editingBlacklistPresets == null ? java.util.List.of() : chestsort$editingBlacklistPresets);
        ContainerFilterSpec blacklistForContainer = new ContainerFilterSpec(blItems, blTags, blPresets, false).normalized();

        if (hasServerContext) {
            ClientNetworkingUtil.sendSafe(new SetContainerFiltersPayload(
                dimId,
                posLong,
                whitelistForContainer,
                blacklistForContainer,
                chestsort$editingWhitelistPriority
            ));

            // Legacy whitelist-only payloads (still useful for compatibility).
            ClientNetworkingUtil.sendSafe(new SetFilterV2Payload(dimId, posLong, whitelistForContainer));
            ClientNetworkingUtil.sendSafe(new SetFilterPayload(dimId, posLong, whitelistForContainer.items()));
        }

        ClientContainerContext.set(
            dimId,
            posLong,
            ClientContainerContext.containerType(),
            whitelistForContainer,
            blacklistForContainer,
            chestsort$editingWhitelistPriority
        );

        // Client-only persistence (works on unmodded servers once we have a key).
        if (dimId != null && !dimId.isEmpty() && posLong != 0L) {
            ClientContainerFilterStorage.put(dimId, posLong, whitelistForContainer, blacklistForContainer, chestsort$editingWhitelistPriority);
        }

        chestsort$editingWhitelistItems = new java.util.ArrayList<>(whitelistForContainer.items());
        chestsort$editingWhitelistTags = new java.util.ArrayList<>(whitelistForContainer.tags());
        chestsort$editingWhitelistPresets = new java.util.ArrayList<>(whitelistForContainer.presets());

        chestsort$editingBlacklistItems = new java.util.ArrayList<>(blacklistForContainer.items());
        chestsort$editingBlacklistTags = new java.util.ArrayList<>(blacklistForContainer.tags());
        chestsort$editingBlacklistPresets = new java.util.ArrayList<>(blacklistForContainer.presets());

        chestsort$applyActiveTabToEditingLists();
        chestsort$filterDirty = false;
    }

    @Inject(method = "init", at = @At("TAIL"))
    @SuppressWarnings("unused")
    private void chestsort$init(CallbackInfo ci) {
        // Client-only bootstrap: on unmodded servers we won't get ContainerContext packets.
        // Infer the container position from the last interacted block and load per-container filters.
        if (this.handler instanceof net.minecraft.screen.GenericContainerScreenHandler) {
            String currentDim = ClientContainerContext.dimensionId();
            if (currentDim == null || currentDim.isEmpty()) {
                String dimId = ClientLastInteractedBlock.dimIdIfRecent(5000);
                long posLong = ClientLastInteractedBlock.posLongIfRecent(5000);
                if (dimId != null && !dimId.isEmpty() && posLong != 0L) {
                    var entry = ClientContainerFilterStorage.get(dimId, posLong);
                    if (entry != null) {
                        ClientContainerContext.set(dimId, posLong, "chest", entry.whitelist(), entry.blacklist(), entry.whitelistPriority());
                    } else {
                        // Keep last-used filter values, but associate them with this container's key.
                        ClientContainerContext.setKey(dimId, posLong, "chest");
                    }
                }
            }
        }

        var whitelist = ClientContainerContext.filterSpec();
        var blacklist = ClientContainerContext.blacklistSpec();
        chestsort$editingWhitelistItems = new java.util.ArrayList<>(whitelist == null ? java.util.List.of() : whitelist.items());
        chestsort$editingWhitelistTags = new java.util.ArrayList<>(whitelist == null ? java.util.List.of() : whitelist.tags());
        chestsort$editingWhitelistPresets = new java.util.ArrayList<>(whitelist == null ? java.util.List.of() : (whitelist.presets() == null ? java.util.List.of() : whitelist.presets()));
        chestsort$editingBlacklistItems = new java.util.ArrayList<>(blacklist == null ? java.util.List.of() : blacklist.items());
        chestsort$editingBlacklistTags = new java.util.ArrayList<>(blacklist == null ? java.util.List.of() : blacklist.tags());
        chestsort$editingBlacklistPresets = new java.util.ArrayList<>(blacklist == null ? java.util.List.of() : (blacklist.presets() == null ? java.util.List.of() : blacklist.presets()));
        chestsort$editingWhitelistPriority = ClientContainerContext.whitelistPriority();
        chestsort$filterTab = CHESTSORT_TAB_WHITELIST;
        chestsort$applyActiveTabToEditingLists();
        chestsort$editingAutosort = whitelist != null && whitelist.autosort();

        chestsort$updateLayout();
        int rightW = CHESTSORT_PANEL_W;
        int rightX = chestsort$rightPanelX;
        int rightY = chestsort$rightPanelY;

        chestsort$undoButton = ButtonWidget.builder(Text.literal("Undo"), b -> {
            if (!ClientSortNotificationState.isActiveForCurrentContainer()) return;
            long undoId = ClientSortNotificationState.undoId();
            if (undoId == 0L) return;
            if (ClientContainerContext.dimensionId().isEmpty()) return;
            ClientNetworkingUtil.sendSafe(new UndoSortPayload(ClientContainerContext.dimensionId(), ClientContainerContext.posLong(), undoId));
        }).dimensions(0, 0, 50, 20).build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$undoButton);

        chestsort$filterButton = ButtonWidget.builder(Text.literal("Filter"), b -> {
            if (!chestsort$isTargetContainer()) return;
            var wl = ClientContainerContext.filterSpec();
            var bl = ClientContainerContext.blacklistSpec();
            chestsort$editingWhitelistItems = new java.util.ArrayList<>(wl == null ? java.util.List.of() : wl.items());
            chestsort$editingWhitelistTags = new java.util.ArrayList<>(wl == null ? java.util.List.of() : wl.tags());
            chestsort$editingWhitelistPresets = new java.util.ArrayList<>(wl == null ? java.util.List.of() : (wl.presets() == null ? java.util.List.of() : wl.presets()));
            chestsort$editingBlacklistItems = new java.util.ArrayList<>(bl == null ? java.util.List.of() : bl.items());
            chestsort$editingBlacklistTags = new java.util.ArrayList<>(bl == null ? java.util.List.of() : bl.tags());
            chestsort$editingBlacklistPresets = new java.util.ArrayList<>(bl == null ? java.util.List.of() : (bl.presets() == null ? java.util.List.of() : bl.presets()));
            chestsort$editingWhitelistPriority = ClientContainerContext.whitelistPriority();
            chestsort$filterTab = CHESTSORT_TAB_WHITELIST;
            chestsort$applyActiveTabToEditingLists();
            chestsort$editingAutosort = wl != null && wl.autosort();
            chestsort$filterMode = true;
            chestsort$filterDirty = false;
            chestsort$tagBrowserMode = false;
            chestsort$tagBrowserTagId = "";
            chestsort$tagBrowserItems = java.util.List.of();
            chestsort$editingPresetName = "";
            chestsort$editPresetMode = false;
            chestsort$importPopupOpen = false;
            chestsort$exportPopupOpen = false;
            chestsort$presetImportPopupOpen = false;
            chestsort$presetExportPopupOpen = false;

            chestsort$leftScroll = 0;
            chestsort$resultsScroll = 0;

            if (chestsort$searchField != null) {
                chestsort$searchField.setText("");
                chestsort$searchField.setFocused(true);
            }
            chestsort$selectedLeftRowIndex = -1;
            chestsort$selectedResultIndex = -1;
            if (chestsort$searchField != null) {
                chestsort$updateSearchResults();
            }
        })
            .dimensions(rightX, rightY, rightW, 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$filterButton);

        chestsort$whitelistTabButton = ButtonWidget.builder(Text.literal("Whitelist"), b -> chestsort$switchFilterTab(CHESTSORT_TAB_WHITELIST))
            .dimensions(0, 0, 10, 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$whitelistTabButton);

        chestsort$blacklistTabButton = ButtonWidget.builder(Text.literal("Blacklist"), b -> chestsort$switchFilterTab(CHESTSORT_TAB_BLACKLIST))
            .dimensions(0, 0, 10, 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$blacklistTabButton);

        chestsort$importOpenButton = ButtonWidget.builder(Text.literal("Import"), b -> {
            if (!chestsort$isTargetContainer()) return;
            chestsort$importPopupOpen = true;
            chestsort$exportPopupOpen = false;
            chestsort$importError = "";
            if (chestsort$importField != null) {
                chestsort$importField.setText("");
                chestsort$importField.setFocused(true);
            }
        })
            .dimensions(rightX, rightY + 24, Math.max(0, (rightW - 18) / 2), 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$importOpenButton);

        chestsort$exportOpenButton = ButtonWidget.builder(Text.literal("Export"), b -> {
            if (!chestsort$isTargetContainer()) return;
            chestsort$exportPopupOpen = true;
            chestsort$importPopupOpen = false;
            chestsort$exportError = "";

            ContainerFilterSpec spec2 = ClientContainerContext.filterSpec();
            if (spec2 == null || spec2.isEmpty()) {
                chestsort$exportError = "No filter set";
                if (chestsort$exportField != null) {
                    chestsort$exportField.setText("");
                }
            } else {
                String encoded = chestsort$encodeFilterSpec(spec2);
                if (chestsort$exportField != null) {
                    chestsort$exportField.setText(encoded);
                    chestsort$exportField.setFocused(true);
                }
            }
        })
            .dimensions(rightX, rightY + 24, Math.max(0, (rightW - 18) / 2), 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$exportOpenButton);

        chestsort$sortButton = ButtonWidget.builder(Text.literal("Sort"), b -> {
            if (!chestsort$isTargetContainer()) return;
            if (!ClientContainerContext.hasFilter()) return;

            String dimId = ClientContainerContext.dimensionId();
            if (dimId != null && !dimId.isEmpty() && ClientNetworkingUtil.canSend(SortRequestPayload.ID)) {
                ClientNetworkingUtil.sendSafe(new SortRequestPayload(dimId, ClientContainerContext.posLong()));
                return;
            }

            // Client-only fallback for servers without ChestSort.
            ClientOnlySorter.sortIntoOpenContainer(
                MinecraftClient.getInstance(),
                this.handler,
                ClientContainerContext.filterSpec(),
                ClientContainerContext.blacklistSpec(),
                ClientContainerContext.whitelistPriority()
            );
        }).dimensions(rightX, rightY + 48, rightW, 20).build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$sortButton);

        chestsort$organizeButton = ButtonWidget.builder(Text.literal("Organize"), b -> {
            if (!chestsort$isTargetContainer()) return;
            String dimId = ClientContainerContext.dimensionId();
            if (dimId != null && !dimId.isEmpty() && ClientNetworkingUtil.canSend(OrganizeRequestPayload.ID)) {
                ClientNetworkingUtil.sendSafe(new OrganizeRequestPayload(dimId, ClientContainerContext.posLong()));
                return;
            }

            // Client-only fallback for servers without ChestSort.
            ClientOnlyOrganizer.organizeOpenContainer(MinecraftClient.getInstance(), this.handler);
        }).dimensions(rightX, rightY + 72, rightW, 20).build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$organizeButton);

        chestsort$autosortButton = ButtonWidget.builder(Text.literal("Autosort: OFF"), b -> {
            if (!chestsort$isTargetContainer()) return;
            if (ClientContainerContext.dimensionId().isEmpty() || !ClientNetworkingUtil.canSend(SetFilterV2Payload.ID)) {
                var client = MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    client.player.sendMessage(Text.literal("[ChestSort] Autosort requires server support").formatted(Formatting.GRAY), false);
                }
                return;
            }

            ContainerFilterSpec current = ClientContainerContext.filterSpec();
            current = (current == null) ? new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of(), false) : current.normalized();

            boolean next = !current.autosort();
            ContainerFilterSpec updated = new ContainerFilterSpec(current.items(), current.tags(), current.presets(), next).normalized();

            // v2 payload (items + tags + presets + autosort) + legacy v1 payload (items only).
            ClientNetworkingUtil.sendSafe(new SetFilterV2Payload(ClientContainerContext.dimensionId(), ClientContainerContext.posLong(), updated));
            ClientNetworkingUtil.sendSafe(new SetFilterPayload(ClientContainerContext.dimensionId(), ClientContainerContext.posLong(), updated.items()));
            ClientContainerContext.set(ClientContainerContext.dimensionId(), ClientContainerContext.posLong(), ClientContainerContext.containerType(), updated);

            // Keep the filter editor state in sync if the user opens it right after toggling.
            chestsort$editingAutosort = next;
        }).dimensions(rightX, rightY + 96, rightW, 20).build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$autosortButton);

        chestsort$lockSlotButton = ButtonWidget.builder(Text.literal("Lock Slots: OFF"), b -> {
            if (!chestsort$isTargetContainer()) return;
            chestsort$lockSlotsMode = !chestsort$lockSlotsMode;
        }).dimensions(rightX, rightY + 120, rightW, 20).build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$lockSlotButton);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr != null) {
            chestsort$searchField = new TextFieldWidget(tr, rightX, rightY, rightW, 20, Text.literal("Search"));
            chestsort$searchField.setMaxLength(64);
            chestsort$searchField.setChangedListener(s -> chestsort$updateSearchResults());
            ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$searchField);

            chestsort$importField = new TextFieldWidget(tr, rightX + 6, rightY + 22, Math.max(0, rightW - 12), 20, Text.literal("Import"));
            chestsort$importField.setMaxLength(32767);
            ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$importField);

            chestsort$exportField = new TextFieldWidget(tr, rightX + 6, rightY + 22, Math.max(0, rightW - 12), 20, Text.literal("Export"));
            chestsort$exportField.setMaxLength(32767);
            ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$exportField);
        }

        chestsort$importConfirmButton = ButtonWidget.builder(Text.literal("Import"), b -> chestsort$tryImportFromPopup())
            .dimensions(rightX + 6, rightY + 46, Math.max(0, (rightW - 18) / 2), 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$importConfirmButton);

        chestsort$importCancelButton = ButtonWidget.builder(Text.literal("Cancel"), b -> chestsort$closeImportPopup())
            .dimensions(rightX + 12 + Math.max(0, (rightW - 18) / 2), rightY + 46, Math.max(0, (rightW - 18) / 2), 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$importCancelButton);

        chestsort$exportCloseButton = ButtonWidget.builder(Text.literal("Close"), b -> chestsort$closeExportPopup())
            .dimensions(rightX + 6, rightY + 46, Math.max(0, rightW - 12), 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$exportCloseButton);

        chestsort$editPresetButton = ButtonWidget.builder(Text.literal("Edit preset: OFF"), b -> {
            if (chestsort$editingPresetName == null || chestsort$editingPresetName.isEmpty()) {
                chestsort$editPresetMode = false;
                return;
            }

            chestsort$editPresetMode = !chestsort$editPresetMode;
        }).dimensions(rightX, rightY + 24, rightW, 20).build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$editPresetButton);

        if (tr != null) {
            chestsort$presetImportField = new TextFieldWidget(tr, rightX + 6, rightY + 22, Math.max(0, rightW - 12), 20, Text.literal("Import Preset"));
            chestsort$presetImportField.setMaxLength(32767);
            ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$presetImportField);

            chestsort$presetExportField = new TextFieldWidget(tr, rightX + 6, rightY + 22, Math.max(0, rightW - 12), 20, Text.literal("Export Preset"));
            chestsort$presetExportField.setMaxLength(32767);
            ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$presetExportField);
        }

        chestsort$presetImportConfirmButton = ButtonWidget.builder(Text.literal("Import"), b -> chestsort$tryImportPresetFromPopup())
            .dimensions(rightX + 6, rightY + 46, Math.max(0, (rightW - 18) / 2), 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$presetImportConfirmButton);

        chestsort$presetImportCancelButton = ButtonWidget.builder(Text.literal("Cancel"), b -> chestsort$closePresetImportPopup())
            .dimensions(rightX + 12 + Math.max(0, (rightW - 18) / 2), rightY + 46, Math.max(0, (rightW - 18) / 2), 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$presetImportCancelButton);

        chestsort$presetExportCloseButton = ButtonWidget.builder(Text.literal("Close"), b -> chestsort$closePresetExportPopup())
            .dimensions(rightX + 6, rightY + 46, Math.max(0, rightW - 12), 20)
            .build();
        ((ScreenInvoker) (Object) this).chestsort$invokeAddDrawableChild(chestsort$presetExportCloseButton);

        // Apply final layout (may shrink panels based on window size).
        chestsort$applyWidgetLayout();

        chestsort$applyVisibility();
    }

    @Unique
    private void chestsort$applyVisibility() {
        // Ensure widgets track current window size/GUI scale.
        chestsort$applyWidgetLayout();

        boolean isTarget = chestsort$isTargetContainer();
        boolean hasRightPanel = chestsort$rightPanelW >= 20;
        boolean showMainButtons = isTarget && !chestsort$filterMode && !chestsort$importPopupOpen && !chestsort$exportPopupOpen && !chestsort$presetImportPopupOpen && !chestsort$presetExportPopupOpen;
        if (chestsort$filterButton != null) chestsort$filterButton.visible = showMainButtons;
        if (chestsort$importOpenButton != null) chestsort$importOpenButton.visible = showMainButtons;
        if (chestsort$exportOpenButton != null) chestsort$exportOpenButton.visible = showMainButtons;
        if (chestsort$sortButton != null) chestsort$sortButton.visible = showMainButtons && ClientContainerContext.hasFilter();
        if (chestsort$organizeButton != null) chestsort$organizeButton.visible = showMainButtons;
        if (chestsort$autosortButton != null) {
            chestsort$autosortButton.visible = showMainButtons;
            ContainerFilterSpec spec = ClientContainerContext.filterSpec();
            boolean on = spec != null && spec.autosort();
            chestsort$autosortButton.setMessage(Text.literal("Autosort: " + (on ? "ON" : "OFF")));
        }

        if (chestsort$lockSlotButton != null) {
            chestsort$lockSlotButton.visible = showMainButtons;
            chestsort$lockSlotButton.active = showMainButtons;
            chestsort$lockSlotButton.setMessage(Text.literal("Lock Slots: " + (chestsort$lockSlotsMode ? "ON" : "OFF")));
        }

        if (chestsort$undoButton != null) {
            boolean showUndo = isTarget
                && ClientSortNotificationState.isActiveForCurrentContainer()
                && ClientSortNotificationState.undoId() != 0L;
            chestsort$undoButton.visible = showUndo;

            if (showUndo) {
                var client = MinecraftClient.getInstance();
                int screenW = client.getWindow().getScaledWidth();
                int screenH = client.getWindow().getScaledHeight();
                int w = chestsort$undoButton.getWidth();
                int h = chestsort$undoButton.getHeight();
                int pad = 4;
                chestsort$undoButton.setX(Math.max(0, screenW - w - pad));
                chestsort$undoButton.setY(Math.max(0, screenH - h - pad));
            }
        }

        if (chestsort$searchField != null) {
            chestsort$searchField.visible = isTarget && chestsort$filterMode && hasRightPanel;
            chestsort$searchField.setEditable(isTarget && chestsort$filterMode && hasRightPanel);
        }

        boolean hasLeftPanel = chestsort$leftPanelW >= 20;
        boolean showTabs = isTarget && chestsort$filterMode && hasLeftPanel;
        if (chestsort$whitelistTabButton != null) {
            chestsort$whitelistTabButton.visible = showTabs;
            chestsort$whitelistTabButton.active = showTabs && (chestsort$filterTab != CHESTSORT_TAB_WHITELIST);
        }
        if (chestsort$blacklistTabButton != null) {
            chestsort$blacklistTabButton.visible = showTabs;
            chestsort$blacklistTabButton.active = showTabs && (chestsort$filterTab != CHESTSORT_TAB_BLACKLIST);
        }

        boolean showEditPreset = isTarget && chestsort$filterMode && hasRightPanel && chestsort$editingPresetName != null && !chestsort$editingPresetName.isEmpty();
        if (chestsort$editPresetButton != null) {
            chestsort$editPresetButton.visible = showEditPreset;
            chestsort$editPresetButton.setMessage(Text.literal("Edit preset: " + (chestsort$editPresetMode ? "ON" : "OFF")));
        }

        boolean showImportPopup = isTarget && chestsort$importPopupOpen && !chestsort$filterMode && hasRightPanel;
        if (chestsort$importField != null) {
            chestsort$importField.visible = showImportPopup;
            chestsort$importField.setEditable(showImportPopup);
        }
        if (chestsort$importConfirmButton != null) chestsort$importConfirmButton.visible = showImportPopup;
        if (chestsort$importCancelButton != null) chestsort$importCancelButton.visible = showImportPopup;

        boolean showExportPopup = isTarget && chestsort$exportPopupOpen && !chestsort$filterMode && hasRightPanel;
        if (chestsort$exportField != null) {
            chestsort$exportField.visible = showExportPopup;
            chestsort$exportField.setEditable(false);
        }
        if (chestsort$exportCloseButton != null) chestsort$exportCloseButton.visible = showExportPopup;

        boolean showPresetImportPopup = isTarget && chestsort$presetImportPopupOpen && !chestsort$filterMode && hasRightPanel;
        if (chestsort$presetImportField != null) {
            chestsort$presetImportField.visible = showPresetImportPopup;
            chestsort$presetImportField.setEditable(showPresetImportPopup);
        }
        if (chestsort$presetImportConfirmButton != null) chestsort$presetImportConfirmButton.visible = showPresetImportPopup;
        if (chestsort$presetImportCancelButton != null) chestsort$presetImportCancelButton.visible = showPresetImportPopup;

        boolean showPresetExportPopup = isTarget && chestsort$presetExportPopupOpen && !chestsort$filterMode && hasRightPanel;
        if (chestsort$presetExportField != null) {
            chestsort$presetExportField.visible = showPresetExportPopup;
            chestsort$presetExportField.setEditable(false);
        }
        if (chestsort$presetExportCloseButton != null) chestsort$presetExportCloseButton.visible = showPresetExportPopup;
    }

    @Unique
    private boolean chestsort$isTargetContainer() {
        // In multiplayer, the server-sent ContainerContext packet can arrive after the screen is already open.
        // If we gate the GUI purely on that packet, the UI can appear to be "missing" on servers.
        // Treat vanilla container screens as eligible immediately, and rely on dimensionId/posLong checks
        // to gate actions that truly require server context.
        return ClientContainerContext.isChestOrBarrel()
            || (this.handler instanceof net.minecraft.screen.GenericContainerScreenHandler);
    }

    @Unique
    private void chestsort$closePresetImportPopup() {
        chestsort$presetImportPopupOpen = false;
        chestsort$presetImportError = "";
        if (chestsort$presetImportField != null) {
            chestsort$presetImportField.setFocused(false);
        }
    }

    @Unique
    private void chestsort$closePresetExportPopup() {
        chestsort$presetExportPopupOpen = false;
        chestsort$presetExportError = "";
        chestsort$presetExportName = "";
        if (chestsort$presetExportField != null) {
            chestsort$presetExportField.setFocused(false);
        }
    }

    @Unique
    private void chestsort$tryImportPresetFromPopup() {
        if (!chestsort$isTargetContainer()) return;

        String raw = chestsort$presetImportField == null ? "" : chestsort$presetImportField.getText();
        if (raw == null || raw.trim().isEmpty()) {
            chestsort$presetImportError = "empty";
            return;
        }

        boolean sent = ClientNetworkingUtil.sendSafe(new ImportPresetPayload(raw));
        if (!sent) {
            // Local import fallback: accept both presetList and single preset.
            try {
                var list = Cs2StringCodec.decodePresetList(raw);
                if (list != null) {
                    if (list.whitelists() != null) {
                        for (var e : list.whitelists().entrySet()) {
                            if (e == null) continue;
                            ClientPresetRegistry.putLocal(e.getKey(), e.getValue());
                        }
                    }
                    if (list.blacklists() != null) {
                        for (var e : list.blacklists().entrySet()) {
                            if (e == null) continue;
                            ClientPresetRegistry.putLocalBlacklist(e.getKey(), e.getValue());
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {
                try {
                    var decoded = Cs2StringCodec.decodePresetImport(raw);
                    ClientPresetRegistry.putLocal(decoded.name(), decoded.whitelist());
                    ClientPresetRegistry.putLocalBlacklist(decoded.name(), decoded.blacklist());
                } catch (IllegalArgumentException e) {
                    chestsort$presetImportError = e.getMessage();
                    return;
                }
            }
        }
        chestsort$closePresetImportPopup();
    }

    @Unique
    private void chestsort$openPresetExportPopup(String presetName) {
        if (!chestsort$isTargetContainer()) return;

        String name = presetName == null ? "" : presetName.trim();
        chestsort$presetExportName = name;
        chestsort$presetExportPopupOpen = true;
        chestsort$presetImportPopupOpen = false;
        chestsort$importPopupOpen = false;
        chestsort$exportPopupOpen = false;
        chestsort$presetExportError = "";

        ContainerFilterSpec wl = ClientPresetRegistry.get(name);
        ContainerFilterSpec bl = ClientPresetRegistry.getBlacklist(name);
        if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) {
            chestsort$presetExportError = "No preset / empty";
            if (chestsort$presetExportField != null) chestsort$presetExportField.setText("");
            return;
        }

        ContainerFilterSpec safeWl = wl == null ? new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of()) : wl;
        ContainerFilterSpec safeBl = bl == null ? new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of()) : bl;
        String encoded = Cs2StringCodec.encodePreset(name, safeWl, safeBl);
        if (chestsort$presetExportField != null) {
            chestsort$presetExportField.setText(encoded);
            chestsort$presetExportField.setFocused(true);
        }
    }

    @Unique
    private void chestsort$openPresetImportPopup() {
        if (!chestsort$isTargetContainer()) return;
        chestsort$presetImportPopupOpen = true;
        chestsort$presetExportPopupOpen = false;
        chestsort$importPopupOpen = false;
        chestsort$exportPopupOpen = false;
        chestsort$presetImportError = "";
        if (chestsort$presetImportField != null) {
            chestsort$presetImportField.setText("");
            chestsort$presetImportField.setFocused(true);
        }
    }

    @Unique
    private void chestsort$openPresetEditor(String presetName) {
        if (!chestsort$isTargetContainer()) return;
        String name = presetName == null ? "" : presetName.trim();
        if (name.isEmpty()) return;

        ContainerFilterSpec wl = ClientPresetRegistry.get(name);
        ContainerFilterSpec bl = ClientPresetRegistry.getBlacklist(name);

        ContainerFilterSpec safeWl = (wl == null)
            ? new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of())
            : wl.normalized();
        ContainerFilterSpec safeBl = (bl == null)
            ? new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of())
            : bl.normalized();

        chestsort$editingWhitelistItems = new java.util.ArrayList<>(safeWl.items());
        chestsort$editingWhitelistTags = new java.util.ArrayList<>(safeWl.tags());
        chestsort$editingWhitelistPresets = new java.util.ArrayList<>();

        chestsort$editingBlacklistItems = new java.util.ArrayList<>(safeBl.items());
        chestsort$editingBlacklistTags = new java.util.ArrayList<>(safeBl.tags());
        chestsort$editingBlacklistPresets = new java.util.ArrayList<>();

        chestsort$filterTab = CHESTSORT_TAB_WHITELIST;
        chestsort$applyActiveTabToEditingLists();
        chestsort$editingPresetName = name;
        chestsort$editPresetMode = false;
        chestsort$filterDirty = false;

        chestsort$tagBrowserMode = false;
        chestsort$tagBrowserTagId = "";
        chestsort$tagBrowserItems = java.util.List.of();

        if (chestsort$searchField != null) {
            chestsort$searchField.setText("");
            chestsort$searchField.setFocused(true);
            chestsort$updateSearchResults();
        }
    }

    @Unique
    private void chestsort$toggleAppliedPresetForContainer(String presetName) {
        if (!chestsort$isTargetContainer()) return;
        String name = presetName == null ? "" : presetName.trim();
        if (name.isEmpty()) return;

        ContainerFilterSpec current = ClientContainerContext.filterSpec();
        ContainerFilterSpec safe = (current == null)
            ? new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of())
            : current.normalized();

        java.util.ArrayList<String> presets = new java.util.ArrayList<>(safe.presets() == null ? java.util.List.of() : safe.presets());
        boolean removed = presets.removeIf(p -> p != null && p.trim().equals(name));
        if (!removed) {
            presets.add(name);
        }

        ContainerFilterSpec updated = new ContainerFilterSpec(safe.items(), safe.tags(), presets).normalized();

        String dimId = ClientContainerContext.dimensionId();
        if (dimId != null && !dimId.isEmpty()) {
            ClientNetworkingUtil.sendSafe(new SetFilterV2Payload(dimId, ClientContainerContext.posLong(), updated));
            ClientNetworkingUtil.sendSafe(new SetFilterPayload(dimId, ClientContainerContext.posLong(), updated.items()));
        }
        ClientContainerContext.set(dimId, ClientContainerContext.posLong(), ClientContainerContext.containerType(), updated);
    }

    @Unique
    private void chestsort$closeImportPopup() {
        chestsort$importPopupOpen = false;
        chestsort$importError = "";
        if (chestsort$importField != null) {
            chestsort$importField.setFocused(false);
        }
    }

    @Unique
    private void chestsort$closeExportPopup() {
        chestsort$exportPopupOpen = false;
        chestsort$exportError = "";
        if (chestsort$exportField != null) {
            chestsort$exportField.setFocused(false);
        }
    }

    @Unique
    private void chestsort$tryImportFromPopup() {
        if (!chestsort$isTargetContainer()) return;

        String raw = chestsort$importField == null ? "" : chestsort$importField.getText();
        ContainerFilterSpec spec;
        try {
            var decoded = Cs2StringCodec.decodeFilterImport(raw);
            if (decoded != null && decoded.embeddedPresets() != null && !decoded.embeddedPresets().isEmpty()) {
                for (var e : decoded.embeddedPresets().entrySet()) {
                    if (e == null) continue;
                    String name = e.getKey();
                    ContainerFilterSpec pSpec = e.getValue();
                    if (name == null || name.trim().isEmpty()) continue;
                    if (pSpec == null) continue;
                    // Prefer server if supported; otherwise store locally.
                    if (!ClientNetworkingUtil.sendSafe(new SetPresetPayload(name, pSpec))) {
                        ClientPresetRegistry.putLocal(name, pSpec);
                    }
                }
            }
            spec = decoded == null ? null : decoded.filter();
        } catch (IllegalArgumentException e) {
            chestsort$importError = e.getMessage();
            return;
        }

        spec = (spec == null) ? new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of()) : spec.normalized();

        String dimId = ClientContainerContext.dimensionId();
        if (dimId != null && !dimId.isEmpty()) {
            ClientNetworkingUtil.sendSafe(new SetFilterV2Payload(dimId, ClientContainerContext.posLong(), spec));
            ClientNetworkingUtil.sendSafe(new SetFilterPayload(dimId, ClientContainerContext.posLong(), spec.items()));
        }
        ClientContainerContext.set(dimId, ClientContainerContext.posLong(), ClientContainerContext.containerType(), spec);

        chestsort$closeImportPopup();
    }

    @Unique
    private static String chestsort$encodeFilterSpec(ContainerFilterSpec spec) {
        ContainerFilterSpec normalized = (spec == null)
            ? new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of())
            : spec.normalized();

        java.util.LinkedHashMap<String, ContainerFilterSpec> inline = new java.util.LinkedHashMap<>();
        if (normalized.presets() != null) {
            for (String name : normalized.presets()) {
                if (name == null || name.trim().isEmpty()) continue;
                ContainerFilterSpec p = ClientPresetRegistry.get(name);
                if (p != null && !p.isEmpty()) {
                    inline.put(name, p);
                }
            }
        }

        return Cs2StringCodec.encodeFilter(normalized, inline);
    }

    @Unique
    private static ContainerFilterSpec chestsort$decodeFilterSpec(String raw) {
        return Cs2StringCodec.decodeSpec(raw);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unused")
    private void chestsort$mouseClicked(net.minecraft.client.gui.Click click, boolean bl, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!chestsort$isTargetContainer()) {
            return;
        }

        int button = click.button();
        double mouseX = click.x();
        double mouseY = click.y();

        boolean showMainButtons = !chestsort$filterMode && !chestsort$importPopupOpen && !chestsort$exportPopupOpen && !chestsort$presetImportPopupOpen && !chestsort$presetExportPopupOpen;
        if (showMainButtons && chestsort$lockSlotsMode && button == 0) {
            Integer idx = chestsort$hoveredPlayerInventoryIndex((int) mouseX, (int) mouseY);
            if (idx != null) {
                ClientLockedSlotsState.toggleLocal(idx);
                ClientNetworkingUtil.sendSafe(new ToggleLockedSlotPayload(idx));
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
        }

        if (!chestsort$filterMode) {
            return;
        }

        if (button != 0) {
            return;
        }

        chestsort$updateLayout();
        int rightW = chestsort$rightPanelW;
        int rightX = chestsort$rightPanelX;
        int rightY = chestsort$rightPanelY;

        int leftW = chestsort$leftPanelW;
        int leftX = chestsort$leftPanelX;
        int leftY = chestsort$leftPanelY;
        int leftHeaderH = CHESTSORT_HEADER_H;
        int leftRowsY = leftY + leftHeaderH;

        // Left: filter items + tags sections
        int rowH = CHESTSORT_ROW_H;
        if (leftW >= 20) {
            java.util.List<Integer> rows = chestsort$getLeftRows();
            int shown = Math.min(chestsort$leftRowsShown, Math.max(0, rows.size() - chestsort$leftScroll));
            for (int i = 0; i < shown; i++) {
                int rx1 = leftX;
                int ry1 = leftRowsY + i * rowH;
                int rx2 = leftX + leftW;
                int ry2 = ry1 + rowH;
                if (mouseX >= rx1 && mouseX < rx2 && mouseY >= ry1 && mouseY < ry2) {
                    int rowIndex = chestsort$leftScroll + i;
                    int encoded = rows.get(rowIndex);
                    int kind = chestsort$leftRowKind(encoded);
                    int idx = chestsort$leftRowIndex(encoded);

                    int actionX1 = (leftX + leftW) - CHESTSORT_ACTION_PAD - CHESTSORT_ACTION_W;
                    int actionX2 = (leftX + leftW) - CHESTSORT_ACTION_PAD;

                    if (kind == CHESTSORT_LEFT_KIND_ITEMS_HEADER) {
                        chestsort$itemsExpanded = !chestsort$itemsExpanded;
                        chestsort$selectedLeftRowIndex = rowIndex;
                        chestsort$clampScroll();
                    } else if (kind == CHESTSORT_LEFT_KIND_AUTOSORT) {
                        // Toggle autosort for this container.
                        chestsort$editingAutosort = !chestsort$editingAutosort;
                        chestsort$filterDirty = true;
                        chestsort$selectedLeftRowIndex = rowIndex;
                        chestsort$clampScroll();
                    } else if (kind == CHESTSORT_LEFT_KIND_PRIORITY) {
                        // Toggle conflict priority when an item matches both lists.
                        chestsort$editingWhitelistPriority = !chestsort$editingWhitelistPriority;
                        chestsort$filterDirty = true;
                        chestsort$selectedLeftRowIndex = rowIndex;
                        chestsort$clampScroll();
                    } else if (kind == CHESTSORT_LEFT_KIND_TAGS_HEADER) {
                        chestsort$tagsExpanded = !chestsort$tagsExpanded;
                        chestsort$selectedLeftRowIndex = rowIndex;
                        chestsort$clampScroll();
                    } else if (kind == CHESTSORT_LEFT_KIND_PRESETS_HEADER) {
                        chestsort$presetsExpanded = !chestsort$presetsExpanded;
                        chestsort$selectedLeftRowIndex = rowIndex;
                        chestsort$clampScroll();
                    } else if (kind == CHESTSORT_LEFT_KIND_ITEM) {
                        // Click on red 'x' removes that item filter entry.
                        if (mouseX >= actionX1 && mouseX < actionX2) {
                            if (idx >= 0 && idx < chestsort$editingFilterItems.size()) {
                                if (!(chestsort$editingFilterItems instanceof java.util.ArrayList)) {
                                    chestsort$editingFilterItems = new java.util.ArrayList<>(chestsort$editingFilterItems);
                                }
                                chestsort$editingFilterItems.remove(idx);
                                chestsort$filterDirty = true;
                                chestsort$selectedLeftRowIndex = rowIndex;
                                chestsort$clampScroll();
                            }
                        } else {
                            chestsort$selectedLeftRowIndex = rowIndex;
                            chestsort$clampScroll();
                        }
                    } else if (kind == CHESTSORT_LEFT_KIND_TAG) {
                        if (idx >= 0 && idx < chestsort$editingFilterTags.size()) {
                            TagFilterSpec tag = chestsort$editingFilterTags.get(idx);
                            if (tag == null) {
                                cir.setReturnValue(true);
                                cir.cancel();
                                return;
                            }
                            // Click on red 'x' removes the tag filter entry.
                            if (mouseX >= actionX1 && mouseX < actionX2) {
                                if (!(chestsort$editingFilterTags instanceof java.util.ArrayList)) {
                                    chestsort$editingFilterTags = new java.util.ArrayList<>(chestsort$editingFilterTags);
                                }
                                chestsort$editingFilterTags.remove(idx);
                                chestsort$filterDirty = true;
                                if (chestsort$tagBrowserMode && tag.tagId() != null && tag.tagId().equals(chestsort$tagBrowserTagId)) {
                                    chestsort$tagBrowserMode = false;
                                    chestsort$tagBrowserTagId = "";
                                    chestsort$tagBrowserItems = java.util.List.of();
                                    if (chestsort$searchField != null) chestsort$updateSearchResults();
                                }
                                chestsort$selectedLeftRowIndex = rowIndex;
                                chestsort$clampScroll();
                            } else {
                                // Open tag exception browser.
                                chestsort$tagBrowserMode = true;
                                chestsort$tagBrowserTagId = ContainerFilterSpec.normalizeTagId(tag.tagId());
                                chestsort$rebuildTagBrowserItems();
                                if (chestsort$searchField != null) {
                                    chestsort$searchField.setText("");
                                    chestsort$updateSearchResults();
                                }
                                chestsort$selectedLeftRowIndex = rowIndex;
                                chestsort$selectedResultIndex = -1;
                                chestsort$resultsScroll = 0;
                                chestsort$clampScroll();
                            }
                        }
                    } else if (kind == CHESTSORT_LEFT_KIND_PRESET) {
                        if (idx >= 0 && idx < chestsort$editingFilterPresets.size()) {
                            String presetName = chestsort$editingFilterPresets.get(idx);
                            if (presetName == null) presetName = "";
                            presetName = presetName.trim();
                            if (presetName.isEmpty()) {
                                cir.setReturnValue(true);
                                cir.cancel();
                                return;
                            }

                            // Click on red 'x' removes that applied preset.
                            if (mouseX >= actionX1 && mouseX < actionX2) {
                                if (!(chestsort$editingFilterPresets instanceof java.util.ArrayList)) {
                                    chestsort$editingFilterPresets = new java.util.ArrayList<>(chestsort$editingFilterPresets);
                                }
                                chestsort$editingFilterPresets.remove(idx);
                                chestsort$filterDirty = true;

                                // If we were editing this preset, exit the editor back to the container filter context.
                                if (chestsort$editingPresetName != null && chestsort$editingPresetName.equals(presetName)) {
                                    chestsort$editingPresetName = "";
                                    chestsort$editPresetMode = false;
                                    var spec = ClientContainerContext.filterSpec();
                                    chestsort$editingFilterItems = new java.util.ArrayList<>(spec == null ? java.util.List.of() : spec.items());
                                    chestsort$editingFilterTags = new java.util.ArrayList<>(spec == null ? java.util.List.of() : spec.tags());
                                    chestsort$editingFilterPresets = new java.util.ArrayList<>(spec == null ? java.util.List.of() : (spec.presets() == null ? java.util.List.of() : spec.presets()));
                                    chestsort$editingAutosort = spec != null && spec.autosort();
                                    chestsort$filterDirty = false;
                                    if (chestsort$searchField != null) {
                                        chestsort$searchField.setText("");
                                        chestsort$updateSearchResults();
                                    }
                                }

                                chestsort$selectedLeftRowIndex = rowIndex;
                                chestsort$selectedResultIndex = -1;
                                chestsort$resultsScroll = 0;
                                chestsort$clampScroll();
                            } else {
                                // Open local edit view for this preset.
                                chestsort$openPresetEditor(presetName);
                                chestsort$selectedLeftRowIndex = rowIndex;
                                chestsort$selectedResultIndex = -1;
                                chestsort$resultsScroll = 0;
                                chestsort$clampScroll();
                            }
                        }
                    }

                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }
        }

        // Right: results rows (items or tags; in tag browser mode, item rows add exceptions)
        if (rightW >= 20) {
            int resultsTop = chestsort$resultsRowsY;
            int resultsRows = Math.min(chestsort$resultsRowsShown, Math.max(0, chestsort$getSearchResultsSize() - chestsort$resultsScroll));
            for (int i = 0; i < resultsRows; i++) {
                int rx1 = rightX;
                int ry1 = resultsTop + i * rowH;
                int rx2 = rightX + rightW;
                int ry2 = ry1 + rowH;
                if (mouseX >= rx1 && mouseX < rx2 && mouseY >= ry1 && mouseY < ry2) {
                    int idx = chestsort$resultsScroll + i;
                    int actionX1 = (rightX + rightW) - CHESTSORT_ACTION_PAD - CHESTSORT_ACTION_W;
                    int actionX2 = (rightX + rightW) - CHESTSORT_ACTION_PAD;

                    // Click on action adds an item filter / tag filter / tag exception.
                    if (mouseX >= actionX1 && mouseX < actionX2) {
                        if (idx >= 0 && idx < chestsort$getSearchResultsSize()) {
                            Item entryItem = chestsort$searchResultItems.get(idx);
                            String entryTagId = idx < chestsort$searchResultTagIds.size() ? chestsort$searchResultTagIds.get(idx) : "";
                            String entryPreset = idx < chestsort$searchResultPresetNames.size() ? chestsort$searchResultPresetNames.get(idx) : "";

                            boolean isPreset = entryItem == null && (entryPreset != null && !entryPreset.isEmpty());
                            if (isPreset) {
                                String presetName = entryPreset.trim();
                                if (!presetName.isEmpty()) {
                                    if (!(chestsort$editingFilterPresets instanceof java.util.ArrayList)) {
                                        chestsort$editingFilterPresets = new java.util.ArrayList<>(chestsort$editingFilterPresets);
                                    }
                                    int existing = chestsort$editingFilterPresets.indexOf(presetName);
                                    if (existing >= 0) {
                                        chestsort$editingFilterPresets.remove(existing);
                                    }
                                    chestsort$editingFilterPresets.add(0, presetName);
                                    chestsort$filterDirty = true;
                                    if (chestsort$searchField != null) {
                                        chestsort$updateSearchResults();
                                    }
                                }
                                chestsort$selectedResultIndex = idx;
                                chestsort$clampScroll();
                                cir.setReturnValue(true);
                                cir.cancel();
                                return;
                            }

                            boolean isTag = entryItem == null && entryTagId != null && !entryTagId.isEmpty();

                            if (isTag) {
                                // Add tag filter (or switch tag browser tag, if we're in tag browser mode).
                                String tagId = ContainerFilterSpec.normalizeTagId(entryTagId);
                                if (tagId.isEmpty()) {
                                    // noop
                                } else if (chestsort$tagBrowserMode) {
                                    chestsort$tagBrowserTagId = tagId;
                                    chestsort$rebuildTagBrowserItems();
                                    if (chestsort$searchField != null) {
                                        chestsort$searchField.setText("");
                                        chestsort$updateSearchResults();
                                    }
                                } else {
                                    int existing = chestsort$indexOfFilterTag(tagId);
                                    if (existing >= 0) {
                                        chestsort$moveFilterTagToTop(existing);
                                        chestsort$filterDirty = true;
                                        chestsort$clampScroll();
                                    } else {
                                        if (!(chestsort$editingFilterTags instanceof java.util.ArrayList)) {
                                            chestsort$editingFilterTags = new java.util.ArrayList<>(chestsort$editingFilterTags);
                                        }
                                        chestsort$editingFilterTags.add(0, new TagFilterSpec(tagId, java.util.List.of()));
                                        chestsort$filterDirty = true;
                                        chestsort$clampScroll();
                                    }
                                }
                            } else {
                                if (entryItem != null) {
                                    String itemId = String.valueOf(Registries.ITEM.getId(entryItem));
                                    if (chestsort$tagBrowserMode) {
                                        chestsort$addTagException(itemId);
                                    } else {
                                        if (!itemId.isEmpty()) {
                                            int existing = chestsort$editingFilterItems == null ? -1 : chestsort$editingFilterItems.indexOf(itemId);
                                            if (existing >= 0) {
                                                chestsort$moveFilterItemToTop(existing);
                                                chestsort$filterDirty = true;
                                                chestsort$clampScroll();
                                            } else {
                                                if (!(chestsort$editingFilterItems instanceof java.util.ArrayList)) {
                                                    chestsort$editingFilterItems = new java.util.ArrayList<>(chestsort$editingFilterItems);
                                                }
                                                chestsort$editingFilterItems.add(0, itemId);
                                                chestsort$filterDirty = true;
                                                chestsort$clampScroll();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        String entryPreset = idx < chestsort$searchResultPresetNames.size() ? chestsort$searchResultPresetNames.get(idx) : "";
                        // For preset autocomplete rows, only the action button adds.
                        chestsort$selectedResultIndex = idx;
                        chestsort$clampScroll();
                    }

                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unused")
    private void chestsort$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!chestsort$isTargetContainer() || !chestsort$filterMode) {
            return;
        }
        if (verticalAmount == 0.0) {
            return;
        }

        chestsort$updateLayout();

        int delta = verticalAmount > 0 ? -1 : 1;

        boolean handled = false;

        // Scroll left list if hovering its panel.
        if (chestsort$leftPanelW >= 20) {
            int lx1 = chestsort$leftPanelX;
            int ly1 = chestsort$leftPanelY;
            int lx2 = lx1 + chestsort$leftPanelW;
            int leftHeaderH = CHESTSORT_HEADER_H;
            int ly2 = ly1 + leftHeaderH + (chestsort$leftRowsShown * CHESTSORT_ROW_H) + CHESTSORT_PAD_BOTTOM;
            if (mouseX >= lx1 && mouseX < lx2 && mouseY >= ly1 && mouseY < ly2) {
                chestsort$leftScroll += delta;
                handled = true;
            }
        }

        // Scroll results list if hovering its panel.
        if (!handled && chestsort$rightPanelW >= 20) {
            int rx1 = chestsort$rightPanelX;
            int ry1 = chestsort$resultsPanelY;
            int rx2 = rx1 + chestsort$rightPanelW;
            int ry2 = ry1 + CHESTSORT_HEADER_H + (chestsort$resultsRowsShown * CHESTSORT_ROW_H) + CHESTSORT_PAD_BOTTOM;
            if (mouseX >= rx1 && mouseX < rx2 && mouseY >= ry1 && mouseY < ry2) {
                chestsort$resultsScroll += delta;
                handled = true;
            }
        }

        if (handled) {
            chestsort$clampScroll();
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unused")
    private void chestsort$keyPressed(net.minecraft.client.input.KeyInput keyInput, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!chestsort$isTargetContainer()) {
            return;
        }

        if (chestsort$importPopupOpen && !chestsort$filterMode) {
            MinecraftClient client = MinecraftClient.getInstance();

            boolean importFocused = chestsort$importField != null && chestsort$importField.visible && chestsort$importField.isFocused();
            if (importFocused) {
                if (chestsort$importField.keyPressed(keyInput)) {
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }

                if (client != null && client.options != null && client.options.inventoryKey != null && client.options.inventoryKey.matchesKey(keyInput)) {
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }

            if (client != null && client.options != null && client.options.backKey != null && client.options.backKey.matchesKey(keyInput)) {
                chestsort$closeImportPopup();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }

            // Enter to import (even if field not focused).
            if (keyInput.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyInput.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                chestsort$tryImportFromPopup();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }

            return;
        }

        if (chestsort$exportPopupOpen && !chestsort$filterMode) {
            MinecraftClient client = MinecraftClient.getInstance();

            boolean exportFocused = chestsort$exportField != null && chestsort$exportField.visible && chestsort$exportField.isFocused();
            if (exportFocused) {
                if (chestsort$exportField.keyPressed(keyInput)) {
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }

                if (client != null && client.options != null && client.options.inventoryKey != null && client.options.inventoryKey.matchesKey(keyInput)) {
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }

            if (client != null && client.options != null && client.options.backKey != null && client.options.backKey.matchesKey(keyInput)) {
                chestsort$closeExportPopup();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }

            return;
        }

        if (chestsort$presetImportPopupOpen && !chestsort$filterMode) {
            MinecraftClient client = MinecraftClient.getInstance();

            boolean focused = chestsort$presetImportField != null && chestsort$presetImportField.visible && chestsort$presetImportField.isFocused();
            if (focused) {
                if (chestsort$presetImportField.keyPressed(keyInput)) {
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
                if (client != null && client.options != null && client.options.inventoryKey != null && client.options.inventoryKey.matchesKey(keyInput)) {
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }

            if (client != null && client.options != null && client.options.backKey != null && client.options.backKey.matchesKey(keyInput)) {
                chestsort$closePresetImportPopup();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }

            if (keyInput.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyInput.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                chestsort$tryImportPresetFromPopup();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }

            return;
        }

        if (chestsort$presetExportPopupOpen && !chestsort$filterMode) {
            MinecraftClient client = MinecraftClient.getInstance();

            boolean focused = chestsort$presetExportField != null && chestsort$presetExportField.visible && chestsort$presetExportField.isFocused();
            if (focused) {
                if (chestsort$presetExportField.keyPressed(keyInput)) {
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
                if (client != null && client.options != null && client.options.inventoryKey != null && client.options.inventoryKey.matchesKey(keyInput)) {
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }

            if (client != null && client.options != null && client.options.backKey != null && client.options.backKey.matchesKey(keyInput)) {
                chestsort$closePresetExportPopup();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }

            return;
        }

        if (!chestsort$filterMode) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        boolean searchFocused = chestsort$searchField != null && chestsort$searchField.visible && chestsort$searchField.isFocused();

        // When typing in the search box, do not let close keybinds fire.
        // (This prevents cases like rebinding "back" to a letter from closing the UI while typing.)
        if (searchFocused) {
            // Let the search field handle special keys (backspace, arrows, etc.) first.
            if (chestsort$searchField.keyPressed(keyInput)) {
                cir.setReturnValue(true);
                cir.cancel();
            }

            // Prevent the inventory keybind from closing the screen while typing.
            if (client != null && client.options != null && client.options.inventoryKey != null && client.options.inventoryKey.matchesKey(keyInput)) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        } else {
            // ESC: in tag browser mode, go back to the filter editor; otherwise save and exit filter UI.
            if (client != null && client.options != null && client.options.backKey != null && client.options.backKey.matchesKey(keyInput)) {
                if (chestsort$tagBrowserMode) {
                    chestsort$tagBrowserMode = false;
                    chestsort$tagBrowserTagId = "";
                    chestsort$tagBrowserItems = java.util.List.of();
                    if (chestsort$searchField != null) {
                        chestsort$searchField.setText("");
                        chestsort$updateSearchResults();
                    }
                } else if (chestsort$editingPresetName != null && !chestsort$editingPresetName.isEmpty()) {
                    if (chestsort$editPresetMode) {
                        chestsort$saveEditingFilter();
                        // Restore container filter context so we don't accidentally apply preset edits locally.
                        var wl = ClientContainerContext.filterSpec();
                        var bl = ClientContainerContext.blacklistSpec();

                        chestsort$editingWhitelistItems = new java.util.ArrayList<>(wl == null ? java.util.List.of() : wl.items());
                        chestsort$editingWhitelistTags = new java.util.ArrayList<>(wl == null ? java.util.List.of() : wl.tags());
                        chestsort$editingWhitelistPresets = new java.util.ArrayList<>(wl == null ? java.util.List.of() : (wl.presets() == null ? java.util.List.of() : wl.presets()));

                        chestsort$editingBlacklistItems = new java.util.ArrayList<>(bl == null ? java.util.List.of() : bl.items());
                        chestsort$editingBlacklistTags = new java.util.ArrayList<>(bl == null ? java.util.List.of() : bl.tags());
                        chestsort$editingBlacklistPresets = new java.util.ArrayList<>(bl == null ? java.util.List.of() : (bl.presets() == null ? java.util.List.of() : bl.presets()));

                        chestsort$editingWhitelistPriority = ClientContainerContext.whitelistPriority();
                        chestsort$editingAutosort = wl != null && wl.autosort();
                        chestsort$filterTab = CHESTSORT_TAB_WHITELIST;
                        chestsort$applyActiveTabToEditingLists();
                        chestsort$filterDirty = false;
                    } else {
                        // Local preset edit: save to container filter.
                        chestsort$saveEditingFilter();
                    }
                    chestsort$editingPresetName = "";
                    chestsort$editPresetMode = false;
                    if (chestsort$searchField != null) {
                        chestsort$searchField.setText("");
                        chestsort$updateSearchResults();
                    }
                } else {
                    chestsort$saveEditingFilter();
                    chestsort$filterMode = false;
                }
                cir.setReturnValue(true);
                cir.cancel();
            } else if (client != null && client.options != null && client.options.inventoryKey != null && client.options.inventoryKey.matchesKey(keyInput)) {
                // Save when the user closes with the inventory key (usually E).
                // (If in tag browser, still close the whole filter UI; spec only mentions ESC to go back.)
                chestsort$saveEditingFilter();
                chestsort$filterMode = false;
                chestsort$tagBrowserMode = false;
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    @SuppressWarnings("unused")
    private void chestsort$removed(CallbackInfo ci) {
        // Safety net: if the screen closes while editing, persist changes.
        if (chestsort$filterMode) {
            chestsort$saveEditingFilter();
        }
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unused")
    private void chestsort$close(CallbackInfo ci) {
        // When filter UI is open, ESC/E should close the filter UI (and save), not the inventory screen.
        if (chestsort$importPopupOpen && !chestsort$filterMode) {
            chestsort$closeImportPopup();
            ci.cancel();
            return;
        }

        if (chestsort$exportPopupOpen && !chestsort$filterMode) {
            chestsort$closeExportPopup();
            ci.cancel();
            return;
        }

        if (chestsort$presetImportPopupOpen && !chestsort$filterMode) {
            chestsort$closePresetImportPopup();
            ci.cancel();
            return;
        }

        if (chestsort$presetExportPopupOpen && !chestsort$filterMode) {
            chestsort$closePresetExportPopup();
            ci.cancel();
            return;
        }

        if (chestsort$filterMode) {
            if (chestsort$tagBrowserMode) {
                // First go back to the filter editor.
                chestsort$tagBrowserMode = false;
                chestsort$tagBrowserTagId = "";
                chestsort$tagBrowserItems = java.util.List.of();
                if (chestsort$searchField != null) {
                    chestsort$searchField.setText("");
                    chestsort$updateSearchResults();
                }
            } else {
                chestsort$saveEditingFilter();
                chestsort$filterMode = false;
            }
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    @SuppressWarnings("unused")
    private void chestsort$renderPanels(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        chestsort$applyVisibility();

        if (chestsort$isTargetContainer()) {
            if (ClientPresetRegistry.hasPendingOpen()) {
                byte mode = ClientPresetRegistry.pendingOpenMode();
                String name = ClientPresetRegistry.pendingOpenName();

                // Only consume the request once we're in a chest/barrel handled screen.
                ClientPresetRegistry.clearPendingOpen();

                if (mode == OpenPresetUiPayload.MODE_EDIT) {
                    // Ensure filter UI is open.
                    if (!chestsort$filterMode) {
                        var spec = ClientContainerContext.filterSpec();
                        chestsort$editingFilterItems = new java.util.ArrayList<>(spec == null ? java.util.List.of() : spec.items());
                        chestsort$editingFilterTags = new java.util.ArrayList<>(spec == null ? java.util.List.of() : spec.tags());
                        chestsort$editingAutosort = spec != null && spec.autosort();
                        chestsort$filterMode = true;
                        chestsort$filterDirty = false;
                        if (chestsort$searchField != null) {
                            chestsort$searchField.setText("");
                            chestsort$searchField.setFocused(true);
                            chestsort$updateSearchResults();
                        }
                    }
                    chestsort$openPresetEditor(name);
                } else if (mode == OpenPresetUiPayload.MODE_IMPORT) {
                    chestsort$filterMode = false;
                    chestsort$openPresetImportPopup();
                } else if (mode == OpenPresetUiPayload.MODE_EXPORT) {
                    chestsort$filterMode = false;
                    chestsort$openPresetExportPopup(name);
                }
            }

            // Left-side notification bar (sort/autosort/undo results)
            if (!chestsort$filterMode && ClientSortNotificationState.isActiveForCurrentContainer()) {
                chestsort$renderSortNotification(context, mouseX, mouseY);
            }

            if (chestsort$importPopupOpen && !chestsort$filterMode) {
                chestsort$updateLayout();

                TextRenderer tr = MinecraftClient.getInstance().textRenderer;
                if (tr != null && chestsort$rightPanelW >= 20) {
                    int rx = chestsort$rightPanelX;
                    int ry = chestsort$rightPanelY;
                    int rw = chestsort$rightPanelW;
                    int boxH = 70;

                    context.fill(rx, ry, rx + rw, ry + boxH, 0xAA000000);
                    context.drawTextWithShadow(tr, Text.literal("Import rule"), rx + 8, ry + 6, 0xFFFFFFFF);

                    if (chestsort$importError != null && !chestsort$importError.isEmpty()) {
                        context.drawTextWithShadow(tr, Text.literal(chestsort$importError).formatted(Formatting.RED), rx + 8, ry + 58, 0xFFFFFFFF);
                    } else {
                        context.drawTextWithShadow(tr, Text.literal("Paste a cs2| string"), rx + 8, ry + 58, 0xFFAAAAAA);
                    }
                }
                return;
            }

            if (chestsort$presetImportPopupOpen && !chestsort$filterMode) {
                chestsort$updateLayout();

                TextRenderer tr = MinecraftClient.getInstance().textRenderer;
                if (tr != null && chestsort$rightPanelW >= 20) {
                    int rx = chestsort$rightPanelX;
                    int ry = chestsort$rightPanelY;
                    int rw = chestsort$rightPanelW;
                    int boxH = 70;

                    context.fill(rx, ry, rx + rw, ry + boxH, 0xAA000000);
                    context.drawTextWithShadow(tr, Text.literal("Import preset"), rx + 8, ry + 6, 0xFFFFFFFF);

                    if (chestsort$presetImportError != null && !chestsort$presetImportError.isEmpty()) {
                        context.drawTextWithShadow(tr, Text.literal(chestsort$presetImportError).formatted(Formatting.RED), rx + 8, ry + 58, 0xFFFFFFFF);
                    } else {
                        context.drawTextWithShadow(tr, Text.literal("Paste a cs2| string"), rx + 8, ry + 58, 0xFFAAAAAA);
                    }
                }
                return;
            }

            if (chestsort$presetExportPopupOpen && !chestsort$filterMode) {
                chestsort$updateLayout();

                TextRenderer tr = MinecraftClient.getInstance().textRenderer;
                if (tr != null && chestsort$rightPanelW >= 20) {
                    int rx = chestsort$rightPanelX;
                    int ry = chestsort$rightPanelY;
                    int rw = chestsort$rightPanelW;
                    int boxH = 70;

                    context.fill(rx, ry, rx + rw, ry + boxH, 0xAA000000);
                    String title = (chestsort$presetExportName == null || chestsort$presetExportName.isEmpty()) ? "Export preset" : ("Export preset: " + chestsort$presetExportName);
                    context.drawTextWithShadow(tr, Text.literal(title), rx + 8, ry + 6, 0xFFFFFFFF);

                    if (chestsort$presetExportError != null && !chestsort$presetExportError.isEmpty()) {
                        context.drawTextWithShadow(tr, Text.literal(chestsort$presetExportError).formatted(Formatting.RED), rx + 8, ry + 58, 0xFFFFFFFF);
                    } else {
                        context.drawTextWithShadow(tr, Text.literal("Select + copy from box"), rx + 8, ry + 58, 0xFFAAAAAA);
                    }
                }
                return;
            }

            if (chestsort$exportPopupOpen && !chestsort$filterMode) {
                chestsort$updateLayout();

                TextRenderer tr = MinecraftClient.getInstance().textRenderer;
                if (tr != null && chestsort$rightPanelW >= 20) {
                    int rx = chestsort$rightPanelX;
                    int ry = chestsort$rightPanelY;
                    int rw = chestsort$rightPanelW;
                    int boxH = 70;

                    context.fill(rx, ry, rx + rw, ry + boxH, 0xAA000000);
                    context.drawTextWithShadow(tr, Text.literal("Export rule"), rx + 8, ry + 6, 0xFFFFFFFF);

                    if (chestsort$exportError != null && !chestsort$exportError.isEmpty()) {
                        context.drawTextWithShadow(tr, Text.literal(chestsort$exportError).formatted(Formatting.RED), rx + 8, ry + 58, 0xFFFFFFFF);
                    } else {
                        context.drawTextWithShadow(tr, Text.literal("Select + copy from box"), rx + 8, ry + 58, 0xFFAAAAAA);
                    }
                }
                return;
            }

            if (chestsort$filterMode) {
                TextRenderer tr = MinecraftClient.getInstance().textRenderer;
                if (tr != null) {

                    // Left panel: filter items + filter tags
                    chestsort$updateLayout();
                    int leftW = chestsort$leftPanelW;
                    int leftX = chestsort$leftPanelX;
                    int leftY = chestsort$leftPanelY;
                    int leftHeaderH = CHESTSORT_HEADER_H;
                    if (leftW >= 20) {
                        int leftPanelH = leftHeaderH + (chestsort$leftRowsShown * CHESTSORT_ROW_H) + CHESTSORT_PAD_BOTTOM;
                        context.fill(leftX, leftY, leftX + leftW, leftY + leftPanelH, 0xAA000000);
                    }

                    int rowH = CHESTSORT_ROW_H;
                    if (leftW >= 20) {
                        java.util.List<Integer> rows = chestsort$getLeftRows();
                        int actionX1 = (leftX + leftW) - CHESTSORT_ACTION_PAD - CHESTSORT_ACTION_W;
                        int actionX2 = (leftX + leftW) - CHESTSORT_ACTION_PAD;
                        int textX = leftX + 28;
                        int textW = Math.max(0, actionX1 - textX - 4);
                        final float leftTextScale = 0.90f;
                        int rowsY = leftY + leftHeaderH;

                        for (int i = 0; i < chestsort$leftRowsShown; i++) {
                            int rowIndex = chestsort$leftScroll + i;
                            int ry1 = rowsY + i * rowH;
                            int bg = (rowIndex == chestsort$selectedLeftRowIndex) ? 0xFF404040 : 0xFF202020;
                            context.fill(leftX + 6, ry1, leftX + leftW - 6, ry1 + rowH - 1, bg);

                            if (rowIndex >= rows.size()) {
                                context.drawTextWithShadow(tr, Text.literal(""), leftX + 8, ry1 + 5, 0xFFFFFFFF);
                                continue;
                            }

                            int encoded = rows.get(rowIndex);
                            int kind = chestsort$leftRowKind(encoded);
                            int idx = chestsort$leftRowIndex(encoded);

                            if (kind == CHESTSORT_LEFT_KIND_ITEMS_HEADER) {
                                String chevron = chestsort$itemsExpanded ? "v " : "> ";
                                String label = chevron + "Filter Items (" + chestsort$editingFilterItems.size() + ")";
                                context.drawTextWithShadow(tr, Text.literal(label), leftX + 8, ry1 + 5, 0xFFFFFFFF);
                            } else if (kind == CHESTSORT_LEFT_KIND_AUTOSORT) {
                                String label = "Autosort: " + (chestsort$editingAutosort ? "ON" : "OFF");
                                context.drawTextWithShadow(tr, Text.literal(label), leftX + 8, ry1 + 5, 0xFFFFFFFF);
                            } else if (kind == CHESTSORT_LEFT_KIND_PRIORITY) {
                                String label = "Priority: " + (chestsort$editingWhitelistPriority ? "Whitelist" : "Blacklist");
                                context.drawTextWithShadow(tr, Text.literal(label), leftX + 8, ry1 + 5, 0xFFFFFFFF);
                            } else if (kind == CHESTSORT_LEFT_KIND_TAGS_HEADER) {
                                String chevron = chestsort$tagsExpanded ? "v " : "> ";
                                String label = chevron + "Filter Tags (" + chestsort$editingFilterTags.size() + ")";
                                context.drawTextWithShadow(tr, Text.literal(label), leftX + 8, ry1 + 5, 0xFFFFFFFF);
                            } else if (kind == CHESTSORT_LEFT_KIND_PRESETS_HEADER) {
                                String chevron = chestsort$presetsExpanded ? "v " : "> ";
                                String label = chevron + "Filter Presets (" + chestsort$editingFilterPresets.size() + ")";
                                context.drawTextWithShadow(tr, Text.literal(label), leftX + 8, ry1 + 5, 0xFFFFFFFF);
                            } else if (kind == CHESTSORT_LEFT_KIND_ITEM) {
                                // Inline remove button (red "x")
                                context.fill(actionX1, ry1, actionX2, ry1 + rowH - 1, 0xFFFF5555);
                                context.drawTextWithShadow(tr, Text.literal("x"), actionX1 + 4, ry1 + 5, 0xFFFFFFFF);

                                if (idx < chestsort$editingFilterItems.size()) {
                                    String idStr = chestsort$editingFilterItems.get(idx);
                                    var id = net.minecraft.util.Identifier.tryParse(idStr);
                                    if (id != null && Registries.ITEM.containsId(id)) {
                                        var item = Registries.ITEM.get(id);
                                        context.drawItem(new ItemStack(item), leftX + 8, ry1 + 1);
                                        String name = Text.translatable(item.getTranslationKey()).getString();
                                        String nameTrim = textW <= 0 ? name : tr.trimToWidth(name, textW);
                                        String subTrim = textW <= 0 ? idStr : tr.trimToWidth(idStr, textW);
                                        chestsort$drawScaledTextWithShadow(context, tr, Text.literal(nameTrim), textX, ry1 + 2, 0xFFFFFFFF, leftTextScale);
                                        chestsort$drawScaledTextWithShadow(context, tr, Text.literal(subTrim), textX, ry1 + 10, 0xFF9A9A9A, leftTextScale);
                                    } else {
                                        String nameTrim = textW <= 0 ? idStr : tr.trimToWidth(idStr, textW);
                                        chestsort$drawScaledTextWithShadow(context, tr, Text.literal(nameTrim), leftX + 8, ry1 + 5, 0xFFFFFFFF, leftTextScale);
                                    }
                                }
                            } else if (kind == CHESTSORT_LEFT_KIND_TAG) {
                                // Inline remove button (red "x")
                                context.fill(actionX1, ry1, actionX2, ry1 + rowH - 1, 0xFFFF5555);
                                context.drawTextWithShadow(tr, Text.literal("x"), actionX1 + 4, ry1 + 5, 0xFFFFFFFF);

                                if (idx < chestsort$editingFilterTags.size()) {
                                    TagFilterSpec tag = chestsort$editingFilterTags.get(idx);
                                    String tagId = tag == null ? "" : ContainerFilterSpec.normalizeTagId(tag.tagId());
                                    java.util.Set<String> exc = (tag != null && tag.exceptions() != null && !tag.exceptions().isEmpty())
                                        ? chestsort$getExceptionSetForTag(tagId)
                                        : java.util.Set.of();
                                    Item iconItem = chestsort$getTagCycleIcon(tagId, exc);
                                    if (iconItem != null) {
                                        context.drawItem(new ItemStack(iconItem), leftX + 8, ry1 + 1);
                                    }
                                    int exceptionCount = (exc == null) ? 0 : exc.size();
                                    boolean hasExceptions = exceptionCount > 0;
                                    String name = chestsort$formatTagDisplayName(tagId);
                                    String displayName = hasExceptions ? (name + "* (" + exceptionCount + ")") : name;
                                    String nameTrim = textW <= 0 ? displayName : tr.trimToWidth(displayName, textW);
                                    String subTrim = textW <= 0 ? tagId : tr.trimToWidth(tagId, textW);
                                    chestsort$drawScaledTextWithShadow(context, tr, Text.literal(nameTrim), textX, ry1 + 2, 0xFFFFFFFF, leftTextScale);
                                    chestsort$drawScaledTextWithShadow(context, tr, Text.literal(subTrim), textX, ry1 + 10, 0xFF9A9A9A, leftTextScale);
                                }
                            } else if (kind == CHESTSORT_LEFT_KIND_PRESET) {
                                if (idx < chestsort$editingFilterPresets.size()) {
                                    String presetName = chestsort$editingFilterPresets.get(idx);

                                    // Inline remove button (red "x").
                                    context.fill(actionX1, ry1, actionX2, ry1 + rowH - 1, 0xFFFF5555);
                                    context.drawTextWithShadow(tr, Text.literal("x"), actionX1 + 4, ry1 + 5, 0xFFFFFFFF);

                                    ContainerFilterSpec spec = ClientPresetRegistry.get(presetName);
                                    Item icon = chestsort$getPresetCycleIcon(spec);
                                    if (icon != null) {
                                        context.drawItem(new ItemStack(icon), leftX + 8, ry1 + 1);
                                    }

                                    boolean dirtyHere = chestsort$filterDirty
                                        && chestsort$editingPresetName != null
                                        && chestsort$editingPresetName.equals(presetName)
                                        && !chestsort$editPresetMode;
                                    String display = dirtyHere ? (presetName + "*") : presetName;
                                    String nameTrim = textW <= 0 ? display : tr.trimToWidth(display, textW);
                                    chestsort$drawScaledTextWithShadow(context, tr, Text.literal(nameTrim), textX, ry1 + 5, 0xFFFFFFFF, leftTextScale);
                                }
                            }
                        }
                    }

                    // Right panel: search results / tag exception browser
                    int rightW = chestsort$rightPanelW;
                    int rightX = chestsort$rightPanelX;
                    int rightY = chestsort$rightPanelY;
                    if (rightW >= 20) {
                        int resultsPanelH = CHESTSORT_HEADER_H + (chestsort$resultsRowsShown * CHESTSORT_ROW_H) + CHESTSORT_PAD_BOTTOM;
                        context.fill(rightX, chestsort$resultsPanelY, rightX + rightW, chestsort$resultsPanelY + resultsPanelH, 0xAA000000);
                        String title = chestsort$tagBrowserMode
                            ? ("Tag: " + chestsort$tagBrowserTagId)
                            : "Results";
                        context.drawTextWithShadow(tr, Text.literal(title), rightX + 6, chestsort$resultsPanelY + 2, 0xFFFFFFFF);
                    }

                    if (rightW >= 20) {
                        int actionX1 = (rightX + rightW) - CHESTSORT_ACTION_PAD - CHESTSORT_ACTION_W;
                        int actionX2 = (rightX + rightW) - CHESTSORT_ACTION_PAD;
                        int textX = rightX + 28;
                        int textW = Math.max(0, actionX1 - textX - 4);
                        for (int i = 0; i < chestsort$resultsRowsShown; i++) {
                            int ry1 = chestsort$resultsRowsY + i * rowH;
                            int idx = chestsort$resultsScroll + i;
                            int bg = (idx == chestsort$selectedResultIndex) ? 0xFF404040 : 0xFF202020;
                            context.fill(rightX + 6, ry1, rightX + rightW - 6, ry1 + rowH - 1, bg);

                            boolean rowIsTag = false;
                            boolean rowIsPreset = false;
                            if (idx < chestsort$getSearchResultsSize()) {
                                Item entryItem = chestsort$searchResultItems.get(idx);
                                String entryTagId = idx < chestsort$searchResultTagIds.size() ? chestsort$searchResultTagIds.get(idx) : "";
                                rowIsTag = entryItem == null && entryTagId != null && !entryTagId.isEmpty();
                                String entryPreset = idx < chestsort$searchResultPresetNames.size() ? chestsort$searchResultPresetNames.get(idx) : "";
                                rowIsPreset = entryItem == null && (entryPreset != null && !entryPreset.isEmpty());
                            }

                            // Inline action button
                            boolean actionIsException = chestsort$tagBrowserMode && !rowIsTag && !rowIsPreset;
                            int actionColor = actionIsException ? 0xFFFF5555 : 0xFF55FF55;
                            String actionLabel = actionIsException ? "x" : "+";
                            int actionTextColor = actionIsException ? 0xFFFFFFFF : 0xFF000000;
                            context.fill(actionX1, ry1, actionX2, ry1 + rowH - 1, actionColor);
                            context.drawTextWithShadow(tr, Text.literal(actionLabel), actionX1 + 4, ry1 + 5, actionTextColor);

                            if (idx < chestsort$getSearchResultsSize()) {
                                Item entryItem = chestsort$searchResultItems.get(idx);
                                String entryTagId = idx < chestsort$searchResultTagIds.size() ? chestsort$searchResultTagIds.get(idx) : "";
                                String entryPreset = idx < chestsort$searchResultPresetNames.size() ? chestsort$searchResultPresetNames.get(idx) : "";
                                boolean isTag = entryItem == null && entryTagId != null && !entryTagId.isEmpty();
                                boolean isPreset = entryItem == null && (entryPreset != null && !entryPreset.isEmpty());

                                if (isPreset) {
                                    ContainerFilterSpec spec = ClientPresetRegistry.get(entryPreset);
                                    Item icon = chestsort$getPresetCycleIcon(spec);
                                    if (icon != null) context.drawItem(new ItemStack(icon), rightX + 8, ry1 + 1);
                                    String subtitle = (idx < chestsort$searchResultSubtitles.size()) ? chestsort$searchResultSubtitles.get(idx) : "";
                                    String nameTrim = textW <= 0 ? entryPreset : tr.trimToWidth(entryPreset, textW);
                                    String subTrim = textW <= 0 ? subtitle : tr.trimToWidth(subtitle, textW);
                                    context.drawTextWithShadow(tr, Text.literal(nameTrim), textX, ry1 + 2, 0xFFFFFFFF);
                                    context.drawTextWithShadow(tr, Text.literal(subTrim), textX, ry1 + 10, 0xFF9A9A9A);
                                } else if (isTag) {
                                    String tagId = ContainerFilterSpec.normalizeTagId(entryTagId);
                                    Item icon = chestsort$getTagCycleIcon(tagId);
                                    if (icon != null) context.drawItem(new ItemStack(icon), rightX + 8, ry1 + 1);
                                    String name = chestsort$formatTagDisplayName(tagId);
                                    String subtitle = (idx < chestsort$searchResultSubtitles.size()) ? chestsort$searchResultSubtitles.get(idx) : tagId;
                                    String nameTrim = textW <= 0 ? name : tr.trimToWidth(name, textW);
                                    String subTrim = textW <= 0 ? subtitle : tr.trimToWidth(subtitle, textW);
                                    context.drawTextWithShadow(tr, Text.literal(nameTrim), textX, ry1 + 2, 0xFFFFFFFF);
                                    context.drawTextWithShadow(tr, Text.literal(subTrim), textX, ry1 + 10, 0xFF9A9A9A);
                                } else {
                                    if (entryItem != null) {
                                        String name = Text.translatable(entryItem.getTranslationKey()).getString();
                                        String subtitle = (idx < chestsort$searchResultSubtitles.size()) ? chestsort$searchResultSubtitles.get(idx) : String.valueOf(Registries.ITEM.getId(entryItem));
                                        String nameTrim = textW <= 0 ? name : tr.trimToWidth(name, textW);
                                        String subTrim = textW <= 0 ? subtitle : tr.trimToWidth(subtitle, textW);
                                        context.drawItem(new ItemStack(entryItem), rightX + 8, ry1 + 1);
                                        context.drawTextWithShadow(tr, Text.literal(nameTrim), textX, ry1 + 2, 0xFFFFFFFF);
                                        context.drawTextWithShadow(tr, Text.literal(subTrim), textX, ry1 + 10, 0xFF9A9A9A);
                                    }
                                }
                            } else {
                                context.drawTextWithShadow(tr, Text.literal(""), rightX + 8, ry1 + 5, 0xFFFFFFFF);
                            }
                        }
                    }
                }
            }
        }

    }

    @Unique
    private void chestsort$renderSortNotification(DrawContext context, int mouseX, int mouseY) {
        chestsort$updateLayout();

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return;

        int leftW = chestsort$leftPanelW;
        int leftX = chestsort$leftPanelX;
        int leftY = chestsort$leftPanelY;
        if (leftW < 20) return;

        int rowH = CHESTSORT_ROW_H;
        int rowsShown = Math.min(12, Math.max(4, chestsort$leftRowsShown));
        int panelH = (rowsShown * rowH) + CHESTSORT_PAD_BOTTOM;

        context.fill(leftX, leftY, leftX + leftW, leftY + panelH, 0xAA000000);

        byte kind = ClientSortNotificationState.kind();
        String title;
        if (kind == SortResultPayload.KIND_AUTOSORT) {
            title = "Autosort activated";
        } else if (kind == SortResultPayload.KIND_ORGANIZE) {
            title = "Organize complete";
        } else if (kind == SortResultPayload.KIND_UNDO) {
            title = "Undo complete";
        } else {
            title = "Sort complete";
        }
        context.drawTextWithShadow(tr, Text.literal(title), leftX + 8, leftY + 6, 0xFFFFFFFF);

        String mode = ClientSortNotificationState.autosortMode();
        String modeLabel = mode == null || mode.isEmpty() ? "" : ("Mode: " + mode);
        String containerAuto = ClientSortNotificationState.containerAutosort() ? "Container: ON" : "Container: OFF";
        String moved;
        if (kind == SortResultPayload.KIND_ORGANIZE) {
            moved = "Changed: " + ClientSortNotificationState.movedTotal();
        } else {
            moved = "Moved: " + ClientSortNotificationState.movedTotal();
        }

        String subtitle;
        if (kind == SortResultPayload.KIND_AUTOSORT) {
            subtitle = modeLabel.isEmpty() ? containerAuto : (modeLabel + " | " + containerAuto);
        } else {
            subtitle = modeLabel.isEmpty() ? moved : (moved + " | " + modeLabel);
        }
        context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(subtitle, Math.max(0, leftW - 16))), leftX + 8, leftY + 18, 0xFFAAAAAA);

        int listStartY = leftY + 30;
        int listRows = rowsShown - 2;

        var lines = ClientSortNotificationState.lines();
        boolean tooltipShown = false;

        for (int i = 0; i < listRows; i++) {
            int idx = i;
            int ry1 = listStartY + i * rowH;
            int bg = 0xFF202020;
            context.fill(leftX + 6, ry1, leftX + leftW - 6, ry1 + rowH - 1, bg);

            if (lines == null || idx >= lines.size()) {
                continue;
            }

            SortResultPayload.SortLine line = lines.get(idx);
            if (line == null) continue;

            String itemIdStr = line.itemId();
            int count = line.count();

            Item iconItem = null;
            var id = Identifier.tryParse(itemIdStr);
            if (id != null && Registries.ITEM.containsId(id)) {
                iconItem = Registries.ITEM.get(id);
            }
            if (iconItem != null) {
                context.drawItem(new ItemStack(iconItem), leftX + 8, ry1 + 1);
            }

            String name = itemIdStr;
            if (iconItem != null) {
                name = Text.translatable(iconItem.getTranslationKey()).getString();
            }
            String label = count + "x " + name;
            int textX = leftX + 28;
            int textW = Math.max(0, (leftX + leftW - 8) - textX);
            context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(label, textW)), textX, ry1 + 5, 0xFFFFFFFF);

            // Hover tooltip: show why it matched.
            if (!tooltipShown && mouseX >= leftX + 6 && mouseX < leftX + leftW - 6 && mouseY >= ry1 && mouseY < ry1 + rowH) {
                java.util.ArrayList<Text> tooltip = new java.util.ArrayList<>();
                if (itemIdStr != null && !itemIdStr.isEmpty()) {
                    tooltip.add(Text.literal(itemIdStr).formatted(Formatting.GRAY));
                }
                if (line.reasons() != null) {
                    for (String r : line.reasons()) {
                        if (r == null || r.isEmpty()) continue;
                        tooltip.add(Text.literal(r));
                    }
                }
                if (!tooltip.isEmpty()) {
                    context.drawTooltip(tr, tooltip, mouseX, mouseY);
                    tooltipShown = true;
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    @SuppressWarnings("unused")
    private void chestsort$renderHighlight(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!ClientHighlightState.shouldHighlight()) {
            return;
        }
        String itemId = ClientHighlightState.currentItemId();
        if (itemId == null || itemId.isEmpty()) {
            return;
        }

        // Highlight matching slots in the currently open container.
        try {
            if (handler != null && handler.slots != null) {
                for (var slot : handler.slots) {
                    if (slot == null) continue;
                    var stack = slot.getStack();
                    if (stack == null || stack.isEmpty()) continue;
                    var id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
                    if (id == null) continue;
                    if (!itemId.equals(id.toString())) continue;

                    int sx = this.x + slot.x;
                    int sy = this.y + slot.y;
                    // 16x16 slot area; draw a translucent yellow overlay.
                    context.fill(sx, sy, sx + 16, sy + 16, 0x66FFFF00);
                    // thin border
                    context.fill(sx, sy, sx + 16, sy + 1, 0xCCFFFF00);
                    context.fill(sx, sy + 15, sx + 16, sy + 16, 0xCCFFFF00);
                    context.fill(sx, sy, sx + 1, sy + 16, 0xCCFFFF00);
                    context.fill(sx + 15, sy, sx + 16, sy + 16, 0xCCFFFF00);
                }
            }
        } catch (Throwable ignored) {
            // If mappings change, fail gracefully rather than crashing the UI.
        }

        Text text = Text.literal("[CS] Contains: " + itemId).formatted(Formatting.YELLOW);

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        if (textRenderer == null) {
            return;
        }

        context.drawTextWithShadow(textRenderer, text, 8, 8, 0xFFFFFFFF);
    }

    @Inject(method = "render", at = @At("TAIL"))
    @SuppressWarnings("unused")
    private void chestsort$renderLockedSlots(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!chestsort$isTargetContainer()) return;

        var client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        try {
            var inv = client.player.getInventory();
            if (handler != null && handler.slots != null) {
                for (var slot : handler.slots) {
                    if (slot == null) continue;
                    if (slot.inventory != inv) continue;

                    int idx = slot.getIndex();
                    if (!ClientLockedSlotsState.isLocked(idx)) continue;

                    int sx = this.x + slot.x;
                    int sy = this.y + slot.y;
                    // Dim + border to show "protected".
                    context.fill(sx, sy, sx + 16, sy + 16, 0x44000000);
                    context.fill(sx, sy, sx + 16, sy + 1, 0xCC888888);
                    context.fill(sx, sy + 15, sx + 16, sy + 16, 0xCC888888);
                    context.fill(sx, sy, sx + 1, sy + 16, 0xCC888888);
                    context.fill(sx + 15, sy, sx + 16, sy + 16, 0xCC888888);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private Integer chestsort$hoveredPlayerInventoryIndex(int mouseX, int mouseY) {
        if (mouseX < 0 || mouseY < 0) return null;

        var client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return null;

        try {
            var inv = client.player.getInventory();
            if (handler == null || handler.slots == null) return null;

            for (var slot : handler.slots) {
                if (slot == null) continue;
                if (slot.inventory != inv) continue;

                int sx = this.x + slot.x;
                int sy = this.y + slot.y;
                if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                    return slot.getIndex();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Unique
    private java.util.List<Integer> chestsort$getLeftRows() {
        chestsort$ensureState();
        java.util.ArrayList<Integer> rows = new java.util.ArrayList<>();
        // Autosort is a per-container setting, not a preset setting.
        if (chestsort$editingPresetName == null || chestsort$editingPresetName.isEmpty()) {
            rows.add(chestsort$leftRowEncode(CHESTSORT_LEFT_KIND_AUTOSORT, -1));
            rows.add(chestsort$leftRowEncode(CHESTSORT_LEFT_KIND_PRIORITY, -1));
        }
        rows.add(chestsort$leftRowEncode(CHESTSORT_LEFT_KIND_ITEMS_HEADER, -1));
        if (chestsort$itemsExpanded) {
            for (int i = 0; i < chestsort$editingFilterItems.size(); i++) {
                rows.add(chestsort$leftRowEncode(CHESTSORT_LEFT_KIND_ITEM, i));
            }
        }
        rows.add(chestsort$leftRowEncode(CHESTSORT_LEFT_KIND_TAGS_HEADER, -1));
        if (chestsort$tagsExpanded) {
            for (int i = 0; i < chestsort$editingFilterTags.size(); i++) {
                rows.add(chestsort$leftRowEncode(CHESTSORT_LEFT_KIND_TAG, i));
            }
        }
        rows.add(chestsort$leftRowEncode(CHESTSORT_LEFT_KIND_PRESETS_HEADER, -1));
        if (chestsort$presetsExpanded) {
            for (int i = 0; i < chestsort$editingFilterPresets.size(); i++) {
                rows.add(chestsort$leftRowEncode(CHESTSORT_LEFT_KIND_PRESET, i));
            }
        }
        return rows;
    }

    @Unique
    private static Identifier chestsort$parseTagIdentifier(String tagId) {
        if (tagId == null) return null;
        String t = tagId.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("#")) t = t.substring(1);

        // Allow shorthand: "#logs" -> "minecraft:logs".
        // If a namespace is provided, keep it as-is.
        Identifier full = Identifier.tryParse(t);
        if (full != null) return full;
        if (t.indexOf(':') < 0) {
            return Identifier.tryParse("minecraft:" + t);
        }
        return null;
    }

    @Unique
    private static String[] chestsort$splitTagId(String tagIdRaw) {
        if (tagIdRaw == null) return null;
        String t = tagIdRaw.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("#")) t = t.substring(1);
        if (t.isEmpty()) return null;
        int colon = t.indexOf(':');
        if (colon < 0) return new String[] { "", t };
        String ns = t.substring(0, colon);
        String path = t.substring(colon + 1);
        return new String[] { ns, path };
    }

    @Unique
    private static String chestsort$formatTagDisplayName(String tagId) {
        if (tagId == null || tagId.isEmpty()) return "";
        String t = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        int slash = t.indexOf(':');
        String path = slash >= 0 ? t.substring(slash + 1) : t;
        if (path.isEmpty()) return tagId;
        String[] parts = path.split("[_/\\\\-]+", -1);
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.length() == 0 ? tagId : sb.toString();
    }

    @Unique
    private Item chestsort$getTagCycleIcon(String tagId) {
        return chestsort$getTagCycleIcon(tagId, java.util.Set.of());
    }

    @Unique
    private Item chestsort$getPresetCycleIcon(ContainerFilterSpec spec) {
        if (spec == null || spec.items() == null || spec.items().isEmpty()) return null;

        java.util.List<String> items = spec.items();
        long now = System.currentTimeMillis();
        int start = (int) ((now / 750L) % items.size());
        for (int step = 0; step < items.size(); step++) {
            int idx = (start + step) % items.size();
            String itemId = items.get(idx);
            if (itemId == null || itemId.isEmpty()) continue;
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) continue;
            if (!Registries.ITEM.containsId(id)) continue;
            return Registries.ITEM.get(id);
        }
        return null;
    }

    @Unique
    private Item chestsort$getTagCycleIcon(String tagId, java.util.Set<String> excludedItemIds) {
        Identifier id = chestsort$parseTagIdentifier(tagId);
        if (id == null) return null;

        java.util.List<Item> items = chestsort$tagItemsCache.computeIfAbsent(tagId, k -> chestsort$computeItemsForTag(id));
        if (items == null || items.isEmpty()) return null;

        if (excludedItemIds == null || excludedItemIds.isEmpty()) {
            long now = System.currentTimeMillis();
            int idx = (int) ((now / 750L) % items.size());
            return items.get(Math.max(0, Math.min(idx, items.size() - 1)));
        }

        // Avoid allocation: pick the next non-excluded item by scanning.
        long now = System.currentTimeMillis();
        int start = (int) ((now / 750L) % items.size());
        for (int step = 0; step < items.size(); step++) {
            int idx = (start + step) % items.size();
            Item it = items.get(idx);
            if (it == null) continue;
            String itemId = String.valueOf(Registries.ITEM.getId(it));
            if (itemId.isEmpty()) continue;
            if (!excludedItemIds.contains(itemId)) return it;
        }
        return null;
    }

    @Unique
    private java.util.List<Item> chestsort$computeItemsForTag(Identifier tagIdentifier) {
        TagKey<Item> key = TagKey.of(RegistryKeys.ITEM, tagIdentifier);
        java.util.ArrayList<Item> out = new java.util.ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item.getDefaultStack().isIn(key)) {
                out.add(item);
                if (out.size() >= 200) break;
            }
        }
        return java.util.List.copyOf(out);
    }

    @Unique
    private void chestsort$rebuildTagBrowserItems() {
        Identifier id = chestsort$parseTagIdentifier(chestsort$tagBrowserTagId);
        if (id == null) {
            chestsort$tagBrowserItems = java.util.List.of();
            return;
        }
        java.util.List<Item> items = chestsort$tagItemsCache.computeIfAbsent(chestsort$tagBrowserTagId, k -> chestsort$computeItemsForTag(id));
        chestsort$tagBrowserItems = items == null ? java.util.List.of() : items;
    }

    @Unique
    private void chestsort$addTagException(String itemId) {
        if (itemId == null || itemId.isEmpty()) return;
        String tagId = ContainerFilterSpec.normalizeTagId(chestsort$tagBrowserTagId);
        if (tagId.isEmpty()) return;

        if (!(chestsort$editingFilterTags instanceof java.util.ArrayList)) {
            chestsort$editingFilterTags = new java.util.ArrayList<>(chestsort$editingFilterTags);
        }

        for (int i = 0; i < chestsort$editingFilterTags.size(); i++) {
            TagFilterSpec tag = chestsort$editingFilterTags.get(i);
            if (tag == null) continue;
            if (!ContainerFilterSpec.normalizeTagId(tag.tagId()).equals(tagId)) continue;

            java.util.LinkedHashSet<String> exc = new java.util.LinkedHashSet<>();
            if (tag.exceptions() != null) {
                for (String e : tag.exceptions()) {
                    if (e != null && !e.trim().isEmpty()) exc.add(e.trim());
                }
            }
            if (exc.add(itemId)) {
                chestsort$editingFilterTags.set(i, new TagFilterSpec(tagId, java.util.List.copyOf(exc)));
                chestsort$filterDirty = true;
                // Provide immediate feedback: refresh results so the newly-excepted item disappears.
                chestsort$updateSearchResults();
            }
            return;
        }
    }

    @Unique
    private java.util.Set<String> chestsort$getExceptionSetForTag(String tagIdRaw) {
        String tagId = ContainerFilterSpec.normalizeTagId(tagIdRaw);
        if (tagId.isEmpty() || chestsort$editingFilterTags == null) return java.util.Set.of();
        for (TagFilterSpec tag : chestsort$editingFilterTags) {
            if (tag == null) continue;
            if (!ContainerFilterSpec.normalizeTagId(tag.tagId()).equals(tagId)) continue;
            if (tag.exceptions() == null || tag.exceptions().isEmpty()) return java.util.Set.of();
            java.util.HashSet<String> out = new java.util.HashSet<>(tag.exceptions().size());
            for (String e : tag.exceptions()) {
                if (e == null) continue;
                String s = e.trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
        return java.util.Set.of();
    }

    @Unique
    private void chestsort$appendMatchingTagResults(String queryRaw, java.util.List<Item> outItems, java.util.List<String> outTagIds, java.util.List<String> outSubtitles) {
        String q = ContainerFilterSpec.normalizeTagId(queryRaw);
        if (q.isEmpty() || !q.startsWith("#")) return;

        // Match behavior:
        // - "#minecraft:foo" is treated the same as "#foo" (namespace ignored when minecraft)
        // - "#foo" matches tags whose PATH contains "foo" (so "#mod:yyyy_foo" matches)
        // - For non-minecraft namespaces, keep namespace filtering ("#c:ingots" matches only c:*)
        String[] qParts = chestsort$splitTagId(q);
        if (qParts == null) return;
        String qNs = chestsort$lc(qParts[0]);
        String qPath = chestsort$lc(qParts[1]);
        boolean namespaceAgnostic = qNs.isEmpty() || "minecraft".equals(qNs) || "c".equals(qNs);
        String qMatch = namespaceAgnostic ? qPath : (qNs + ":" + qPath);

        // Ensure at least the typed tag id appears if it's syntactically valid.
        Identifier typed = chestsort$parseTagIdentifier(q);
        String typedDisplay = null;
        if (typed != null) {
            typedDisplay = "#" + typed;
        }
        if (typedDisplay != null && chestsort$indexOfFilterTag(typedDisplay) < 0) {
            outItems.add(null);
            outTagIds.add(typedDisplay);
            outSubtitles.add(typedDisplay);
        }

        // Provide tag suggestions if we have them cached (scan happens lazily/off-thread).
        java.util.List<String> all = chestsort$getAllItemTagIds();
        if (all != null && !all.isEmpty()) {
            for (String t : all) {
                if (t == null) continue;

                String[] tParts = chestsort$splitTagId(t);
                if (tParts == null) continue;
                String tNs = chestsort$lc(tParts[0]);
                String tPath = chestsort$lc(tParts[1]);

                boolean matches;
                if (namespaceAgnostic) {
                    matches = !qMatch.isEmpty() && tPath.contains(qMatch);
                } else {
                    matches = (tNs + ":" + tPath).contains(qMatch);
                }
                if (!matches) continue;
                if (typedDisplay != null && t.equalsIgnoreCase(typedDisplay)) continue;
                if (chestsort$indexOfFilterTag(t) >= 0) continue;
                outItems.add(null);
                outTagIds.add(t);
                outSubtitles.add(t);
                if (outItems.size() >= 200) break;
            }
        }
    }

    @Unique
    private java.util.List<String> chestsort$getAllItemTagIds() {
        if (chestsort$allItemTagIds != null) return chestsort$allItemTagIds;
        chestsort$ensureItemTagIdsScanStarted();
        return java.util.List.of();
    }

    @Unique
    private void chestsort$ensureItemTagIdsScanStarted() {
        if (chestsort$allItemTagIds != null) return;
        long now = System.currentTimeMillis();
        if (chestsort$tagIdScanStarted && (now - chestsort$tagIdScanLastAttemptMs) < 5000L) return;
        chestsort$tagIdScanStarted = true;
        chestsort$tagIdScanLastAttemptMs = now;

        Thread t = new Thread(() -> {
            java.util.List<String> ids = chestsort$collectAllItemTagIdsBlocking();
            if (ids == null || ids.isEmpty()) {
                // Tags may not be bound yet when the UI first opens; allow retry later.
                chestsort$tagIdScanStarted = false;
                return;
            }

            chestsort$allItemTagIds = ids;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (chestsort$searchField != null) chestsort$updateSearchResults();
                });
            }
        }, "chestsort-tag-ids");
        t.setDaemon(true);
        t.start();
    }

    @Unique
    private static java.util.List<String> chestsort$collectAllItemTagIdsBlocking() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            net.minecraft.registry.Registry<Item> registry = (mc != null && mc.world != null)
                ? mc.world.getRegistryManager().getOrThrow(RegistryKeys.ITEM)
                : Registries.ITEM;

            registry.streamTags().forEach(named -> {
                if (named == null) return;
                TagKey<Item> key = named.getTag();
                if (key == null) return;
                Identifier id = key.id();
                if (id != null) out.add("#" + id);
            });
        } catch (Throwable ignored) {
            // If tag enumeration isn't available, we simply won't show suggestions.
        }

        java.util.ArrayList<String> list = new java.util.ArrayList<>(out.size());
        for (String s : out) {
            if (s == null) continue;
            String norm = ContainerFilterSpec.normalizeTagId(s);
            if (!norm.isEmpty()) list.add(norm);
        }
        list.sort(java.util.Comparator.naturalOrder());
        return java.util.List.copyOf(list);
    }

    @Unique
    private int chestsort$indexOfFilterTag(String tagIdRaw) {
        String tagId = ContainerFilterSpec.normalizeTagId(tagIdRaw);
        if (tagId.isEmpty()) return -1;
        for (int i = 0; i < chestsort$editingFilterTags.size(); i++) {
            TagFilterSpec t = chestsort$editingFilterTags.get(i);
            if (t != null && ContainerFilterSpec.normalizeTagId(t.tagId()).equals(tagId)) return i;
        }
        return -1;
    }

    @Unique
    private void chestsort$moveFilterItemToTop(int idx) {
        if (idx <= 0 || idx >= chestsort$editingFilterItems.size()) return;
        if (!(chestsort$editingFilterItems instanceof java.util.ArrayList)) {
            chestsort$editingFilterItems = new java.util.ArrayList<>(chestsort$editingFilterItems);
        }
        String v = chestsort$editingFilterItems.remove(idx);
        chestsort$editingFilterItems.add(0, v);
    }

    @Unique
    private void chestsort$moveFilterTagToTop(int idx) {
        if (idx <= 0 || idx >= chestsort$editingFilterTags.size()) return;
        if (!(chestsort$editingFilterTags instanceof java.util.ArrayList)) {
            chestsort$editingFilterTags = new java.util.ArrayList<>(chestsort$editingFilterTags);
        }
        TagFilterSpec v = chestsort$editingFilterTags.remove(idx);
        chestsort$editingFilterTags.add(0, v);
    }

    @Unique
    private void chestsort$bumpMatchingFilterItemsToTop(String queryLower) {
        String q = chestsort$lc(queryLower).trim();
        if (q.isEmpty() || q.startsWith("#") || q.startsWith("&")) return;
        if (chestsort$editingFilterItems == null || chestsort$editingFilterItems.isEmpty()) return;

        java.util.ArrayList<String> matches = new java.util.ArrayList<>();
        java.util.ArrayList<String> rest = new java.util.ArrayList<>();
        for (String itemId : chestsort$editingFilterItems) {
            if (itemId == null) continue;
            String idLower = chestsort$lc(itemId);
            boolean match = idLower.contains(q);
            if (!match) {
                Identifier id = Identifier.tryParse(itemId);
                if (id != null) {
                    Item item = Registries.ITEM.get(id);
                    if (item != null) {
                        String name = chestsort$lc(Text.translatable(item.getTranslationKey()).getString());
                        match = name.contains(q);
                    }
                }
            }
            if (match) matches.add(itemId);
            else rest.add(itemId);
        }

        if (!matches.isEmpty()) {
            if (!(chestsort$editingFilterItems instanceof java.util.ArrayList)) {
                chestsort$editingFilterItems = new java.util.ArrayList<>(matches.size() + rest.size());
            } else {
                chestsort$editingFilterItems.clear();
            }
            chestsort$editingFilterItems.addAll(matches);
            chestsort$editingFilterItems.addAll(rest);
        }
    }

    @Unique
    private void chestsort$bumpMatchingFilterTagsToTop(String queryLower) {
        String q = chestsort$lc(ContainerFilterSpec.normalizeTagId(queryLower)).trim();
        if (q.isEmpty() || !q.startsWith("#")) return;
        if (chestsort$editingFilterTags == null || chestsort$editingFilterTags.isEmpty()) return;

        java.util.ArrayList<TagFilterSpec> matches = new java.util.ArrayList<>();
        java.util.ArrayList<TagFilterSpec> rest = new java.util.ArrayList<>();
        for (TagFilterSpec t : chestsort$editingFilterTags) {
            String tagId = ContainerFilterSpec.normalizeTagId(t == null ? "" : t.tagId());
            if (!tagId.isEmpty() && chestsort$lc(tagId).contains(q)) matches.add(t);
            else rest.add(t);
        }

        if (!matches.isEmpty()) {
            if (!(chestsort$editingFilterTags instanceof java.util.ArrayList)) {
                chestsort$editingFilterTags = new java.util.ArrayList<>(matches.size() + rest.size());
            } else {
                chestsort$editingFilterTags.clear();
            }
            chestsort$editingFilterTags.addAll(matches);
            chestsort$editingFilterTags.addAll(rest);
        }
    }

    @Unique
    private static void chestsort$collectTagIdFromUnknown(Object o, java.util.Set<String> out) {
        if (o == null) return;

        // Direct TagKey
        if (o instanceof TagKey<?> tagKey) {
            Identifier id = tagKey.id();
            if (id != null) out.add("#" + id);
            return;
        }

        // Map.Entry-like (key=TagKey)
        if (o instanceof java.util.Map.Entry<?, ?> e) {
            Object k = e.getKey();
            if (k instanceof TagKey<?> tk) {
                Identifier id = tk.id();
                if (id != null) out.add("#" + id);
                return;
            }
        }

        // Reflective shapes: try getKey()/getTag()/key()/tag()
        try {
            for (String methodName : java.util.List.of("getKey", "getTag", "key", "tag")) {
                java.lang.reflect.Method m;
                try {
                    m = o.getClass().getMethod(methodName);
                } catch (NoSuchMethodException ignored) {
                    continue;
                }
                Object k = m.invoke(o);
                if (k instanceof TagKey<?> tk) {
                    Identifier id = tk.id();
                    if (id != null) out.add("#" + id);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static void chestsort$drawScaledTextWithShadow(DrawContext context, TextRenderer tr, Text text, int x, int y, int color, float scale) {
        if (context == null || tr == null || text == null) return;
        if (scale <= 0.0f || scale == 1.0f) {
            context.drawTextWithShadow(tr, text, x, y, color);
            return;
        }
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(scale, scale);
        int sx = Math.round(x / scale);
        int sy = Math.round(y / scale);
        context.drawTextWithShadow(tr, text, sx, sy, color);
        matrices.popMatrix();
    }
}
