package dev.dromer.chestsort.mixin.client;

import dev.dromer.chestsort.client.ClientHighlightState;
import dev.dromer.chestsort.client.ClientContainerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void chestsort$setScreen(Screen screen, CallbackInfo ci) {
        if (screen == null) {
            ClientHighlightState.clear();
            ClientContainerContext.clear();
            return;
        }

        // Leaving a handled screen should also clear container context.
        if (!(screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) {
            ClientContainerContext.clear();
        }
    }
}
