package com.grib.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Renderer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void init() {}

    public static void registerHud() {
        HudRenderCallback.EVENT.register(new HudRenderCallback() {
            @Override
            public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
                if (!Config.enabled) return;
                if (mc.world == null || mc.player == null) return;

                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();
                int cx = sw/2; int cy = sh/2;

                Vec3d cam = mc.gameRenderer.getCamera().getPos();
                double camX = cam.x; double camY = cam.y; double camZ = cam.z;
                double yaw = mc.gameRenderer.getCamera().getYaw();
                double pitch = mc.gameRenderer.getCamera().getPitch();

                List<PlayerEntity> players = mc.world.getPlayers().stream().filter(p->p!=mc.player && !p.isSpectator()).collect(Collectors.toList());

                List<PlayerEntity> toRender;
                synchronized (Config.tracked) {
                    if (Config.displayMode == Config.Mode.TRACKED_ONLY) {
                        toRender = players.stream().filter(p -> Config.tracked.contains(p.getUuidAsString())).collect(Collectors.toList());
                    } else if (Config.displayMode == Config.Mode.NEAREST) {
                        toRender = players.stream().sorted((a,b) -> Double.compare(Utils.distanceSq(camX, camY, camZ, a.getX(), a.getY(), a.getZ()), Utils.distanceSq(camX, camY, camZ, b.getX(), b.getY(), b.getZ()))).limit(Config.nearestCount).collect(Collectors.toList());
                    } else {
                        if (!Config.tracked.isEmpty()) {
                            List<PlayerEntity> tracked = players.stream().filter(p -> Config.tracked.contains(p.getUuidAsString())).collect(Collectors.toList());
                            List<PlayerEntity> others = players.stream().filter(p -> !Config.tracked.contains(p.getUuidAsString())).collect(Collectors.toList());
                            toRender = new ArrayList<>();
                            toRender.addAll(tracked);
                            toRender.addAll(others);
                        } else toRender = players;
                    }
                }

                // legend
                int lx=8, ly=8, ll=0;
                Utils.drawText(drawContext, "Sculk Tracker (K toggle, P menu)", lx, ly + (ll++ * 12), 0xFFFFFF);
                Utils.drawText(drawContext, "Mode: " + Config.displayMode.name() + "  Lines:" + (Config.drawLines ? "ON" : "OFF") + " Boxes:" + (Config.drawBoxes ? "ON" : "OFF"), lx, ly + (ll++ * 12), 0xAAAAAA);

                long time = System.currentTimeMillis();

                for (PlayerEntity other : toRender) {
                    double ox = other.getX(); double oy = other.getY(); double oz = other.getZ();
                    double headY = oy + other.getStandingEyeHeight(); double feetY = oy;

                    double dxHead = ox - camX; double dyHead = headY - camY; double dzHead = oz - camZ;
                    double dxFeet = ox - camX; double dyFeet = feetY - camY; double dzFeet = oz - camZ;

                    double horizDist = Math.sqrt(dxHead*dxHead + dzHead*dzHead);
                    if (horizDist > Config.MAX_RENDER_DISTANCE) continue;

                    double[] camHead = Utils.worldToCamera(dxHead, dyHead, dzHead, Math.toRadians(yaw), Math.toRadians(pitch));
                    double[] camFeet = Utils.worldToCamera(dxFeet, dyFeet, dzFeet, Math.toRadians(yaw), Math.toRadians(pitch));

                    double camZHead = camHead[2]; double camZFeet = camFeet[2];

                    if (camZHead <= 0.01 && camZFeet <= 0.01) {
                        double angle = Math.atan2(oz - camZ, ox - camX);
                        double edgeX = cx + Math.cos(angle - Math.toRadians(yaw)) * (Math.min(sw, sh) * 0.45);
                        double edgeY = cy - Math.sin(angle - Math.toRadians(yaw)) * (Math.min(sw, sh) * 0.45);
                        Utils.fillRect(drawContext, (int)edgeX - 5, (int)edgeY - 5, (int)edgeX + 5, (int)edgeY + 5, 0xAA333333);
                        Utils.drawText(drawContext, other.getName().getString(), (int)edgeX + 8, (int)edgeY - 6, 0xFFFFFF);
                        continue;
                    }

                    Double sxHead=null, syHead=null, sxFeet=null, syFeet=null;
                    if (camZHead > 0.01) { sxHead = cx + (camHead[0]/camZHead) * Config.FOCAL; syHead = cy - (camHead[1]/camZHead) * Config.FOCAL; }
                    if (camZFeet > 0.01) { sxFeet = cx + (camFeet[0]/camZFeet) * Config.FOCAL; syFeet = cy - (camFeet[1]/camZFeet) * Config.FOCAL; }

                    if (sxHead == null && sxFeet != null) { sxHead = sxFeet; syHead = syFeet - Math.max(6, (int)((-dyHead) * (Config.FOCAL / Math.max(0.001, camZFeet)))); }
                    if (sxFeet == null && sxHead != null) { sxFeet = sxHead; syFeet = syHead + Math.max(6, (int)((dyHead - dyFeet) * (Config.FOCAL / Math.max(0.001, camZHead)))); }
                    if (sxHead == null || sxFeet == null) continue;

                    int ix = (int)Math.round((sxHead + sxFeet) * 0.5);
                    int topY = (int)Math.round(Math.min(syHead, syFeet));
                    int bottomY = (int)Math.round(Math.max(syHead, syFeet));
                    int height = Math.max(10, bottomY - topY);
                    int width = Math.max(6, height / 2);

                    boolean isTracked = Config.tracked.contains(other.getUuidAsString());
                    int baseColor = isTracked ? 0xFF33FF88 : 0xFF66CCFF;
                    int outlineBase = isTracked ? 0xFF00FF66 : 0xFF66FFFF;
                    float pulse = Config.pulseOutline ? (0.65f + (float)(0.35 * (0.5 + Math.sin(time / 350.0 + (other.getUuidAsString().hashCode() & 0x7FFFFFFF) % 10) * 0.5))) : 1.0f;
                    int outline = Utils.blendAlpha(outlineBase, pulse);

                    if (Config.drawBoxes) {
                        int left = ix - width/2; int right = ix + width/2; int top = topY; int bottom = bottomY;
                        for (int layer=4; layer>=1; layer--) { int a = Math.max(12, 60 / layer); int glow = (a << 24) | (outlineBase & 0x00FFFFFF); Utils.fillRect(drawContext, left - layer, top - layer, right + layer, bottom + layer, glow); }
                        Utils.fillRect(drawContext, left, top, right, bottom, 0x55336666);
                        Utils.drawRectOutline(drawContext, left-1, top-1, right+1, bottom+1, outline);
                    }

                    if (Config.drawLines) { Utils.drawDashedThickLine(drawContext, cx, cy, ix, topY + height/2, Math.max(1, Math.min(4, width/6)), outline); }

                    if (Config.showNames) {
                        double distMeters = Math.sqrt(dxHead*dxHead + dyHead*dyHead + dzHead*dzHead);
                        String label = other.getName().getString() + " [" + (int)Math.round(distMeters) + "m]";
                        Utils.drawTextWithShadow(drawContext, label, ix + width/2 + 8, topY + Math.max(0, height/2 - 6), baseColor);
                    }

                    if (Math.sqrt(dxHead*dxHead + dyHead*dyHead + dzHead*dzHead) < 6.0) { Utils.drawRectOutline(drawContext, ix - width, topY - 6, ix + width, bottomY + 6, 0x88FF4444); }
                }

                Utils.fillRect(drawContext, cx - 2, cy - 2, cx + 2, cy + 2, 0xFF888888);
            }
        });
    }
}
