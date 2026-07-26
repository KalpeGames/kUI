package dev.kui.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;

/**
 * Builds a {@link DrawContext} that records into a caller-supplied render state.
 *
 * <p>Since 1.21.6 a {@code DrawContext} does not touch the GPU: it appends description objects to
 * a {@link GuiRenderState}, which the renderer turns into draw calls later. Handing a renderer a
 * context over a <em>throwaway</em> state therefore runs it for its geometry alone — nothing is
 * ever drawn, and the state is discarded. That is what lets KUI ask vanilla exactly how large its
 * own HUD elements are instead of guessing.
 *
 * <p>The constructor is the only version-sensitive part: 1.21.11 added the cursor position to it.
 */
public final class GuiProbe {
    private GuiProbe() {
    }

    /** A context that writes into {@code state} rather than into this frame's own. */
    public static DrawContext context(MinecraftClient client, GuiRenderState state) {
        //? if >=1.21.11 {
        // The trailing pair is the cursor position, which no HUD element reads.
        return new DrawContext(client, state, 0, 0);
        //?} else {
        /*return new DrawContext(client, state);
        *///?}
    }
}
