package com.akashic.kunftig.fishing;

/**
 * Minecraft 1.16+'s "open water" rule: treasure can only be caught when the 5x4x5 area around the bobber (from one
 * block below to two above) is made of whole layers of water source blocks with only air or lily pads above them.
 * Each horizontal layer must be uniform; layers go from water (bottom) to air (top), never back.
 */
public final class OpenWater {

    /** What one block (or one uniform layer) is for the rule. */
    public enum Type {
        /** Air or a lily pad. */
        ABOVE_WATER,
        /** A water source block with no collision. */
        INSIDE_WATER,
        INVALID
    }

    /** Classifies the block at a position. */
    public interface Probe {

        Type typeAt(int x, int y, int z);
    }

    private OpenWater() {}

    /** Whether the hook at block (x, y, z) is in open water. */
    public static boolean isOpenWater(Probe probe, int x, int y, int z) {
        Type previous = Type.INVALID;
        for (int dy = -1; dy <= 2; dy++) {
            Type layer = layerType(probe, x, y + dy, z);
            switch (layer) {
                case ABOVE_WATER:
                    if (previous == Type.INVALID) {
                        return false;
                    }
                    break;
                case INSIDE_WATER:
                    if (previous == Type.ABOVE_WATER) {
                        return false;
                    }
                    break;
                case INVALID:
                default:
                    return false;
            }
            previous = layer;
        }
        return true;
    }

    /** The type of the 5x5 layer centered on (x, z), or INVALID when it is mixed. */
    static Type layerType(Probe probe, int x, int y, int z) {
        Type layer = null;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Type type = probe.typeAt(x + dx, y, z + dz);
                if (type == Type.INVALID || (layer != null && layer != type)) {
                    return Type.INVALID;
                }
                layer = type;
            }
        }
        return layer;
    }
}
