package dev.dromer.chestsort.client.gui;

import dev.dromer.chestsort.client.ClientPresetRegistry;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.net.payload.ImportPresetPayload;
import dev.dromer.chestsort.util.Cs2StringCodec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Checkbox-based preset list import/export helper screen. */
public final class PresetListTransferScreen extends Screen {
    public enum Mode {
        EXPORT_SELECT,
        IMPORT_LIST
    }

    private static final int ROW_H = 22;
    private static final int BOX_SIZE = 12;
    private static final int CONFLICT_LABEL_W = 64;

    private final Mode mode;
    private final List<Entry> entries;

    private int scrollY = 0;

    private TextFieldWidget outputField;
    private ButtonWidget primary;
    private ButtonWidget secondary;

    private String error = "";

    private static final class Entry {
        final String originalName;
        final ContainerFilterSpec whitelist;
        final ContainerFilterSpec blacklist;
        boolean selected;
        TextFieldWidget nameField; // import only

        Entry(String originalName, ContainerFilterSpec whitelist, ContainerFilterSpec blacklist, boolean selected) {
            this.originalName = originalName == null ? "" : originalName;
            this.whitelist = whitelist;
            this.blacklist = blacklist;
            this.selected = selected;
        }

        String desiredName() {
            if (nameField == null) return originalName.trim();
            String t = nameField.getText();
            return t == null ? "" : t.trim();
        }
    }

    private PresetListTransferScreen(Mode mode, List<Entry> entries) {
        super(Text.literal(mode == Mode.EXPORT_SELECT ? "Export selected presets" : "Import preset list"));
        this.mode = mode == null ? Mode.EXPORT_SELECT : mode;
        this.entries = entries == null ? List.of() : entries;
    }

