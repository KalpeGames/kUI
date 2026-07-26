package dev.kui.compat;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The single point where KUI attaches to the game's HUD rendering.
 *
 * <p>Every mod that registered its own hook paid for HUD wiring separately — and worse, two mods
 * picking different Fabric APIs ended up drawing in different layers, so their relative z-order was
 * whatever the loader happened to do. KUI registers one layer here and orders everything itself.
 *
 * <p>{@code HudElementRegistry} exists with an identical signature on every version KUI targets, so
 * unlike the input and keybinding APIs this needs no version guard.
 */
public final class HudHook {
    private HudHook() {
    }

    /** Registers {@code renderer} as the last HUD layer, above vanilla's own elements. */
    public static void register(Identifier id, Consumer<DrawContext> renderer) {
        HudElementRegistry.addLast(id, (context, tickCounter) -> renderer.accept(context));
    }

    /**
     * Wraps a vanilla HUD element so KUI can transform or suppress it.
     *
     * <p>{@code wrapper} receives the frame's {@link DrawContext} plus a way to draw the element
     * exactly as vanilla would, so it can set up a transform around it. This is how vanilla's own
     * hotbar, status bars and chat become movable without touching a mixin.
     *
     * <p>Vanilla's renderer is handed out as a {@code Consumer<DrawContext>} rather than a bare
     * {@code Runnable} because the context is a parameter of the draw, not a fixed part of it:
     * passing a different one draws the same element somewhere KUI can measure it instead of onto
     * the screen. See {@link dev.kui.hud.BoundsProbe}.
     */
    public static void wrapVanilla(Identifier vanillaId, VanillaWrapper wrapper) {
        HudElementRegistry.replaceElement(vanillaId, (UnaryOperator<net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement>)
                original -> (context, tickCounter) ->
                        wrapper.render(context, ctx -> original.render(ctx, tickCounter)));
    }

    /** Draws a vanilla element, given a way to invoke vanilla's own rendering into any context. */
    @FunctionalInterface
    public interface VanillaWrapper {
        void render(DrawContext context, Consumer<DrawContext> drawVanilla);
    }
}
