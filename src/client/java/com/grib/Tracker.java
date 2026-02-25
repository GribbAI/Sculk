package com.grib.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;
import java.util.stream.Collectors;

public class Tracker {

    public static void init() {
        // meow
    }

    public static void maybeAutoTrack() {
        if (!Config.autoTrackNearest) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        List<PlayerEntity> list = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && !p.isSpectator())
                .sorted((a,b) -> Double.compare(Utils.distanceSq(cam.x, cam.y, cam.z, a.getX(), a.getY(), a.getZ()),
                                                   Utils.distanceSq(cam.x, cam.y, cam.z, b.getX(), b.getY(), b.getZ())))
                .limit(Config.nearestCount)
                .collect(Collectors.toList());
        synchronized (Config.tracked) {
            Config.tracked.clear();
            for (PlayerEntity p : list) Config.tracked.add(p.getUuidAsString());
        }
    }

}
