package dev.kui.hud;

import dev.kui.compat.Keys;
import dev.kui.config.KuiConfig;
import dev.kui.config.LayoutStore;
import dev.kui.draw.Draw;
import dev.kui.draw.Icons;
import dev.kui.theme.Colors;
import dev.kui.theme.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The one screen where every HUD element is arranged together — each mod's, and vanilla's.
 *
 * <p>Chrome is deliberately minimal: two small buttons in the corner and panels you can dismiss or
 * move, because the screen's real content is the HUD itself and anything permanently covering it is
 * in the way. Vanilla elements are not drawn as stand-ins here; the game renders the genuine
 * article behind this screen, already transformed, so what you drag is what you get.
 *
 * <p>Interactions: drag to move, Ctrl+click to multi-select and group, double-click to step inside
 * a group, scroll to resize, Ctrl+scroll for opacity, right-click to show/hide, middle-click to
 * cycle style, arrow keys to nudge.
 */
public class HudLayoutScreen extends Screen {
    private static final int SNAP = 7;
    private static final int TOOL = 18;
    private static final int LIST_W = 154;
    private static final int INSPECTOR_W = 134;
    private static final int SETTINGS_W = 250;
    private static final int ROW_H = 13;
    private static final int GROUP_HEADER_H = 12;
    private static final long DOUBLE_CLICK_MS = 250;

    /**
     * Just enough tint to signal "edit mode" without hiding the HUD. Deliberately much lighter
     * than vanilla's screen darkening, which this screen replaces entirely.
     */
    private static final int SCRIM = 0x33040A12;

    private final Screen parent;

    // ---- selection ----
    private HudElement selected;
    private HudGroup selectedGroup;
    /** The group the user has stepped inside, whose members select individually. */
    private HudGroup enteredGroup;
    private final Set<String> multi = new LinkedHashSet<>();

    // ---- dragging ----
    private HudElement dragging;
    private boolean draggingGroup;
    private int dragOffsetX;
    private int dragOffsetY;
    private int dragMouseX;
    private int dragMouseY;
    private final Map<String, Rect> dragStartRects = new HashMap<>();
    private Rect dragStartBounds;
    private boolean draggingInspector;
    private int inspectorGrabX;
    private int inspectorGrabY;

    // ---- panels ----
    private boolean showElements;
    private boolean showSettings;
    private boolean inspectorCollapsed;
    private final Set<String> collapsedMods = new HashSet<>();
    private final Set<String> expandedGroups = new HashSet<>();
    private int listScroll;
    private int listContentHeight;

    private long lastClickMs;
    private String lastClickKey = "";

    /** Rebuilt every frame: clickable control regions, then areas that merely swallow clicks. */
    private final List<Region> regions = new ArrayList<>();
    private final List<Rect> blockers = new ArrayList<>();

    /** This frame's resolved element rectangles, reused for hit-testing. */
    private List<HudManager.Positioned> frame = List.of();

    private record Region(int x, int y, int w, int h, Runnable action, Runnable rightAction) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public HudLayoutScreen() {
        this(null);
    }

