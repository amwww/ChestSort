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

import java.util.List;

public final class PresetTransferScreen extends Screen {
    public enum Mode {
        IMPORT,
        EXPORT,
        EXPORT_ALL
    }

    private static final String PREFIX = "cs2|";

    private final Mode mode;
    private final String exportPresetName;

    private TextFieldWidget field;
    private ButtonWidget primaryButton;
    private ButtonWidget secondaryButton;

    private String error = "";

    private record ComputeResult(String text, String error) {
    }

    public PresetTransferScreen(Mode mode, String presetName) {
        super(Text.literal(mode == Mode.IMPORT ? "Import preset" : (mode == Mode.EXPORT_ALL ? "Export all presets" : "Export preset")));
        this.mode = mode == null ? Mode.IMPORT : mode;
        this.exportPresetName = presetName == null ? "" : presetName.trim();
    }

    @Override
    protected void init() {
        this.error = "";

        int boxW = Math.min(320, Math.max(180, this.width - 40));
        int x = (this.width - boxW) / 2;
        int y = Math.max(20, (this.height - 120) / 2);

        this.field = new TextFieldWidget(this.textRenderer, x, y + 28, boxW, 20, Text.literal(""));
        this.field.setMaxLength(32767);
        this.addDrawableChild(this.field);

        if (this.mode == Mode.IMPORT) {
            this.field.setEditable(true);
            this.field.setText("");
            this.field.setFocused(true);

            this.primaryButton = ButtonWidget.builder(Text.literal("Import"), b -> tryImport())
                .dimensions(x, y + 56, (boxW - 6) / 2, 20)
                .build();
            this.secondaryButton = ButtonWidget.builder(Text.literal("Cancel"), b -> this.close())
                .dimensions(x + (boxW - 6) / 2 + 6, y + 56, (boxW - 6) / 2, 20)
                .build();
            this.addDrawableChild(this.primaryButton);
            this.addDrawableChild(this.secondaryButton);
        } else {
            this.field.setEditable(false);
            this.field.setFocused(true);

            // Computing exports can be expensive for large presets; do it off-thread.
            this.field.setText("Loading...");

            Thread t = new Thread(() -> {
                ComputeResult r = computeExport();
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null) return;
                mc.execute(() -> {
                    this.error = (r == null || r.error == null) ? "" : r.error;
                    if (this.field != null) {
                        String text = (r == null || r.text == null) ? "" : r.text;
                        // Keep UI responsive and avoid pathological giant strings.
                        if (text.length() > 32767) {
                            this.error = (this.error == null || this.error.isEmpty())
                                ? ("Export too large to display (len=" + text.length() + ")")
                                : this.error;
                            text = text.substring(0, 32767);
                        }
                        this.field.setText(text);
                    }
                });
            }, "ChestSort preset export");
            t.setDaemon(true);
            t.start();

            this.primaryButton = ButtonWidget.builder(Text.literal("Close"), b -> this.close())
                .dimensions(x, y + 56, boxW, 20)
                .build();
            this.addDrawableChild(this.primaryButton);
        }
    }

    private void tryImport() {
        String raw = this.field == null ? "" : this.field.getText();
        if (raw == null || raw.trim().isEmpty()) {
            this.error = "empty";
            return;
        }

        // If this is a presetList, offer a selection + rename UI.
        try {
            var decoded = Cs2StringCodec.decodePresetList(raw);
            if (decoded != null && decoded.whitelists() != null && !decoded.whitelists().isEmpty()) {
                MinecraftClient.getInstance().setScreen(PresetListTransferScreen.forImportList(raw));
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // Not a presetList; fall through to single preset import.
        }

        ClientPlayNetworking.send(new ImportPresetPayload(raw));
        this.close();
    }

    private ComputeResult computeExport() {
        if (this.mode == Mode.EXPORT_ALL) {
            try {
                var allWl = ClientPresetRegistry.all();
                var allBl = ClientPresetRegistry.allBlacklists();
                if ((allWl == null || allWl.isEmpty()) && (allBl == null || allBl.isEmpty())) {
                    return new ComputeResult("", "No presets");
                }

                java.util.LinkedHashMap<String, ContainerFilterSpec> orderedWl = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, ContainerFilterSpec> orderedBl = new java.util.LinkedHashMap<>();
                for (String name : ClientPresetRegistry.namesSorted()) {
                    if (name == null) continue;
                    String n = name.trim();
                    if (n.isEmpty()) continue;

                    ContainerFilterSpec wl = allWl == null ? null : allWl.get(n);
                    ContainerFilterSpec bl = allBl == null ? null : allBl.get(n);
                    if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) continue;

                    orderedWl.put(n, wl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl);
                    if (bl != null && !bl.isEmpty()) {
                        orderedBl.put(n, bl);
                    }
                }

                if (orderedWl.isEmpty()) {
                    return new ComputeResult("", "No presets");
                }

                return new ComputeResult(Cs2StringCodec.encodePresetList(orderedWl, orderedBl), "");
            } catch (Throwable t) {
                String msg = t.getMessage();
                if (msg == null || msg.isEmpty()) msg = t.getClass().getSimpleName();
                return new ComputeResult("", msg);
            }
        }

        if (this.exportPresetName.isEmpty()) {
            return new ComputeResult("", "No preset name");
        }
        try {
            ContainerFilterSpec wl = ClientPresetRegistry.get(this.exportPresetName);
            ContainerFilterSpec bl = ClientPresetRegistry.getBlacklist(this.exportPresetName);
            if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) {
                return new ComputeResult("", "No preset / empty");
            }
            ContainerFilterSpec safeWl = wl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl;
            ContainerFilterSpec safeBl = bl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : bl;
            return new ComputeResult(encode(this.exportPresetName, safeWl, safeBl), "");
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || msg.isEmpty()) msg = t.getClass().getSimpleName();
            return new ComputeResult("", msg);
        }
    }

    private static String encode(String presetName, ContainerFilterSpec whitelist, ContainerFilterSpec blacklist) {
        // Keep PREFIX constant for UI text, but use the shared codec.
        return Cs2StringCodec.encodePreset(presetName, whitelist, blacklist);
    }

    @SuppressWarnings("unused")
    private static ContainerFilterSpec decode(String raw) {
        return Cs2StringCodec.decodeSpec(raw);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        MinecraftClient client = MinecraftClient.getInstance();

        boolean focused = this.field != null && this.field.visible && this.field.isFocused();
        if (focused && this.field.keyPressed(keyInput)) {
            return true;
        }

        if (client != null && client.options != null && client.options.backKey != null && client.options.backKey.matchesKey(keyInput)) {
            this.close();
            return true;
        }

        if (this.mode == Mode.IMPORT && (keyInput.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyInput.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            tryImport();
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Avoid Screen.renderBackground(), which applies the GUI blur effect.
        // In newer Minecraft versions, blur can only be applied once per frame.
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        int boxW = Math.min(320, Math.max(180, this.width - 40));
        int x = (this.width - boxW) / 2;
        int y = Math.max(20, (this.height - 120) / 2);

        context.fill(x - 8, y - 12, x + boxW + 8, y + 92, 0xAA000000);

        String title = this.mode == Mode.IMPORT
            ? "Import preset"
            : (this.mode == Mode.EXPORT_ALL
                ? "Export all presets"
                : (this.exportPresetName.isEmpty() ? "Export preset" : ("Export preset: " + this.exportPresetName)));

        context.drawTextWithShadow(this.textRenderer, Text.literal(title), x, y - 2, 0xFFFFFFFF);

        if (this.mode == Mode.IMPORT) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Paste cs2|preset/<name>| or cs2|presetList|"), x, y + 12, 0xFFAAAAAA);
        } else {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Select + copy from box"), x, y + 12, 0xFFAAAAAA);
        }

        if (this.error != null && !this.error.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(this.error).formatted(Formatting.RED), x, y + 80, 0xFFFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }
}
