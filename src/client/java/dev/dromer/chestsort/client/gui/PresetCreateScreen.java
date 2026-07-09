package dev.dromer.chestsort.client.gui;

import java.util.ArrayList;
import java.util.List;

import dev.dromer.chestsort.client.ClientPresetRegistry;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Creates a named preset directly from a pasted list of items/tags, without the chat text
 * length limit that /cs presets create <name> <items> ran into.
 */
public final class PresetCreateScreen extends Screen {
    private EditBox nameField;
    private EditBox itemsField;
    private Button createButton;
    private Button cancelButton;

    private String error = "";

    public PresetCreateScreen() {
        super(Component.literal("Create preset"));
    }

    @Override
    protected void init() {
        this.error = "";

        int boxW = Math.min(360, Math.max(200, this.width - 40));
        int x = (this.width - boxW) / 2;
        int y = Math.max(20, (this.height - 140) / 2);

        this.nameField = new EditBox(this.font, x, y + 16, boxW, 20, Component.literal("Name"));
        this.nameField.setMaxLength(64);
        this.nameField.setFocused(true);
        this.addRenderableWidget(this.nameField);

        this.itemsField = new EditBox(this.font, x, y + 52, boxW, 20, Component.literal("Items"));
        this.itemsField.setMaxLength(32767);
        this.addRenderableWidget(this.itemsField);

        this.createButton = Button.builder(Component.literal("Create"), b -> tryCreate())
            .bounds(x, y + 84, (boxW - 6) / 2, 20)
            .build();
        this.addRenderableWidget(this.createButton);

        this.cancelButton = Button.builder(Component.literal("Cancel"), b -> this.onClose())
            .bounds(x + (boxW - 6) / 2 + 6, y + 84, (boxW - 6) / 2, 20)
            .build();
        this.addRenderableWidget(this.cancelButton);
    }

    private void tryCreate() {
        String name = this.nameField == null ? "" : this.nameField.getValue().trim();
        if (name.isEmpty()) {
            this.error = "Name cannot be empty";
            return;
        }
        if (ClientPresetRegistry.get(name) != null) {
            this.error = "Preset already exists: " + name;
            return;
        }

        String itemsRaw = this.itemsField == null ? "" : this.itemsField.getValue();
        List<String> items = new ArrayList<>();
        List<TagFilterSpec> tags = new ArrayList<>();
        String[] tokens = itemsRaw.split("[,\\s]+");
        int skipped = 0;
        for (String tok : tokens) {
            if (tok == null) continue;
            String t = tok.trim();
            if (t.isEmpty()) continue;

            if (t.startsWith("#")) {
                Identifier tagId = parseTagIdentifier(t);
                if (tagId != null) {
                    tags.add(new TagFilterSpec(tagId.toString(), List.of()));
                } else {
                    skipped++;
                }
            } else {
                Identifier id = parseItemIdentifier(t);
                if (id != null) {
                    items.add(id.toString());
                } else {
                    skipped++;
                }
            }
        }

        ContainerFilterSpec spec = new ContainerFilterSpec(items, tags, List.of()).normalized();
        ClientPresetRegistry.putLocal(name, spec);

        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal("[ChestSort] Created preset: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + items.size() + " item(s), " + tags.size() + " tag(s))").withStyle(ChatFormatting.GRAY)));
            if (skipped > 0) {
                player.sendSystemMessage(Component.literal("[ChestSort] Skipped " + skipped + " unrecognized entr" + (skipped == 1 ? "y" : "ies")).withStyle(ChatFormatting.GRAY));
            }
        }

        Minecraft.getInstance().gui.setScreen(new PresetEditorScreen(name));
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

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        if (this.nameField != null && this.nameField.isFocused() && this.nameField.keyPressed(event)) {
            return true;
        }
        if (this.itemsField != null && this.itemsField.isFocused() && this.itemsField.keyPressed(event)) {
            return true;
        }

        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            tryCreate();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        int boxW = Math.min(360, Math.max(200, this.width - 40));
        int x = (this.width - boxW) / 2;
        int y = Math.max(20, (this.height - 140) / 2);

        context.fill(x - 8, y - 12, x + boxW + 8, y + 112, 0xAA000000);

        context.text(this.font, Component.literal("Create preset"), x, y - 2, 0xFFFFFFFF);
        context.text(this.font, Component.literal("Name"), x, y + 6, 0xFFAAAAAA);
        context.text(this.font, Component.literal("Items (comma/space separated, #tag for tags)"), x, y + 42, 0xFFAAAAAA);

        if (this.error != null && !this.error.isEmpty()) {
            context.text(this.font, Component.literal(this.error).withStyle(ChatFormatting.RED), x, y + 74, 0xFFFFFFFF);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