    public HudLayoutScreen(Screen parent) {
        super(Text.translatable("kui.layout.title"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Replaces vanilla's screen background.
     *
     * <p>{@code Screen.renderBackground} blurs and heavily darkens everything behind the screen.
     * Behind <em>this</em> screen is the HUD being arranged — including vanilla's own elements,
     * which render live here rather than as stand-ins — so vanilla's treatment made the very thing
     * the user is positioning unreadable. A light scrim replaces it.
     */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Draw.rect(ctx, 0, 0, this.width, this.height, SCRIM);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        regions.clear();
        blockers.clear();
        super.render(ctx, mouseX, mouseY, delta);

        renderElements(ctx, mouseX, mouseY);
        renderToolbar(ctx, mouseX, mouseY);
        if (showElements) {
            renderElementList(ctx, mouseX, mouseY);
        }
        if (showSettings) {
            renderSettings(ctx, mouseX, mouseY);
        }
        if (selectedGroup != null || selected != null || !multi.isEmpty()) {
            renderInspector(ctx, mouseX, mouseY);
        }
    }

    private void renderElements(DrawContext ctx, int mouseX, int mouseY) {
        frame = HudManager.layout(this.width, this.height, true, true);

        for (HudManager.Positioned p : frame) {
            HudElement element = p.element();
            if (!element.isPositionable()) {
                safeRender(ctx, p);
                continue;
            }

            boolean hovered = p.contains(mouseX, mouseY) && !overPanel(mouseX, mouseY);
            if (!element.visible()) {
                Draw.rect(ctx, p.x(), p.y(), p.w(), p.h(), 0x33000000);
            }
            safeRender(ctx, p);

            boolean inSelectedGroup = selectedGroup != null && selectedGroup.contains(element.key());
            boolean isMulti = multi.contains(element.key());
            boolean isSelected = element == selected;

            // Outlines only where they mean something. With ~23 elements registered, drawing a box
            // around every one turns the screen into a wireframe and hides the HUD being arranged.
            int edge = 0;
            if (collides(p)) {
                edge = Theme.DANGER;
            } else if (isSelected || isMulti) {
                edge = Theme.accentHover(element.modId());
            } else if (inSelectedGroup) {
                edge = Colors.withAlpha(Theme.accent(element.modId()), 0xAA);
            } else if (hovered || element == dragging) {
                edge = Theme.accent(element.modId());
            }
            if (edge != 0) {
                Draw.border(ctx, p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2, edge);
            }

            if (isSelected || isMulti || hovered) {
                // "Not on screen" is worth saying out loud: it is the one case where the box does
                // not hug anything, because there is nothing drawn for it to hug.
                String label = element.displayName()
                        + (element.hasStyles() ? "  [" + element.styleName() + "]" : "")
                        + (element.visible() ? "" : "  (hidden)")
                        + (hasContent(element) ? "" : "  (not on screen)");
                Draw.textNoShadow(ctx, label, p.x(), p.y() - 10, Theme.accent(element.modId()));
            }
        }

        // A dashed-feel box around a selected group, so it reads as one draggable thing.
        if (selectedGroup != null) {
            Rect bounds = groupBounds(selectedGroup);
            if (bounds != null) {
                Draw.border(ctx, bounds.x() - 3, bounds.y() - 3, bounds.w() + 6, bounds.h() + 6,
                        Colors.withAlpha(Theme.accent(), 0xFF));
                String label = selectedGroup.name + "  (" + selectedGroup.members.size() + ")"
                        + (enteredGroup == selectedGroup ? "  — editing inside" : "");
                Draw.textNoShadow(ctx, label, bounds.x() - 3, bounds.y() - 14, Theme.accent());
            }
        }
    }

    /** {@link HudElement#hasContent()} is a mod's own code; a throw from it must not break the editor. */
    private static boolean hasContent(HudElement element) {
        try {
            return element.hasContent();
        } catch (Throwable t) {
            return true;
        }
    }

    private void safeRender(DrawContext ctx, HudManager.Positioned p) {
        HudElement element = p.element();
        try {
            if (element.hasContent()) {
                element.render(ctx, p.x(), p.y(), p.scale(), true);
            } else {
                element.renderPlaceholder(ctx, p.x(), p.y(), p.scale());
            }
        } catch (Throwable t) {
            Draw.roundedCard(ctx, p.x(), p.y(), p.w(), p.h(),
                    Colors.withAlpha(Theme.DANGER, 0x40), Theme.DANGER);
        }
    }

    /**
     * Whether a freely-placed element overlaps another one badly enough to warn about.
     *
     * <p>Vanilla elements are excluded on both sides. Their rectangles are nominal approximations
     * that legitimately overlap — the crosshair sits inside the title area, the held-item name over
     * the hotbar — so flagging them turned nearly the whole screen red and buried the one case the
     * warning exists for: two mod cards landing on top of each other.
     */
    private boolean collides(HudManager.Positioned p) {
        HudElement element = p.element();
        if (!element.managedRendering() || !element.visible()
                || element.placement().mode != Placement.Mode.FREE) {
            return false;
        }
        for (HudManager.Positioned other : frame) {
            if (other.element() == element || !other.element().isPositionable()
                    || !other.element().visible() || !other.element().managedRendering()) {
                continue;
            }
            if (p.overlaps(other)) {
                return true;
            }
        }
        return false;
    }

    /** The union of a group's member rectangles this frame, or null if none are on screen. */
    private Rect groupBounds(HudGroup group) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (HudManager.Positioned p : frame) {
            if (!group.contains(p.element().key())) {
                continue;
            }
            x0 = Math.min(x0, p.x());
            y0 = Math.min(y0, p.y());
            x1 = Math.max(x1, p.x() + p.w());
            y1 = Math.max(y1, p.y() + p.h());
        }
        return x0 == Integer.MAX_VALUE ? null : new Rect(x0, y0, x1 - x0, y1 - y0);
    }

    // ---- toolbar ---------------------------------------------------------

    private void renderToolbar(DrawContext ctx, int mouseX, int mouseY) {
        toolButton(ctx, 6, 6, mouseX, mouseY, showElements, () -> {
            showElements = !showElements;
            showSettings = false;
        }, (cx, cy, color) -> Icons.menu(ctx, cx, cy, 9, color));

        toolButton(ctx, 6 + TOOL + 4, 6, mouseX, mouseY, showSettings, () -> {
            showSettings = !showSettings;
            showElements = false;
        }, (cx, cy, color) -> Icons.gear(ctx, cx, cy, 10, color));
    }

    private interface IconPainter {
        void paint(int cx, int cy, int color);
    }

    private void toolButton(DrawContext ctx, int x, int y, int mouseX, int mouseY,
                            boolean active, Runnable action, IconPainter icon) {
        boolean hovered = Draw.hovered(mouseX, mouseY, x, y, TOOL, TOOL);
        int bg = active ? Colors.withAlpha(Theme.accent(), 0xD0)
                : hovered ? Theme.controlHover() : Colors.withAlpha(Theme.PANEL, 0xD0);
        Draw.roundedRect(ctx, x, y, TOOL, TOOL, bg);
        icon.paint(x + TOOL / 2, y + TOOL / 2, active ? Colors.contrastingText(bg) : Theme.TEXT);
        addRegion(x, y, TOOL, TOOL, action);
    }

    // ---- elements list ---------------------------------------------------

    /**
     * Total height the list will occupy. Measured before drawing so {@link #listScroll} can be
     * clamped up front — clamping after the draw made an over-scrolled frame appear and then snap
     * back, which is what read as jitter.
     */
    private int measureList(Map<String, List<HudElement>> grouped, List<HudGroup> groups) {
        int h = 0;
        if (!groups.isEmpty()) {
            h += GROUP_HEADER_H;
            for (HudGroup group : groups) {
                h += ROW_H;
                if (expandedGroups.contains(group.name)) {
                    h += groupElements(group).size() * ROW_H;
                }
            }
            h += 3;
        }
        for (Map.Entry<String, List<HudElement>> entry : grouped.entrySet()) {
            int count = ungrouped(entry.getValue()).size();
            if (count == 0) {
                continue;
            }
            h += GROUP_HEADER_H;
            if (!collapsedMods.contains(entry.getKey())) {
                h += count * ROW_H;
            }
            h += 3;
        }
        return h;
    }

    /**
     * The arrangeable elements of a mod that are not in a group.
     *
     * <p>A grouped element is reachable by expanding its group, so listing it again under its mod
     * would just be the same row twice — and with the hotbar cluster pre-grouped that is nine
     * duplicates in the vanilla section alone.
     */
    private List<HudElement> ungrouped(List<HudElement> elements) {
        LayoutStore store = LayoutStore.get();
        return elements.stream()
                .filter(HudElement::isPositionable)
                .filter(e -> store.groupOf(e.key()) == null)
                .toList();
    }

    private void renderElementList(DrawContext ctx, int mouseX, int mouseY) {
        Map<String, List<HudElement>> grouped = HudManager.byMod();
        List<HudGroup> groups = LayoutStore.get().groups;
        int px = 6;
        int py = 6 + TOOL + 4;
        int ph = Math.min(this.height - py - 6, 260);
        panel(ctx, px, py, LIST_W, ph);

        Draw.text(ctx, "Elements", px + 8, py + 6, Theme.TEXT);
        if (grouped.isEmpty()) {
            Draw.textWrapped(ctx, Text.translatable("kui.layout.empty").getString(),
                    px + 8, py + 20, LIST_W - 16, 10, 4, Theme.MUTED);
            return;
        }

        int contentTop = py + 18;
        int contentH = ph - 24;

        // Measure and clamp before a single pixel is drawn.
        listContentHeight = measureList(grouped, groups);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, listContentHeight - contentH)));

        Draw.scissorOn(ctx, px + 1, contentTop, LIST_W - 2, contentH);
        int y = contentTop - listScroll;

        if (!groups.isEmpty()) {
            Draw.textNoShadow(ctx, "Groups", px + 8, y + 1, Theme.MUTED);
            y += GROUP_HEADER_H;
            for (HudGroup group : new ArrayList<>(groups)) {
                boolean expanded = expandedGroups.contains(group.name);
                boolean isSel = group == selectedGroup;
                boolean hovered = Draw.hovered(mouseX, mouseY, px + 4, y, LIST_W - 8, ROW_H);
                if (isSel || hovered) {
                    Draw.roundedRect(ctx, px + 4, y, LIST_W - 8, ROW_H,
                            isSel ? Colors.withAlpha(Theme.accent(), 0x60)
                                    : Colors.withAlpha(Theme.CARD_HOVER, 0xB0));
                }
                if (expanded) {
                    Icons.triangleDown(ctx, px + 8, y + 5, 6, 4, Theme.SUBTEXT);
                } else {
                    Icons.triangleRight(ctx, px + 9, y + 3, 4, 6, Theme.SUBTEXT);
                }
                List<HudElement> members = groupElements(group);
                Draw.textNoShadow(ctx, Draw.truncate(group.name, LIST_W - 44), px + 18, y + 3, Theme.TEXT);
                Draw.textRight(ctx, String.valueOf(members.size()), px + LIST_W - 10, y + 3, Theme.MUTED);

                addRegion(px + 4, y, 12, ROW_H, () -> {
                    if (!expandedGroups.remove(group.name)) {
                        expandedGroups.add(group.name);
                    }
                });
                addRegion(px + 16, y, LIST_W - 24, ROW_H,
                        () -> selectGroup(group),
                        () -> resetGroup(group));
                y += ROW_H;

                if (expanded) {
                    for (HudElement member : members) {
                        y = elementRow(ctx, px + 10, y, LIST_W - 10, member, mouseX, mouseY, group);
                    }
                }
            }
            y += 3;
        }

        for (Map.Entry<String, List<HudElement>> entry : grouped.entrySet()) {
            List<HudElement> arrangeable = ungrouped(entry.getValue());
            if (arrangeable.isEmpty()) {
                continue;
            }
            String mod = entry.getKey();
            boolean collapsed = collapsedMods.contains(mod);

            boolean headerHover = Draw.hovered(mouseX, mouseY, px + 4, y, LIST_W - 8, 11);
            if (collapsed) {
                Icons.triangleRight(ctx, px + 8, y + 2, 4, 6, Theme.SUBTEXT);
            } else {
                Icons.triangleDown(ctx, px + 7, y + 3, 6, 4, Theme.SUBTEXT);
            }
            Draw.textNoShadow(ctx, Draw.truncate(mod, LIST_W - 40), px + 17, y + 1,
                    headerHover ? Theme.TEXT : Theme.MUTED);
            Draw.textRight(ctx, String.valueOf(arrangeable.size()), px + LIST_W - 8, y + 1, Theme.MUTED);
            addRegion(px + 4, y, LIST_W - 8, 11, () -> {
                if (!collapsedMods.remove(mod)) {
                    collapsedMods.add(mod);
                }
            });
            y += GROUP_HEADER_H;

            if (!collapsed) {
                for (HudElement element : arrangeable) {
                    y = elementRow(ctx, px + 4, y, LIST_W - 4, element, mouseX, mouseY, null);
                }
            }
            y += 3;
        }
        Draw.scissorOff(ctx);
    }

    /** One element row. Left-click selects, right-click resets it, the eye toggles visibility. */
    private int elementRow(DrawContext ctx, int x, int y, int w, HudElement element,
                           int mouseX, int mouseY, HudGroup owningGroup) {
        boolean hovered = Draw.hovered(mouseX, mouseY, x, y, w - 8, ROW_H);
        boolean isSelected = element == selected;
        boolean isMulti = multi.contains(element.key());
        if (isSelected || isMulti || hovered) {
            Draw.roundedRect(ctx, x, y, w - 8, ROW_H,
                    isSelected ? Colors.withAlpha(Theme.accent(element.modId()), 0x60)
                            : isMulti ? Colors.withAlpha(Theme.accent(element.modId()), 0x38)
                            : Colors.withAlpha(Theme.CARD_HOVER, 0xB0));
        }
        Draw.textNoShadow(ctx, Draw.truncate(element.displayName(), w - 34), x + 6, y + 3,
                element.visible() ? Theme.TEXT : Theme.MUTED);

        int eyeX = x + w - 19;
        Icons.eye(ctx, eyeX, y + ROW_H / 2, 7, element.visible(),
                element.visible() ? Theme.accent(element.modId()) : Theme.MUTED);

        addRegion(x + w - 28, y, 20, ROW_H, () -> toggleVisible(element));
        addRegion(x, y, w - 34, ROW_H,
                () -> {
                    if (Keys.isCtrlDown()) {
                        toggleMulti(element);
                    } else {
                        selectElement(element, owningGroup);
                    }
                },
                () -> resetElement(element));
        return y + ROW_H;
    }

    // ---- settings panel --------------------------------------------------

    /** One line of the controls cheat-sheet: the keys to press, and what they do. */
    private record Shortcut(String action, String... keys) {
    }

    private static final List<Shortcut> CONTROLS = List.of(
            new Shortcut("Move", "Drag"),
            new Shortcut("Add to selection", "Ctrl", "Click"),
            new Shortcut("Edit inside a group", "Double-click"),
            new Shortcut("Resize", "Scroll"),
            new Shortcut("Opacity", "Ctrl", "Scroll"),
            new Shortcut("Show / hide", "Right-click"),
            new Shortcut("Cycle style", "Middle-click"),
            new Shortcut("Nudge  (Shift: 10px)", "Arrows"),
            new Shortcut("Close", "Esc"));

    /** The accents offered here. KUI's own blue leads; the rest span the wheel without clashing. */
    private static final int[] ACCENTS = {
            KuiConfig.DEFAULT_ACCENT, 0x2DD4BF, 0x1DB954, 0xF2C744, 0xF3701B, 0xE5484D, 0x9B5DE5,
    };

    private static final int SECTION_H = 12;
    private static final int SHORTCUT_H = 12;
    /** Width reserved for the key caps, so every description starts on the same column. */
    private static final int KEYS_W = 94;

    private void renderSettings(DrawContext ctx, int mouseX, int mouseY) {
        KuiConfig cfg = KuiConfig.get();
        int innerW = SETTINGS_W - 20;
        int halfW = (innerW - 4) / 2;

        int ph = 21                                  // title
                + 13                                 // subtitle
                + SECTION_H + 15 + 16 + 14 + 14      // appearance
                + 4 + SECTION_H                      // controls heading
                + CONTROLS.size() * SHORTCUT_H
                + 8 + 16 + 8;                        // footer
        int px = 6;
        // Prefer sitting under the toolbar, but never run off the bottom of a small screen.
        int py = Math.max(6, Math.min(6 + TOOL + 4, this.height - 6 - ph));
        panel(ctx, px, py, SETTINGS_W, ph);
        // A hairline of accent along the top edge: the one place the panel states its colour.
        Draw.rect(ctx, px + 1, py + 1, SETTINGS_W - 2, 1, Theme.accent());

        int cx = px + 10;
        Icons.gear(ctx, cx + 5, py + 12, 9, Theme.accent());
        Draw.text(ctx, Text.translatable("kui.layout.title").getString(), cx + 16, py + 8, Theme.TEXT);
        Draw.textNoShadow(ctx, "Arrange every mod's HUD, and vanilla's.", cx, py + 21, Theme.MUTED);

        int y = py + 34;
        sectionLabel(ctx, cx, y, innerW, "APPEARANCE");
        y += SECTION_H;

        stepperRow(ctx, cx, y, innerW, mouseX, mouseY,
                Text.translatable("kui.layout.ui_scale").getString(),
                (int) Math.round(cfg.uiScale * 100) + "%",
                () -> adjustUiScale(-0.1), () -> adjustUiScale(0.1));
        y += 15;

        accentRow(ctx, cx, y, innerW, mouseX, mouseY);
        y += 16;

        toggleRow(ctx, cx, y, innerW, mouseX, mouseY, "Tint cards with accent", cfg.accentTintCards,
                () -> {
                    cfg.accentTintCards = !cfg.accentTintCards;
                    cfg.save();
                });
        y += 14;

        toggleRow(ctx, cx, y, innerW, mouseX, mouseY, "Outline elements while playing",
                cfg.debugOutlines, () -> {
                    cfg.debugOutlines = !cfg.debugOutlines;
                    cfg.save();
                });
        y += 18;

        sectionLabel(ctx, cx, y, innerW, "CONTROLS");
        y += SECTION_H;
        for (Shortcut shortcut : CONTROLS) {
            shortcutRow(ctx, cx, y, shortcut);
            y += SHORTCUT_H;
        }
        y += 8;

        button(ctx, cx, y, halfW, 16, Text.translatable("kui.layout.reset_all").getString(),
                mouseX, mouseY, false, () -> {
                    LayoutStore.get().resetAll();
                    LayoutStore.get().save();
                    clearSelection();
                });
        button(ctx, cx + halfW + 4, y, halfW, 16, Text.translatable("kui.layout.done").getString(),
                mouseX, mouseY, true, this::close);
    }

    /** A small caps heading with a rule running out to the panel edge. */
    private void sectionLabel(DrawContext ctx, int x, int y, int w, String label) {
        Draw.textNoShadow(ctx, label, x, y, Theme.MUTED);
        int rx = x + Draw.textWidth(label) + 5;
        Draw.rect(ctx, rx, y + 4, Math.max(0, x + w - rx), 1, Theme.rule());
    }

    /** The accent picker: a strip of swatches, the current one ringed. */
    private void accentRow(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY) {
        KuiConfig cfg = KuiConfig.get();
        Draw.textNoShadow(ctx, "Accent colour", x, y + 3, Theme.SUBTEXT);

        int size = 12;
        int gap = 3;
        int sx = x + w - (ACCENTS.length * (size + gap) - gap);
        for (int rgb : ACCENTS) {
            boolean active = (cfg.accentColor & 0xFFFFFF) == rgb;
            boolean hovered = Draw.hovered(mouseX, mouseY, sx, y, size, size);
            Draw.roundedRect(ctx, sx, y, size, size, Colors.opaque(rgb));
            if (active || hovered) {
                Draw.border(ctx, sx - 1, y - 1, size + 2, size + 2,
                        Colors.withAlpha(Theme.TEXT, active ? 0xFF : 0x55));
            }
            int chosen = rgb;
            addRegion(sx, y, size, size, () -> {
                cfg.accentColor = chosen;
                cfg.save();
            });
            sx += size + gap;
        }
    }

    /** {@code Label ................ (o  )} — a switch reads as on/off at a glance, unlike a button. */
    private void toggleRow(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY,
                           String label, boolean on, Runnable action) {
        int h = 13;
        int trackW = 20;
        int trackH = 11;
        int tx = x + w - trackW;
        boolean hovered = Draw.hovered(mouseX, mouseY, x, y, w, h);

        Draw.textNoShadow(ctx, Draw.truncate(label, w - trackW - 6), x, y + 2,
                on ? Theme.TEXT : Theme.SUBTEXT);
        int track = on ? (hovered ? Theme.accentHover() : Theme.accentButton())
                : (hovered ? Theme.controlHover() : Theme.control());
        Draw.roundedRect(ctx, tx, y + 1, trackW, trackH, track);
        Draw.roundedRect(ctx, on ? tx + trackW - 9 : tx + 2, y + 3, 7, 7,
                on ? Colors.contrastingText(track) : Theme.MUTED);
        addRegion(x, y, w, h, action);
    }

    /** One cheat-sheet line: key caps, then what they do. */
    private void shortcutRow(DrawContext ctx, int x, int y, Shortcut shortcut) {
        int kx = x;
        for (int i = 0; i < shortcut.keys().length; i++) {
            if (i > 0) {
                Draw.textNoShadow(ctx, "+", kx + 1, y + 1, Theme.MUTED);
                kx += 7;
            }
            kx += keyCap(ctx, kx, y, shortcut.keys()[i]) + 1;
        }
        Draw.textNoShadow(ctx, shortcut.action(), x + KEYS_W, y + 1, Theme.SUBTEXT);
    }

    /** A label boxed like a keyboard key. Returns the width it took. */
    private int keyCap(DrawContext ctx, int x, int y, String key) {
        int w = Draw.textWidth(key) + 8;
        Draw.roundedCard(ctx, x, y, w, 11, Theme.control(), Colors.withAlpha(Theme.TEXT, 0x1F));
        Draw.textNoShadow(ctx, key, x + 4, y + 1, Theme.SUBTEXT);
        return w;
    }

    // ---- inspector -------------------------------------------------------

    private int inspectorX() {
        KuiConfig cfg = KuiConfig.get();
        int x = cfg.inspectorX < 0 ? this.width - INSPECTOR_W - 6 : cfg.inspectorX;
        return Math.max(0, Math.min(x, this.width - INSPECTOR_W));
    }

    private int inspectorY() {
        KuiConfig cfg = KuiConfig.get();
        int y = cfg.inspectorY < 0 ? 6 : cfg.inspectorY;
        return Math.max(0, Math.min(y, Math.max(0, this.height - 20)));
    }

    private void renderInspector(DrawContext ctx, int mouseX, int mouseY) {
        boolean groupMode = selectedGroup != null;
        boolean multiMode = !groupMode && !multi.isEmpty();
        HudElement element = selected;

        boolean docking = !groupMode && !multiMode && element != null && element.supportsDocking();
        boolean fading = groupMode || multiMode || (element != null && element.supportsOpacity());

        int ph;
        if (inspectorCollapsed) {
            ph = 16;
        } else if (multiMode) {
            ph = 60;
        } else if (groupMode) {
            ph = 22 + 2 * 15 + 22 + 18;
        } else {
            ph = 22 + (docking ? 18 + 54 : 0) + (fading ? 3 : 2) * 15 + 22;
        }

        int px = inspectorX();
        int py = inspectorY();
        panel(ctx, px, py, INSPECTOR_W, ph);

        int cx = px + 8;
        int innerW = INSPECTOR_W - 16;

        // Grip on the left drags the panel; the caret on the right collapses it. Keeping them
        // separate avoids the usual "was that a click or a drag?" ambiguity.
        Icons.grip(ctx, px + 4, py + 4, Colors.withAlpha(Theme.MUTED, 0xCC));
        addRegion(px + 2, py + 2, 12, 12, () -> {
        });

        String title = groupMode ? selectedGroup.name
                : multiMode ? multi.size() + " selected"
                : element != null ? element.displayName() : "";
        Draw.textNoShadow(ctx, Draw.truncate(title, innerW - 26), cx + 10, py + 4,
                groupMode || multiMode ? Theme.accentHover() : Theme.accentHover(element.modId()));

        if (inspectorCollapsed) {
            Icons.triangleDown(ctx, px + INSPECTOR_W - 14, py + 6, 7, 4, Theme.SUBTEXT);
        } else {
            Icons.triangleUp(ctx, px + INSPECTOR_W - 14, py + 6, 7, 4, Theme.SUBTEXT);
        }
        addRegion(px + INSPECTOR_W - 18, py, 18, 14, () -> inspectorCollapsed = !inspectorCollapsed);
        if (inspectorCollapsed) {
            return;
        }

        int y = py + 18;
        int halfW = (innerW - 4) / 2;

        if (multiMode) {
            boolean enough = multi.size() >= 2;
            Draw.textNoShadow(ctx, enough ? "Group them to move together."
                    : "Ctrl+click another to group.", cx, y, Theme.MUTED);
            y += 14;
            button(ctx, cx, y, halfW, 15, "Group", mouseX, mouseY, enough,
                    () -> { if (enough) groupSelection(); });
            button(ctx, cx + halfW + 4, y, halfW, 15, "Clear", mouseX, mouseY, false, multi::clear);
            return;
        }

        if (groupMode) {
            stepperRow(ctx, cx, y, innerW, mouseX, mouseY, "Size", "± all",
                    () -> adjustGroupScale(-0.1), () -> adjustGroupScale(0.1));
            y += 15;
            stepperRow(ctx, cx, y, innerW, mouseX, mouseY, "Layer", "± all",
                    () -> adjustGroupOrder(-10), () -> adjustGroupOrder(10));
            y += 18;
            button(ctx, cx, y, halfW, 14, enteredGroup == selectedGroup ? "Leave" : "Edit inside",
                    mouseX, mouseY, enteredGroup == selectedGroup,
                    () -> enteredGroup = enteredGroup == selectedGroup ? null : selectedGroup);
            button(ctx, cx + halfW + 4, y, halfW, 14, "Ungroup", mouseX, mouseY, false, this::ungroup);
            y += 18;
            button(ctx, cx, y, innerW, 14, "Reset all in group", mouseX, mouseY, false,
                    () -> resetGroup(selectedGroup));
            return;
        }

        if (element == null) {
            return;
        }
        Placement placement = element.placement();

        if (docking) {
            boolean isDocked = placement.mode == Placement.Mode.DOCKED;
            button(ctx, cx, y, halfW, 14, Text.translatable("kui.layout.docked").getString(),
                    mouseX, mouseY, isDocked, () -> setMode(element, Placement.Mode.DOCKED));
            button(ctx, cx + halfW + 4, y, halfW, 14, Text.translatable("kui.layout.free").getString(),
                    mouseX, mouseY, !isDocked, () -> setMode(element, Placement.Mode.FREE));
            y += 18;

            int cell = 16;
            int gap = 2;
            int gridX = px + (INSPECTOR_W - (cell * 3 + gap * 2)) / 2;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    anchorCell(ctx, gridX + col * (cell + gap), y + row * (cell + gap), cell,
                            Anchor.of(col, row), element, mouseX, mouseY);
                }
            }
            y += cell * 3 + gap * 2 + 4;
        }

        stepperRow(ctx, cx, y, innerW, mouseX, mouseY, "Size",
                Math.round(placement.scale * 100) + "%",
                () -> adjustScale(element, -0.1), () -> adjustScale(element, 0.1));
        y += 15;

        if (element.supportsOpacity()) {
            stepperRow(ctx, cx, y, innerW, mouseX, mouseY, "Fade",
                    Math.round(placement.opacity * 100) + "%",
                    () -> adjustOpacity(element, -0.08), () -> adjustOpacity(element, 0.08));
            y += 15;
        }

        stepperRow(ctx, cx, y, innerW, mouseX, mouseY, "Layer", String.valueOf(placement.order),
                () -> adjustOrder(element, -10), () -> adjustOrder(element, 10));
        y += 18;

        if (element.hasStyles()) {
            button(ctx, cx, y, halfW, 14, Draw.truncate(element.styleName(), halfW - 6),
                    mouseX, mouseY, false, () -> {
                        element.cycleStyle();
                        LayoutStore.get().save();
                    });
            button(ctx, cx + halfW + 4, y, halfW, 14, "Reset", mouseX, mouseY, false,
                    () -> resetElement(element));
        } else {
            button(ctx, cx, y, innerW, 14, "Reset element", mouseX, mouseY, false,
                    () -> resetElement(element));
        }
    }

    private void anchorCell(DrawContext ctx, int x, int y, int size, Anchor anchor,
                            HudElement element, int mouseX, int mouseY) {
        boolean active = element.placement().anchor == anchor;
        boolean hovered = Draw.hovered(mouseX, mouseY, x, y, size, size);
        int fill = active ? Colors.withAlpha(Theme.accent(element.modId()), 0xCC)
                : hovered ? Theme.controlHover() : Colors.withAlpha(Theme.CARD, 0x90);
        Draw.roundedRect(ctx, x, y, size, size, fill);
        int dot = 3;
        int dx = switch (anchor.col) {
            case 0 -> x + 3;
            case 2 -> x + size - dot - 3;
            default -> x + (size - dot) / 2;
        };
        int dy = switch (anchor.row) {
            case 0 -> y + 3;
            case 2 -> y + size - dot - 3;
            default -> y + (size - dot) / 2;
        };
        Draw.rect(ctx, dx, dy, dot, dot, active ? Theme.TEXT : Theme.SUBTEXT);
        addRegion(x, y, size, size, () -> setAnchor(element, anchor));
    }

    // ---- small controls --------------------------------------------------

    private void panel(DrawContext ctx, int x, int y, int w, int h) {
        Draw.roundedCard(ctx, x, y, w, h, Theme.window(), Colors.withAlpha(Theme.accent(), 0x3A));
        blockers.add(new Rect(x, y, w, h));
    }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label,
                        int mouseX, int mouseY, boolean primary, Runnable action) {
        boolean hovered = Draw.hovered(mouseX, mouseY, x, y, w, h);
        int accent = selected != null ? Theme.accent(selected.modId()) : Theme.accent();
        int bg = primary
                ? (hovered ? Colors.lighten(accent, 0.16f) : Colors.darken(accent, 0.10f))
                : (hovered ? Theme.controlHover() : Theme.control());
        Draw.roundedRect(ctx, x, y, w, h, bg);
        int fg = primary ? Colors.contrastingText(bg) : Theme.TEXT;
        Draw.textCenteredFit(ctx, label, x + w / 2, y + (h - Draw.fontHeight()) / 2 + 1, w - 6, fg);
        addRegion(x, y, w, h, action);
    }

    /** {@code Label  [-] value [+]} on a single 12px line. */
    private void stepperRow(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY,
                            String label, String value, Runnable minus, Runnable plus) {
        int btn = 14;
        int h = 12;
        int minusX = x + w - btn * 2 - 34;
        int valueCenter = minusX + btn + 17;
        int plusX = x + w - btn;

        Draw.textNoShadow(ctx, Draw.truncate(label, minusX - x - 4), x, y + 2, Theme.SUBTEXT);
        boolean hoverMinus = Draw.hovered(mouseX, mouseY, minusX, y, btn, h);
        boolean hoverPlus = Draw.hovered(mouseX, mouseY, plusX, y, btn, h);
        Draw.roundedRect(ctx, minusX, y, btn, h, hoverMinus ? Theme.controlHover() : Theme.control());
        Draw.roundedRect(ctx, plusX, y, btn, h, hoverPlus ? Theme.controlHover() : Theme.control());
        Draw.textCentered(ctx, "-", minusX + btn / 2, y + 2, Theme.TEXT);
        Draw.textCentered(ctx, "+", plusX + btn / 2, y + 2, Theme.TEXT);
        Draw.textCentered(ctx, value, valueCenter, y + 2, Theme.TEXT);

        addRegion(minusX, y, btn, h, minus);
        addRegion(plusX, y, btn, h, plus);
    }

    private void addRegion(int x, int y, int w, int h, Runnable action) {
        regions.add(new Region(x, y, w, h, action, null));
    }

    private void addRegion(int x, int y, int w, int h, Runnable action, Runnable rightAction) {
        regions.add(new Region(x, y, w, h, action, rightAction));
    }

    private boolean overPanel(double mx, double my) {
        for (Rect r : blockers) {
            if (r.contains(mx, my)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Selection
    // ------------------------------------------------------------------

    private void clearSelection() {
        selected = null;
        selectedGroup = null;
        enteredGroup = null;
        multi.clear();
    }

    private void selectElement(HudElement element, HudGroup owningGroup) {
        selected = element;
        selectedGroup = null;
        multi.clear();
        // Picking a member from inside the list counts as stepping into its group.
        if (owningGroup != null) {
            enteredGroup = owningGroup;
        }
    }

    private void selectGroup(HudGroup group) {
        selectedGroup = group;
        selected = null;
        multi.clear();
        if (enteredGroup != group) {
            enteredGroup = null;
        }
    }

    private void toggleMulti(HudElement element) {
        if (!multi.remove(element.key())) {
            multi.add(element.key());
        }
        selected = null;
        selectedGroup = null;
    }

    private void groupSelection() {
        if (multi.size() < 2) {
            return;
        }
        HudGroup group = LayoutStore.get().createGroup(multi);
        LayoutStore.get().save();
        multi.clear();
        selectGroup(group);
        expandedGroups.add(group.name);
    }

    private void ungroup() {
        if (selectedGroup == null) {
            return;
        }
        LayoutStore.get().removeGroup(selectedGroup);
        LayoutStore.get().save();
        selectedGroup = null;
        enteredGroup = null;
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    private void toggleVisible(HudElement element) {
        element.placement().visible = !element.placement().visible;
        LayoutStore.get().save();
    }

    private void resetElement(HudElement element) {
        LayoutStore.get().reset(element.key());
        LayoutStore.get().save();
    }

    private void resetGroup(HudGroup group) {
        for (String key : group.members) {
            LayoutStore.get().reset(key);
        }
        LayoutStore.get().save();
    }

    private List<HudElement> groupElements(HudGroup group) {
        List<HudElement> out = new ArrayList<>();
        for (String key : group.members) {
            HudElement element = HudManager.byKey(key);
            if (element != null) {
                out.add(element);
            }
        }
        return out;
    }

    private void adjustGroupScale(double delta) {
        for (HudElement element : groupElements(selectedGroup)) {
            adjustScale(element, delta);
        }
    }

    private void adjustGroupOrder(int delta) {
        for (HudElement element : groupElements(selectedGroup)) {
            adjustOrder(element, delta);
        }
    }

    private void setMode(HudElement element, Placement.Mode mode) {
        Placement placement = element.placement();
        if (placement.mode == mode || !element.supportsDocking()) {
            return;
        }
        if (mode == Placement.Mode.FREE) {
            currentRect(element).ifPresent(p -> {
                placement.anchor = Anchor.nearest(p.x() + p.w() / 2, p.y() + p.h() / 2, this.width, this.height);
                placement.offsetX = HudManager.offsetXFor(placement.anchor, p.x(), p.w(), this.width);
                placement.offsetY = HudManager.offsetYFor(placement.anchor, p.y(), p.h(), this.height);
            });
        }
        placement.mode = mode;
        LayoutStore.get().save();
    }

    private void setAnchor(HudElement element, Anchor anchor) {
        Placement placement = element.placement();
        placement.anchor = anchor;
        if (placement.mode == Placement.Mode.FREE) {
            placement.offsetX = 0;
            placement.offsetY = 0;
        }
        selected = element;
        LayoutStore.get().save();
    }

    private void adjustScale(HudElement element, double delta) {
        Placement p = element.placement();
        p.scale = clamp(p.scale + delta, 0.5, 2.5);
        LayoutStore.get().save();
    }

    private void adjustOpacity(HudElement element, double delta) {
        if (!element.supportsOpacity()) {
            return;
        }
        Placement p = element.placement();
        p.opacity = clamp(p.opacity + delta, 0.15, 1.0);
        LayoutStore.get().save();
    }

    private void adjustOrder(HudElement element, int delta) {
        Placement p = element.placement();
        p.order = Math.max(0, Math.min(1000, p.order + delta));
        LayoutStore.get().save();
    }

    private void adjustUiScale(double delta) {
        KuiConfig cfg = KuiConfig.get();
        cfg.uiScale = clamp(cfg.uiScale + delta, 0.5, 2.5);
        cfg.save();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private Optional<HudManager.Positioned> currentRect(HudElement element) {
        return frame.stream().filter(p -> p.element() == element).findFirst();
    }

    private HudManager.Positioned pick(double mouseX, double mouseY) {
        if (overPanel(mouseX, mouseY)) {
            return null;
        }
        HudManager.Positioned found = null;
        for (HudManager.Positioned p : frame) {
            if (p.element().isPositionable() && p.contains(mouseX, mouseY)) {
                found = p;
            }
        }
        return found;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private boolean onClick(double mouseX, double mouseY, int button) {
        for (Region region : regions) {
            if (!region.contains(mouseX, mouseY)) {
                continue;
            }
            if (button == 0) {
                region.action().run();
                return true;
            }
            if (button == 1 && region.rightAction() != null) {
                region.rightAction().run();
                return true;
            }
        }
        // The inspector's grip starts a panel drag rather than acting on the HUD.
        if (button == 0 && (selected != null || selectedGroup != null || multi.size() >= 2)
                && Draw.hovered(mouseX, mouseY, inspectorX() + 2, inspectorY() + 2, 12, 12)) {
            draggingInspector = true;
            inspectorGrabX = (int) mouseX - inspectorX();
            inspectorGrabY = (int) mouseY - inspectorY();
            return true;
        }
        if (overPanel(mouseX, mouseY)) {
            return true;
        }

        HudManager.Positioned hit = pick(mouseX, mouseY);
        if (hit == null) {
            if (button == 0) {
                clearSelection(); // Clicking bare screen steps out of everything.
            }
            return false;
        }
        HudElement element = hit.element();

        if (button == 1) {
            toggleVisible(element);
            return true;
        }
        if (button == 2) {
            if (element.hasStyles()) {
                element.cycleStyle();
                LayoutStore.get().save();
                return true;
            }
            return false;
        }
        if (button != 0) {
            return false;
        }

        if (Keys.isCtrlDown()) {
            toggleMulti(element);
            return true;
        }

        long now = System.currentTimeMillis();
        boolean doubleClick = element.key().equals(lastClickKey) && now - lastClickMs < DOUBLE_CLICK_MS;
        lastClickMs = now;
        lastClickKey = element.key();

        HudGroup group = LayoutStore.get().groupOf(element.key());
        if (group != null && group != enteredGroup) {
            if (doubleClick) {
                // Second click steps inside and grabs the member itself.
                enteredGroup = group;
                selectElement(element, group);
            } else {
                selectGroup(group);
                beginGroupDrag(group, mouseX, mouseY);
                return true;
            }
        } else {
            selectElement(element, group);
        }

        dragging = element;
        draggingGroup = false;
        dragOffsetX = (int) mouseX - hit.x();
        dragOffsetY = (int) mouseY - hit.y();
        return true;
    }

    private void beginGroupDrag(HudGroup group, double mouseX, double mouseY) {
        dragStartRects.clear();
        for (HudManager.Positioned p : frame) {
            if (group.contains(p.element().key())) {
                dragStartRects.put(p.element().key(), new Rect(p.x(), p.y(), p.w(), p.h()));
            }
        }
        dragStartBounds = groupBounds(group);
        dragMouseX = (int) mouseX;
        dragMouseY = (int) mouseY;
        draggingGroup = dragStartBounds != null;
        dragging = null;
    }

    private boolean onDrag(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (draggingInspector) {
            KuiConfig cfg = KuiConfig.get();
            cfg.inspectorX = Math.max(0, Math.min((int) mouseX - inspectorGrabX, this.width - INSPECTOR_W));
            cfg.inspectorY = Math.max(0, Math.min((int) mouseY - inspectorGrabY, this.height - 20));
            return true;
        }
        if (draggingGroup) {
            return dragGroup(mouseX, mouseY);
        }
        if (dragging == null) {
            return false;
        }
        HudManager.Positioned rect = currentRect(dragging).orElse(null);
        if (rect == null) {
            return false;
        }
        int w = rect.w();
        int h = rect.h();
        int x = snapX((int) mouseX - dragOffsetX, w, dragging, Set.of());
        int y = snapY((int) mouseY - dragOffsetY, h, dragging, Set.of());

        // The element decides what the point means: an anchor plus offset for a mod's element,
        // a delta from vanilla's own position for a vanilla one.
        dragging.moveTo(x, y, this.width, this.height);
        LayoutStore.get().markDirty();
        return true;
    }

    /** Moves every member by the same delta, snapping the group's bounding box as a whole. */
    private boolean dragGroup(double mouseX, double mouseY) {
        if (selectedGroup == null || dragStartBounds == null) {
            return false;
        }
        int rawDx = (int) mouseX - dragMouseX;
        int rawDy = (int) mouseY - dragMouseY;
        // The group's own members move with it, so they must not pull the bounding box around.
        Set<String> members = Set.copyOf(selectedGroup.members);
        int nx = snapX(dragStartBounds.x() + rawDx, dragStartBounds.w(), null, members);
        int ny = snapY(dragStartBounds.y() + rawDy, dragStartBounds.h(), null, members);
        int dx = nx - dragStartBounds.x();
        int dy = ny - dragStartBounds.y();

        for (Map.Entry<String, Rect> entry : dragStartRects.entrySet()) {
            HudElement element = HudManager.byKey(entry.getKey());
            if (element == null) {
                continue;
            }
            Rect start = entry.getValue();
            element.moveTo(start.x() + dx, start.y() + dy, this.width, this.height);
        }
        LayoutStore.get().markDirty();
        return true;
    }

    /** {@code exclude} holds keys that must not act as snap targets — a dragged group's own members. */
    private int snapX(int x, int w, HudElement moving, Set<String> exclude) {
        x = snap(x, SafeArea.MARGIN);
        x = snap(x, this.width - w - SafeArea.MARGIN);
        x = snap(x, (this.width - w) / 2);
        for (HudManager.Positioned other : frame) {
            if (other.element() == moving || !other.element().isPositionable()
                    || exclude.contains(other.element().key())) {
                continue;
            }
            x = snap(x, other.x());
            x = snap(x, other.x() + other.w() - w);
        }
        return x;
    }

    private int snapY(int y, int h, HudElement moving, Set<String> exclude) {
        y = snap(y, SafeArea.MARGIN);
        y = snap(y, this.height - h - SafeArea.MARGIN);
        y = snap(y, (this.height - h) / 2);
        for (HudManager.Positioned other : frame) {
            if (other.element() == moving || !other.element().isPositionable()
                    || exclude.contains(other.element().key())) {
                continue;
            }
            y = snap(y, other.y());
            y = snap(y, other.y() + other.h() + 3);
            y = snap(y, other.y() - h - 3);
        }
        return y;
    }

    private boolean onRelease() {
        if (draggingInspector) {
            draggingInspector = false;
            KuiConfig.get().save();
            return true;
        }
        if (dragging == null && !draggingGroup) {
            return false;
        }
        dragging = null;
        draggingGroup = false;
        dragStartRects.clear();
        LayoutStore.get().save();
        return true;
    }

    private boolean onKey(int keyCode) {
        int dx = switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> -1;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> 1;
            default -> 0;
        };
        int dy = switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> -1;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> 1;
            default -> 0;
        };
        if (dx == 0 && dy == 0) {
            return false;
        }
        int step = Keys.isShiftDown() ? 10 : 1;

        List<HudElement> targets = selectedGroup != null ? groupElements(selectedGroup)
                : selected != null ? List.of(selected) : List.of();
        if (targets.isEmpty()) {
            return false;
        }
        for (HudElement element : targets) {
            Placement placement = element.placement();
            if (placement.mode == Placement.Mode.DOCKED && element.supportsDocking()) {
                setMode(element, Placement.Mode.FREE);
            }
            placement.offsetX += dx * step;
            placement.offsetY += dy * step;
        }
        LayoutStore.get().markDirty();
        return true;
    }

    private static int snap(int value, int target) {
        return Math.abs(value - target) <= SNAP ? target : value;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        }
        if (showElements && Draw.hovered(mouseX, mouseY, 6, 6 + TOOL + 4, LIST_W, this.height - 40)) {
            // Whole rows per notch, clamped against the height measured this frame, so the list
            // cannot be scrolled into empty space and bounced back.
            int contentH = Math.min(this.height - (6 + TOOL + 4) - 6, 260) - 24;
            int max = Math.max(0, listContentHeight - contentH);
            listScroll = Math.max(0, Math.min(max, listScroll - (int) Math.signum(vertical) * ROW_H));
            return true;
        }
        HudManager.Positioned hit = pick(mouseX, mouseY);
        if (hit != null) {
            HudElement target = hit.element();
            if (selectedGroup != null && selectedGroup.contains(target.key())) {
                for (HudElement member : groupElements(selectedGroup)) {
                    if (Keys.isCtrlDown()) {
                        adjustOpacity(member, vertical > 0 ? 0.08 : -0.08);
                    } else {
                        adjustScale(member, vertical > 0 ? 0.1 : -0.1);
                    }
                }
                return true;
            }
            selected = target;
            if (Keys.isCtrlDown()) {
                adjustOpacity(target, vertical > 0 ? 0.08 : -0.08);
            } else {
                adjustScale(target, vertical > 0 ? 0.1 : -0.1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    //? if >=1.21.11 {
    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        return onClick(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        if (onDrag(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (onRelease()) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (onKey(input.key())) {
            return true;
        }
        return super.keyPressed(input);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return onClick(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (onDrag(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (onRelease()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (onKey(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    *///?}

    @Override
    public void close() {
        LayoutStore.get().save();
        KuiConfig.get().save();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
