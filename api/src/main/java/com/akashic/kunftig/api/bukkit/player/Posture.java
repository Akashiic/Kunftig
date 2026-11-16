package com.akashic.kunftig.api.bukkit.player;

import com.akashic.kunftig.api.bukkit.display.Display;

/**
 * How and where a player sits or lies ({@link KunftigPlayers#setPosture}, API 15). Immutable.
 * <p>
 * The point is the feet of one who sits (the seat is about 0.6 block above them: vanilla's riding pose bends the
 * legs forward at the hip, 0.75 above the feet) and the middle of the body of one who lies, on the surface he lies
 * on. The yaw is Minecraft's (0 looks to +z, 90 to -x): where one who sits faces, where the head of one who lies
 * points. On a display ({@link #on}), point and yaw are of the display's frame: the point in blocks from its origin,
 * the yaw added to the display's.
 */
public final class Posture {

    /** Sitting. */
    public static final int SIT = 1;
    /** Lying. */
    public static final int LIE = 2;
    /** How high above the point the eyes of one who lies are, unless {@link #eyes} says otherwise. */
    public static final float LYING_EYES = 0.45f;

    private final int kind;
    private final Display display;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float eyes;

    private Posture(int kind, Display display, double x, double y, double z, float yaw, float eyes) {
        this.kind = kind;
        this.display = display;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.eyes = eyes;
    }

    /** Sitting with the feet at a point of the world, facing {@code yaw}. */
    public static Posture sit(double x, double y, double z, float yaw) {
        return new Posture(SIT, null, x, y, z, yaw, 1.62f);
    }

    /** Lying with the middle of the body at a point of the world, the head towards {@code yaw}. */
    public static Posture lie(double x, double y, double z, float yaw) {
        return new Posture(LIE, null, x, y, z, yaw, LYING_EYES);
    }

    /** This posture with its point and yaw read in the display's frame (null: the world's). */
    public Posture on(Display display) {
        return new Posture(kind, display, x, y, z, yaw, eyes);
    }

    /**
     * This posture with the eyes of the one who lies that high above the point (0 to 1.62; his own client lowers him
     * so that he looks from there and draws his model as much higher). Nothing changes for one who sits.
     */
    public Posture eyes(float height) {
        return new Posture(kind, display, x, y, z, yaw, Math.max(0, Math.min(1.62f, height)));
    }

    public int getKind() {
        return kind;
    }

    public boolean isSitting() {
        return kind == SIT;
    }

    public boolean isLying() {
        return kind == LIE;
    }

    /** The display whose frame the point is in, or null. */
    public Display getDisplay() {
        return display;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getEyes() {
        return eyes;
    }
}
