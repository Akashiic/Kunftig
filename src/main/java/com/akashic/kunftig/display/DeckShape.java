package com.akashic.kunftig.display;

/**
 * What can be walked on and bumped into on a display (API 12): boxes in the display's own frame (X across, Y up, Z to
 * its front), in blocks, at the size the display is drawn. A deck plank, a bulwark, a step, a mast. They travel as
 * shorts of 1/32 block ({@link #pack}). No Minecraft classes; immutable.
 */
public final class DeckShape {

    public static final int MAX_BOXES = 2048;
    /** Shorts per block on the wire. */
    public static final double UNITS = 32;
    /** Farthest a box may reach from the display's origin, in blocks. */
    public static final double MAX_REACH = Short.MAX_VALUE / UNITS;

    public static final DeckShape EMPTY = new DeckShape(new short[0]);

    private final short[] packed;
    final int size;
    final double[] minX;
    final double[] minY;
    final double[] minZ;
    final double[] maxX;
    final double[] maxY;
    final double[] maxZ;
    /** A sphere about the origin holding every box. */
    final double radius;

    private DeckShape(short[] packed) {
        this.packed = packed;
        size = packed.length / 6;
        minX = new double[size];
        minY = new double[size];
        minZ = new double[size];
        maxX = new double[size];
        maxY = new double[size];
        maxZ = new double[size];
        double far = 0;
        for (int i = 0; i < size; i++) {
            minX[i] = packed[i * 6] / UNITS;
            minY[i] = packed[i * 6 + 1] / UNITS;
            minZ[i] = packed[i * 6 + 2] / UNITS;
            maxX[i] = packed[i * 6 + 3] / UNITS;
            maxY[i] = packed[i * 6 + 4] / UNITS;
            maxZ[i] = packed[i * 6 + 5] / UNITS;
            double x = Math.max(Math.abs(minX[i]), Math.abs(maxX[i]));
            double y = Math.max(Math.abs(minY[i]), Math.abs(maxY[i]));
            double z = Math.max(Math.abs(minZ[i]), Math.abs(maxZ[i]));
            far = Math.max(far, Math.sqrt(x * x + y * y + z * z));
        }
        radius = far;
    }

    /**
     * The shape of packed boxes (six shorts each: min x, y, z, max x, y, z), or null when malformed: a length that is
     * not a multiple of six, more than {@link #MAX_BOXES}, or a box with a max below its min.
     */
    public static DeckShape of(short[] packed) {
        if (packed == null || packed.length % 6 != 0 || packed.length / 6 > MAX_BOXES) {
            return null;
        }
        for (int i = 0; i < packed.length; i += 6) {
            if (packed[i + 3] < packed[i] || packed[i + 4] < packed[i + 1] || packed[i + 5] < packed[i + 2]) {
                return null;
            }
        }
        return packed.length == 0 ? EMPTY : new DeckShape(packed.clone());
    }

    /**
     * Boxes in blocks ({@code {minX, minY, minZ, maxX, maxY, maxZ}} each) as wire shorts, rounded to 1/32 block.
     *
     * @throws IllegalArgumentException for too many boxes, a box that is not six finite numbers with min &lt;= max,
     *                                  or one farther than {@link #MAX_REACH} from the origin
     */
    public static short[] pack(double[][] boxes) {
        if (boxes == null) {
            return new short[0];
        }
        if (boxes.length > MAX_BOXES) {
            throw new IllegalArgumentException("at most " + MAX_BOXES + " boxes: " + boxes.length);
        }
        short[] out = new short[boxes.length * 6];
        for (int i = 0; i < boxes.length; i++) {
            double[] b = boxes[i];
            if (b == null || b.length != 6) {
                throw new IllegalArgumentException("a box is {minX, minY, minZ, maxX, maxY, maxZ}");
            }
            for (int k = 0; k < 6; k++) {
                if (!(Math.abs(b[k]) <= MAX_REACH)) {
                    throw new IllegalArgumentException("box " + i + " must be within " + MAX_REACH + " blocks");
                }
                out[i * 6 + k] = (short) Math.round(b[k] * UNITS);
            }
            if (b[3] < b[0] || b[4] < b[1] || b[5] < b[2]) {
                throw new IllegalArgumentException("box " + i + " has a max below its min");
            }
        }
        return out;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** The wire form (a copy). */
    public short[] packed() {
        return packed.clone();
    }

    /** Blocks from the origin to the farthest corner. */
    public double radius() {
        return radius;
    }
}
