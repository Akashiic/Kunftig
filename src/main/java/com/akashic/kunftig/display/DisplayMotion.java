package com.akashic.kunftig.display;

/**
 * Where a display is on the client, tick by tick (API 9): it jumps, glides to a point over some ticks (MOVE with
 * {@code glideTicks}), or flies by a motion per tick (MOTION), and the frame in between is interpolated from the last
 * tick, like an entity. Angles glide the short way round. No Minecraft classes; client thread only.
 */
public final class DisplayMotion {

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private float roll;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;
    private float lastRoll;
    private double vx;
    private double vy;
    private double vz;
    private double toX;
    private double toY;
    private double toZ;
    private float toYaw;
    private float toPitch;
    private float toRoll;
    private int glide;

    /** Jumps there: no glide, no flight. */
    public void jump(double nx, double ny, double nz, float nyaw, float npitch, float nroll) {
        x = lastX = nx;
        y = lastY = ny;
        z = lastZ = nz;
        yaw = lastYaw = nyaw;
        pitch = lastPitch = npitch;
        roll = lastRoll = nroll;
        vx = 0;
        vy = 0;
        vz = 0;
        glide = 0;
    }

    /** Glides there over {@code ticks} (0 or less: jumps); stops a flight. */
    public void glide(double nx, double ny, double nz, float nyaw, float npitch, float nroll, int ticks) {
        if (ticks <= 0) {
            jump(nx, ny, nz, nyaw, npitch, nroll);
            return;
        }
        vx = 0;
        vy = 0;
        vz = 0;
        toX = nx;
        toY = ny;
        toZ = nz;
        toYaw = yaw + wrap(nyaw - yaw);
        toPitch = pitch + wrap(npitch - pitch);
        toRoll = roll + wrap(nroll - roll);
        glide = ticks;
    }

    /** Flies from there by {@code vx, vy, vz} blocks per tick (the rotation stays). */
    public void fly(double nx, double ny, double nz, double nvx, double nvy, double nvz) {
        x = lastX = nx;
        y = lastY = ny;
        z = lastZ = nz;
        vx = nvx;
        vy = nvy;
        vz = nvz;
        glide = 0;
    }

    /** One client tick. */
    public void tick() {
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastPitch = pitch;
        lastRoll = roll;
        if (glide > 0) {
            x += (toX - x) / glide;
            y += (toY - y) / glide;
            z += (toZ - z) / glide;
            yaw += (toYaw - yaw) / glide;
            pitch += (toPitch - pitch) / glide;
            roll += (toRoll - roll) / glide;
            glide--;
        } else {
            x += vx;
            y += vy;
            z += vz;
        }
    }

    public boolean moving() {
        return glide > 0 || vx != 0 || vy != 0 || vz != 0;
    }

    public double x(float partial) {
        return lastX + (x - lastX) * partial;
    }

    public double y(float partial) {
        return lastY + (y - lastY) * partial;
    }

    public double z(float partial) {
        return lastZ + (z - lastZ) * partial;
    }

    public float yaw(float partial) {
        return lastYaw + (yaw - lastYaw) * partial;
    }

    public float pitch(float partial) {
        return lastPitch + (pitch - lastPitch) * partial;
    }

    public float roll(float partial) {
        return lastRoll + (roll - lastRoll) * partial;
    }

    /** An angle difference in -180..180. */
    static float wrap(float degrees) {
        float d = degrees % 360;
        if (d >= 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }
}
