package dev.kui.draw;

import net.minecraft.client.gui.DrawContext;

/**
 * Vector-ish icon shapes built from filled rectangles.
 *
 * <p>These are drawn rather than loaded from a texture atlas so they take the accent colour, scale
 * cleanly with a widget, and never depend on a font glyph the user's resource pack might not have.
 */
public final class Icons {
    private Icons() {
    }

    // ---- primitives ------------------------------------------------------

    /** A filled disc centred at (cx, cy). */
    public static void disc(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt((double) r * r - dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** A circle outline centred at (cx, cy). */
    public static void ring(DrawContext ctx, int cx, int cy, int r, int thickness, int color) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                double d = Math.sqrt((double) dx * dx + dy * dy);
                if (d <= r + 0.5 && d >= r - thickness) {
                    ctx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    public static void triangleRight(DrawContext ctx, int x, int y, int w, int h, int color) {
        for (int row = 0; row < h; row++) {
            double dc = Math.abs(row - (h - 1) / 2.0);
            int len = (int) Math.round(w * (1.0 - dc / ((h - 1) / 2.0)));
            if (len > 0) {
                ctx.fill(x, y + row, x + len, y + row + 1, color);
            }
        }
    }

    public static void triangleLeft(DrawContext ctx, int x, int y, int w, int h, int color) {
        for (int row = 0; row < h; row++) {
            double dc = Math.abs(row - (h - 1) / 2.0);
            int len = (int) Math.round(w * (1.0 - dc / ((h - 1) / 2.0)));
            if (len > 0) {
                ctx.fill(x + w - len, y + row, x + w, y + row + 1, color);
            }
        }
    }

    public static void triangleDown(DrawContext ctx, int x, int y, int w, int h, int color) {
        for (int row = 0; row < h; row++) {
            int inset = (int) Math.round((w / 2.0) * (row / (double) h));
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    public static void triangleUp(DrawContext ctx, int x, int y, int w, int h, int color) {
        for (int row = 0; row < h; row++) {
            int inset = (int) Math.round((w / 2.0) * (1.0 - row / (double) h));
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    // ---- transport -------------------------------------------------------

    public static void play(DrawContext ctx, int cx, int cy, int size, int color) {
        triangleRight(ctx, cx - size / 2, cy - size / 2, size, size, color);
    }

    public static void pause(DrawContext ctx, int cx, int cy, int size, int color) {
        int barW = Math.max(2, size / 3);
        int half = size / 2;
        ctx.fill(cx - half, cy - half, cx - half + barW, cy + half, color);
        ctx.fill(cx + half - barW, cy - half, cx + half, cy + half, color);
    }

    public static void next(DrawContext ctx, int cx, int cy, int size, int color) {
        int half = size / 2;
        triangleRight(ctx, cx - half, cy - half, size - 2, size, color);
        ctx.fill(cx + half - 2, cy - half, cx + half, cy + half, color);
    }

    public static void previous(DrawContext ctx, int cx, int cy, int size, int color) {
        int half = size / 2;
        ctx.fill(cx - half, cy - half, cx - half + 2, cy + half, color);
        triangleLeft(ctx, cx - half + 2, cy - half, size - 2, size, color);
    }

    // ---- interface -------------------------------------------------------

    /** A cog: a hollow rim ringed by eight teeth. */
    public static void gear(DrawContext ctx, int cx, int cy, int size, int color) {
        int r = Math.max(4, size / 2);
        int hole = Math.max(2, r / 2);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            int tx = cx + (int) Math.round(Math.cos(a) * (r + 1));
            int ty = cy + (int) Math.round(Math.sin(a) * (r + 1));
            ctx.fill(tx - 1, ty - 1, tx + 2, ty + 2, color);
        }
        ring(ctx, cx, cy, r, r - hole, color);
    }

    public static void search(DrawContext ctx, int cx, int cy, int size, int color) {
        int r = Math.max(2, size / 2 - 1);
        ring(ctx, cx - 1, cy - 1, r, 2, color);
        for (int i = 0; i < r; i++) {
            int px = cx - 1 + r - 1 + i;
            int py = cy - 1 + r - 1 + i;
            ctx.fill(px, py, px + 2, py + 2, color);
        }
    }

    /** A tick, drawn as two strokes. */
    public static void check(DrawContext ctx, int x, int y, int size, int color) {
        int third = Math.max(1, size / 3);
        for (int i = 0; i < third; i++) {
            ctx.fill(x + i, y + size / 2 + i, x + i + 2, y + size / 2 + i + 2, color);
        }
        for (int i = 0; i < size - third; i++) {
            ctx.fill(x + third + i, y + size - third + 1 - i, x + third + i + 2, y + size - third + 3 - i, color);
        }
    }

    /** A cross, drawn as two diagonals. */
    public static void close(DrawContext ctx, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            ctx.fill(x + i, y + i, x + i + 2, y + i + 2, color);
            ctx.fill(x + size - i - 1, y + i, x + size - i + 1, y + i + 2, color);
        }
    }

    /** An eye, used for visibility toggles. Draws a slash through it when {@code open} is false. */
    public static void eye(DrawContext ctx, int cx, int cy, int size, boolean open, int color) {
        int r = Math.max(2, size / 2);
        ring(ctx, cx, cy, r, 1, color);
        if (open) {
            disc(ctx, cx, cy, Math.max(1, r / 2), color);
        } else {
            for (int i = -r; i <= r; i++) {
                ctx.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
            }
        }
    }

    /** Three stacked bars — the conventional "open the list" affordance. */
    public static void menu(DrawContext ctx, int cx, int cy, int size, int color) {
        int w = Math.max(6, size);
        int gap = Math.max(2, size / 3);
        for (int i = -1; i <= 1; i++) {
            ctx.fill(cx - w / 2, cy + i * gap - 1, cx + w / 2, cy + i * gap, color);
        }
    }

    /** Six dots, the conventional "drag me" affordance. */
    public static void grip(DrawContext ctx, int x, int y, int color) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                ctx.fill(x + col * 3, y + row * 3, x + col * 3 + 2, y + row * 3 + 2, color);
            }
        }
    }

    /** A pixel-art heart; total size is {@code 8 * pixel} wide by {@code 7 * pixel} tall. */
    public static void heart(DrawContext ctx, int x, int y, int pixel, int color) {
        for (int row = 0; row < HEART.length; row++) {
            for (int col = 0; col < HEART[row].length; col++) {
                if (HEART[row][col] == 1) {
                    ctx.fill(x + col * pixel, y + row * pixel,
                            x + (col + 1) * pixel, y + (row + 1) * pixel, color);
                }
            }
        }
    }

    public static int heartWidth(int pixel) {
        return 8 * pixel;
    }

    private static final int[][] HEART = {
            {0, 1, 1, 0, 0, 1, 1, 0},
            {1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1},
            {0, 1, 1, 1, 1, 1, 1, 0},
            {0, 0, 1, 1, 1, 1, 0, 0},
            {0, 0, 0, 1, 1, 0, 0, 0},
    };
}
