package com.grib.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class InputHandler {
    public static void handleKeys(MinecraftClient client) {
        if (Keybinds.TOGGLE.wasPressed()) {
            Config.enabled = !Config.enabled;
            if (client.player != null) {
                Text state = Text.translatable(Config.enabled ? "sculk.on" : "sculk.off");
                client.player.sendMessage(Text.translatable("sculk.msg.toggle", state), true);
            }
        }

        if (Keybinds.OPEN_MENU.wasPressed()) {
            client.execute(() -> {
                if (client.currentScreen == null) client.setScreen(new PlayerSelectScreen());
            });
        }
    }
}