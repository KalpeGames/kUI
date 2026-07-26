package dev.kui.hud;

import dev.kui.compat.HudHook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * One of vanilla's own HUD elements, made movable.
 *
 * <p>The game draws these itself at hardcoded positions, so KUI cannot place them the way it places
 * a mod's element. Instead it wraps vanilla's renderer in a transform: the saved
 * {@link Placement#offsetX}/{@link Placement#offsetY} are a <em>delta from wherever vanilla would
 * have drawn it</em>, and scale is applied about the element's own centre. Hiding is just skipping
 * the wrapped call.
 *
 * <p>The rectangle the editor draws around one of these is measured, not assumed: while the layout
 * editor is open the element is run once more against a throwaway render state, and the union of
 * what it drew becomes its rectangle (see {@link BoundsProbe}). So the box fits the scoreboard that
 * is actually on screen, with the lines it actually has — the nominal rectangles in
 * {@link VanillaHud} are only the fallback for an element that draws nothing right now.
 */
public class VanillaHudElement extends HudElement {
    /**
     * How long a measurement is trusted. An element's size changes when its content does — a
     * scoreboard line arriving, a boss dying — none of which is worth re-measuring every frame,
     * and a fifth of a second is far below the point where a stale selection box is noticeable.
     */
    private static final long MEASURE_INTERVAL_MS = 200;

    private final BiFunction<Integer, Integer, Rect> nominalRect;
    private final int defaultOrder;

    /** The last measured bounds, or null if the element drew nothing when last measured. */
    private Rect measured;
    private boolean everMeasured;
    private long measuredAtMs;
    private int measuredWidth;
    private int measuredHeight;

    /**
     * @param vanillaId   the Fabric {@code VanillaHudElements} identifier to wrap
     * @param displayName name shown in the layout editor
     * @param nominalRect vanilla's nominal rectangle, given the scaled screen width and height,
     *                    used only until the element has been measured
     * @param order       draw order, used for the editor's sorting
     */
    public VanillaHudElement(Identifier vanillaId, String displayName,
                             BiFunction<Integer, Integer, Rect> nominalRect, int order) {
        super(vanillaId, displayName);
        this.nominalRect = nominalRect;
        this.defaultOrder = order;
    }

    @Override
    protected Placement defaults() {
        // Always free: the position is a nudge away from vanilla's, which no anchor can express.
        return Placement.free(Anchor.TOP_LEFT, 0, 0).withOrder(defaultOrder);
    }

    /**
     * Where vanilla draws this element, before the user's nudge and scale.
     *
     * <p>The measured bounds when there are any for this screen size, vanilla's nominal rectangle
     * otherwise — either because the editor has not been open long enough to take a measurement,
     * or because the element genuinely draws nothing at the moment and there is no box to measure.
     */
    public Rect defaultRect(int screenWidth, int screenHeight) {
        Rect current = measured;
        if (current != null && measuredWidth == screenWidth && measuredHeight == screenHeight) {
            return current;
        }
        return nominalRect.apply(screenWidth, screenHeight);
    }

    /** False once a measurement has found that vanilla draws nothing for this element. */
    @Override
    public boolean hasContent() {
        return !everMeasured || measured != null;
    }

    /**
     * Re-measures this element if the last measurement has gone stale.
     *
     * <p>Called from inside the wrap, <em>before</em> vanilla draws for real, which matters for the
     * handful of vanilla renderers that write state as they draw: the health bar latches its
     * heart-jump animation, the subtitle layer hands a deferred renderer back to the HUD. Measuring
     * first means the real draw is the last writer in the frame and wins, so the measurement stays
     * invisible.
     */
    private void measure(Consumer<DrawContext> drawVanilla, DrawContext ctx,
                         int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        boolean sameScreen = measuredWidth == screenWidth && measuredHeight == screenHeight;
        if (everMeasured && sameScreen && now - measuredAtMs < MEASURE_INTERVAL_MS) {
            return;
        }
        measuredAtMs = now;
        measuredWidth = screenWidth;
        measuredHeight = screenHeight;
        measured = BoundsProbe.measure(drawVanilla, ctx);
        everMeasured = true;
    }

    @Override
    public Rect overrideRect(int screenWidth, int screenHeight) {
        Rect base = defaultRect(screenWidth, screenHeight);
        Placement placement = placement();
        float scale = scale();
        int w = Math.round(base.w() * scale);
        int h = Math.round(base.h() * scale);
        // Scaling happens about the centre, so growing the hotbar does not shove it sideways.
        return new Rect(base.centerX() - w / 2 + placement.offsetX,
                base.centerY() - h / 2 + placement.offsetY, w, h);
    }

    @Override
    public void moveTo(int x, int y, int screenWidth, int screenHeight) {
        Rect base = defaultRect(screenWidth, screenHeight);
        float scale = scale();
        Placement placement = placement();
        placement.offsetX = x - (base.centerX() - Math.round(base.w() * scale) / 2);
        placement.offsetY = y - (base.centerY() - Math.round(base.h() * scale) / 2);
    }

    /** Returns this element to exactly where vanilla puts it. */
    public void resetOffset() {
        Placement placement = placement();
        placement.offsetX = 0;
        placement.offsetY = 0;
    }

    @Override
    public int width() {
        return defaultRect(screenWidth(), screenHeight()).w();
    }

    @Override
    public int height() {
        return defaultRect(screenWidth(), screenHeight()).h();
    }

    @Override
    public boolean supportsDocking() {
        return false;
    }

    @Override
    public boolean supportsOpacity() {
        return false;
    }

    @Override
    public boolean managedRendering() {
        return false;
    }

    /**
     * Draws nothing.
     *
     * <p>The game renders this element itself, behind the layout editor and already transformed
     * by {@link #install()}, so what the user sees while arranging it is the genuine hotbar or
     * health bar rather than a stand-in. The editor supplies a selection outline on hover, which
     * is all the grab affordance a real, visible element needs.
     */
    @Override
    public void render(DrawContext ctx, int x, int y, float scale, boolean editing) {
    }

    /** Also nothing: an inactive vanilla element (no boss bar right now) should just be absent. */
    @Override
    public void renderPlaceholder(DrawContext ctx, int x, int y, float scale) {
    }

    /**
     * Installs the transform around vanilla's renderer. Called once during client init; after that
     * the element follows its saved placement everywhere, including live behind the layout editor.
     */
    public void install() {
        HudHook.wrapVanilla(id, (ctx, drawVanilla) -> {
            int screenWidth = ctx.getScaledWindowWidth();
            int screenHeight = ctx.getScaledWindowHeight();

            // Only while the editor is open: the measurement exists to draw a selection box, and
            // running every vanilla element twice a frame is not a cost to pay during play. A
            // hidden element is measured too, so it can be found and brought back.
            if (MinecraftClient.getInstance().currentScreen instanceof HudLayoutScreen) {
                measure(drawVanilla, ctx, screenWidth, screenHeight);
            }

            if (!visible()) {
                return;
            }
            Placement placement = placement();
            float scale = scale();
            boolean moved = placement.offsetX != 0 || placement.offsetY != 0;
            boolean scaled = Math.abs(scale - 1f) > 0.001f;
            if (!moved && !scaled) {
                drawVanilla.accept(ctx); // Untouched by the user: stay a pure pass-through.
                return;
            }

            Rect base = defaultRect(screenWidth, screenHeight);
            Matrix3x2fStack matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.translate(placement.offsetX, placement.offsetY);
            if (scaled) {
                matrices.translate(base.centerX(), base.centerY());
                matrices.scale(scale, scale);
                matrices.translate(-base.centerX(), -base.centerY());
            }
            drawVanilla.accept(ctx);
            matrices.popMatrix();
        });
    }

    private static int screenWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.getWindow() != null
                ? Math.max(1, client.getWindow().getScaledWidth()) : 320;
    }

    private static int screenHeight() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.getWindow() != null
                ? Math.max(1, client.getWindow().getScaledHeight()) : 240;
    }
}
