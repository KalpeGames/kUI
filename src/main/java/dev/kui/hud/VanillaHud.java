package dev.kui.hud;

import dev.kui.Kui;
import dev.kui.config.LayoutStore;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Vanilla's HUD, exposed to the layout editor.
 *
 * <p>Each entry wraps one of Fabric's {@code VanillaHudElements} so the player can nudge, resize or
 * hide it alongside their mods' elements — the hotbar and the now-playing card end up arranged in
 * the same screen, by the same controls.
 *
 * <p>The rectangles here are vanilla's nominal geometry, reconstructed from how {@code InGameHud}
 * lays out at a given scaled screen size. They are a <em>fallback</em>: while the layout editor is
 * open each element's real bounds are measured from what it actually draws
 * ({@link VanillaHudElement#defaultRect}), so these values only stand in for an element that is
 * drawing nothing at the moment — a boss bar with no boss, a scoreboard with no sidebar — where
 * there is no box to measure but the user still needs somewhere to grab.
 */
public final class VanillaHud {
    /** Half the width of the hotbar and the bars that sit above it. */
    private static final int BAR_HALF = 91;

    private static final List<VanillaHudElement> ELEMENTS = new ArrayList<>();

    private VanillaHud() {
    }

    /**
     * Registers every supported vanilla element and installs its transform. Called once by
     * {@code KuiClient}; safe to call before any mod registers its own elements.
     */
    public static void register() {
        // Bottom cluster, ordered as vanilla stacks it upward from the hotbar.
        add(VanillaHudElements.HOTBAR, "Hotbar", 10,
                (w, h) -> new Rect(w / 2 - BAR_HALF, h - 22, BAR_HALF * 2, 22));
        add(VanillaHudElements.INFO_BAR, "Experience / locator bar", 11,
                (w, h) -> new Rect(w / 2 - BAR_HALF, h - 32, BAR_HALF * 2, 5));
        add(VanillaHudElements.EXPERIENCE_LEVEL, "Experience level", 12,
                (w, h) -> new Rect(w / 2 - 10, h - 37, 20, 9));
        add(VanillaHudElements.HEALTH_BAR, "Health", 13,
                (w, h) -> new Rect(w / 2 - BAR_HALF, h - 39, 81, 10));
        add(VanillaHudElements.FOOD_BAR, "Hunger", 14,
                (w, h) -> new Rect(w / 2 + 10, h - 39, 81, 10));
        add(VanillaHudElements.ARMOR_BAR, "Armour", 15,
                (w, h) -> new Rect(w / 2 - BAR_HALF, h - 49, 81, 10));
        add(VanillaHudElements.AIR_BAR, "Air", 16,
                (w, h) -> new Rect(w / 2 + 10, h - 49, 81, 10));
        add(VanillaHudElements.MOUNT_HEALTH, "Mount health", 17,
                (w, h) -> new Rect(w / 2 + 10, h - 39, 81, 10));
        add(VanillaHudElements.HELD_ITEM_TOOLTIP, "Held item name", 18,
                (w, h) -> new Rect(w / 2 - 60, h - 59, 120, 10));
        add(VanillaHudElements.OVERLAY_MESSAGE, "Action bar text", 19,
                (w, h) -> new Rect(w / 2 - 60, h - 68, 120, 10));

        // Centre and edges.
        add(VanillaHudElements.CROSSHAIR, "Crosshair", 20,
                (w, h) -> new Rect(w / 2 - 7, h / 2 - 7, 15, 15));
        add(VanillaHudElements.TITLE_AND_SUBTITLE, "Title text", 21,
                (w, h) -> new Rect(w / 2 - 80, h / 2 - 20, 160, 30));
        add(VanillaHudElements.SUBTITLES, "Sound subtitles", 22,
                (w, h) -> new Rect(w - 130, h - 70, 120, 30));

        // Top and sides.
        add(VanillaHudElements.BOSS_BAR, "Boss bar", 23,
                (w, h) -> new Rect(w / 2 - BAR_HALF, 12, BAR_HALF * 2, 20));
        add(VanillaHudElements.STATUS_EFFECTS, "Status effects", 24,
                (w, h) -> new Rect(w - 26, 1, 25, 25));
        add(VanillaHudElements.SCOREBOARD, "Scoreboard", 25,
                (w, h) -> new Rect(w - 100, h / 2 - 30, 96, 60));
        add(VanillaHudElements.PLAYER_LIST, "Player list", 26,
                (w, h) -> new Rect(w / 2 - 80, 10, 160, 60));
        add(VanillaHudElements.CHAT, "Chat", 27,
                (w, h) -> new Rect(2, h - 48, 320, 40));

        for (VanillaHudElement element : ELEMENTS) {
            element.install();
            HudManager.register(element);
        }
        seedGroups();
        Kui.LOGGER.info("[KUI] Made {} vanilla HUD elements movable", ELEMENTS.size());
    }

    /**
     * Groups the bottom cluster out of the box.
     *
     * <p>The hotbar and the bars welded to it are one thing as far as a player is concerned —
     * moving the hotbar without the health and hunger bars following it is almost never what
     * anyone wants. Shipping it pre-grouped means the common case ("shift the whole bottom bar
     * up a bit") is one drag, while stepping inside the group still allows the rare case.
     */
    private static void seedGroups() {
        LayoutStore store = LayoutStore.get();
        if (store.seededDefaultGroups) {
            return;
        }
        List<String> hotbar = Stream.of(
                        VanillaHudElements.HOTBAR,
                        VanillaHudElements.INFO_BAR,
                        VanillaHudElements.EXPERIENCE_LEVEL,
                        VanillaHudElements.HEALTH_BAR,
                        VanillaHudElements.FOOD_BAR,
                        VanillaHudElements.ARMOR_BAR,
                        VanillaHudElements.AIR_BAR,
                        VanillaHudElements.MOUNT_HEALTH,
                        VanillaHudElements.HELD_ITEM_TOOLTIP)
                .map(Identifier::toString)
                .toList();
        store.seedGroup("Hotbar", hotbar);
        store.seededDefaultGroups = true;
        store.save();
    }

    private static void add(Identifier vanillaId, String displayName, int order,
                            BiFunction<Integer, Integer, Rect> rect) {
        ELEMENTS.add(new VanillaHudElement(vanillaId, displayName, rect, order));
    }

    public static List<VanillaHudElement> elements() {
        return List.copyOf(ELEMENTS);
    }
}
