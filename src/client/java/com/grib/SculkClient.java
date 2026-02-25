package com.grib.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class SculkClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Keybinds.register();
        Tracker.init();
        Renderer.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null) return;
            InputHandler.handleKeys(client);
            Tracker.maybeAutoTrack();
        });

        Renderer.registerHud();
    }
}
