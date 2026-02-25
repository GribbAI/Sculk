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

    // layout
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

        panelWidth = Math.max(320, Math.min(this.width - 40, 480));
        panelHeight = Math.max(160, this.height - 80);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = 28;

        listLeft = panelLeft + 16;
        int cursorY = panelTop + 12;

        searchField = new TextFieldWidget(textRenderer, listLeft, cursorY, panelWidth - 32, 20, Text.translatable("sculk.ui.search"));
        addDrawableChild(searchField);
        cursorY += 28;

        int bottomReserved = 110;
        int available = panelTop + panelHeight - cursorY - bottomReserved;
        perPage = Math.max(4, available / (buttonHeight + buttonGap));
        if (perPage < 4) perPage = 4;

        listTop = cursorY;

        // buttonsys
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
                    updatePlayerButtons();
                }
            }).dimensions(listLeft, listTop + i * (buttonHeight + buttonGap), panelWidth - 32, buttonHeight).build();
            playerButtons.add(btn);
            addDrawableChild(btn);
        }

        // bottom controls
        int bottomY = panelTop + panelHeight - 34;
        int x = listLeft;

        // prev
        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.prev"), b -> {
            if (page > 0) page--;
            updatePlayerButtons();
        }).dimensions(x, bottomY, 72, 20).build());
        x += 82;

        // next
        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.next"), b -> {
            page++;
            updatePlayerButtons();
        }).dimensions(x, bottomY, 72, 20).build());
        x += 82;

        // mode toggle (all / tacked / nearest)
        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.mode", Text.translatable("sculk.mode." + Config.displayMode.name().toLowerCase(Locale.ROOT))),
                b -> {
                    if (Config.displayMode == Config.Mode.ALL) Config.displayMode = Config.Mode.TRACKED_ONLY;
                    else if (Config.displayMode == Config.Mode.TRACKED_ONLY) Config.displayMode = Config.Mode.NEAREST;
                    else Config.displayMode = Config.Mode.ALL;
                    b.setMessage(Text.translatable("sculk.ui.mode", Text.translatable("sculk.mode." + Config.displayMode.name().toLowerCase(Locale.ROOT))));
                }).dimensions(x, bottomY, 160, 20).build());
        x = listLeft;

        // track all
        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.track_all"), b -> {
            if (MinecraftClient.getInstance().world == null) return;
            synchronized (Config.tracked) {
                for (PlayerEntity p : MinecraftClient.getInstance().world.getPlayers())
                    if (p != MinecraftClient.getInstance().player && !p.isSpectator()) Config.tracked.add(p.getUuidAsString());
            }
            updatePlayerButtons();
        }).dimensions(x, bottomY - 28, 110, 20).build());

        // untrack all
        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.untrack_all"), b -> {
            synchronized (Config.tracked) { Config.tracked.clear(); }
            updatePlayerButtons();
        }).dimensions(x + 120, bottomY - 28, 110, 20).build());

        // autotrack toggle
        addDrawableChild(ButtonWidget.builder(Text.translatable("sculk.ui.autotrack", Text.translatable(Config.autoTrackNearest ? "sculk.on" : "sculk.off")), b -> {
            Config.autoTrackNearest = !Config.autoTrackNearest;
            b.setMessage(Text.translatable("sculk.ui.autotrack", Text.translatable(Config.autoTrackNearest ? "sculk.on" : "sculk.off")));
        }).dimensions(x + 240, bottomY - 28, 130, 20).build());

        // done
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

            // sort. tracked first, then by distance, then name
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
        ctx.fill(0, 0, this.width, this.height, 0x88000000); // dim background

        // panel background (on top of dim)
        ctx.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xCC1E1E1E);

        // title
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelTop - 12, 0xFFFFFF);

        // render children (buttons, textfield) after drawing background/panel
        super.render(ctx, mouseX, mouseY, delta);

        // draw minimal icon (square + initial) for each visible button
        int start = page * perPage;
        for (int i = 0; i < playerButtons.size(); i++) {
            int idx = start + i;
            if (idx >= cachedPlayers.size()) continue;
            PlayerEntity p = cachedPlayers.get(idx);

            int bx = listLeft;
            int by = listTop + i * (buttonHeight + buttonGap);
            int iconSize = 14;

            // icon background
            ctx.fill(bx + 4, by + 4, bx + 4 + iconSize, by + 4 + iconSize, 0xFF2B2B2B);

            // first character of name
            String name = p.getName().getString();
            String ch = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            int textX = bx + 4 + iconSize / 2;
            int textY = by + 4 + (iconSize - 8) / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(ch), textX, textY, 0xFFFFFF);
        }

        // hint text
        ctx.drawText(textRenderer, Text.translatable("sculk.ui.hint").getString(), panelLeft + 10, panelTop + panelHeight - 96, 0xAAAAAA, false);

        // live search update
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