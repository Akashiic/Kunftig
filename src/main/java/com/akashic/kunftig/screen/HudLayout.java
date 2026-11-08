package com.akashic.kunftig.screen;

/** Where a HUD element goes and how opaque it is (API 11). No Minecraft classes. */
public final class HudLayout {

    private HudLayout() {}

    /**
     * The top-left corner of an element {@code w} x {@code h} on a screen {@code screenW} x {@code screenH}, aligned
     * to its anchor ({@link HudWire#TOP_LEFT} ...) and moved by the offset.
     */
    public static int[] corner(int anchor, int screenW, int screenH, int w, int h, int dx, int dy) {
        int column = anchor % 3; // 0 left, 1 middle, 2 right
        int row = anchor / 3; // 0 top, 1 middle, 2 bottom
        int x = column == 0 ? 0 : column == 1 ? (screenW - w) / 2 : screenW - w;
        int y = row == 0 ? 0 : row == 1 ? (screenH - h) / 2 : screenH - h;
        return new int[] { x + dx, y + dy };
    }

    /**
     * Opacity 0..1 of an element {@code age} ticks old: 1, then fading over the last {@code fade} ticks of its
     * {@code duration} (0: forever); 0 once it is over.
     */
    public static float alpha(long age, int duration, int fade) {
        if (duration <= 0) {
            return 1;
        }
        if (age >= duration) {
            return 0;
        }
        long left = duration - age;
        return fade <= 0 || left >= fade ? 1 : left / (float) fade;
    }
}
