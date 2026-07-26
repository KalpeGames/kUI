package dev.kui.hud;

/**
 * One of the nine screen anchor points an element can be attached to.
 *
 * <p>Anchoring beats storing a raw screen fraction: an element whose own size changes — a track
 * title getting longer, a chunk counter gaining a digit — stays welded to its corner instead of
 * drifting, and the layout survives a window resize without recomputation.
 */
public enum Anchor {
    TOP_LEFT(0, 0),
    TOP_CENTER(1, 0),
    TOP_RIGHT(2, 0),
    MIDDLE_LEFT(0, 1),
    MIDDLE_CENTER(1, 1),
    MIDDLE_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2),
    BOTTOM_CENTER(1, 2),
    BOTTOM_RIGHT(2, 2);

    /** 0 = left, 1 = centre, 2 = right. */
    public final int col;
    /** 0 = top, 1 = middle, 2 = bottom. */
    public final int row;

    Anchor(int col, int row) {
        this.col = col;
        this.row = row;
    }

    public static Anchor of(int col, int row) {
        for (Anchor a : values()) {
            if (a.col == col && a.row == row) {
                return a;
            }
        }
        return TOP_LEFT;
    }

    public boolean isTop() {
        return row == 0;
    }

    public boolean isBottom() {
        return row == 2;
    }

    /** Elements docked to a bottom anchor stack upward, so the newest sits above the previous. */
    public int stackDirection() {
        return isBottom() ? -1 : 1;
    }

    /**
     * The x of an element of width {@code w} placed at this anchor within the given content box.
     */
    public int originX(int boxX, int boxWidth, int w) {
        return switch (col) {
            case 0 -> boxX;
            case 2 -> boxX + boxWidth - w;
            default -> boxX + (boxWidth - w) / 2;
        };
    }

    /**
     * The y of an element of height {@code h} placed at this anchor within the given content box.
     */
    public int originY(int boxY, int boxHeight, int h) {
        return switch (row) {
            case 0 -> boxY;
            case 2 -> boxY + boxHeight - h;
            default -> boxY + (boxHeight - h) / 2;
        };
    }

    /** The anchor point itself, used to measure a free element's offset from its corner. */
    public int anchorX(int boxX, int boxWidth) {
        return switch (col) {
            case 0 -> boxX;
            case 2 -> boxX + boxWidth;
            default -> boxX + boxWidth / 2;
        };
    }

    public int anchorY(int boxY, int boxHeight) {
        return switch (row) {
            case 0 -> boxY;
            case 2 -> boxY + boxHeight;
            default -> boxY + boxHeight / 2;
        };
    }

    /** The anchor whose point is nearest to (x, y) on a screen of the given size. */
    public static Anchor nearest(int x, int y, int screenWidth, int screenHeight) {
        int col = x < screenWidth / 3 ? 0 : x < screenWidth * 2 / 3 ? 1 : 2;
        int row = y < screenHeight / 3 ? 0 : y < screenHeight * 2 / 3 ? 1 : 2;
        return of(col, row);
    }

    public String displayName() {
        String vertical = switch (row) {
            case 0 -> "Top";
            case 2 -> "Bottom";
            default -> "Middle";
        };
        String horizontal = switch (col) {
            case 0 -> "left";
            case 2 -> "right";
            default -> "centre";
        };
        return vertical + " " + horizontal;
    }
}
