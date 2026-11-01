package com.akashic.kunftig.client.particle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.particle.KunftigParticle;
import com.akashic.kunftig.particle.EfrParticleTable;

import cpw.mods.fml.common.Loader;

/**
 * Draws modern particles with Et Futurum Requiem, when it is installed. No compile-time dependency: each
 * {@code CustomParticles} method is resolved by method handle the first time it is needed, and a method that is
 * missing or changed only sends that particle back to its vanilla fallback (logged once).
 */
final class EfrParticles {

    private static final MethodHandle MISSING = MethodHandles.constant(Object.class, null);
    private static final Map<KunftigParticle, MethodHandle> HANDLES = new EnumMap<>(KunftigParticle.class);
    private static Boolean installed;
    private static Class<?> customParticles;

    private EfrParticles() {}

    static boolean isInstalled() {
        if (installed == null) {
            installed = Loader.isModLoaded(EfrParticleTable.MOD_ID);
            if (installed) {
                try {
                    customParticles = Class.forName(EfrParticleTable.CLASS, true, EfrParticles.class.getClassLoader());
                } catch (ClassNotFoundException | LinkageError e) {
                    Kunftig.LOG.warn(
                        "Kunftig particles: Et Futurum Requiem has no {}, using vanilla particles",
                        EfrParticleTable.CLASS);
                    installed = false;
                }
            }
        }
        return installed;
    }

    /**
     * Spawns {@code particle} through Et Futurum.
     *
     * @return false if Et Futurum cannot draw it (then the caller draws the fallback)
     */
    static boolean spawn(KunftigParticle particle, World world, double x, double y, double z, double vx, double vy,
        double vz, int argb) {
        if (!isInstalled()) {
            return false;
        }
        EfrParticleTable.Entry entry = EfrParticleTable.get(particle);
        MethodHandle handle = entry == null ? MISSING : handle(particle, entry);
        if (handle == MISSING) {
            return false;
        }
        EntityFX fx;
        try {
            switch (entry.shape) {
                case POSITION_COLOR:
                    fx = (EntityFX) handle.invoke(world, x, y, z, argb != 0 ? argb : particle.defaultColor());
                    break;
                case POSITION_VELOCITY:
                    if (particle == KunftigParticle.BUBBLE_COLUMN_UP && vx == 0 && vy == 0 && vz == 0) {
                        vy = EfrParticleTable.BUBBLE_COLUMN_RISE;
                    }
                    fx = (EntityFX) handle.invoke(world, x, y, z, vx, vy, vz);
                    break;
                default:
                    fx = (EntityFX) handle.invoke(world, x, y, z);
                    break;
            }
        } catch (Throwable e) {
            synchronized (HANDLES) {
                HANDLES.put(particle, MISSING);
            }
            Kunftig.LOG
                .warn("Kunftig particles: Et Futurum failed to draw {}, using its vanilla fallback", particle, e);
            return false;
        }
        if (fx != null && entry.shape != EfrParticleTable.Shape.POSITION_VELOCITY && (vx != 0 || vy != 0 || vz != 0)) {
            fx.motionX = vx;
            fx.motionY = vy;
            fx.motionZ = vz;
        }
        return true;
    }

    private static MethodHandle handle(KunftigParticle particle, EfrParticleTable.Entry entry) {
        synchronized (HANDLES) {
            MethodHandle handle = HANDLES.get(particle);
            if (handle == null) {
                handle = resolve(entry);
                if (handle == MISSING) {
                    Kunftig.LOG.warn(
                        "Kunftig particles: this Et Futurum has no CustomParticles.{} for {}, using its vanilla fallback",
                        entry.method,
                        particle);
                }
                HANDLES.put(particle, handle);
            }
            return handle;
        }
    }

    private static MethodHandle resolve(EfrParticleTable.Entry entry) {
        MethodType type = MethodType.methodType(EntityFX.class, World.class, double.class, double.class, double.class);
        if (entry.shape == EfrParticleTable.Shape.POSITION_COLOR) {
            type = type.appendParameterTypes(int.class);
        } else if (entry.shape == EfrParticleTable.Shape.POSITION_VELOCITY) {
            type = type.appendParameterTypes(double.class, double.class, double.class);
        }
        try {
            return MethodHandles.publicLookup()
                .findStatic(customParticles, entry.method, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            return MISSING;
        }
    }
}
