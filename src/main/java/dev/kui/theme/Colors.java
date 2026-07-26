package dev.kui.theme;

/**
 * ARGB colour arithmetic. Every value in this class is a packed {@code 0xAARRGGBB} int, the
 * format {@code DrawContext} expects.
 */
public final class Colors {
    private Colors() {
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    /** Adds full opacity to a 0xRRGGBB value. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Blends two ARGB colours by {@code t} in [0,1]. */
    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return argb(
                (int) (alpha(a) + (alpha(b) - alpha(a)) * t),
                (int) (red(a) + (red(b) - red(a)) * t),
                (int) (green(a) + (green(b) - green(a)) * t),
                (int) (blue(a) + (blue(b) - blue(a)) * t));
    }

    /** Moves a colour toward white by {@code amt} in [0,1] (alpha preserved). */
    public static int lighten(int argb, float amt) {
        int r = red(argb);
        int g = green(argb);
        int b = blue(argb);
        return argb(alpha(argb),
                (int) (r + (255 - r) * amt),
                (int) (g + (255 - g) * amt),
                (int) (b + (255 - b) * amt));
    }

    /** Moves a colour toward black by {@code amt} in [0,1] (alpha preserved). */
    public static int darken(int argb, float amt) {
        return argb(alpha(argb),
                (int) (red(argb) * (1 - amt)),
                (int) (green(argb) * (1 - amt)),
                (int) (blue(argb) * (1 - amt)));
    }

    /** Replaces a colour's alpha channel outright. */
    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (clamp255(alpha) << 24);
    }

    /** Scales a colour's existing alpha by {@code factor} in [0,1]. */
    public static int scaleAlpha(int argb, double factor) {
        return withAlpha(argb, (int) Math.round(alpha(argb) * factor));
    }

    /**
     * Perceived brightness in [0,1] using the standard luma weights. Used to decide whether text
     * on top of a colour should be black or white.
     */
    public static float luminance(int argb) {
        return (0.2126f * red(argb) + 0.7152f * green(argb) + 0.0722f * blue(argb)) / 255f;
    }

    /** Black or white, whichever stays readable on {@code background}. */
    public static int contrastingText(int background) {
        return luminance(background) > 0.55f ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
