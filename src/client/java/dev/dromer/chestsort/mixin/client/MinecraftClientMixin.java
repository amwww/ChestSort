package dev.dromer.chestsort.mixin.client;

import dev.dromer.chestsort.client.ClientHighlightState;
import dev.dromer.chestsort.client.ClientContainerContext;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MinecraftClientMixin {

    @Inject(
            method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("HEAD")
    )
    private void chestsort$setScreen(Screen screen, CallbackInfo ci) {
        if (screen == null) {
            ClientHighlightState.clear();
            ClientContainerContext.clear();
            return;
        }

        // Leaving all container screens should clear container context. Re-entrant setScreen
        // calls for the currently open (or any) container screen must NOT clear it, since Gui
        // can re-invoke setScreen with the same/another container screen mid-session (e.g. on
        // menu resync) without the server re-sending ContainerContext in between.
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) {
            ClientContainerContext.clear();
        }
    }
}
