package dev.kui.hud;

/** A screen rectangle in scaled GUI pixels. */
public record Rect(int x, int y, int w, int h) {
    public int centerX() {
        return x + w / 2;
    }

    public int centerY() {
        return y + h / 2;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public boolean overlaps(Rect other) {
        return x < other.x + other.w && x + w > other.x
                && y < other.y + other.h && y + h > other.y;
    }
}
