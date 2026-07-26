package dev.kui.theme;

import dev.kui.config.KuiConfig;

/**
 * The shared palette. Colours are named by <em>role</em> rather than by hue, so a mod written
 * against {@code Theme.TEXT} and {@code Theme.accent()} follows the user's chosen accent
 * automatically instead of hard-coding a shade.
 *
 * <p>Every mod may override the accent for its own elements ({@link #accent(String)}); anything
 * that does not falls back to the single global accent, which is what makes a stack of elements
 * from four different mods read as one interface.
 */
public final class Theme {
    // ----- neutrals -------------------------------------------------------
    // Not pure greys: every neutral carries a slight blue cast, so KUI's own chrome reads as one
    // cool-toned interface rather than as grey boxes that happen to have a coloured accent in them.
    /** Full-screen scrim behind a modal screen. */
    public static final int OVERLAY = 0xC8000000;
    /** Lighter scrim, for overlays that must stay readable over the game. */
    public static final int SCRIM = 0x73020509;
    /** Window/background fill. */
    public static final int BG = 0xF00C1017;
    /** Panel surface — the body of a card or HUD element. */
    public static final int PANEL = 0xFF11161F;
    /** Raised surface sitting on a panel (list rows, buttons). */
    public static final int CARD = 0xFF1B2330;
    /** Hovered raised surface. */
    public static final int CARD_HOVER = 0xFF27313F;
    /** Hairline separator / border. */
    public static final int BORDER = 0x55334055;
    /** Primary text. */
    public static final int TEXT = 0xFFFFFFFF;
    /** Secondary text. */
    public static final int SUBTEXT = 0xFFB5C0CE;
    /** De-emphasised text and disabled controls. */
    public static final int MUTED = 0xFF74818F;
    /** Unfilled portion of a progress/track bar. */
    public static final int TRACK = 0xFF3A4757;
    /** Drop shadow. */
    public static final int SHADOW = 0x80000000;

    // ----- status ---------------------------------------------------------
    public static final int SUCCESS = 0xFF3FB950;
    public static final int WARNING = 0xFFD29922;
    public static final int DANGER = 0xFFF85149;
    public static final int INFO = 0xFF8CC8FF;

    private Theme() {
    }

    // ----- accent ---------------------------------------------------------

    /** The global accent, fully opaque. */
    public static int accent() {
        return Colors.opaque(KuiConfig.get().accentColor);
    }

    /** The accent for {@code modId}, falling back to the global accent. */
    public static int accent(String modId) {
        return Colors.opaque(KuiConfig.get().accentFor(modId));
    }

    /** Brighter accent, for hover states and active text. */
    public static int accentHover() {
        return Colors.lighten(accent(), 0.16f);
    }

    public static int accentHover(String modId) {
        return Colors.lighten(accent(modId), 0.16f);
    }

    /** Slightly darker accent, for the resting state of a primary button. */
    public static int accentButton() {
        return Colors.darken(accent(), 0.10f);
    }

    public static int accentButton(String modId) {
        return Colors.darken(accent(modId), 0.10f);
    }

    // ----- accent-aware surfaces -----------------------------------------

    public static int panel() {
        return tint(PANEL, accent(), 0.14f);
    }

    public static int panel(String modId) {
        return tint(PANEL, accent(modId), 0.14f);
    }

    public static int card() {
        return tint(CARD, accent(), 0.16f);
    }

    public static int card(String modId) {
        return tint(CARD, accent(modId), 0.16f);
    }

    public static int cardHover() {
        return tint(CARD_HOVER, accent(), 0.18f);
    }

    public static int cardHover(String modId) {
        return tint(CARD_HOVER, accent(modId), 0.18f);
    }

    private static int tint(int base, int accent, float amount) {
        return KuiConfig.get().accentTintCards ? Colors.lerp(base, accent, amount) : base;
    }

    // ----- chrome ---------------------------------------------------------
    // KUI's own screens float over live gameplay, so their surfaces are translucent versions of
    // the same roles a HUD card uses. Named here rather than spelled out at each call site, so a
    // panel in the layout editor and a panel in a mod's settings screen cannot drift apart.

    /** Body of a floating panel in a KUI screen. */
    public static int window() {
        return Colors.withAlpha(tint(BG, accent(), 0.10f), 0xE8);
    }

    /** Resting fill of a small control — button, stepper, key cap. */
    public static int control() {
        return Colors.withAlpha(card(), 0xD8);
    }

    /** Hovered fill of the same control. */
    public static int controlHover() {
        return Colors.withAlpha(cardHover(), 0xF2);
    }

    /** Hairline used to separate sections inside a panel. */
    public static int rule() {
        return Colors.withAlpha(TEXT, 0x18);
    }

    // ----- scale ----------------------------------------------------------

    /** The user's global UI multiplier, applied on top of each element's own scale. */
    public static float uiScale() {
        return (float) KuiConfig.get().uiScale;
    }
}
