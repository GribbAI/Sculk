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

        var camera = mc.gameRenderer.getCamera();
        Vec3d cam = null;
        try {
            cam = camera.getCameraPos();
        } catch (NoSuchMethodError ignored) { }
        if (cam == null) {
            if (camera.getFocusedEntity() != null) {
                var focused = camera.getFocusedEntity();
                cam = new Vec3d(focused.getX(), focused.getY(), focused.getZ());
            } else {
                cam = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            }
        }

        final double camX = cam.x;
        final double camY = cam.y;
        final double camZ = cam.z;

        List<PlayerEntity> list = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && !p.isSpectator())
                .sorted((a, b) -> Double.compare(
                        Utils.distanceSq(camX, camY, camZ, a.getX(), a.getY(), a.getZ()),
                        Utils.distanceSq(camX, camY, camZ, b.getX(), b.getY(), b.getZ())
                ))
                .limit(Config.nearestCount)
                .collect(Collectors.toList());

        synchronized (Config.tracked) {
            Config.tracked.clear();
            for (PlayerEntity p : list) Config.tracked.add(p.getUuidAsString());
        }
    }

}
