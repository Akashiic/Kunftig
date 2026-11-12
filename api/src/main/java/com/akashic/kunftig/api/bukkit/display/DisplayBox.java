package com.akashic.kunftig.api.bukkit.display;

/**
 * A solid box of a display ({@link Display#setCollision}, API 12), in blocks, in the display's own frame: X across,
 * Y up, Z to its front, from the model's origin, at the size the model is drawn (the scale already applied). The
 * frame turns with the display's yaw, pitch and roll. Immutable.
 */
public final class DisplayBox {

    /** Farthest a box may reach from the origin, in blocks. */
    public static final double MAX_REACH = 1023;

    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    /**
     * @throws IllegalArgumentException when a number is not finite, a max is below its min or the box reaches
     *                                  farther than {@link #MAX_REACH} blocks from the origin
     */
    public DisplayBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (double v : new double[] { minX, minY, minZ, maxX, maxY, maxZ }) {
            if (!(Math.abs(v) <= MAX_REACH)) {
                throw new IllegalArgumentException("box coordinates must be within " + MAX_REACH + " blocks: " + v);
            }
        }
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("box max below min");
        }
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    @Override
    public String toString() {
        return "DisplayBox[" + minX + ", " + minY + ", " + minZ + " .. " + maxX + ", " + maxY + ", " + maxZ + "]";
    }
}
