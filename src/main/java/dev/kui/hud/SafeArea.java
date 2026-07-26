package dev.kui.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

/**
 * The region of the screen a docked element may occupy, per anchor.
 *
 * <p>Vanilla owns parts of the screen, and an auto-stacking layout that ignored them would happily
 * dump a now-playing card on top of the hotbar. Each anchor therefore gets its own content box
 * inset past whatever vanilla draws there.
 *
 * <p>Covered today: the hotbar and status bars (bottom centre), status-effect icons (top right)
 * and the chat box while it is open (bottom left). Anything not modelled here simply gets the
 * default margin — free positioning is always available as the escape hatch.
 */
public final class SafeArea {
    /** Breathing room from the screen edge, applied to every anchor. */
    public static final int MARGIN = 4;

    /** Hotbar plus the health/hunger/armour rows and the XP bar above it. */
    private static final int HOTBAR_RESERVE = 40;

    /** One row of status-effect icons in the top-right corner. */
    private static final int EFFECTS_RESERVE = 26;

    /** Roughly the height of the chat box when it is open. */
    private static final int CHAT_RESERVE = 100;

    public final int x;
    public final int y;
    public final int width;
    public final int height;

    private SafeArea(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * The content box for {@code anchor} on a screen of the given size.
     *
     * @param editing when true, vanilla's reserved regions are ignored so the layout editor shows
     *                the whole screen as available
     */
    public static SafeArea forAnchor(Anchor anchor, int screenWidth, int screenHeight, boolean editing) {
        int top = MARGIN;
        int bottom = MARGIN;
        int left = MARGIN;
        int right = MARGIN;

        if (!editing) {
            MinecraftClient client = MinecraftClient.getInstance();

            // The hotbar and its status rows sit in the bottom-centre third.
            if (anchor.isBottom() && anchor.col == 1) {
                bottom += HOTBAR_RESERVE;
            }
            // Status-effect icons render in the top-right corner.
            if (anchor == Anchor.TOP_RIGHT && client.player != null
                    && !client.player.getStatusEffects().isEmpty()) {
                top += EFFECTS_RESERVE;
            }
            // Keep the bottom-left clear while the player is typing.
            if (anchor == Anchor.BOTTOM_LEFT && client.currentScreen instanceof ChatScreen) {
                bottom += CHAT_RESERVE;
            }
        }

        int w = Math.max(1, screenWidth - left - right);
        int h = Math.max(1, screenHeight - top - bottom);
        return new SafeArea(left, top, w, h);
    }
}
