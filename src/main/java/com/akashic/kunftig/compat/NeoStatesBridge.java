package com.akashic.kunftig.compat;

import java.lang.reflect.Method;

import net.minecraft.world.IBlockAccess;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Optional link to Neo States, which owns "where is the surface of the water in this cell" once it is installed
 * (water held inside slabs and stairs, and any later deformation of the surface). Without it, or when its API does
 * not match, callers keep their own rule. By reflection: no class of Neo States is linked.
 */
public final class NeoStatesBridge {

    private static final Logger LOG = LogManager.getLogger("Kunftig");

    private static final Method IS_WATER_AT;
    private static final Method SURFACE_Y;

    static {
        Method isWaterAt = null;
        Method surfaceY = null;
        try {
            Class<?> api = Class.forName("com.akashic.neostates.api.NeoStates");
            isWaterAt = api.getMethod("isWaterAt", IBlockAccess.class, int.class, int.class, int.class);
            surfaceY = api.getMethod("surfaceY", IBlockAccess.class, int.class, int.class, int.class);
        } catch (ClassNotFoundException absent) {
            // Neo States is not installed.
        } catch (ReflectiveOperationException | LinkageError mismatch) {
            LOG.warn(
                "Neo States is present but its API did not match; the hook keeps its own water surface.",
                mismatch);
            isWaterAt = null;
            surfaceY = null;
        }
        IS_WATER_AT = isWaterAt;
        SURFACE_Y = surfaceY;
    }

    private NeoStatesBridge() {}

    /**
     * The absolute height of the water surface in the cell, or null when Neo States is absent or the cell has no
     * water (lava is not its business here).
     */
    public static Double waterSurface(IBlockAccess world, int x, int y, int z) {
        if (IS_WATER_AT == null) {
            return null;
        }
        try {
            if (!((Boolean) IS_WATER_AT.invoke(null, world, x, y, z))) {
                return null;
            }
            double surface = (Double) SURFACE_Y.invoke(null, world, x, y, z);
            return Double.isNaN(surface) ? null : surface;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }
}
