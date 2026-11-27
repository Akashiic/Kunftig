package com.akashic.kunftig.modern.math;

import java.util.Random;

import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * Immutable double vector with the modern {@code net.minecraft.world.phys.Vec3} API, for porting modern code. Convert
 * with {@link #toVanilla()} / {@link #of(net.minecraft.util.Vec3)} at MC boundaries.
 */
public final class Vec3 {

    public static final Vec3 ZERO = new Vec3(0D, 0D, 0D);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vec3 of(net.minecraft.util.Vec3 vanilla) {
        return new Vec3(vanilla.xCoord, vanilla.yCoord, vanilla.zCoord);
    }

    /**
     * Feet position of an entity (modern {@code entity.position()}).
     */
    public static Vec3 position(Entity entity) {
        return new Vec3(entity.posX, entity.boundingBox.minY, entity.posZ);
    }

    /**
     * Interpolated feet position (modern {@code entity.getPosition(partialTicks)}).
     */
    public static Vec3 position(Entity entity, float partialTicks) {
        return new Vec3(
            Mth.lerp(partialTicks, entity.prevPosX, entity.posX),
            Mth.lerp(partialTicks, entity.prevPosY, entity.posY) - entity.yOffset + entity.ySize,
            Mth.lerp(partialTicks, entity.prevPosZ, entity.posZ));
    }

    public static Vec3 eyePosition(Entity entity) {
        return new Vec3(entity.posX, entity.boundingBox.minY + eyeHeight(entity), entity.posZ);
    }

    /**
     * Distance from the feet to the eyes. 1.7.10 client players report it relative to posY, so it is
     * normalised here to always be relative to the bounding box bottom.
     */
    public static double eyeHeight(Entity entity) {
        return entity.posY + entity.getEyeHeight() - entity.boundingBox.minY;
    }

    public static Vec3 deltaMovement(Entity entity) {
        return new Vec3(entity.motionX, entity.motionY, entity.motionZ);
    }

    public static void setDeltaMovement(Entity entity, Vec3 motion) {
        entity.motionX = motion.x;
        entity.motionY = motion.y;
        entity.motionZ = motion.z;
    }

    /**
     * Unit look vector from pitch/yaw in degrees (modern {@code Entity#calculateViewVector}).
     */
    public static Vec3 directionFromRotation(float xRot, float yRot) {
        float f = xRot * Mth.DEG_TO_RAD;
        float f1 = -yRot * Mth.DEG_TO_RAD;
        float f2 = MathHelper.cos(f1);
        float f3 = MathHelper.sin(f1);
        float f4 = MathHelper.cos(f);
        float f5 = MathHelper.sin(f);

        return new Vec3(f3 * f4, -f5, f2 * f4);
    }

    public static Vec3 viewVector(Entity entity) {
        return directionFromRotation(entity.rotationPitch, entity.rotationYaw);
    }

    public static Vec3 atCenterOf(int x, int y, int z) {
        return new Vec3(x + 0.5D, y + 0.5D, z + 0.5D);
    }

    public static Vec3 atBottomCenterOf(int x, int y, int z) {
        return new Vec3(x + 0.5D, y, z + 0.5D);
    }

    public net.minecraft.util.Vec3 toVanilla() {
        return net.minecraft.util.Vec3.createVectorHelper(x, y, z);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public Vec3 vectorTo(Vec3 other) {
        return new Vec3(other.x - x, other.y - y, other.z - z);
    }

    public Vec3 normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);

        return length < 1.0E-5F ? ZERO : new Vec3(x / length, y / length, z / length);
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    public Vec3 subtract(Vec3 other) {
        return subtract(other.x, other.y, other.z);
    }

    public Vec3 subtract(double dx, double dy, double dz) {
        return add(-dx, -dy, -dz);
    }

    public Vec3 add(Vec3 other) {
        return add(other.x, other.y, other.z);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public boolean closerThan(Vec3 other, double distance) {
        return distanceToSqr(other) < distance * distance;
    }

    public double distanceTo(Vec3 other) {
        return Math.sqrt(distanceToSqr(other));
    }

    public double distanceToSqr(Vec3 other) {
        return distanceToSqr(other.x, other.y, other.z);
    }

    public double distanceToSqr(double ox, double oy, double oz) {
        double dx = ox - x;
        double dy = oy - y;
        double dz = oz - z;

        return dx * dx + dy * dy + dz * dz;
    }

    public Vec3 scale(double factor) {
        return multiply(factor, factor, factor);
    }

    public Vec3 reverse() {
        return scale(-1D);
    }

    public Vec3 multiply(Vec3 other) {
        return multiply(other.x, other.y, other.z);
    }

    public Vec3 multiply(double fx, double fy, double fz) {
        return new Vec3(x * fx, y * fy, z * fz);
    }

    public Vec3 offsetRandom(Random random, float factor) {
        return add(
            (random.nextFloat() - 0.5F) * factor,
            (random.nextFloat() - 0.5F) * factor,
            (random.nextFloat() - 0.5F) * factor);
    }

    public double length() {
        return Math.sqrt(lengthSqr());
    }

    public double lengthSqr() {
        return x * x + y * y + z * z;
    }

    public double horizontalDistance() {
        return Math.sqrt(x * x + z * z);
    }

    public double horizontalDistanceSqr() {
        return x * x + z * z;
    }

    public Vec3 lerp(Vec3 to, double delta) {
        return new Vec3(Mth.lerp(delta, x, to.x), Mth.lerp(delta, y, to.y), Mth.lerp(delta, z, to.z));
    }

    public Vec3 xRot(float angle) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);

        return new Vec3(x, y * cos + z * sin, z * cos - y * sin);
    }

    public Vec3 yRot(float angle) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);

        return new Vec3(x * cos + z * sin, y, z * cos - x * sin);
    }

    public Vec3 zRot(float angle) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);

        return new Vec3(x * cos + y * sin, y * cos - x * sin, z);
    }

    public Vec3 withX(double value) {
        return new Vec3(value, y, z);
    }

    public Vec3 withY(double value) {
        return new Vec3(x, value, z);
    }

    public Vec3 withZ(double value) {
        return new Vec3(x, y, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof Vec3)) {
            return false;
        }
        Vec3 other = (Vec3) o;
        return Double.compare(other.x, x) == 0 && Double.compare(other.y, y) == 0 && Double.compare(other.z, z) == 0;
    }

    @Override
    public int hashCode() {
        long j = Double.doubleToLongBits(x);
        int i = (int) (j ^ j >>> 32);

        j = Double.doubleToLongBits(y);
        i = 31 * i + (int) (j ^ j >>> 32);
        j = Double.doubleToLongBits(z);

        return 31 * i + (int) (j ^ j >>> 32);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
