package com.akashic.kunftig.fishing;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * The world-facing pieces of the fishing cycle, written to match 1.7.10's {@code EntityFishHook.onUpdate} so that
 * hooks look, sound and consume the random generator exactly as in vanilla. Server side only.
 * <p>
 * Lava fishing (a Kunftig extension, no vanilla equivalent) uses the same code with the fluid swapped and fire
 * effects in place of water ones, one for one, so the random generator is consumed in the same order.
 */
public final class HookWorld {

    private HookWorld() {}

    /** Fraction (0, 0.2, ... 1) of the hook's height that is in water, measured as vanilla does. */
    public static double waterFraction(EntityFishHook hook) {
        return fluidFraction(hook, Material.water);
    }

    /** Fraction (0, 0.2, ... 1) of the hook's height that is in {@code fluid}, measured as vanilla measures water. */
    public static double fluidFraction(EntityFishHook hook, Material fluid) {
        AxisAlignedBB box = hook.boundingBox;
        int slices = 5;
        double fraction = 0.0D;
        for (int j = 0; j < slices; ++j) {
            double minY = box.minY + (box.maxY - box.minY) * j / slices - 0.125D + 0.125D;
            double maxY = box.minY + (box.maxY - box.minY) * (j + 1) / slices - 0.125D + 0.125D;
            AxisAlignedBB slice = AxisAlignedBB.getBoundingBox(box.minX, minY, box.minZ, box.maxX, maxY, box.maxZ);
            if (hook.worldObj.isAABBInMaterial(slice, fluid)) {
                fraction += 1.0D / slices;
            }
        }
        return fraction;
    }

    /** 1.16+'s open water test around a block position. */
    public static boolean isOpenWater(World world, int x, int y, int z) {
        return isOpenFluid(world, x, y, z, Material.water);
    }

    /** The open water test with {@code fluid} in place of water (lava fishing: open lava). */
    public static boolean isOpenFluid(final World world, int x, int y, int z, final Material fluid) {
        return OpenWater.isOpenWater(new OpenWater.Probe() {

            @Override
            public OpenWater.Type typeAt(int bx, int by, int bz) {
                Block block = world.getBlock(bx, by, bz);
                if (block.getMaterial() == Material.air || block == Blocks.waterlily) {
                    return OpenWater.Type.ABOVE_WATER;
                }
                // A source block (metadata 0) of the fluid; liquids have no collision box in 1.7.10.
                if (block.getMaterial() == fluid && world.getBlockMetadata(bx, by, bz) == 0) {
                    return OpenWater.Type.INSIDE_WATER;
                }
                return OpenWater.Type.INVALID;
            }
        }, x, y, z);
    }

    public static void waitingSplash(EntityFishHook hook, Random rand, boolean lava) {
        float angle = MathHelper.randomFloatClamp(rand, 0.0F, 360.0F) * 0.017453292F;
        float distance = MathHelper.randomFloatClamp(rand, 25.0F, 60.0F);
        double x = hook.posX + (double) (MathHelper.sin(angle) * distance * 0.1F);
        double y = (double) ((float) MathHelper.floor_double(hook.boundingBox.minY) + 1.0F);
        double z = hook.posZ + (double) (MathHelper.cos(angle) * distance * 0.1F);
        ((WorldServer) hook.worldObj).func_147487_a(
            lava ? "lava" : "splash",
            x,
            y,
            z,
            2 + rand.nextInt(2),
            0.10000000149011612D,
            0.0D,
            0.10000000149011612D,
            0.0D);
    }

    public static void approachEffects(EntityFishHook hook, Random rand, float fishAngle, int approachTime,
        boolean lava) {
        WorldServer world = (WorldServer) hook.worldObj;
        float radians = fishAngle * 0.017453292F;
        float sin = MathHelper.sin(radians);
        float cos = MathHelper.cos(radians);
        double x = hook.posX + (double) (sin * (float) approachTime * 0.1F);
        double y = (double) ((float) MathHelper.floor_double(hook.boundingBox.minY) + 1.0F);
        double z = hook.posZ + (double) (cos * (float) approachTime * 0.1F);
        if (rand.nextFloat() < 0.15F) {
            world.func_147487_a(
                lava ? "smoke" : "bubble",
                x,
                y - 0.10000000149011612D,
                z,
                1,
                (double) sin,
                0.1D,
                (double) cos,
                0.0D);
        }
        float sideX = sin * 0.04F;
        float sideZ = cos * 0.04F;
        String trail = lava ? "flame" : "wake";
        world.func_147487_a(trail, x, y, z, 0, (double) sideZ, 0.01D, (double) (-sideX), 1.0D);
        world.func_147487_a(trail, x, y, z, 0, (double) (-sideZ), 0.01D, (double) sideX, 1.0D);
    }

    public static void biteEffects(EntityFishHook hook, Random rand, boolean lava) {
        hook.motionY -= 0.20000000298023224D;
        hook.playSound(
            lava ? "liquid.lavapop" : "random.splash",
            0.25F,
            1.0F + (rand.nextFloat() - rand.nextFloat()) * 0.4F);
        float y = (float) MathHelper.floor_double(hook.boundingBox.minY);
        int count = (int) (1.0F + hook.width * 20.0F);
        WorldServer world = (WorldServer) hook.worldObj;
        world.func_147487_a(
            lava ? "lava" : "bubble",
            hook.posX,
            (double) (y + 1.0F),
            hook.posZ,
            count,
            (double) hook.width,
            0.0D,
            (double) hook.width,
            0.20000000298023224D);
        world.func_147487_a(
            lava ? "flame" : "wake",
            hook.posX,
            (double) (y + 1.0F),
            hook.posZ,
            count,
            (double) hook.width,
            0.0D,
            (double) hook.width,
            0.20000000298023224D);
    }

    public static void nibbleTug(EntityFishHook hook, Random rand) {
        hook.motionY -= (double) (rand.nextFloat() * rand.nextFloat() * rand.nextFloat()) * 0.2D;
    }

    /** 1.7.10's pull of a hooked entity toward the angler (the same one retrieving applies). */
    public static void pull(EntityFishHook hook) {
        if (hook.field_146043_c == null || hook.field_146042_b == null) {
            return;
        }
        double dx = hook.field_146042_b.posX - hook.posX;
        double dy = hook.field_146042_b.posY - hook.posY;
        double dz = hook.field_146042_b.posZ - hook.posZ;
        double distance = (double) MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        double factor = 0.1D;
        hook.field_146043_c.motionX += dx * factor;
        hook.field_146043_c.motionY += dy * factor + (double) MathHelper.sqrt_double(distance) * 0.08D;
        hook.field_146043_c.motionZ += dz * factor;
    }
}
