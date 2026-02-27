package com.grib.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerSelectScreen extends Screen {

    private TextFieldWidget searchField;
    private final List<ButtonWidget> playerButtons = new ArrayList<>();
    private final List<PlayerEntity> cachedPlayers = new ArrayList<>();

    private int page = 0;
    private int perPage = 8;
    private String lastSearch = "";

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int listLeft;
    private int listTop;
    private final int buttonHeight = 22;
    private final int buttonGap = 2;

    protected PlayerSelectScreen() {
        super(Text.translatable("sculk.ui.title"));
    }

    @Override
    protected void init() {
        try { this.clearChildren(); } catch (Throwable ignored) {}

        panelWidth = Math.max(360, Math.min(this.width - 40, 560));
        panelHeight = Math.max(200, this.height - 80);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = 28;

        listLeft = panelLeft + 16;
        int cursorY = panelTop + 12;

        searchField = new TextFieldWidget(textRenderer, listLeft, cursorY, panelWidth - 32, 20, Text.translatable("sculk.ui.search"));
        addDrawableChild(searchField);
        cursorY += 28;

        int bottomReserved = 160;
        int available = panelTop + panelHeight - cursorY - bottomReserved;
        perPage = Math.max(4, available / (buttonHeight + buttonGap));
        if (perPage < 4) perPage = 4;

        listTop = cursorY;

        playerButtons.clear();
        for (int i = 0; i < perPage; i++) {
            final int idx = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal(""), b -> {
                int realIdx = page * perPage + idx;
                if (realIdx >= 0 && realIdx < cachedPlayers.size()) {
                    PlayerEntity p = cachedPlayers.get(realIdx);
                    synchronized (Config.tracked) {
                        String id = p.getUuidAsString();
                        if (Config.tracked.contains(id)) Config.tracked.remove(id);
                        else Config.tracked.add(id);
                    }
                    Config.save();
                    updatePlayerButtons();
                }
            }).dimensions(listLeft, listTop + i * (buttonHeight + buttonGap), panelWidth - 32, buttonHeight).build();
            playerButtons.add(btn);
            addDrawableChild(btn);
        }

        int bottomY = panelTop + panelHeight - 34;
        int x = listLeft;

        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.prev"), b -> {
            if (page > 0) page--;
            updatePlayerButtons();
        }).dimensions(x, bottomY, 72, 20).build());
        x += 82;

        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.next"), b -> {
            page++;
            updatePlayerButtons();
        }).dimensions(x, bottomY, 72, 20).build());
        x += 82;

        ButtonWidget modeBtn = ButtonWidget.builder(Text.translatable("sculk.ui.mode", Text.translatable("sculk.mode." + Config.displayMode.name().toLowerCase(Locale.ROOT))),
                b -> {
                    if (Config.displayMode == Config.Mode.ALL) Config.displayMode = Config.Mode.TRACKED_ONLY;
                    else if (Config.displayMode == Config.Mode.TRACKED_ONLY) Config.displayMode = Config.Mode.NEAREST;
                    else Config.displayMode = Config.Mode.ALL;
                    b.setMessage(Text.translatable("sculk.ui.mode", Text.translatable("sculk.mode." + Config.displayMode.name().toLowerCase(Locale.ROOT))));
                    Config.save();
                }).dimensions(x, bottomY, 160, 20).build();
        addDrawableChild(modeBtn);
        x = listLeft;

        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.track_all"), b -> {
            if (MinecraftClient.getInstance().world == null) return;
            synchronized (Config.tracked) {
                for (PlayerEntity p : MinecraftClient.getInstance().world.getPlayers())
                    if (p != MinecraftClient.getInstance().player && !p.isSpectator()) Config.tracked.add(p.getUuidAsString());
            }
            Config.save();
            updatePlayerButtons();
        }).dimensions(x, bottomY - 28, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.untrack_all"), b -> {
            synchronized (Config.tracked) { Config.tracked.clear(); }
            Config.save();
            updatePlayerButtons();
        }).dimensions(x + 120, bottomY - 28, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.autotrack", Text.translatable(Config.autoTrackNearest ? "sculk.on" : "sculk.off")), b -> {
            Config.autoTrackNearest = !Config.autoTrackNearest;
            b.setMessage(Text.translatable("sculk.ui.autotrack", Text.translatable(Config.autoTrackNearest ? "sculk.on" : "sculk.off")));
            Config.save();
        }).dimensions(x + 240, bottomY - 28, 130, 20).build());

        int settingsY = bottomY - 96;
        int sx = listLeft;
        int bw = 110;

        ButtonWidget boxesBtn = ButtonWidget.builder(Text.translatable("sculk.ui.opt.boxes", Text.translatable(Config.profile().drawBoxes ? "sculk.on" : "sculk.off")), b -> {
            Config.profile().drawBoxes = !Config.profile().drawBoxes;
            b.setMessage(Text.translatable("sculk.ui.opt.boxes", Text.translatable(Config.profile().drawBoxes ? "sculk.on" : "sculk.off")));
            Config.save();
        }).dimensions(sx, settingsY, bw, 20).build();
        addDrawableChild(boxesBtn);
        sx += bw + 8;

        ButtonWidget linesBtn = ButtonWidget.builder(Text.translatable("sculk.ui.opt.lines", Text.translatable(Config.profile().drawLines ? "sculk.on" : "sculk.off")), b -> {
            Config.profile().drawLines = !Config.profile().drawLines;
            b.setMessage(Text.translatable("sculk.ui.opt.lines", Text.translatable(Config.profile().drawLines ? "sculk.on" : "sculk.off")));
            Config.save();
        }).dimensions(sx, settingsY, bw, 20).build();
        addDrawableChild(linesBtn);
        sx += bw + 8;

        ButtonWidget namesBtn = ButtonWidget.builder(Text.translatable("sculk.ui.opt.names", Text.translatable(Config.profile().showNames ? "sculk.on" : "sculk.off")), b -> {
            Config.profile().showNames = !Config.profile().showNames;
            b.setMessage(Text.translatable("sculk.ui.opt.names", Text.translatable(Config.profile().showNames ? "sculk.on" : "sculk.off")));
            Config.save();
        }).dimensions(sx, settingsY, bw, 20).build();
        addDrawableChild(namesBtn);
        sx += bw + 8;

        ButtonWidget distBtn = ButtonWidget.builder(Text.translatable("sculk.ui.opt.distance", Text.translatable(Config.profile().showDistance ? "sculk.on" : "sculk.off")), b -> {
            Config.profile().showDistance = !Config.profile().showDistance;
            b.setMessage(Text.translatable("sculk.ui.opt.distance", Text.translatable(Config.profile().showDistance ? "sculk.on" : "sculk.off")));
            Config.save();
        }).dimensions(sx, settingsY, bw, 20).build();
        addDrawableChild(distBtn);

        int settingsY2 = settingsY + 26;
        int sx2 = listLeft;

        ButtonWidget hpBtn = ButtonWidget.builder(Text.translatable("sculk.ui.opt.hp", Text.translatable(Config.profile().showHealthBar ? "sculk.on" : "sculk.off")), b -> {
            Config.profile().showHealthBar = !Config.profile().showHealthBar;
            b.setMessage(Text.translatable("sculk.ui.opt.hp", Text.translatable(Config.profile().showHealthBar ? "sculk.on" : "sculk.off")));
            Config.save();
        }).dimensions(sx2, settingsY2, bw, 20).build();
        addDrawableChild(hpBtn);
        sx2 += bw + 8;

        ButtonWidget pulseBtn = ButtonWidget.builder(Text.translatable("sculk.ui.opt.pulse", Text.translatable(Config.profile().pulseOutline ? "sculk.on" : "sculk.off")), b -> {
            Config.profile().pulseOutline = !Config.profile().pulseOutline;
            b.setMessage(Text.translatable("sculk.ui.opt.pulse", Text.translatable(Config.profile().pulseOutline ? "sculk.on" : "sculk.off")));
            Config.save();
        }).dimensions(sx2, settingsY2, bw, 20).build();
        addDrawableChild(pulseBtn);
        sx2 += bw + 8;

        ButtonWidget mapBtn = ButtonWidget.builder(Text.translatable("sculk.ui.opt.minimap", Text.translatable(Config.profile().minimapEnabled ? "sculk.on" : "sculk.off")), b -> {
            Config.profile().minimapEnabled = !Config.profile().minimapEnabled;
            b.setMessage(Text.translatable("sculk.ui.opt.minimap", Text.translatable(Config.profile().minimapEnabled ? "sculk.on" : "sculk.off")));
            Config.save();
        }).dimensions(sx2, settingsY2, bw, 20).build();
        addDrawableChild(mapBtn);
        sx2 += bw + 8;

        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.done"), b -> this.close()).dimensions(panelLeft + panelWidth - 86, bottomY, 70, 20).build());

        updatePlayerButtons();
    }

    private void updatePlayerButtons() {
        String search = (searchField == null) ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        cachedPlayers.clear();

        if (client != null && client.world != null) {
            List<PlayerEntity> players = client.world.getPlayers().stream()
                    .filter(p -> p != client.player && !p.isSpectator())
                    .collect(Collectors.toList());

            if (!search.isEmpty()) {
                players = players.stream()
                        .filter(p -> p.getName().getString().toLowerCase(Locale.ROOT).contains(search))
                        .collect(Collectors.toList());
            }

            players.sort((a, b) -> {
                boolean aTracked = Config.tracked.contains(a.getUuidAsString());
                boolean bTracked = Config.tracked.contains(b.getUuidAsString());
                if (aTracked != bTracked) return aTracked ? -1 : 1;

                double da = a.squaredDistanceTo(client.player);
                double db = b.squaredDistanceTo(client.player);
                if (da != db) return Double.compare(da, db);

                return a.getName().getString().compareToIgnoreCase(b.getName().getString());
            });

            cachedPlayers.addAll(players);
        }

        int maxPage = Math.max(0, (cachedPlayers.size() - 1) / perPage);
        page = Math.max(0, Math.min(page, maxPage));
        int start = page * perPage;

        for (int i = 0; i < playerButtons.size(); i++) {
            ButtonWidget b = playerButtons.get(i);
            int idx = start + i;
            if (idx < cachedPlayers.size()) {
                PlayerEntity p = cachedPlayers.get(idx);
                boolean tracked = Config.tracked.contains(p.getUuidAsString());
                String prefix = tracked ? "✓ " : "  ";
                b.setMessage(Text.literal(prefix + p.getName().getString()));
                b.visible = true;
                b.active = true;
            } else {
                b.visible = false;
                b.active = false;
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x88000000);

        ctx.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xCC1E1E1E);

        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelTop - 12, 0xFFFFFF);

        super.render(ctx, mouseX, mouseY, delta);

        int start = page * perPage;
        for (int i = 0; i < playerButtons.size(); i++) {
            int idx = start + i;
            if (idx >= cachedPlayers.size()) continue;
            PlayerEntity p = cachedPlayers.get(idx);

            int bx = listLeft;
            int by = listTop + i * (buttonHeight + buttonGap);
            int iconSize = 14;

            ctx.fill(bx + 4, by + 4, bx + 4 + iconSize, by + 4 + iconSize, 0xFF2B2B2B);

            String name = p.getName().getString();
            String ch = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            int textX = bx + 4 + iconSize / 2;
            int textY = by + 4 + (iconSize - 8) / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(ch), textX, textY, 0xFFFFFF);
        }

        ctx.drawText(textRenderer, Text.translatable("sculk.ui.hint").getString(), panelLeft + 10, panelTop + panelHeight - 96, 0xAAAAAA, false);

        String cur = (searchField == null) ? "" : searchField.getText();
        if (!Objects.equals(cur, lastSearch)) {
            lastSearch = cur;
            page = 0;
            updatePlayerButtons();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}