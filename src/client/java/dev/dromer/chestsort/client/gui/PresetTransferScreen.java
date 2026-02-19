package dev.dromer.chestsort.client.gui;

import java.nio.charset.StandardCharsets;
import dev.dromer.chestsort.util.Cs2StringCodec;
import java.util.Base64;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.dromer.chestsort.client.ClientPresetRegistry;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.net.payload.ImportPresetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class PresetTransferScreen extends Screen {
    public enum Mode {
        IMPORT,
        EXPORT
    }

    private static final String PREFIX = "cs2:";

    private final Mode mode;
    private final String exportPresetName;

    private TextFieldWidget field;
    private ButtonWidget primaryButton;
    private ButtonWidget secondaryButton;

    private String error = "";

    private record ComputeResult(String text, String error) {
    }

    public PresetTransferScreen(Mode mode, String presetName) {
        super(Text.literal(mode == Mode.IMPORT ? "Import preset" : "Export preset"));
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

            // Computing + base64 encoding can be expensive for large presets; do it off-thread.
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
        ClientPlayNetworking.send(new ImportPresetPayload(raw));
        this.close();
    }

    private ComputeResult computeExport() {
        if (this.exportPresetName.isEmpty()) {
            return new ComputeResult("", "No preset name");
        }
        try {
            ContainerFilterSpec spec = ClientPresetRegistry.get(this.exportPresetName);
            if (spec == null || spec.isEmpty()) {
                return new ComputeResult("", "No preset / empty");
            }
            return new ComputeResult(encode(this.exportPresetName, spec), "");
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || msg.isEmpty()) msg = t.getClass().getSimpleName();
            return new ComputeResult("", msg);
        }
    }

    private static String encode(String presetName, ContainerFilterSpec spec) {
        // Keep PREFIX constant for UI text, but use the shared codec.
        return Cs2StringCodec.encodePreset(presetName, spec);
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
            : (this.exportPresetName.isEmpty() ? "Export preset" : ("Export preset: " + this.exportPresetName));

        context.drawTextWithShadow(this.textRenderer, Text.literal(title), x, y - 2, 0xFFFFFFFF);

        if (this.mode == Mode.IMPORT) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Paste a cs2: string"), x, y + 12, 0xFFAAAAAA);
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
