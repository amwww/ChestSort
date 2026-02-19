package dev.dromer.chestsort;

import net.fabricmc.api.ModInitializer;
import dev.dromer.chestsort.command.ChestSortCommands;
import dev.dromer.chestsort.net.ChestSortNetworking;

public class Chestsort implements ModInitializer {

    public static final String MOD_ID = "chestsort";

    @Override
    public void onInitialize() {
        ChestSortNetworking.init();
        ChestSortCommands.register();
    }
}
