package com.akashic.kunftig.blade;

/**
 * A drive or summoned sword as the client moves it between server messages, tick for tick as SlashBlade's
 * {@code EntityAbstractSummonedSword.tick} (Resharped, MIT code) does in the air: the motion is clipped against blocks,
 * the position advances by it, yaw and pitch go a fifth of the way toward its direction (arrow convention: yaw
 * {@code atan2(vx, vz)}, pitch {@code atan2(vy, horizontal)}), then the motion is multiplied by 0.99 (no gravity). A
 * drive hitting a block ends; a sword lodges 0.05 blocks short of the hit and stays. Stuck swords follow their entity
 * (the client does that). No Minecraft classes.
 */
public final class EffectProjectile {

    /** Ticks a client keeps a projectile the server forgot (SlashBlade removes them after 100 in the air). */
    public static final int MAX_AGE = 1200;

    /** The point where the segment {@code from -> to} first enters a solid block, or null. */
    public interface BlockClip {

        double[] clip(double[] from, double[] to);
    }

    public final int id;
    public final int kind;
    public final String namespace;
    public final String effect;
    public final float roll;
    public final int color;
    public final float lifetime;

    public final double[] pos = new double[3];
    public final double[] prevPos = new double[3];
    public final double[] motion = new double[3];
    public float yaw;
    public float pitch;
    public float prevYaw;
    public float prevPitch;
    /** Ticks since the projectile appeared (SlashBlade's {@code tickCount}). */
    public int age;
    /** Ticks since the last server message. */
    public int idle;
    public boolean inGround;
    /** The entity a sword is stuck in, or -1. */
    public int stuckIn = -1;
    public float offsetYaw;
    public boolean dead;

    public EffectProjectile(EffectWire.Projectile p) {
        id = p.id;
        kind = p.kind;
        namespace = p.namespace;
        effect = p.effect;
        roll = p.roll;
        color = p.color;
        lifetime = p.lifetime;
        set(p.x, p.y, p.z, p.vx, p.vy, p.vz, p.yaw, p.pitch, true);
    }

    /** A server MOVE: the new state, without interpolating from the old one. */
    public void move(EffectWire.Move m) {
        set(m.x, m.y, m.z, m.vx, m.vy, m.vz, m.yaw, m.pitch, false);
        inGround = false;
        stuckIn = -1;
    }

    public void stick(EffectWire.Stick s) {
        stuckIn = s.entityId;
        offsetYaw = s.offsetYaw;
        idle = 0;
    }

    private void set(double x, double y, double z, double vx, double vy, double vz, float yaw, float pitch,
        boolean snap) {
        pos[0] = x;
        pos[1] = y;
        pos[2] = z;
        motion[0] = vx;
        motion[1] = vy;
        motion[2] = vz;
        this.yaw = yaw;
        this.pitch = pitch;
        if (snap) {
            System.arraycopy(pos, 0, prevPos, 0, 3);
            prevYaw = yaw;
            prevPitch = pitch;
        }
        idle = 0;
    }

    /** One tick. {@code clip} may be null (no blocks). Stuck swords only age. */
    public void tick(BlockClip clip) {
        age++;
        idle++;
        System.arraycopy(pos, 0, prevPos, 0, 3);
        prevYaw = yaw;
        prevPitch = pitch;
        if (idle > MAX_AGE || kind == EffectWire.KIND_DRIVE && age > lifetime) {
            dead = true;
            return;
        }
        if (stuckIn >= 0 || inGround) {
            return;
        }
        double[] to = { pos[0] + motion[0], pos[1] + motion[1], pos[2] + motion[2] };
        double[] hit = clip == null ? null : clip.clip(pos.clone(), to);
        if (hit != null) {
            if (kind == EffectWire.KIND_DRIVE) {
                dead = true;
                return;
            }
            double dx = hit[0] - pos[0];
            double dy = hit[1] - pos[1];
            double dz = hit[2] - pos[2];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0) {
                pos[0] -= dx / len * 0.05;
                pos[1] -= dy / len * 0.05;
                pos[2] -= dz / len * 0.05;
            }
            motion[0] = dx;
            motion[1] = dy;
            motion[2] = dz;
            inGround = true;
            return;
        }
        double mx = motion[0];
        double my = motion[1];
        double mz = motion[2];
        pos[0] += mx;
        pos[1] += my;
        pos[2] += mz;
        if (mx != 0 || my != 0 || mz != 0) {
            float targetYaw = (float) Math.toDegrees(Math.atan2(mx, mz));
            float targetPitch = (float) Math.toDegrees(Math.atan2(my, Math.sqrt(mx * mx + mz * mz)));
            yaw = prevYaw + 0.2F * wrap(targetYaw - prevYaw);
            pitch = prevPitch + 0.2F * wrap(targetPitch - prevPitch);
        }
        motion[0] *= 0.99;
        motion[1] *= 0.99;
        motion[2] *= 0.99;
    }

    /** The drive's alpha: SlashBlade's {@code max(0, 0.75 - t^4)}, by whole ticks. */
    public double driveAlpha() {
        double r = Math.min(lifetime, Math.max(0, lifetime - age)) / lifetime;
        return Math.max(0, -Math.pow(r - 1, 4.0) + 0.75);
    }

    public static float wrap(float degrees) {
        float d = degrees % 360F;
        if (d >= 180F) {
            d -= 360F;
        }
        if (d < -180F) {
            d += 360F;
        }
        return d;
    }

    /** {@code a + t * wrap(b - a)}: Minecraft's {@code rotLerp}. */
    public static float rotLerp(float t, float a, float b) {
        return a + t * wrap(b - a);
    }
}
