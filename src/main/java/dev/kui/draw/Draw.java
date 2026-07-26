package dev.kui.draw;

import dev.kui.theme.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing primitives shared by every KUI mod.
 *
 * <p>Everything here is expressed as (x, y, width, height) rather than the corner-pair form
 * {@link DrawContext#fill} uses, because widget layout code almost always has a size in hand
 * rather than a second corner.
 */
public final class Draw {
    private Draw() {
    }

    // ---- text metrics ----------------------------------------------------

    public static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static int fontHeight() {
        return font().fontHeight;
    }

    public static int textWidth(String text) {
        return font().getWidth(text);
    }

    // ---- rectangles ------------------------------------------------------

    public static void rect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, color);
    }

    /** A 1px outline drawn just inside the given bounds. */
    public static void border(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** A filled rectangle with the four corner pixels omitted, which reads as a soft radius. */
    public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) {
            rect(ctx, x, y, w, h, color);
            return;
        }
        rect(ctx, x + 1, y, w - 2, 1, color);           // top edge, inset
        rect(ctx, x, y + 1, w, h - 2, color);           // middle, full width
        rect(ctx, x + 1, y + h - 1, w - 2, 1, color);   // bottom edge, inset
    }

    /** A rounded panel with an optional 1px rounded border. Pass {@code border == 0} to skip it. */
    public static void roundedCard(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
        roundedRect(ctx, x, y, w, h, fill);
        if (border != 0 && w > 2 && h > 2) {
            rect(ctx, x + 1, y, w - 2, 1, border);
            rect(ctx, x + 1, y + h - 1, w - 2, 1, border);
            rect(ctx, x, y + 1, 1, h - 2, border);
            rect(ctx, x + w - 1, y + 1, 1, h - 2, border);
        }
    }

    /** A vertical gradient drawn as smooth bands, so it needs no dedicated render pipeline. */
    public static void vGradient(DrawContext ctx, int x, int y, int w, int h, int top, int bottom) {
        if (h <= 0) {
            return;
        }
        int bands = Math.min(h, 24);
        for (int b = 0; b < bands; b++) {
            int y0 = y + (int) ((long) h * b / bands);
            int y1 = y + (int) ((long) h * (b + 1) / bands);
            float t = bands == 1 ? 0f : b / (float) (bands - 1);
            rect(ctx, x, y0, w, y1 - y0, Colors.lerp(top, bottom, t));
        }
    }

    /** A horizontal gradient drawn as smooth bands. */
    public static void hGradient(DrawContext ctx, int x, int y, int w, int h, int left, int right) {
        if (w <= 0) {
            return;
        }
        int bands = Math.min(w, 24);
        for (int b = 0; b < bands; b++) {
            int x0 = x + (int) ((long) w * b / bands);
            int x1 = x + (int) ((long) w * (b + 1) / bands);
            float t = bands == 1 ? 0f : b / (float) (bands - 1);
            rect(ctx, x0, y, x1 - x0, h, Colors.lerp(left, right, t));
        }
    }

    /** A horizontal progress/track bar filled to {@code fraction} in [0,1]. */
    public static void bar(DrawContext ctx, int x, int y, int w, int h, float fraction, int bg, int fg) {
        rect(ctx, x, y, w, h, bg);
        int filled = Math.max(0, Math.min(w, Math.round(w * fraction)));
        if (filled > 0) {
            rect(ctx, x, y, filled, h, fg);
        }
    }

    /** A soft drop shadow just outside the given bounds. */
    public static void shadow(DrawContext ctx, int x, int y, int w, int h, int layers, int color) {
        for (int i = layers; i >= 1; i--) {
            int a = Colors.alpha(color) / (i + 1);
            rect(ctx, x - i, y - i, w + i * 2, h + i * 2, Colors.withAlpha(color, a));
        }
    }

    // ---- text ------------------------------------------------------------

    public static void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawTextWithShadow(font(), s, x, y, color);
    }

    public static void textNoShadow(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(font(), s, x, y, color, false);
    }

    public static void textCentered(DrawContext ctx, String s, int centerX, int y, int color) {
        ctx.drawTextWithShadow(font(), s, centerX - textWidth(s) / 2, y, color);
    }

    public static void textRight(DrawContext ctx, String s, int rightX, int y, int color) {
        ctx.drawTextWithShadow(font(), s, rightX - textWidth(s), y, color);
    }

    /** Centred text that shrinks to fit {@code maxWidth} instead of being truncated. */
    public static void textCenteredFit(DrawContext ctx, String s, int centerX, int y, int maxWidth, int color) {
        int tw = textWidth(s);
        if (tw <= maxWidth || tw == 0) {
            ctx.drawTextWithShadow(font(), s, centerX - tw / 2, y, color);
            return;
        }
        float scale = (float) maxWidth / tw;
        pushScale(ctx, centerX, y + (fontHeight() - fontHeight() * scale) / 2f, scale);
        ctx.drawTextWithShadow(font(), s, -tw / 2, 0, color);
        pop(ctx);
    }

    /**
     * Draws {@code s} greedily word-wrapped to {@code maxWidth}, up to {@code maxLines} lines.
     * Any overflow is crammed onto the last visible line and ellipsised.
     */
    public static void textWrapped(DrawContext ctx, String s, int x, int y, int maxWidth,
                                   int lineHeight, int maxLines, int color) {
        List<String> lines = wrap(s, maxWidth);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            String line = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) {
                StringBuilder rest = new StringBuilder(line);
                for (int j = i + 1; j < lines.size(); j++) {
                    rest.append(' ').append(lines.get(j));
                }
                line = rest.toString();
            }
            textNoShadow(ctx, truncate(line, maxWidth), x, y + i * lineHeight, color);
        }
    }

    /** Greedy word wrap to {@code maxWidth} pixels. */
    public static List<String> wrap(String s, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (textWidth(candidate) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** Trims to fit {@code maxWidth} pixels, appending an ellipsis when it had to cut. */
    public static String truncate(String s, int maxWidth) {
        if (s == null) {
            return "";
        }
        if (textWidth(s) <= maxWidth) {
            return s;
        }
        String ellipsis = "...";
        int budget = Math.max(0, maxWidth - textWidth(ellipsis));
        return font().trimToWidth(s, budget) + ellipsis;
    }

    // ---- textures --------------------------------------------------------

    /** Draws a whole texture into a {@code size × size} quad. */
    public static void image(DrawContext ctx, Identifier id, int x, int y, int size) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, size, size, size, size);
    }

    /** Draws a whole texture into an arbitrary rectangle. */
    public static void image(DrawContext ctx, Identifier id, int x, int y, int w, int h) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, w, h);
    }

    // ---- transforms and clipping ----------------------------------------

    /** Translates to (dx, dy) and scales about that point. Always pair with {@link #pop}. */
    public static void pushScale(DrawContext ctx, float dx, float dy, float scale) {
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(dx, dy);
        matrices.scale(scale, scale);
    }

    /** Translates without scaling. Always pair with {@link #pop}. */
    public static void pushTranslate(DrawContext ctx, float dx, float dy) {
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(dx, dy);
    }

    public static void pop(DrawContext ctx) {
        ctx.getMatrices().popMatrix();
    }

    public static void scissorOn(DrawContext ctx, int x, int y, int w, int h) {
        ctx.enableScissor(x, y, x + w, y + h);
    }

    public static void scissorOff(DrawContext ctx) {
        ctx.disableScissor();
    }

    // ---- misc ------------------------------------------------------------

    /** Formats milliseconds as {@code m:ss}. */
    public static String formatTime(long ms) {
        long totalSeconds = Math.max(0, ms / 1000);
        return (totalSeconds / 60) + ":" + (totalSeconds % 60 < 10 ? "0" : "") + (totalSeconds % 60);
    }

    /** True when (mx, my) falls inside the given rectangle. */
    public static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
