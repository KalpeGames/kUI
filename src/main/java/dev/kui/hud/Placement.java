package dev.kui.hud;

/**
 * The saved, user-editable state of one HUD element: where it sits, how big it is and whether it
 * is shown. Serialised to {@code config/kui/layout.json} by {@link dev.kui.config.LayoutStore}.
 *
 * <p>This is a mutable bean rather than a record because the layout editor writes fields directly
 * as the user drags, and Gson populates it by field.
 */
public class Placement {
    /** How an element's position is decided. */
    public enum Mode {
        /**
         * The manager stacks the element with the others sharing its anchor, so nothing can ever
         * overlap. This is the default, because it solves collisions with no user effort.
         */
        DOCKED,
        /**
         * The user has dragged the element somewhere specific. Position is stored as a pixel
         * offset from {@link #anchor}, so it stays put relative to the nearest corner on resize.
         */
        FREE
    }

    public Mode mode = Mode.DOCKED;
    public Anchor anchor = Anchor.TOP_LEFT;

    /** Offset from the anchor point, in unscaled screen pixels. Only read in {@link Mode#FREE}. */
    public int offsetX;
    public int offsetY;

    /** Per-element size multiplier, on top of the global UI scale. */
    public double scale = 1.0;

    /** Background opacity for this element, clamped to a readable range when read. */
    public double opacity = 1.0;

    public boolean visible = true;

    /** Display variant, for elements that offer more than one look. */
    public int style = 0;

    /**
     * Sort key within a docked lane, and z-order overall. Lower draws first (further back) and
     * docks nearer the anchor.
     */
    public int order = 100;

    public Placement() {
    }

    public Placement(Anchor anchor) {
        this.anchor = anchor;
    }

    public Placement(Anchor anchor, int order) {
        this.anchor = anchor;
        this.order = order;
    }

    /** A free-positioned placement, offset from {@code anchor}. */
    public static Placement free(Anchor anchor, int offsetX, int offsetY) {
        Placement p = new Placement(anchor);
        p.mode = Mode.FREE;
        p.offsetX = offsetX;
        p.offsetY = offsetY;
        return p;
    }

    public Placement withScale(double scale) {
        this.scale = scale;
        return this;
    }

    public Placement withOpacity(double opacity) {
        this.opacity = opacity;
        return this;
    }

    public Placement withOrder(int order) {
        this.order = order;
        return this;
    }

    public Placement hidden() {
        this.visible = false;
        return this;
    }

    /** Repairs anything a hand-edited or outdated config file might have got wrong. */
    public Placement sanitized() {
        if (mode == null) {
            mode = Mode.DOCKED;
        }
        if (anchor == null) {
            anchor = Anchor.TOP_LEFT;
        }
        scale = clamp(scale, 0.5, 2.5);
        opacity = clamp(opacity, 0.15, 1.0);
        style = Math.max(0, style);
        return this;
    }

    public Placement copy() {
        Placement p = new Placement(anchor, order);
        p.mode = mode;
        p.offsetX = offsetX;
        p.offsetY = offsetY;
        p.scale = scale;
        p.opacity = opacity;
        p.visible = visible;
        p.style = style;
        return p;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
