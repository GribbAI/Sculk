package com.grib.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.stream.Collectors;

public class Renderer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final Map<String, Float> visibilityAlpha = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, Long> lastSeen = Collections.synchronizedMap(new HashMap<>());

    private static long lastTickTime = System.currentTimeMillis();

    public static void init() { }

    public static void registerHud() {
        HudRenderCallback.EVENT.register(new HudRenderCallback() {
            @Override
            public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
                Config.tickAutosave();

                if (!Config.enabled) return;

                Config.Profile p = Config.profile();
                if (p == null) return;
                if (mc.world == null || mc.player == null) return;

                long now = System.currentTimeMillis();
                double dtSeconds = Math.max(0.001, (now - lastTickTime) / 1000.0);
                lastTickTime = now;

                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();
                int cx = sw / 2;
                int cy = sh / 2;

                Vec3d cam = mc.gameRenderer.getCamera().getPos();
                double camX = cam.x, camY = cam.y, camZ = cam.z;
                double yaw = mc.gameRenderer.getCamera().getYaw();
                double pitch = mc.gameRenderer.getCamera().getPitch();

                List<PlayerEntity> players = mc.world.getPlayers().stream()
                        .filter(ply -> ply != mc.player && !ply.isSpectator())
                        .collect(Collectors.toList());

                if (players.size() > p.maxEntitiesRendered) {
                    players = players.subList(0, p.maxEntitiesRendered);
                }

                List<PlayerEntity> toRender;
                synchronized (Config.tracked) {
                    if (Config.displayMode == Config.Mode.TRACKED_ONLY) {
                        toRender = players.stream().filter(pl -> Config.tracked.contains(pl.getUuidAsString())).collect(Collectors.toList());
                    } else if (Config.displayMode == Config.Mode.NEAREST) {
                        toRender = players.stream()
                                .sorted(Comparator.comparingDouble(a -> Utils.distanceSq(camX, camY, camZ, a.getX(), a.getY(), a.getZ())))
                                .limit(Config.nearestCount)
                                .collect(Collectors.toList());
                    } else {
                        if (!Config.tracked.isEmpty()) {
                            List<PlayerEntity> tracked = players.stream().filter(pl -> Config.tracked.contains(pl.getUuidAsString())).collect(Collectors.toList());
                            List<PlayerEntity> others = players.stream().filter(pl -> !Config.tracked.contains(pl.getUuidAsString())).collect(Collectors.toList());
                            toRender = new ArrayList<>();
                            toRender.addAll(tracked);
                            toRender.addAll(others);
                        } else {
                            toRender = players;
                        }
                    }
                }

                int lx = 8, ly = 8, ll = 0;
                int legendColor = Utils.blendAlpha(Config.getBaseColor(), p.hudOpacity);
                Utils.drawText(drawContext, Text.translatable("sculk.hud.legend").getString(), lx, ly + (ll++ * 12), legendColor);
                Utils.drawText(drawContext, "Profile: " + Config.get().activeProfile + "  Mode: " + Config.displayMode.name(), lx, ly + (ll++ * 12), Utils.blendAlpha(0xFFAAAAAA, p.hudOpacity));

                long time = System.currentTimeMillis();

                Set<String> currentlyVisible = new HashSet<>();

                for (PlayerEntity other : toRender) {
                    double ox = other.getX(), oy = other.getY(), oz = other.getZ();
                    double headY = oy + other.getStandingEyeHeight(), feetY = oy;

                    double dxHead = ox - camX, dyHead = headY - camY, dzHead = oz - camZ;
                    double dxFeet = ox - camX, dyFeet = feetY - camY, dzFeet = oz - camZ;

                    double horizDist = Math.sqrt(dxHead * dxHead + dzHead * dzHead);
                    if (horizDist > p.maxRenderDistance) continue;

                    double[] camHead = Utils.worldToCamera(dxHead, dyHead, dzHead, Math.toRadians(yaw), Math.toRadians(pitch));
                    double[] camFeet = Utils.worldToCamera(dxFeet, dyFeet, dzFeet, Math.toRadians(yaw), Math.toRadians(pitch));

                    double camZHead = camHead[2], camZFeet = camFeet[2];

                    String uuid = other.getUuidAsString();

                    boolean onScreen = (camZHead > 0.01) || (camZFeet > 0.01);

                    if (onScreen) {
                        lastSeen.put(uuid, time);
                        currentlyVisible.add(uuid);
                    }

                    float alpha = visibilityAlpha.getOrDefault(uuid, onScreen ? 0f : 0f);
                    if (p.smoothFade) {
                        float target = onScreen ? 1.0f : 0.0f;
                        float speed = p.fadeSpeed;
                        if (alpha < target) {
                            alpha = (float) Math.min(target, alpha + speed * dtSeconds);
                        } else if (alpha > target) {
                            alpha = (float) Math.max(target, alpha - speed * dtSeconds);
                        }
                    } else {
                        alpha = onScreen ? 1.0f : 0.0f;
                    }
                    visibilityAlpha.put(uuid, alpha);
                    if (alpha <= 0.001f) continue;

                    Double sxHead = null, syHead = null, sxFeet = null, syFeet = null;
                    if (camZHead > 0.01) {
                        sxHead = cx + (camHead[0] / camZHead) * (p.focal * p.hudScale);
                        syHead = cy - (camHead[1] / camZHead) * (p.focal * p.hudScale);
                    }
                    if (camZFeet > 0.01) {
                        sxFeet = cx + (camFeet[0] / camZFeet) * (p.focal * p.hudScale);
                        syFeet = cy - (camFeet[1] / camZFeet) * (p.focal * p.hudScale);
                    }

                    if (sxHead == null && sxFeet != null) {
                        sxHead = sxFeet;
                        syHead = syFeet - Math.max(6, (int) ((-dyHead) * (p.focal * p.hudScale / Math.max(0.001, camZFeet))));
                    }
                    if (sxFeet == null && sxHead != null) {
                        sxFeet = sxHead;
                        syFeet = syHead + Math.max(6, (int) ((dyHead - dyFeet) * (p.focal * p.hudScale / Math.max(0.001, camZHead))));
                    }
                    if (sxHead == null || sxFeet == null) continue;

                    int ix = (int) Math.round((sxHead + sxFeet) * 0.5);
                    int topY = (int) Math.round(Math.min(syHead, syFeet));
                    int bottomY = (int) Math.round(Math.max(syHead, syFeet));
                    int height = Math.max(10, bottomY - topY);
                    int width = Math.max(6, (int) (height / 2.0 * p.hudScale));

                    boolean isTracked = Config.tracked.contains(uuid);
                    int baseColor = isTracked ? Config.profile().trackedColor : Config.getBaseColor();
                    int outlineBase = isTracked ? Config.profile().trackedColor : Config.getOutlineColor(isTracked);

                    float pulse = p.pulseOutline ? (0.65f + (float) (p.pulseStrength * (0.5 + Math.sin(time / (350.0 / (p.pulseFrequency + 0.0001)) ) * 0.5))) : 1.0f;
                    float finalAlpha = Math.max(0.0f, Math.min(1.0f, alpha * p.hudOpacity));
                    int outlineColor = Utils.blendAlpha(outlineBase, pulse * finalAlpha);
                    int nameColor = Utils.blendAlpha(baseColor, finalAlpha);

                    if (p.drawBoxes) {
                        int left = ix - width / 2;
                        int right = ix + width / 2;
                        int top = topY;
                        int bottom = bottomY;
                        for (int layer = 4; layer >= 1; layer--) {
                            int a = Math.max(12, 60 / layer);
                            int glow = (a << 24) | (outlineBase & 0x00FFFFFF);
                            Utils.fillRect(drawContext, left - layer, top - layer, right + layer, bottom + layer, Utils.blendAlpha(glow, finalAlpha));
                        }
                        Utils.fillRect(drawContext, left, top, right, bottom, Utils.blendAlpha(0x55336666, finalAlpha));
                        Utils.drawRectOutline(drawContext, left - 1, top - 1, right + 1, bottom + 1, outlineColor);
                    }

                    if (p.drawLines) {
                        Utils.drawDashedThickLine(drawContext, cx, cy, ix, topY + height / 2, Math.max(1, Math.min(4, width / 6)), outlineColor);
                    }

                    int textH = mc.textRenderer.fontHeight;
                    int nameY = Math.max(2, topY - 6 - 2 * textH);
                    int distY = Math.max(2, topY - 6 - textH);
                    int healthY = Math.max(2, topY - 6);

                    if (p.showNames) {
                        drawContext.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(other.getName().getString()), ix, nameY, nameColor);
                    }

                    if (p.showDistance) {
                        double distMeters = Math.sqrt(dxHead * dxHead + dyHead * dyHead + dzHead * dzHead);
                        String distLabel = "[" + (int) Math.round(distMeters) + "m]";
                        drawContext.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(distLabel), ix, distY, Utils.blendAlpha(0xFFCCCCCC, finalAlpha));
                    }

                    if (p.showHealthBar) {
                        try {
                            float hp = other.getHealth();
                            float maxHp = other.getMaxHealth();
                            float pct = Math.max(0.0f, Math.min(1.0f, hp / Math.max(1f, maxHp)));
                            int barW = Math.max(36, width);
                            int barH = 5;
                            int bx = ix - (barW / 2);
                            int by = healthY;
                            Utils.fillRect(drawContext, bx, by, bx + barW, by + barH, Utils.blendAlpha(0xFF222222, finalAlpha));
                            int green = Utils.blendAlpha(0xFF44CC44, finalAlpha);
                            int red = Utils.blendAlpha(0xFFCC4444, finalAlpha);
                            int fillColor = pct > 0.5f ? green : red;
                            int filled = bx + (int) Math.round(barW * pct);
                            Utils.fillRect(drawContext, bx, by, filled, by + barH, fillColor);
                            Utils.drawRectOutline(drawContext, bx - 1, by - 1, bx + barW + 1, by + barH + 1, outlineColor);
                        } catch (Throwable ignored) {}
                    }

                    if (Math.sqrt(dxHead * dxHead + dyHead * dyHead + dzHead * dzHead) < 3.2) {
                        Utils.drawRectOutline(drawContext, ix - width, topY - 6, ix + width, bottomY + 6, Utils.blendAlpha(0x88FF4444, finalAlpha));
                    }

                    Utils.fillRect(drawContext, cx - 2, cy - 2, cx + 2, cy + 2, Utils.blendAlpha(0xFF888888, p.hudOpacity));
                }

                synchronized (visibilityAlpha) {
                    Iterator<Map.Entry<String, Float>> it = visibilityAlpha.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, Float> e = it.next();
                        String id = e.getKey();
                        if (!currentlyVisible.contains(id)) {
                            if (e.getValue() <= 0.001f) {
                                Long last = lastSeen.get(id);
                                if (last == null || (now - last) > 60_000L) {
                                    it.remove();
                                    lastSeen.remove(id);
                                }
                            }
                        }
                    }
                }

                if (p.minimapEnabled) {
                    drawMiniMap(drawContext, sw, sh, cx, cy, camX, camY, camZ, yaw, toRender, p);
                }
            }
        });
    }

    private static void drawMiniMap(DrawContext drawContext, int sw, int sh, int cx, int cy, double camX, double camY, double camZ, double yaw, List<PlayerEntity> players, Config.Profile p) {
        int mapR = p.minimapRadius;
        int px = sw - mapR - 8;
        int py = 8 + mapR;
        Utils.fillRect(drawContext, px - mapR, py - mapR, px + mapR, py + mapR, Utils.blendAlpha(0xAA1E1E1E, p.hudOpacity));
        Utils.drawRectOutline(drawContext, px - mapR - 1, py - mapR - 1, px + mapR + 1, py + mapR + 1, Utils.blendAlpha(0xFF666666, p.hudOpacity));

        Utils.fillRect(drawContext, px - 3, py - 3, px + 3, py + 3, Utils.blendAlpha(0xFFFFFFFF, p.hudOpacity));

        double rangeMeters = p.minimapRangeMeters;
        for (PlayerEntity other : players) {
            double dx = other.getX() - camX;
            double dz = other.getZ() - camZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > rangeMeters) continue;
            double rad = Math.toRadians(yaw);
            double cos = Math.cos(-rad), sin = Math.sin(-rad);
            double rx = dx * cos - dz * sin;
            double rz = dx * sin + dz * cos;
            double nx = (rx / rangeMeters) * (mapR - 6);
            double ny = (rz / rangeMeters) * (mapR - 6);
            int bx = px + (int) Math.round(nx);
            int by = py - (int) Math.round(ny);
            boolean tracked = Config.tracked.contains(other.getUuidAsString());
            int col = tracked ? Utils.blendAlpha(Config.profile().trackedColor, p.hudOpacity) : Utils.blendAlpha(Config.getBaseColor(), p.hudOpacity);
            Utils.fillRect(drawContext, bx - 3, by - 3, bx + 3, by + 3, col);
        }
    }
}