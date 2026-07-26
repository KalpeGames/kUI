package dev.kui.hud;

import dev.kui.Kui;
import dev.kui.config.KuiConfig;
import dev.kui.draw.Draw;
import dev.kui.theme.Colors;
import dev.kui.theme.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single registry and layout engine for every HUD element across every KUI mod.
 *
 * <p>This is the piece that makes the library worth having. Because all elements land here rather
 * than in per-mod render hooks:
 *
 * <ul>
 *   <li>docked elements sharing an anchor are stacked into a lane, so they cannot overlap;
 *   <li>z-order between two different mods is a number the user controls, not an accident of
 *       which Fabric API each mod happened to register with;
 *   <li>one editor can arrange all of them together.
 * </ul>
 */
public final class HudManager {
    /** Vertical gap between two elements stacked in the same lane. */
    private static final int LANE_GAP = 3;

    private static final List<HudElement> ELEMENTS = new ArrayList<>();

    /** Elements whose render threw. Skipped for the rest of the session so one bug is contained. */
    private static final Set<String> BROKEN = new HashSet<>();

    private HudManager() {
    }

    // ---- registry --------------------------------------------------------

    /**
     * Adds an element to the shared HUD. Call during client init; registering the same id twice
     * is a no-op so a mod reloading its own config cannot double-register.
     */
    public static void register(HudElement element) {
        for (HudElement existing : ELEMENTS) {
            if (existing.id.equals(element.id)) {
                Kui.LOGGER.warn("[KUI] HUD element {} registered twice, ignoring the second one", element.id);
                return;
            }
        }
        ELEMENTS.add(element);
        Kui.LOGGER.info("[KUI] Registered HUD element {}", element.id);
    }

    public static List<HudElement> elements() {
        return Collections.unmodifiableList(ELEMENTS);
    }

    /** The registered element with this namespaced key, or null. */
    public static HudElement byKey(String key) {
        for (HudElement element : ELEMENTS) {
            if (element.key().equals(key)) {
                return element;
            }
        }
        return null;
    }

    /** Every element grouped by owning mod, mods in alphabetical order. */
    public static Map<String, List<HudElement>> byMod() {
        Map<String, List<HudElement>> grouped = new LinkedHashMap<>();
        ELEMENTS.stream()
                .sorted(Comparator.comparing(HudElement::modId).thenComparing(HudElement::key))
                .forEach(e -> grouped.computeIfAbsent(e.modId(), k -> new ArrayList<>()).add(e));
        return grouped;
    }

    // ---- layout ----------------------------------------------------------

