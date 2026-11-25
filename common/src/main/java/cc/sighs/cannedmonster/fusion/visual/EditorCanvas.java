package cc.sighs.cannedmonster.fusion.visual;

/**
 * Minimal GUI drawing surface so one editor layout can run on every supported version.
 * Implementations live in the version-specific screen adapters.
 */
public interface EditorCanvas {
    void fill(int x0, int y0, int x1, int y1, int color);

    void text(String text, int x, int y, int color, boolean shadow);

    int width(String text);

    default void border(int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y, x + 1, y + height, color);
        fill(x + width - 1, y, x + width, y + height, color);
    }

    default void line(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) return;
            int twice = error * 2;
            if (twice >= dy) {
                error += dy;
                x += sx;
            }
            if (twice <= dx) {
                error += dx;
                y += sy;
            }
        }
    }

    default void thickLine(int x0, int y0, int x1, int y1, int color) {
        line(x0, y0, x1, y1, color);
        line(x0 + 1, y0, x1 + 1, y1, color);
        line(x0, y0 + 1, x1, y1 + 1, color);
    }

    default void scissor(int x0, int y0, int x1, int y1) {
    }

    default void unscissor() {
    }
}
