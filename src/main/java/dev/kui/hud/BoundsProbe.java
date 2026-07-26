package dev.kui.hud;

import dev.kui.Kui;
import dev.kui.compat.GuiProbe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.render.state.TextGuiElementRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;

import java.util.function.Consumer;

/**
 * Measures what a renderer <em>actually</em> draws.
 *
 * <p>The layout editor needs a rectangle for every vanilla HUD element, and the honest one is not
 * something that can be written down: the scoreboard is as wide as its longest line, the boss bar
 * as tall as the number of bosses, the health row as tall as the player's absorption hearts. A
 * table of nominal rectangles can only ever approximate that, and the approximation is what the
 * user sees as a selection box that does not fit around its element.
 *
 * <p>So instead of describing vanilla's geometry, this asks for it. A GUI renderer since 1.21.6
 * only appends elements to a {@link GuiRenderState}; run one against a state of our own and the
 * union of everything it appended <em>is</em> the element's true bounding box, in screen pixels,
 * for this frame's contents — with no per-element knowledge of vanilla's layout, and therefore
 * nothing to keep up to date when vanilla's layout changes.
 *
 * <p>Nothing measured here is ever rendered: the state is thrown away when the call returns.
 */
public final class BoundsProbe {
    /** Logged once rather than every frame — a broken element would otherwise flood the log. */
    private static boolean loggedFailure;

    private BoundsProbe() {
    }

    /**
     * Runs {@code render} against a throwaway render state and returns the union of what it drew.
     *
     * @param render the renderer to measure
     * @param frame  the context the caller is really drawing into; its transform is copied so the
     *               measurement lands where the element would land on screen
     * @return the bounding box in scaled screen pixels, or null if the renderer drew nothing at
     *         all (an absent boss bar, an empty scoreboard) or threw
     */
    public static Rect measure(Consumer<DrawContext> render, DrawContext frame) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        Collector collector = new Collector();
        try {
            DrawContext probe = GuiProbe.context(client, collector);
            probe.getMatrices().set(frame.getMatrices());
            render.accept(probe);
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                Kui.LOGGER.warn("[KUI] Measuring a HUD element threw; falling back to nominal "
                        + "rectangles for it", t);
            }
            return null;
        }
        return collector.bounds();
    }

    /**
     * A render state that notes where each element landed and otherwise behaves normally.
     *
     * <p>Every {@code add} still delegates: the point is to be an ordinary state that happens to
     * be watched, so a renderer that reads anything back from the state it is writing to finds
     * what it expects.
     */
    private static final class Collector extends GuiRenderState {
        private int left = Integer.MAX_VALUE;
        private int top = Integer.MAX_VALUE;
        private int right = Integer.MIN_VALUE;
        private int bottom = Integer.MIN_VALUE;

        @Override
        public void addSimpleElement(SimpleGuiElementRenderState element) {
            include(element);
            super.addSimpleElement(element);
        }

        @Override
        public void addText(TextGuiElementRenderState element) {
            include(element);
            super.addText(element);
        }

        @Override
        public void addItem(ItemGuiElementRenderState element) {
            include(element);
            super.addItem(element);
        }

        @Override
        public void addSpecialElement(SpecialGuiElementRenderState element) {
            include(element);
            super.addSpecialElement(element);
        }

        @Override
        public void addSimpleElementToCurrentLayer(TexturedQuadGuiElementRenderState element) {
            include(element);
            super.addSimpleElementToCurrentLayer(element);
        }

        @Override
        public void addPreparedTextElement(SimpleGuiElementRenderState element) {
            include(element);
            super.addPreparedTextElement(element);
        }

        private void include(GuiElementRenderState element) {
            ScreenRect rect = element.bounds();
            // bounds() is already clipped to the active scissor, so a chat line scrolled out of
            // its own box contributes nothing — which is what the user sees, and so what the
            // selection box should cover.
            if (rect == null || rect.width() <= 0 || rect.height() <= 0) {
                return;
            }
            left = Math.min(left, rect.getLeft());
            top = Math.min(top, rect.getTop());
            right = Math.max(right, rect.getRight());
            bottom = Math.max(bottom, rect.getBottom());
        }

        Rect bounds() {
            return left == Integer.MAX_VALUE
                    ? null : new Rect(left, top, right - left, bottom - top);
        }
    }
}