    public static PresetListTransferScreen forExportSelect() {
        Map<String, ContainerFilterSpec> allWl = ClientPresetRegistry.all();
        Map<String, ContainerFilterSpec> allBl = ClientPresetRegistry.allBlacklists();
        ArrayList<Entry> out = new ArrayList<>();
        for (String name : ClientPresetRegistry.namesSorted()) {
            if (name == null) continue;
            String n = name.trim();
            if (n.isEmpty()) continue;
            ContainerFilterSpec wl = allWl == null ? null : allWl.get(n);
            ContainerFilterSpec bl = allBl == null ? null : allBl.get(n);
            if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) continue;
            out.add(new Entry(n,
                wl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl,
                bl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : bl,
                true));
        }
        return new PresetListTransferScreen(Mode.EXPORT_SELECT, out);
    }

    public static PresetListTransferScreen forImportList(String rawPresetList) {
        Cs2StringCodec.DecodedPresetList decoded = Cs2StringCodec.decodePresetList(rawPresetList);
        LinkedHashMap<String, ContainerFilterSpec> whitelists = decoded == null ? new LinkedHashMap<>() : new LinkedHashMap<>(decoded.whitelists());
        Map<String, ContainerFilterSpec> blacklists = decoded == null ? Map.of() : decoded.blacklists();
        ArrayList<Entry> out = new ArrayList<>();
        for (var e : whitelists.entrySet()) {
            if (e == null) continue;
            String name = e.getKey() == null ? "" : e.getKey().trim();
            if (name.isEmpty()) continue;
            ContainerFilterSpec wl = e.getValue();
            ContainerFilterSpec bl = blacklists == null ? null : blacklists.get(name);
            if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) continue;
            out.add(new Entry(name,
                wl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl,
                bl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : bl,
                true));
        }
        return new PresetListTransferScreen(Mode.IMPORT_LIST, out);
    }

    @Override
    protected void init() {
        this.error = "";

        int boxW = Math.min(420, Math.max(260, this.width - 40));
        int x = (this.width - boxW) / 2;
        int y = 24;

        int listTop = y + 16;
        int listH = Math.max(80, this.height - (listTop + 74));

        // Output field (export only)
        this.outputField = new TextFieldWidget(this.textRenderer, x, listTop + listH + 10, boxW, 20, Text.literal(""));
        this.outputField.setMaxLength(32767);
        this.outputField.setEditable(false);
        this.outputField.setText("");
        this.addDrawableChild(this.outputField);

        if (this.mode == Mode.IMPORT_LIST) {
            this.outputField.visible = false;
        }

        // Per-entry fields (import only)
        if (this.mode == Mode.IMPORT_LIST) {
            for (Entry e : this.entries) {
                TextFieldWidget tf = new TextFieldWidget(this.textRenderer, x + 44, 0, Math.max(0, boxW - 60 - CONFLICT_LABEL_W), 18, Text.literal(""));
                tf.setMaxLength(64);
                tf.setText(e.originalName);
                e.nameField = tf;
                this.addDrawableChild(tf);
            }
        }

        int btnY = this.height - 34;
        if (this.mode == Mode.EXPORT_SELECT) {
            this.primary = ButtonWidget.builder(Text.literal("Generate"), b -> generateExport())
                .dimensions(x, btnY, (boxW - 6) / 2, 20)
                .build();
            this.secondary = ButtonWidget.builder(Text.literal("Close"), b -> this.close())
                .dimensions(x + (boxW - 6) / 2 + 6, btnY, (boxW - 6) / 2, 20)
                .build();
        } else {
            this.primary = ButtonWidget.builder(Text.literal("Import"), b -> tryImportSelected())
                .dimensions(x, btnY, (boxW - 6) / 2, 20)
                .build();
            this.secondary = ButtonWidget.builder(Text.literal("Cancel"), b -> this.close())
                .dimensions(x + (boxW - 6) / 2 + 6, btnY, (boxW - 6) / 2, 20)
                .build();
        }
        this.addDrawableChild(this.primary);
        this.addDrawableChild(this.secondary);

        clampScroll(listTop, listH);
        updateEntryWidgetPositions(x, listTop, boxW, listH);
        updatePrimaryEnabled();
    }

    private void generateExport() {
        try {
            LinkedHashMap<String, ContainerFilterSpec> selectedWl = new LinkedHashMap<>();
            LinkedHashMap<String, ContainerFilterSpec> selectedBl = new LinkedHashMap<>();
            for (Entry e : this.entries) {
                if (e == null || !e.selected) continue;
                String n = e.originalName == null ? "" : e.originalName.trim();
                if (n.isEmpty()) continue;
                ContainerFilterSpec wl = e.whitelist;
                ContainerFilterSpec bl = e.blacklist;
                if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) continue;
                selectedWl.put(n, wl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl);
                if (bl != null && !bl.isEmpty()) selectedBl.put(n, bl);
            }
            if (selectedWl.isEmpty()) {
                this.error = "No presets selected";
                if (this.outputField != null) this.outputField.setText("");
                return;
            }
            String s = Cs2StringCodec.encodePresetList(selectedWl, selectedBl);
            if (this.outputField != null) this.outputField.setText(s);
            this.error = "";
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || msg.isEmpty()) msg = t.getClass().getSimpleName();
            this.error = msg;
        }
    }

    private void tryImportSelected() {
        this.error = "";

        LinkedHashMap<String, ContainerFilterSpec> whitelists = new LinkedHashMap<>();
        LinkedHashMap<String, ContainerFilterSpec> blacklists = new LinkedHashMap<>();

        // Validate uniqueness against existing presets and within selection.
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        java.util.HashSet<String> existing = new java.util.HashSet<>();
        for (String n : ClientPresetRegistry.namesSorted()) {
            if (n == null) continue;
            String t = n.trim();
            if (!t.isEmpty()) existing.add(t.toLowerCase());
        }

        for (Entry e : this.entries) {
            if (e == null || !e.selected) continue;
            String desired = e.desiredName();
            if (desired.isEmpty()) {
                this.error = "Preset name cannot be empty";
                return;
            }
            String key = desired.toLowerCase();
            if (existing.contains(key)) {
                this.error = "Duplicate preset exists: " + desired;
                return;
            }
            if (seen.contains(key)) {
                this.error = "Duplicate name in selection: " + desired;
                return;
            }
            seen.add(key);

            ContainerFilterSpec wl = e.whitelist;
            ContainerFilterSpec bl = e.blacklist;
            if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) continue;
            whitelists.put(desired, wl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl);
            if (bl != null && !bl.isEmpty()) blacklists.put(desired, bl);
        }

        if (whitelists.isEmpty()) {
            this.error = "Nothing selected";
            return;
        }

        String encoded = Cs2StringCodec.encodePresetList(whitelists, blacklists);
        ClientPlayNetworking.send(new ImportPresetPayload(encoded));
        this.close();
    }

    private void updatePrimaryEnabled() {
        if (this.primary == null) return;
        if (this.mode == Mode.EXPORT_SELECT) {
            boolean any = false;
            for (Entry e : this.entries) {
                if (e != null && e.selected) {
                    any = true;
                    break;
                }
            }
            this.primary.active = any;
            return;
        }

        // Import mode: enable if at least one selected and no obvious empty names.
        boolean any = false;
        boolean ok = true;
        for (Entry e : this.entries) {
            if (e == null || !e.selected) continue;
            any = true;
            if (e.desiredName().isEmpty()) {
                ok = false;
                break;
            }
        }
        this.primary.active = any && ok;
    }

    private int listContentHeight() {
        return Math.max(0, this.entries.size() * ROW_H);
    }

    private void clampScroll(int listTop, int listH) {
        int max = Math.max(0, listContentHeight() - listH);
        if (this.scrollY < 0) this.scrollY = 0;
        if (this.scrollY > max) this.scrollY = max;

        // Hide focus when scrolled out.
        if (this.mode == Mode.IMPORT_LIST) {
            for (int i = 0; i < this.entries.size(); i++) {
                Entry e = this.entries.get(i);
                if (e == null || e.nameField == null) continue;
                int rowY = listTop + (i * ROW_H) - this.scrollY;
                boolean visible = rowY + ROW_H >= listTop && rowY <= listTop + listH;
                e.nameField.visible = visible;
            }
        }
    }

    private void updateEntryWidgetPositions(int x, int listTop, int boxW, int listH) {
        if (this.mode != Mode.IMPORT_LIST) return;

        for (int i = 0; i < this.entries.size(); i++) {
            Entry e = this.entries.get(i);
            if (e == null || e.nameField == null) continue;

            int rowY = listTop + i * ROW_H - this.scrollY;
            boolean visible = rowY + ROW_H >= listTop && rowY <= listTop + listH;

            e.nameField.setX(x + 44);
            e.nameField.setY(rowY + 2);
            e.nameField.setWidth(Math.max(0, boxW - 60 - CONFLICT_LABEL_W));
            e.nameField.visible = visible;
        }

        updatePrimaryEnabled();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int boxW = Math.min(420, Math.max(260, this.width - 40));
        int x = (this.width - boxW) / 2;
        int y = 24;
        int listTop = y + 16;
        int listH = Math.max(80, this.height - (listTop + 74));

        this.scrollY -= (int) (verticalAmount * 12);
        clampScroll(listTop, listH);
        updateEntryWidgetPositions(x, listTop, boxW, listH);
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean bl) {
        int button = click.button();
        double mouseX = click.x();
        double mouseY = click.y();
        if (button != 0) return super.mouseClicked(click, bl);

        int boxW = Math.min(420, Math.max(260, this.width - 40));
        int x = (this.width - boxW) / 2;
        int y = 24;
        int listTop = y + 16;
        int listH = Math.max(80, this.height - (listTop + 74));

        // Toggle checkbox clicks in list area.
        if (mouseX >= x && mouseX <= x + boxW && mouseY >= listTop && mouseY <= listTop + listH) {
            int rel = (int) mouseY - listTop + this.scrollY;
            int idx = rel / ROW_H;
            if (idx >= 0 && idx < this.entries.size()) {
                Entry e = this.entries.get(idx);
                if (e != null) {
                    int rowY = listTop + idx * ROW_H - this.scrollY;
                    int cbX = x + 8;
                    int cbY = rowY + 5;
                    if (mouseX >= cbX && mouseX <= cbX + BOX_SIZE && mouseY >= cbY && mouseY <= cbY + BOX_SIZE) {
                        e.selected = !e.selected;
                        updatePrimaryEnabled();
                        return true;
                    }
                }
            }
        }

        boolean handled = super.mouseClicked(click, bl);
        updatePrimaryEnabled();
        return handled;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null && mc.options.backKey != null && mc.options.backKey.matchesKey(keyInput)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        int boxW = Math.min(420, Math.max(260, this.width - 40));
        int x = (this.width - boxW) / 2;
        int y = 24;

        int listTop = y + 16;
        int listH = Math.max(80, this.height - (listTop + 74));
        int listBottom = listTop + listH;

        context.fill(x - 8, y - 12, x + boxW + 8, this.height - 16, 0xAA000000);

        String screenTitle = this.mode == Mode.EXPORT_SELECT ? "Export selected presets" : "Import preset list";
        context.drawTextWithShadow(this.textRenderer, Text.literal(screenTitle), x, y - 2, 0xFFFFFFFF);

        context.drawTextWithShadow(this.textRenderer,
            Text.literal(this.mode == Mode.EXPORT_SELECT ? "Check presets to include" : "Check presets to import, rename duplicates"),
            x,
            y + 10,
            0xFFAAAAAA
        );

        // List background
        context.fill(x, listTop, x + boxW, listBottom, 0x66000000);

        // Clip drawing to list area
        context.enableScissor(x, listTop, x + boxW, listBottom);

        java.util.HashSet<String> existingLower = new java.util.HashSet<>();
        if (this.mode == Mode.IMPORT_LIST) {
            for (String n : ClientPresetRegistry.namesSorted()) {
                if (n == null) continue;
                String t = n.trim();
                if (!t.isEmpty()) existingLower.add(t.toLowerCase());
            }
        }

        java.util.HashMap<String, Integer> countsLower = new java.util.HashMap<>();
        if (this.mode == Mode.IMPORT_LIST) {
            for (Entry e : this.entries) {
                if (e == null || !e.selected) continue;
                String key = e.desiredName().toLowerCase();
                if (key.isEmpty()) continue;
                countsLower.put(key, countsLower.getOrDefault(key, 0) + 1);
            }
        }

        for (int i = 0; i < this.entries.size(); i++) {
            Entry e = this.entries.get(i);
            if (e == null) continue;

            int rowY = listTop + i * ROW_H - this.scrollY;
            if (rowY > listBottom || rowY + ROW_H < listTop) continue;

            int cbX = x + 8;
            int cbY = rowY + 5;

            // checkbox
            int border = 0xFFCCCCCC;
            int fill = 0xFF222222;
            context.fill(cbX, cbY, cbX + BOX_SIZE, cbY + BOX_SIZE, border);
            context.fill(cbX + 1, cbY + 1, cbX + BOX_SIZE - 1, cbY + BOX_SIZE - 1, fill);
            if (e.selected) {
                context.drawTextWithShadow(this.textRenderer, Text.literal("x"), cbX + 4, cbY + 2, 0xFFFFFFFF);
            }

            if (this.mode == Mode.EXPORT_SELECT) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(e.originalName), x + 28, rowY + 7, 0xFFFFFF55);
            } else {
                // Name field is a widget; just render conflict markers.
                String desired = e.desiredName();
                boolean conflictExisting = !desired.isEmpty() && existingLower.contains(desired.toLowerCase());
                boolean conflictSelection = !desired.isEmpty() && countsLower.getOrDefault(desired.toLowerCase(), 0) > 1;

                if (e.selected && (conflictExisting || conflictSelection)) {
                    String msg = conflictExisting ? "(exists)" : "(duplicate)";
                    context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(msg).formatted(Formatting.RED),
                        x + boxW - CONFLICT_LABEL_W + 4,
                        rowY + 7,
                        0xFFFFFFFF
                    );
                }
            }
        }

        context.disableScissor();

        // Output field label
        if (this.mode == Mode.EXPORT_SELECT) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Copy from box:"), x, listBottom + 2, 0xFFAAAAAA);
        }

        // Error text
        if (this.error != null && !this.error.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(this.error).formatted(Formatting.RED), x, this.height - 58, 0xFFFFFFFF);
        }

        updateEntryWidgetPositions(x, listTop, boxW, listH);

        super.render(context, mouseX, mouseY, delta);
    }
}
