package com.grib.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;

public class Utils {
    public static double[] worldToCamera(double dx, double dy, double dz, double yawRad, double pitchRad) {
        double cosPitch = Math.cos(pitchRad);
        double fx = -Math.sin(yawRad) * cosPitch;
        double fz = Math.cos(yawRad) * cosPitch;
        double fy = -Math.sin(pitchRad);

        double rx = -fz; double ry = 0.0; double rz = fx;
        double rlen = Math.sqrt(rx*rx + ry*ry + rz*rz); if (rlen == 0.0) rlen = 1.0; rx /= rlen; ry /= rlen; rz /= rlen;

        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        double camX = dx * rx + dy * ry + dz * rz;
        double camY = dx * ux + dy * uy + dz * uz;
        double camZ = dx * fx + dy * fy + dz * fz;

        return new double[] { camX, camY, camZ };
    }

    public static void drawText(DrawContext ctx, String text, int x, int y, int color) { ctx.drawText(MinecraftClient.getInstance().textRenderer, text, x, y, color, true); }
    public static void drawTextWithShadow(DrawContext ctx, String text, int x, int y, int color) { ctx.drawText(MinecraftClient.getInstance().textRenderer, text, x+1, y+1, 0x80000000, true); ctx.drawText(MinecraftClient.getInstance().textRenderer, text, x, y, color, true); }
    public static void fillRect(DrawContext ctx, int left, int top, int right, int bottom, int color) { if (right < left) { int t = left; left = right; right = t; } if (bottom < top) { int t = top; top = bottom; bottom = t; } ctx.fill(left, top, right, bottom, color); }
    public static void drawRectOutline(DrawContext ctx, int left, int top, int right, int bottom, int color) { fillRect(ctx, left, top, right, top+1, color); fillRect(ctx, left, bottom-1, right, bottom, color); fillRect(ctx, left, top, left+1, bottom, color); fillRect(ctx, right-1, top, right, bottom, color); }
    public static void drawDashedThickLine(DrawContext ctx, int x0, int y0, int x1, int y1, int thickness, int color) { int dx = x1 - x0; int dy = y1 - y0; int len = Math.max(Math.abs(dx), Math.abs(dy)); if (len == 0) return; double stepX = dx / (double) len; double stepY = dy / (double) len; int gap = 4; int i = 0; while (i <= len && i < 2000) { int seg = (i % (gap * 2) < gap) ? 1 : 0; if (seg == 1) { int cx = (int)Math.round(x0 + stepX * i); int cy = (int)Math.round(y0 + stepY * i); int half = thickness / 2; fillRect(ctx, cx - half, cy - half, cx + half + 1, cy + half + 1, color); } i++; } }

    public static double distanceSq(double ax, double ay, double az, double bx, double by, double bz) { double dx = ax - bx; double dy = ay - by; double dz = az - bz; return dx*dx + dy*dy + dz*dz; }

    public static int blendAlpha(int color, float alphaFactor) { int a = (color >> 24) & 0xFF; int r = (color >> 16) & 0xFF; int g = (color >> 8) & 0xFF; int b = color & 0xFF; int na = (int) Math.max(0, Math.min(255, a * alphaFactor)); return (na << 24) | (r << 16) | (g << 8) | b; }
}
