package com.akashic.kunftig.modern.math;

import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Immutable block position with the modern {@code BlockPos} API (1.7.10 passes raw x/y/z ints).
 */
public final class BlockPos {

    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }

    public static BlockPos containing(Vec3 vec) {
        return containing(vec.x, vec.y, vec.z);
    }

    /**
     * Block the entity's feet are in (modern {@code entity.blockPosition()}).
     */
    public static BlockPos of(Entity entity) {
        return containing(entity.posX, entity.boundingBox.minY, entity.posZ);
    }

    private final int x;
    private final int y;
    private final int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new BlockPos(x + dx, y + dy, z + dz);
    }

    public BlockPos offset(BlockPos other) {
        return offset(other.x, other.y, other.z);
    }

    public BlockPos relative(ForgeDirection direction) {
        return relative(direction, 1);
    }

    public BlockPos relative(ForgeDirection direction, int distance) {
        return offset(direction.offsetX * distance, direction.offsetY * distance, direction.offsetZ * distance);
    }

    public BlockPos above() {
        return offset(0, 1, 0);
    }

    public BlockPos above(int distance) {
        return offset(0, distance, 0);
    }

    public BlockPos below() {
        return offset(0, -1, 0);
    }

    public BlockPos below(int distance) {
        return offset(0, -distance, 0);
    }

    public BlockPos north() {
        return offset(0, 0, -1);
    }

    public BlockPos south() {
        return offset(0, 0, 1);
    }

    public BlockPos west() {
        return offset(-1, 0, 0);
    }

    public BlockPos east() {
        return offset(1, 0, 0);
    }

    public double distSqr(BlockPos other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;

        return dx * dx + dy * dy + dz * dz;
    }

    public double distToCenterSqr(double px, double py, double pz) {
        double dx = x + 0.5D - px;
        double dy = y + 0.5D - py;
        double dz = z + 0.5D - pz;

        return dx * dx + dy * dy + dz * dz;
    }

    public Vec3 getCenter() {
        return Vec3.atCenterOf(x, y, z);
    }

    public Vec3 getBottomCenter() {
        return Vec3.atBottomCenterOf(x, y, z);
    }

    public long asLong() {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockPos)) {
            return false;
        }
        BlockPos other = (BlockPos) o;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return (y + z * 31) * 31 + x;
    }

    @Override
    public String toString() {
        return "BlockPos{x=" + x + ", y=" + y + ", z=" + z + "}";
    }
}
