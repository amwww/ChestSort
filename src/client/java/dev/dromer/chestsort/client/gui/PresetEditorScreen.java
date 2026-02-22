package dev.dromer.chestsort.client.gui;

import dev.dromer.chestsort.client.ClientPresetRegistry;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;
import dev.dromer.chestsort.net.payload.SetPresetV2Payload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class PresetEditorScreen extends Screen {
    private static final int PANEL_W = 180;
    private static final int PANEL_GAP = 8;
    private static final int PANEL_H = 200;
    private static final int ROW_H = 18;
    private static final int HEADER_H = 16;
    private static final int ACTION_W = 14;
    private static final int ACTION_PAD = 6;

    private final String presetName;

    private static final int TAB_WHITELIST = 0;
    private static final int TAB_BLACKLIST = 1;
    private int chestsort$tab = TAB_WHITELIST;

    private ArrayList<String> editingItems = new ArrayList<>();
    private ArrayList<TagFilterSpec> editingTags = new ArrayList<>();

    private ArrayList<String> chestsort$whitelistItems = new ArrayList<>();
    private ArrayList<TagFilterSpec> chestsort$whitelistTags = new ArrayList<>();
    private ArrayList<String> chestsort$blacklistItems = new ArrayList<>();
    private ArrayList<TagFilterSpec> chestsort$blacklistTags = new ArrayList<>();

    private boolean tagBrowserMode = false;
    private String tagBrowserTagId = "";
    private List<Item> tagBrowserItems = List.of();
    private final Map<String, List<Item>> tagItemsCache = new HashMap<>();

    private TextFieldWidget searchField;
    private ButtonWidget doneButton;
    private ButtonWidget cancelButton;

    private ArrayList<Item> allItems;

    private volatile List<String> chestsort$allItemTagIds;
    private volatile boolean chestsort$tagIdScanStarted = false;

    private List<Item> resultItems = List.of();
    private List<String> resultTagIds = List.of();
    private List<String> resultSubtitles = List.of();
    private int resultsScroll = 0;

    private int leftScroll = 0;

    private int chestsort$getRowsAreaH() {
        // PANEL_H includes the search field and buttons at the top.
        return Math.max(ROW_H, PANEL_H - (24 + 24 + HEADER_H));
    }

    private int getLeftTotalRows() {
        // items header + items + tags header + tags
        return 2 + this.editingItems.size() + this.editingTags.size();
    }

    private int getLeftRowsShown() {
        return Math.max(1, chestsort$getRowsAreaH() / ROW_H);
    }

    private void chestsort$clampLeftScroll() {
        int shown = getLeftRowsShown();
        int max = Math.max(0, getLeftTotalRows() - Math.max(1, shown));
        this.leftScroll = Math.max(0, Math.min(this.leftScroll, max));
    }

    private int chestsort$panelW() {
        // Keep the UI on-screen even at high GUI scales / small windows.
        int maxTotalW = Math.max(0, this.width - 20);
        int perPanel = (maxTotalW - PANEL_GAP) / 2;
        if (perPanel <= 0) return Math.max(60, this.width / 2);
        return Math.max(60, Math.min(PANEL_W, perPanel));
    }

    private int chestsort$leftX(int panelW) {
        int totalW = (panelW * 2) + PANEL_GAP;
        int x = (this.width - totalW) / 2;
        // Keep a small margin so text/buttons don't clip.
        return Math.max(10, x);
    }

    public PresetEditorScreen(String presetName) {
        super(Text.literal("Edit preset"));
        this.presetName = presetName == null ? "" : presetName.trim();
    }

    @Override
    protected void init() {
        loadPresetSpec();

        this.leftScroll = 0;
        this.resultsScroll = 0;

        int panelW = chestsort$panelW();
        int leftX = chestsort$leftX(panelW);
        int rightX = leftX + panelW + PANEL_GAP;
        int topY = Math.max(10, (this.height - PANEL_H) / 2);

        this.searchField = new TextFieldWidget(this.textRenderer, rightX + 6, topY + 4, panelW - 12, 16, Text.literal("Search"));
        this.searchField.setMaxLength(256);
        this.searchField.setChangedListener(s -> updateSearchResults());
        this.addDrawableChild(this.searchField);

        this.doneButton = ButtonWidget.builder(Text.literal("Done"), b -> {
            saveToServer();
            MinecraftClient.getInstance().setScreen(null);
        }).dimensions(rightX + 6, topY + 24, (panelW - 18) / 2, 20).build();
        this.addDrawableChild(this.doneButton);

        this.cancelButton = ButtonWidget.builder(Text.literal("Cancel"), b -> MinecraftClient.getInstance().setScreen(null))
            .dimensions(rightX + 12 + (panelW - 18) / 2, topY + 24, (panelW - 18) / 2, 20)
            .build();
        this.addDrawableChild(this.cancelButton);

        updateSearchResults();
    }

    private void loadPresetSpec() {
        if (this.presetName.isEmpty()) {
            this.chestsort$whitelistItems = new ArrayList<>();
            this.chestsort$whitelistTags = new ArrayList<>();
            this.chestsort$blacklistItems = new ArrayList<>();
            this.chestsort$blacklistTags = new ArrayList<>();
            chestsort$applyTab(TAB_WHITELIST);
            return;
        }

        ContainerFilterSpec wl = ClientPresetRegistry.get(this.presetName);
        ContainerFilterSpec bl = ClientPresetRegistry.getBlacklist(this.presetName);

        ContainerFilterSpec safeWl = (wl == null) ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl.normalized();
        ContainerFilterSpec safeBl = (bl == null) ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : bl.normalized();

        this.chestsort$whitelistItems = new ArrayList<>(safeWl.items() == null ? List.of() : safeWl.items());
        this.chestsort$whitelistTags = new ArrayList<>(safeWl.tags() == null ? List.of() : safeWl.tags());
        this.chestsort$blacklistItems = new ArrayList<>(safeBl.items() == null ? List.of() : safeBl.items());
        this.chestsort$blacklistTags = new ArrayList<>(safeBl.tags() == null ? List.of() : safeBl.tags());

        chestsort$applyTab(this.chestsort$tab);

        this.tagBrowserMode = false;
        this.tagBrowserTagId = "";
        this.tagBrowserItems = List.of();
        this.tagItemsCache.clear();
    }

    private void chestsort$applyTab(int tab) {
        this.chestsort$tab = (tab == TAB_BLACKLIST) ? TAB_BLACKLIST : TAB_WHITELIST;
        if (this.chestsort$tab == TAB_BLACKLIST) {
            this.editingItems = this.chestsort$blacklistItems;
            this.editingTags = this.chestsort$blacklistTags;
        } else {
            this.editingItems = this.chestsort$whitelistItems;
            this.editingTags = this.chestsort$whitelistTags;
        }

        this.leftScroll = 0;
        this.resultsScroll = 0;

        if (this.tagBrowserMode) {
            this.tagBrowserMode = false;
            this.tagBrowserTagId = "";
            this.tagBrowserItems = List.of();
        }
        if (this.searchField != null) {
            this.searchField.setText("");
        }
        updateSearchResults();
    }

    private void saveToServer() {
        if (this.presetName.isEmpty()) return;

        ContainerFilterSpec wl = new ContainerFilterSpec(
            ContainerFilterSpec.normalizeStrings(this.chestsort$whitelistItems),
            this.chestsort$whitelistTags,
            List.of()
        ).normalized();

        ContainerFilterSpec bl = new ContainerFilterSpec(
            ContainerFilterSpec.normalizeStrings(this.chestsort$blacklistItems),
            this.chestsort$blacklistTags,
            List.of()
        ).normalized();

        ClientPlayNetworking.send(new SetPresetV2Payload(this.presetName, wl, bl));
        ClientPresetRegistry.putLocal(this.presetName, wl);
        ClientPresetRegistry.putLocalBlacklist(this.presetName, bl);
    }

    private void ensureAllItems() {
        if (this.allItems != null) return;
        this.allItems = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            this.allItems.add(item);
        }
    }

    private void updateSearchResults() {
        ensureAllItems();

        String query = this.searchField == null ? "" : (this.searchField.getText() == null ? "" : this.searchField.getText().trim().toLowerCase(java.util.Locale.ROOT));

        // If the query matches entries already in the filter, move them to the top so the user can see
        // they are already added (and keep them out of the search results).
        if (!this.tagBrowserMode && !query.isEmpty()) {
            if (query.startsWith("#")) {
                chestsort$bumpMatchingTagsToTop(query);
            } else {
                chestsort$bumpMatchingItemsToTop(query);
            }
        }

        if (this.tagBrowserMode) {
            // Mirror container UI behavior: if the query starts with '#', show tag results (to allow
            // selecting a tag to browse), otherwise search within the items of the currently browsed tag.
            if (!query.isEmpty() && query.startsWith("#")) {
                String norm = ContainerFilterSpec.normalizeTagId(query);
                Identifier id = parseTagIdentifier(norm);

                chestsort$ensureItemTagIdsScanStarted();

                ArrayList<Item> items = new ArrayList<>(16);
                ArrayList<String> tagIds = new ArrayList<>(16);
                ArrayList<String> subtitles = new ArrayList<>(16);

                if (id != null && !hasTag(norm)) {
                    items.add(null);
                    tagIds.add(norm);
                    subtitles.add(norm);
                }

                List<String> allTags = this.chestsort$allItemTagIds;
                if (allTags != null && !allTags.isEmpty()) {
                    String q = norm.toLowerCase(java.util.Locale.ROOT);
                    for (String t : allTags) {
                        if (t == null) continue;
                        String tl = t.toLowerCase(java.util.Locale.ROOT);
                        if (!tl.contains(q)) continue;
                        if (t.equals(norm)) continue;
                        if (hasTag(t)) continue;
                        items.add(null);
                        tagIds.add(t);
                        subtitles.add(t);
                        if (items.size() >= 200) break;
                    }
                }

                this.resultItems = items;
                this.resultTagIds = tagIds;
                this.resultSubtitles = subtitles;
                this.resultsScroll = 0;
                return;
            }

            HashSet<String> exc = new HashSet<>(getExceptionSetForTag(this.tagBrowserTagId));
            ArrayList<Item> items = new ArrayList<>(16);
            ArrayList<String> tagIds = new ArrayList<>(16);
            ArrayList<String> subtitles = new ArrayList<>(16);

            for (Item item : this.tagBrowserItems) {
                if (item == null) continue;
                String itemId = String.valueOf(Registries.ITEM.getId(item));
                if (itemId.isEmpty()) continue;
                if (exc.contains(itemId)) continue;

                if (!query.isEmpty()) {
                    String idStr = itemId.toLowerCase(java.util.Locale.ROOT);
                    String nameStr = Text.translatable(item.getTranslationKey()).getString().toLowerCase(java.util.Locale.ROOT);
                    if (!idStr.contains(query) && !nameStr.contains(query)) continue;
                }

                items.add(item);
                tagIds.add("");
                subtitles.add(itemId);
                if (items.size() >= 200) break;
            }

            this.resultItems = items;
            this.resultTagIds = tagIds;
            this.resultSubtitles = subtitles;
            this.resultsScroll = 0;
            return;
        }

        if (query.isEmpty()) {
            this.resultItems = List.of();
            this.resultTagIds = List.of();
            this.resultSubtitles = List.of();
            this.resultsScroll = 0;
            return;
        }

        if (query.startsWith("#")) {
            String norm = ContainerFilterSpec.normalizeTagId(query);
            Identifier id = parseTagIdentifier(norm);

            // Start tag id scan lazily; results update when the scan completes.
            chestsort$ensureItemTagIdsScanStarted();

            ArrayList<Item> items = new ArrayList<>(16);
            ArrayList<String> tagIds = new ArrayList<>(16);
            ArrayList<String> subtitles = new ArrayList<>(16);

            // Always include the typed tag id if it is syntactically valid and not already added.
            if (id != null && !hasTag(norm)) {
                items.add(null);
                tagIds.add(norm);
                subtitles.add(norm);
            }

            // If we have a cached tag list, provide substring suggestions.
            List<String> allTags = this.chestsort$allItemTagIds;
            if (allTags != null && !allTags.isEmpty()) {
                String q = norm.toLowerCase(java.util.Locale.ROOT);
                for (String t : allTags) {
                    if (t == null) continue;
                    String tl = t.toLowerCase(java.util.Locale.ROOT);
                    if (!tl.contains(q)) continue;
                    if (t.equals(norm)) continue;
                    if (hasTag(t)) continue;
                    items.add(null);
                    tagIds.add(t);
                    subtitles.add(t);
                    if (items.size() >= 200) break;
                }
            }

            this.resultItems = items;
            this.resultTagIds = tagIds;
            this.resultSubtitles = subtitles;
            this.resultsScroll = 0;
            return;
        }

        ArrayList<Item> items = new ArrayList<>(16);
        ArrayList<String> tagIds = new ArrayList<>(16);
        ArrayList<String> subtitles = new ArrayList<>(16);

        HashSet<String> already = new HashSet<>(this.editingItems);
        for (Item item : this.allItems) {
            Identifier id = Registries.ITEM.getId(item);
            String idStr = id == null ? "" : id.toString();
            String nameStr = Text.translatable(item.getTranslationKey()).getString().toLowerCase(java.util.Locale.ROOT);

            // Keep already-added entries out of the results.
            if (!idStr.isEmpty() && already.contains(idStr)) continue;

            if (idStr.toLowerCase(java.util.Locale.ROOT).contains(query) || nameStr.contains(query)) {
                items.add(item);
                tagIds.add("");
                subtitles.add(idStr);
                if (items.size() >= 200) break;
            }
        }

        this.resultItems = items;
        this.resultTagIds = tagIds;
        this.resultSubtitles = subtitles;
        this.resultsScroll = 0;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (this.searchField != null && this.searchField.keyPressed(keyInput)) {
            return true;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (keyInput.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (this.tagBrowserMode) {
                this.tagBrowserMode = false;
                this.tagBrowserTagId = "";
                this.tagBrowserItems = List.of();
                if (this.searchField != null) this.searchField.setText("");
                updateSearchResults();
                return true;
            }
            if (mc != null) {
                mc.setScreen(null);
            }
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelW = chestsort$panelW();
        int leftX = chestsort$leftX(panelW);
        int rightX = leftX + panelW + PANEL_GAP;
        int topY = Math.max(10, (this.height - PANEL_H) / 2);
        int rowsY = topY + 24 + 24 + HEADER_H;
        int rowsAreaH = chestsort$getRowsAreaH();

        boolean overLeft = mouseX >= leftX && mouseX < leftX + panelW && mouseY >= rowsY && mouseY < rowsY + rowsAreaH;
        boolean overRight = mouseX >= rightX && mouseX < rightX + panelW && mouseY >= rowsY && mouseY < rowsY + rowsAreaH;

        if (overLeft) {
            int shown = getLeftRowsShown();
            int max = Math.max(0, getLeftTotalRows() - Math.max(1, shown));
            if (verticalAmount > 0) {
                this.leftScroll = Math.max(0, this.leftScroll - 1);
            } else if (verticalAmount < 0) {
                this.leftScroll = Math.min(max, this.leftScroll + 1);
            }
            return true;
        }

        if (overRight) {
            int resultsShown = getResultsRowsShown();
            int maxResults = Math.max(0, getResultsSize() - Math.max(1, resultsShown));
            if (verticalAmount > 0) {
                this.resultsScroll = Math.max(0, this.resultsScroll - 1);
            } else if (verticalAmount < 0) {
                this.resultsScroll = Math.min(maxResults, this.resultsScroll + 1);
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean bl) {
        int button = click.button();
        double mouseX = click.x();
        double mouseY = click.y();
        if (button != 0) return super.mouseClicked(click, bl);

        int panelW = chestsort$panelW();
        int leftX = chestsort$leftX(panelW);
        int rightX = leftX + panelW + PANEL_GAP;
        int topY = Math.max(10, (this.height - PANEL_H) / 2);

        // Tab row (left panel)
        int tabY1 = topY + 24 + 24;
        int tabY2 = tabY1 + HEADER_H;
        if (mouseX >= leftX && mouseX < leftX + panelW && mouseY >= tabY1 && mouseY < tabY2) {
            int half = panelW / 2;
            if (mouseX < leftX + half) {
                if (this.chestsort$tab != TAB_WHITELIST) {
                    chestsort$applyTab(TAB_WHITELIST);
                }
                return true;
            } else {
                if (this.chestsort$tab != TAB_BLACKLIST) {
                    chestsort$applyTab(TAB_BLACKLIST);
                }
                return true;
            }
        }

        int actionX1Left = (leftX + panelW) - ACTION_PAD - ACTION_W;
        int actionX2Left = (leftX + panelW) - ACTION_PAD;

        // Left rows: items header + items + tags header + tags
        int rowY = topY + 24 + 24 + HEADER_H;
        int rowsAreaH = chestsort$getRowsAreaH();
        int visibleRowIndex = (int) ((mouseY - rowY) / ROW_H);
        if (mouseX >= leftX && mouseX < leftX + panelW && mouseY >= rowY && mouseY < rowY + rowsAreaH) {
            chestsort$clampLeftScroll();
            int rowIndex = this.leftScroll + visibleRowIndex;
            int kind = getLeftRowKind(rowIndex);
            int idx = getLeftRowIndex(rowIndex);

            if (kind == 1) {
                // item
                if (mouseX >= actionX1Left && mouseX < actionX2Left && idx >= 0 && idx < this.editingItems.size()) {
                    this.editingItems.remove(idx);
                    chestsort$clampLeftScroll();
                    return true;
                }
            } else if (kind == 3) {
                // tag
                if (idx >= 0 && idx < this.editingTags.size()) {
                    if (mouseX >= actionX1Left && mouseX < actionX2Left) {
                        this.editingTags.remove(idx);
                        if (this.tagBrowserMode) {
                            this.tagBrowserMode = false;
                            this.tagBrowserTagId = "";
                            this.tagBrowserItems = List.of();
                            if (this.searchField != null) this.searchField.setText("");
                            updateSearchResults();
                        }
                        chestsort$clampLeftScroll();
                        return true;
                    }

                    TagFilterSpec tag = this.editingTags.get(idx);
                    String tagId = ContainerFilterSpec.normalizeTagId(tag == null ? "" : tag.tagId());
                    if (!tagId.isEmpty()) {
                        this.tagBrowserMode = true;
                        this.tagBrowserTagId = tagId;
                        rebuildTagBrowserItems();
                        if (this.searchField != null) {
                            this.searchField.setText("");
                        }
                        updateSearchResults();
                        return true;
                    }
                }
            }
        }

        // Right results: action adds
        int actionX1Right = (rightX + panelW) - ACTION_PAD - ACTION_W;
        int actionX2Right = (rightX + panelW) - ACTION_PAD;
        int resultsY = topY + 24 + 24 + HEADER_H;
        int rIdx = (int) ((mouseY - resultsY) / ROW_H);
        int realIdx = this.resultsScroll + rIdx;
        if (mouseX >= rightX && mouseX < rightX + panelW && mouseY >= resultsY) {
            if (mouseX >= actionX1Right && mouseX < actionX2Right) {
                if (realIdx >= 0 && realIdx < getResultsSize()) {
                    Item entryItem = this.resultItems.get(realIdx);
                    String entryTagId = realIdx < this.resultTagIds.size() ? this.resultTagIds.get(realIdx) : "";

                    boolean isTag = entryItem == null && entryTagId != null && !entryTagId.isEmpty();
                    if (this.tagBrowserMode) {
                        if (isTag) {
                            String tagId = ContainerFilterSpec.normalizeTagId(entryTagId);
                            if (!tagId.isEmpty()) {
                                int existing = chestsort$indexOfTag(tagId);
                                if (existing >= 0) {
                                    chestsort$moveTagToTop(existing);
                                } else {
                                    this.editingTags.add(0, new TagFilterSpec(tagId, List.of()));
                                }

                                this.tagBrowserTagId = tagId;
                                rebuildTagBrowserItems();
                                if (this.searchField != null) this.searchField.setText("");
                                updateSearchResults();
                                return true;
                            }
                        }
                        if (entryItem != null) {
                            String itemId = String.valueOf(Registries.ITEM.getId(entryItem));
                            addTagException(itemId);
                            return true;
                        }
                    } else if (isTag) {
                        String tagId = ContainerFilterSpec.normalizeTagId(entryTagId);
                        if (!tagId.isEmpty()) {
                            int existing = chestsort$indexOfTag(tagId);
                            if (existing >= 0) {
                                chestsort$moveTagToTop(existing);
                                return true;
                            }
                            this.editingTags.add(0, new TagFilterSpec(tagId, List.of()));
                            return true;
                        }
                    } else if (entryItem != null) {
                        String itemId = String.valueOf(Registries.ITEM.getId(entryItem));
                        if (!itemId.isEmpty()) {
                            int existing = this.editingItems.indexOf(itemId);
                            if (existing >= 0) {
                                chestsort$moveItemToTop(existing);
                                return true;
                            }
                            this.editingItems.add(0, itemId);
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(click, bl);
    }

    private boolean hasTag(String tagIdRaw) {
        String tagId = ContainerFilterSpec.normalizeTagId(tagIdRaw);
        if (tagId.isEmpty()) return false;
        for (TagFilterSpec t : this.editingTags) {
            if (t != null && ContainerFilterSpec.normalizeTagId(t.tagId()).equals(tagId)) return true;
        }
        return false;
    }

    private int chestsort$indexOfTag(String tagIdRaw) {
        String tagId = ContainerFilterSpec.normalizeTagId(tagIdRaw);
        if (tagId.isEmpty()) return -1;
        for (int i = 0; i < this.editingTags.size(); i++) {
            TagFilterSpec t = this.editingTags.get(i);
            if (t != null && ContainerFilterSpec.normalizeTagId(t.tagId()).equals(tagId)) return i;
        }
        return -1;
    }

    private void chestsort$moveItemToTop(int idx) {
        if (idx <= 0 || idx >= this.editingItems.size()) return;
        String v = this.editingItems.remove(idx);
        this.editingItems.add(0, v);
    }

    private void chestsort$moveTagToTop(int idx) {
        if (idx <= 0 || idx >= this.editingTags.size()) return;
        TagFilterSpec v = this.editingTags.remove(idx);
        this.editingTags.add(0, v);
    }

    private void chestsort$bumpMatchingItemsToTop(String queryLower) {
        if (queryLower == null) return;
        String q = queryLower.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty() || q.startsWith("#")) return;

        ArrayList<String> matches = new ArrayList<>();
        ArrayList<String> rest = new ArrayList<>();
        for (String itemId : this.editingItems) {
            if (itemId == null) continue;
            String idLower = itemId.toLowerCase(java.util.Locale.ROOT);
            boolean match = idLower.contains(q);
            if (!match) {
                Identifier id = Identifier.tryParse(itemId);
                if (id != null) {
                    Item item = Registries.ITEM.get(id);
                    if (item != null) {
                        String name = Text.translatable(item.getTranslationKey()).getString().toLowerCase(java.util.Locale.ROOT);
                        match = name.contains(q);
                    }
                }
            }
            if (match) matches.add(itemId);
            else rest.add(itemId);
        }

        if (!matches.isEmpty()) {
            this.editingItems.clear();
            this.editingItems.addAll(matches);
            this.editingItems.addAll(rest);
        }
    }

    private void chestsort$bumpMatchingTagsToTop(String queryLower) {
        if (queryLower == null) return;
        String q = ContainerFilterSpec.normalizeTagId(queryLower).toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty() || !q.startsWith("#")) return;

        ArrayList<TagFilterSpec> matches = new ArrayList<>();
        ArrayList<TagFilterSpec> rest = new ArrayList<>();
        for (TagFilterSpec t : this.editingTags) {
            String tagId = ContainerFilterSpec.normalizeTagId(t == null ? "" : t.tagId());
            String tl = tagId.toLowerCase(java.util.Locale.ROOT);
            if (!tagId.isEmpty() && tl.contains(q)) matches.add(t);
            else rest.add(t);
        }

        if (!matches.isEmpty()) {
            this.editingTags.clear();
            this.editingTags.addAll(matches);
            this.editingTags.addAll(rest);
        }
    }

    private void chestsort$ensureItemTagIdsScanStarted() {
        if (this.chestsort$allItemTagIds != null) return;
        if (this.chestsort$tagIdScanStarted) return;
        this.chestsort$tagIdScanStarted = true;

        Thread t = new Thread(() -> {
            List<String> ids = chestsort$collectAllItemTagIds();
            this.chestsort$allItemTagIds = ids;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                mc.execute(this::updateSearchResults);
            }
        }, "chestsort-tag-ids");
        t.setDaemon(true);
        t.start();
    }

    private static List<String> chestsort$collectAllItemTagIds() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            Object registry = Registries.ITEM;
            List<String> methodNames = List.of("streamTags", "getTagNames", "getTags", "streamTagKeys", "getTagKeys");
            for (String methodName : methodNames) {
                java.lang.reflect.Method m;
                try {
                    m = registry.getClass().getMethod(methodName);
                } catch (NoSuchMethodException ignored) {
                    continue;
                }

                Object res = m.invoke(registry);
                if (res == null) continue;

                if (res instanceof java.util.stream.Stream<?> stream) {
                    stream.forEach(o -> chestsort$collectTagIdFromUnknown(o, out));
                } else if (res instanceof Iterable<?> it) {
                    for (Object o : it) chestsort$collectTagIdFromUnknown(o, out);
                }

                if (!out.isEmpty()) break;
            }
        } catch (Throwable ignored) {
        }

        ArrayList<String> list = new ArrayList<>(out.size());
        for (String s : out) {
            if (s == null) continue;
            String norm = ContainerFilterSpec.normalizeTagId(s);
            if (!norm.isEmpty()) list.add(norm);
        }
        list.sort(Comparator.naturalOrder());
        return List.copyOf(list);
    }

    private static void chestsort$collectTagIdFromUnknown(Object o, java.util.Set<String> out) {
        if (o == null) return;

        if (o instanceof TagKey<?> tagKey) {
            Identifier id = tagKey.id();
            if (id != null) out.add("#" + id);
            return;
        }

        if (o instanceof Map.Entry<?, ?> e) {
            Object k = e.getKey();
            if (k instanceof TagKey<?> tk) {
                Identifier id = tk.id();
                if (id != null) out.add("#" + id);
                return;
            }
        }

        try {
            for (String methodName : List.of("getKey", "getTag", "key", "tag")) {
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

    private int getLeftRowKind(int rowIndex) {
        // 0 items header, 1 item row, 2 tags header, 3 tag row
        if (rowIndex == 0) return 0;
        int itemsN = this.editingItems.size();
        if (rowIndex >= 1 && rowIndex < 1 + itemsN) return 1;
        if (rowIndex == 1 + itemsN) return 2;
        int tagStart = 2 + itemsN;
        if (rowIndex >= tagStart && rowIndex < tagStart + this.editingTags.size()) return 3;
        return -1;
    }

    private int getLeftRowIndex(int rowIndex) {
        int itemsN = this.editingItems.size();
        if (rowIndex >= 1 && rowIndex < 1 + itemsN) return rowIndex - 1;
        int tagStart = 2 + itemsN;
        if (rowIndex >= tagStart && rowIndex < tagStart + this.editingTags.size()) return rowIndex - tagStart;
        return -1;
    }

    private int getResultsSize() {
        return this.resultItems == null ? 0 : this.resultItems.size();
    }

    private int getResultsRowsShown() {
        return Math.max(1, chestsort$getRowsAreaH() / ROW_H);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Avoid Screen.renderBackground(), which applies the GUI blur effect.
        // In newer Minecraft versions, blur can only be applied once per frame.
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) {
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        int panelW = chestsort$panelW();
        int leftX = chestsort$leftX(panelW);
        int rightX = leftX + panelW + PANEL_GAP;
        int topY = Math.max(10, (this.height - PANEL_H) / 2);

        String headerText = this.presetName.isEmpty() ? "Preset editor" : ("Preset: " + this.presetName);
        context.drawTextWithShadow(tr, Text.literal(headerText), leftX, topY - 10, 0xFFFFFFFF);

        // Panel backgrounds
        context.fill(leftX, topY, leftX + panelW, topY + PANEL_H, 0xAA000000);
        context.fill(rightX, topY, rightX + panelW, topY + PANEL_H, 0xAA000000);

        // Right header
        String rightTitle = this.tagBrowserMode ? ("Tag: " + this.tagBrowserTagId) : "Results";
        context.drawTextWithShadow(tr, Text.literal(rightTitle), rightX + 6, topY + 6 + 24 + 24, 0xFFFFFFFF);

        // Tabs (left header row)
        int tabY1 = topY + 24 + 24;
        int tabY2 = tabY1 + HEADER_H;
        int half = panelW / 2;
        int wlColor = (this.chestsort$tab == TAB_WHITELIST) ? 0xFF303030 : 0xFF202020;
        int blColor = (this.chestsort$tab == TAB_BLACKLIST) ? 0xFF303030 : 0xFF202020;
        context.fill(leftX, tabY1, leftX + half, tabY2, wlColor);
        context.fill(leftX + half, tabY1, leftX + panelW, tabY2, blColor);
        context.drawTextWithShadow(tr, Text.literal("Whitelist"), leftX + 8, tabY1 + 4, 0xFFFFFFFF);
        context.drawTextWithShadow(tr, Text.literal("Blacklist"), leftX + half + 8, tabY1 + 4, 0xFFFFFFFF);

        int rowsY = topY + 24 + 24 + HEADER_H;
        int shownLeft = getLeftRowsShown();
        chestsort$clampLeftScroll();

        // Left rows
        int actionX1Left = (leftX + panelW) - ACTION_PAD - ACTION_W;
        int actionX2Left = (leftX + panelW) - ACTION_PAD;
        for (int visible = 0; visible < shownLeft; visible++) {
            int rowIndex = this.leftScroll + visible;
            int y = rowsY + (visible * ROW_H);
            int kind = getLeftRowKind(rowIndex);
            int idx = getLeftRowIndex(rowIndex);
            if (kind < 0) continue;

            context.fill(leftX + 6, y, leftX + panelW - 6, y + ROW_H - 1, 0xFF202020);

            if (kind == 0) {
                context.drawTextWithShadow(tr, Text.literal("Items (" + this.editingItems.size() + ")"), leftX + 8, y + 5, 0xFFFFFFFF);
                continue;
            }
            if (kind == 2) {
                context.drawTextWithShadow(tr, Text.literal("Tags (" + this.editingTags.size() + ")"), leftX + 8, y + 5, 0xFFFFFFFF);
                continue;
            }

            context.fill(actionX1Left, y, actionX2Left, y + ROW_H - 1, 0xFFFF5555);
            context.drawTextWithShadow(tr, Text.literal("x"), actionX1Left + 4, y + 5, 0xFFFFFFFF);

            if (kind == 1) {
                if (idx < 0 || idx >= this.editingItems.size()) continue;
                String itemId = this.editingItems.get(idx);
                Identifier id = Identifier.tryParse(itemId);
                if (id != null && Registries.ITEM.containsId(id)) {
                    Item item = Registries.ITEM.get(id);
                    context.drawItem(new ItemStack(item), leftX + 8, y + 1);
                    String name = Text.translatable(item.getTranslationKey()).getString();
                    context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(name, actionX1Left - (leftX + 28) - 4)), leftX + 28, y + 2, 0xFFFFFFFF);
                    context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(itemId, actionX1Left - (leftX + 28) - 4)), leftX + 28, y + 10, 0xFF9A9A9A);
                } else {
                    context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(itemId, actionX1Left - (leftX + 8) - 4)), leftX + 8, y + 5, 0xFFFFFFFF);
                }
            } else if (kind == 3) {
                if (idx < 0 || idx >= this.editingTags.size()) continue;
                TagFilterSpec tag = this.editingTags.get(idx);
                String tagId = ContainerFilterSpec.normalizeTagId(tag == null ? "" : tag.tagId());
                Item icon = chestsort$getTagCycleIcon(tagId);
                if (icon != null) {
                    context.drawItem(new ItemStack(icon), leftX + 8, y + 1);
                }

                String name = chestsort$formatTagDisplayName(tagId);
                String sub = tagId;
                int textX = leftX + 28;
                int textW = Math.max(0, actionX1Left - textX - 4);
                context.drawTextWithShadow(tr, Text.literal(textW <= 0 ? name : tr.trimToWidth(name, textW)), textX, y + 2, 0xFFFFFFFF);
                context.drawTextWithShadow(tr, Text.literal(textW <= 0 ? sub : tr.trimToWidth(sub, textW)), textX, y + 10, 0xFF9A9A9A);
            }
        }

        // Right results rows
        int actionX1Right = (rightX + panelW) - ACTION_PAD - ACTION_W;
        int actionX2Right = (rightX + panelW) - ACTION_PAD;
        int shown = Math.min(getResultsRowsShown(), Math.max(0, getResultsSize() - this.resultsScroll));
        for (int i = 0; i < shown; i++) {
            int idx = this.resultsScroll + i;
            int y = rowsY + (i * ROW_H);
            context.fill(rightX + 6, y, rightX + panelW - 6, y + ROW_H - 1, 0xFF202020);

            Item entryItem = this.resultItems.get(idx);
            String entryTagId = idx < this.resultTagIds.size() ? this.resultTagIds.get(idx) : "";
            String subtitle = idx < this.resultSubtitles.size() ? this.resultSubtitles.get(idx) : "";

            boolean isTag = entryItem == null && entryTagId != null && !entryTagId.isEmpty();
            boolean actionIsException = this.tagBrowserMode && !isTag;
            int actionColor = actionIsException ? 0xFFFF5555 : 0xFF55FF55;
            String actionText = actionIsException ? "x" : "+";
            context.fill(actionX1Right, y, actionX2Right, y + ROW_H - 1, actionColor);
            context.drawTextWithShadow(tr, Text.literal(actionText), actionX1Right + 4, y + 5, 0xFFFFFFFF);

            if (isTag) {
                String tagId = ContainerFilterSpec.normalizeTagId(entryTagId);
                Item icon = chestsort$getTagCycleIcon(tagId);
                if (icon != null) {
                    context.drawItem(new ItemStack(icon), rightX + 8, y + 1);
                }

                String name = chestsort$formatTagDisplayName(tagId);
                String sub = subtitle == null || subtitle.isEmpty() ? tagId : subtitle;
                int textX = rightX + 28;
                int textW = Math.max(0, actionX1Right - textX - 4);
                context.drawTextWithShadow(tr, Text.literal(textW <= 0 ? name : tr.trimToWidth(name, textW)), textX, y + 2, 0xFFFFFFFF);
                context.drawTextWithShadow(tr, Text.literal(textW <= 0 ? sub : tr.trimToWidth(sub, textW)), textX, y + 10, 0xFF9A9A9A);
            } else if (entryItem != null) {
                context.drawItem(new ItemStack(entryItem), rightX + 8, y + 1);
                String name = Text.translatable(entryItem.getTranslationKey()).getString();
                context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(name, actionX1Right - (rightX + 28) - 4)), rightX + 28, y + 2, 0xFFFFFFFF);
                context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(subtitle, actionX1Right - (rightX + 28) - 4)), rightX + 28, y + 10, 0xFF9A9A9A);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private static Identifier parseTagIdentifier(String tagId) {
        if (tagId == null) return null;
        String t = tagId.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("#")) t = t.substring(1);
        return Identifier.tryParse(t);
    }

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

    private Item chestsort$getTagCycleIcon(String tagId) {
        Identifier id = parseTagIdentifier(tagId);
        if (id == null) return null;
        List<Item> items = this.tagItemsCache.computeIfAbsent(tagId, k -> computeItemsForTag(id));
        if (items == null || items.isEmpty()) return null;
        long now = System.currentTimeMillis();
        int idx = (int) ((now / 750L) % items.size());
        return items.get(Math.max(0, Math.min(idx, items.size() - 1)));
    }

    private List<Item> computeItemsForTag(Identifier tagIdentifier) {
        TagKey<Item> key = TagKey.of(RegistryKeys.ITEM, tagIdentifier);
        ArrayList<Item> out = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            // Prefer default stack (avoids per-loop explicit new ItemStack(item)).
            if (item.getDefaultStack().isIn(key)) {
                out.add(item);
                if (out.size() >= 200) break;
            }
        }
        return List.copyOf(out);
    }

    private void rebuildTagBrowserItems() {
        Identifier id = parseTagIdentifier(this.tagBrowserTagId);
        if (id == null) {
            this.tagBrowserItems = List.of();
            return;
        }
        List<Item> items = this.tagItemsCache.computeIfAbsent(this.tagBrowserTagId, k -> computeItemsForTag(id));
        this.tagBrowserItems = items == null ? List.of() : items;
    }

    private void addTagException(String itemId) {
        if (itemId == null || itemId.isEmpty()) return;
        String tagId = ContainerFilterSpec.normalizeTagId(this.tagBrowserTagId);
        if (tagId.isEmpty()) return;

        for (int i = 0; i < this.editingTags.size(); i++) {
            TagFilterSpec tag = this.editingTags.get(i);
            if (tag == null) continue;
            if (!ContainerFilterSpec.normalizeTagId(tag.tagId()).equals(tagId)) continue;

            LinkedHashSet<String> exc = new LinkedHashSet<>();
            if (tag.exceptions() != null) {
                for (String e : tag.exceptions()) {
                    if (e != null && !e.trim().isEmpty()) exc.add(e.trim());
                }
            }
            if (exc.add(itemId)) {
                this.editingTags.set(i, new TagFilterSpec(tagId, List.copyOf(exc)));
                updateSearchResults();
            }
            return;
        }
    }

    private java.util.Set<String> getExceptionSetForTag(String tagIdRaw) {
        String tagId = ContainerFilterSpec.normalizeTagId(tagIdRaw);
        if (tagId.isEmpty()) return java.util.Set.of();
        for (TagFilterSpec tag : this.editingTags) {
            if (tag == null) continue;
            if (!ContainerFilterSpec.normalizeTagId(tag.tagId()).equals(tagId)) continue;
            if (tag.exceptions() == null || tag.exceptions().isEmpty()) return java.util.Set.of();
            HashSet<String> out = new HashSet<>(tag.exceptions().size());
            for (String e : tag.exceptions()) {
                if (e == null) continue;
                String s = e.trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
        return java.util.Set.of();
    }
}
