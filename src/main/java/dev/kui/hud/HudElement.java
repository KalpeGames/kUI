package dev.kui.hud;

import dev.kui.config.LayoutStore;
import dev.kui.draw.Draw;
import dev.kui.theme.Colors;
import dev.kui.theme.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * One thing a mod draws on the HUD.
 *
 * <p>An element only has to describe its size and paint itself at a given point; where that point
 * is, whether it is visible, how large and how opaque it is, and how it orders against elements
 * from <em>other</em> mods are all decided by {@link HudManager} from the user's saved
 * {@link Placement}.
 *
 * <p>Ids are namespaced by the owning mod ({@code spotifymod:now_playing}), which is what lets the
 * layout editor group elements by mod and lets a mod reset only its own arrangement.
 */
public abstract class HudElement {
    public final Identifier id;
    private final String displayName;

    protected HudElement(Identifier id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The mod that owns this element. */
    public final String modId() {
        return id.getNamespace();
    }

    /** The key this element's placement is stored under. */
    public final String key() {
        return id.toString();
    }

    /** Human-readable name, shown in the layout editor. */
    public String displayName() {
        return displayName;
    }

    // ---- what a subclass must provide -----------------------------------

    /** Unscaled width. May change between frames (a longer track title, an extra digit). */
    public abstract int width();

    /** Unscaled height. */
    public abstract int height();

    /**
     * Paints the element with its top-left corner at (x, y). The transform is <em>not</em> applied
     * for you: use {@link Draw#pushScale} if {@code scale} matters to your drawing.
     *
     * @param editing true when drawn inside the layout editor, so an element can show more than
     *                it normally would (for example a card that is usually background-free)
     */
    public abstract void render(DrawContext ctx, int x, int y, float scale, boolean editing);

    /** Whether the element has anything worth showing right now. */
    public boolean hasContent() {
        return true;
    }

    /** Where this element sits before the user moves it. */
    protected Placement defaults() {
        return new Placement(Anchor.TOP_LEFT);
    }

    // ---- saved state -----------------------------------------------------

    /** This element's live placement. Mutating it changes the layout; call save afterwards. */
    public final Placement placement() {
        return LayoutStore.get().placement(key(), this::defaults);
    }

    public boolean visible() {
        return placement().visible;
    }

    /** This element's own scale, ignoring the global UI multiplier. */
    public float localScale() {
        return (float) placement().scale;
    }

    /** The scale to draw at: this element's own, times the user's global UI size. */
    public float scale() {
        return (float) placement().scale * Theme.uiScale();
    }

    /** Background opacity, clamped to a range that stays legible. */
    public double opacity() {
        return Math.max(0.15, Math.min(1.0, placement().opacity));
    }

    public int scaledWidth() {
        return Math.round(width() * scale());
    }

    public int scaledHeight() {
        return Math.round(height() * scale());
    }

    // ---- display variants ------------------------------------------------

    /** How many looks this element offers. Override alongside {@link #styleName()}. */
    public int styleCount() {
        return 1;
    }

    public boolean hasStyles() {
        return styleCount() > 1;
    }

    public int style() {
        return Math.max(0, Math.min(styleCount() - 1, placement().style));
    }

    /** Name of the current style, shown in the editor. */
    public String styleName() {
        return "";
    }

    public void cycleStyle() {
        placement().style = (style() + 1) % styleCount();
    }

    // ---- layout behaviour ------------------------------------------------

    /**
     * False for elements that place themselves — a full-width visualiser along the screen edge,
     * say. Those are still registered so their z-order against other mods is controlled, but the
     * manager will not dock, stack or let the user drag them.
     */
    public boolean isPositionable() {
        return true;
    }

    /**
     * Lets an element decide its own rectangle instead of using the manager's anchor and lane
     * maths. Returns null to use the normal placement.
     *
     * <p>Vanilla HUD elements use this: their position is vanilla's, nudged by a saved delta,
     * rather than anything KUI can express as an anchor.
     */
    public Rect overrideRect(int screenWidth, int screenHeight) {
        return null;
    }

    /**
     * Records that the user dragged this element so its top-left is at (x, y).
     *
     * <p>The default converts the point into an anchor plus offset, switching the element to free
     * placement — dragging is inherently free positioning, so this lifts it out of its lane.
     */
    public void moveTo(int x, int y, int screenWidth, int screenHeight) {
        Placement placement = placement();
        int w = scaledWidth();
        int h = scaledHeight();
        placement.mode = Placement.Mode.FREE;
        placement.anchor = Anchor.nearest(x + w / 2, y + h / 2, screenWidth, screenHeight);
        placement.offsetX = HudManager.offsetXFor(placement.anchor, x, w, screenWidth);
        placement.offsetY = HudManager.offsetYFor(placement.anchor, y, h, screenHeight);
    }

    /** False for elements that cannot join an auto-stacked lane (vanilla HUD elements). */
    public boolean supportsDocking() {
        return true;
    }

    /** False for elements KUI cannot fade (vanilla HUD elements draw with their own colours). */
    public boolean supportsOpacity() {
        return true;
    }

    /**
     * False when something other than {@link HudManager} performs the actual drawing during the
     * normal HUD pass — vanilla elements are drawn by the game, with KUI only wrapping them in a
     * transform. Such elements still appear in the layout editor so they can be arranged.
     */
    public boolean managedRendering() {
        return true;
    }

    // ---- theming helpers -------------------------------------------------

    /** This element's accent: its mod's override, or the global accent. */
    protected int accent() {
        return Theme.accent(modId());
    }

    /** Accent-aware, opacity-scaled panel colour for this element's card body. */
    protected int panelBg() {
        return Colors.scaleAlpha(Theme.panel(modId()), opacity());
    }

    /** Opacity-scaled accent, for the edge strip along a card. */
    protected int accentEdge() {
        return Colors.scaleAlpha(accent(), opacity());
    }

    /** Subtle opacity-scaled border for this element's card. */
    protected int cardBorder() {
        return Colors.scaleAlpha(Theme.BORDER, opacity());
    }

    // ---- editor support --------------------------------------------------

    /**
     * Drawn in the layout editor when {@link #hasContent()} is false, so an element can still be
     * positioned while nothing is playing or no download is running. Override for something more
     * representative than the default labelled box.
     */
    public void renderPlaceholder(DrawContext ctx, int x, int y, float scale) {
        int w = Math.round(width() * scale);
        int h = Math.round(height() * scale);
        Draw.roundedCard(ctx, x, y, w, h, Colors.withAlpha(Theme.PANEL, 0x99), Colors.withAlpha(accent(), 0x66));
        String label = Draw.truncate(displayName(), w - 8);
        Draw.textCenteredFit(ctx, label, x + w / 2, y + Math.max(2, (h - Draw.fontHeight()) / 2),
                w - 8, Theme.MUTED);
    }
}