    /** An element together with the rectangle the manager decided it occupies. */
    public record Positioned(HudElement element, int x, int y, int w, int h, float scale) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public boolean overlaps(Positioned other) {
            return x < other.x + other.w && x + w > other.x
                    && y < other.y + other.h && y + h > other.y;
        }
    }

    /**
     * Resolves every eligible element to a screen rectangle, in draw order (lowest
     * {@link Placement#order} first, so it ends up furthest back).
     *
     * @param editing      true inside the layout editor: vanilla's reserved regions are ignored
     *                     and elements with no live content still get a slot
     * @param includeHidden true to lay out elements the user has hidden, so the editor can show
     *                     them dimmed and let them be brought back
     */
    public static List<Positioned> layout(int screenWidth, int screenHeight,
                                          boolean editing, boolean includeHidden) {
        List<HudElement> docked = new ArrayList<>();
        List<Positioned> out = new ArrayList<>();

        for (HudElement element : ELEMENTS) {
            if (BROKEN.contains(element.key())) {
                continue;
            }
            if (!includeHidden && !element.visible()) {
                continue;
            }
            if (!editing && !safeHasContent(element)) {
                continue;
            }
            if (!element.isPositionable()) {
                // Draws itself across the whole screen; it only needs a z-order slot.
                out.add(new Positioned(element, 0, 0, screenWidth, screenHeight, element.scale()));
                continue;
            }
            Rect override = element.overrideRect(screenWidth, screenHeight);
            if (override != null) {
                // Positions itself relative to something KUI does not control — a vanilla element
                // nudged away from where the game drew it.
                out.add(new Positioned(element, override.x(), override.y(),
                        override.w(), override.h(), element.scale()));
                continue;
            }
            Placement placement = element.placement();
            if (placement.mode == Placement.Mode.FREE) {
                int w = element.scaledWidth();
                int h = element.scaledHeight();
                out.add(new Positioned(element,
                        freeX(placement.anchor, placement.offsetX, w, screenWidth),
                        freeY(placement.anchor, placement.offsetY, h, screenHeight),
                        w, h, element.scale()));
            } else {
                docked.add(element);
            }
        }

        layoutDocked(docked, screenWidth, screenHeight, editing, out);

        // Draw order is global, so a low-order element from one mod really does sit behind a
        // high-order element from another.
        out.sort(Comparator.comparingInt(p -> p.element().placement().order));
        return out;
    }

    /** Packs docked elements into per-anchor lanes so that nothing in a lane can overlap. */
    private static void layoutDocked(List<HudElement> docked, int screenWidth, int screenHeight,
                                     boolean editing, List<Positioned> out) {
        Map<Anchor, List<HudElement>> lanes = new EnumMap<>(Anchor.class);
        for (HudElement element : docked) {
            lanes.computeIfAbsent(element.placement().anchor, k -> new ArrayList<>()).add(element);
        }

        for (Map.Entry<Anchor, List<HudElement>> entry : lanes.entrySet()) {
            Anchor anchor = entry.getKey();
            List<HudElement> lane = entry.getValue();
            lane.sort(Comparator.<HudElement>comparingInt(e -> e.placement().order)
                    .thenComparing(HudElement::key));
            // In a bottom lane, "first" should mean nearest the bottom edge, so the stack grows
            // upward away from the anchor rather than downward toward it.
            if (anchor.isBottom()) {
                Collections.reverse(lane);
            }

            SafeArea box = SafeArea.forAnchor(anchor, screenWidth, screenHeight, editing);

            int total = LANE_GAP * Math.max(0, lane.size() - 1);
            for (HudElement element : lane) {
                total += element.scaledHeight();
            }

            int cursor;
            if (anchor.isTop()) {
                cursor = box.y;
            } else if (anchor.isBottom()) {
                cursor = box.y + box.height - total;
            } else {
                cursor = box.y + (box.height - total) / 2;
            }
            // An overfull lane still has to start on-screen; it will simply run past the far edge.
            cursor = Math.max(box.y, cursor);

            for (HudElement element : lane) {
                int w = element.scaledWidth();
                int h = element.scaledHeight();
                out.add(new Positioned(element, anchor.originX(box.x, box.width, w), cursor,
                        w, h, element.scale()));
                cursor += h + LANE_GAP;
            }
        }
    }

    // ---- free-position maths (shared with the layout editor) -------------

    /** Screen x of a free element of width {@code w} offset from its anchor. */
    public static int freeX(Anchor anchor, int offsetX, int w, int screenWidth) {
        int ax = anchor.anchorX(0, screenWidth);
        return switch (anchor.col) {
            case 0 -> ax + offsetX;
            case 2 -> ax + offsetX - w;
            default -> ax + offsetX - w / 2;
        };
    }

    /** Screen y of a free element of height {@code h} offset from its anchor. */
    public static int freeY(Anchor anchor, int offsetY, int h, int screenHeight) {
        int ay = anchor.anchorY(0, screenHeight);
        return switch (anchor.row) {
            case 0 -> ay + offsetY;
            case 2 -> ay + offsetY - h;
            default -> ay + offsetY - h / 2;
        };
    }

    /** Inverse of {@link #freeX}: the offset that puts an element's left edge at {@code x}. */
    public static int offsetXFor(Anchor anchor, int x, int w, int screenWidth) {
        int ax = anchor.anchorX(0, screenWidth);
        return switch (anchor.col) {
            case 0 -> x - ax;
            case 2 -> x + w - ax;
            default -> x + w / 2 - ax;
        };
    }

    /** Inverse of {@link #freeY}. */
    public static int offsetYFor(Anchor anchor, int y, int h, int screenHeight) {
        int ay = anchor.anchorY(0, screenHeight);
        return switch (anchor.row) {
            case 0 -> y - ay;
            case 2 -> y + h - ay;
            default -> y + h / 2 - ay;
        };
    }

    // ---- rendering -------------------------------------------------------

    /** Draws the whole HUD. Installed once by {@code KuiClient} as the only HUD hook. */
    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            return;
        }
        // The layout editor draws its own arrangeable copy of everything.
        if (client.currentScreen instanceof HudLayoutScreen) {
            return;
        }

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        boolean outlines = KuiConfig.get().debugOutlines;

        for (Positioned p : layout(sw, sh, false, false)) {
            HudElement element = p.element();
            if (!element.managedRendering()) {
                // Drawn by the game itself; KUI only wraps it in a transform elsewhere.
                continue;
            }
            try {
                element.render(ctx, p.x(), p.y(), p.scale(), false);
            } catch (Throwable t) {
                // One mod's broken element must not blank every other mod's HUD.
                BROKEN.add(element.key());
                Kui.LOGGER.error("[KUI] HUD element {} threw while rendering and has been disabled "
                        + "for this session", element.id, t);
                continue;
            }
            if (outlines && element.isPositionable()) {
                Draw.border(ctx, p.x(), p.y(), p.w(), p.h(), Colors.withAlpha(Theme.accent(element.modId()), 0x80));
            }
        }
    }

    /** {@link HudElement#hasContent()} can be called on a partly-initialised mod; never let it throw. */
    private static boolean safeHasContent(HudElement element) {
        try {
            return element.hasContent();
        } catch (Throwable t) {
            BROKEN.add(element.key());
            Kui.LOGGER.error("[KUI] HUD element {} threw from hasContent() and has been disabled "
                    + "for this session", element.id, t);
            return false;
        }
    }
}
