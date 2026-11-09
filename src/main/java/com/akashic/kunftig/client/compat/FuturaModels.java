package com.akashic.kunftig.client.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;

import com.akashic.kunftig.Kunftig;

/**
 * The player models of 3D Futura Engine ({@code futura3d}, YSM-style models in place of the biped), reached only by
 * reflection on its stable API ({@code com.akashic.futura.api.FuturaModels}), looked up once and cached. Without
 * futura3d, with another API, or with {@code -Dkunftig.futura3d.disable=true}, every query answers "not modelled" and
 * Kunftig draws on the vanilla biped as before. Client thread only.
 */
public final class FuturaModels {

    public static final String RIGHT_HAND = "RightHandLocator";
    public static final String LEFT_WAIST = "LeftWaistLocator";
    public static final String RIGHT_WAIST = "RightWaistLocator";
    public static final String BLADE = "BladeLocator";
    public static final String SHEATH = "SheathLocator";

    private static final String API = "com.akashic.futura.api.FuturaModels";

    private static volatile boolean looked;
    private static MethodHandle drewModel;
    private static MethodHandle isModelled;
    private static MethodHandle hasLocator;
    private static MethodHandle locatorInLastDraw;
    private static MethodHandle setKeepsItemWhileFishing;
    private static boolean failedOnce;

    private FuturaModels() {}

    public static boolean disabled() {
        return Boolean.getBoolean("kunftig.futura3d.disable");
    }

    private static void lookUp() {
        if (looked) {
            return;
        }
        synchronized (FuturaModels.class) {
            if (looked) {
                return;
            }
            if (!disabled()) {
                try {
                    Class<?> api = Class.forName(API);
                    MethodHandles.Lookup l = MethodHandles.publicLookup();
                    MethodType bool = MethodType.methodType(boolean.class, EntityPlayer.class);
                    MethodType boolName = MethodType.methodType(boolean.class, EntityPlayer.class, String.class);
                    drewModel = l.findStatic(api, "drewModel", bool);
                    isModelled = l.findStatic(api, "isModelled", bool);
                    hasLocator = l.findStatic(api, "hasLocator", boolName);
                    locatorInLastDraw = l.findStatic(
                        api,
                        "locatorInLastDraw",
                        MethodType.methodType(float[].class, EntityPlayer.class, String.class));
                    setKeepsItemWhileFishing = l.findStatic(
                        api,
                        "setKeepsItemWhileFishing",
                        MethodType.methodType(void.class, Predicate.class));
                    Kunftig.LOG.info("Kunftig: 3D Futura Engine found; rods and blades follow its player models");
                } catch (ClassNotFoundException e) {
                    clear(); // not installed: the biped as always
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    clear();
                    Kunftig.LOG.warn("Kunftig: 3D Futura Engine has another API; its player models are ignored", e);
                }
            }
            looked = true;
        }
    }

    private static void clear() {
        drewModel = isModelled = hasLocator = locatorInLastDraw = setKeepsItemWhileFishing = null;
    }

    /** Whether futura3d is installed with the API Kunftig knows. */
    public static boolean present() {
        lookUp();
        return drewModel != null;
    }

    /** In a {@code RenderPlayerEvent.Post} handler: whether the draw that just ended drew this player's model. */
    public static boolean drewModel(EntityPlayer player) {
        lookUp();
        MethodHandle h = drewModel;
        if (h == null) {
            return false;
        }
        try {
            return (boolean) h.invokeExact(player);
        } catch (Throwable e) {
            fail(e);
            return false;
        }
    }

    /** Whether the player was drawn in the world with a model in this frame or the one before. */
    public static boolean isModelled(EntityPlayer player) {
        lookUp();
        MethodHandle h = isModelled;
        if (h == null) {
            return false;
        }
        try {
            return (boolean) h.invokeExact(player);
        } catch (Throwable e) {
            fail(e);
            return false;
        }
    }

    /** Whether the player's model has a bone of that locator group, shown or hidden. */
    public static boolean hasLocator(EntityPlayer player, String name) {
        lookUp();
        MethodHandle h = hasLocator;
        if (h == null) {
            return false;
        }
        try {
            return (boolean) h.invokeExact(player, name);
        } catch (Throwable e) {
            fail(e);
            return false;
        }
    }

    /**
     * In a {@code RenderPlayerEvent.Post} handler: a locator of the model draw that just ended, column-major, relative
     * to the model-view matrix of the event; null when that draw was not a model's or the locator is hidden.
     */
    public static float[] locatorInLastDraw(EntityPlayer player, String name) {
        lookUp();
        MethodHandle h = locatorInLastDraw;
        if (h == null) {
            return null;
        }
        try {
            return (float[]) h.invokeExact(player, name);
        } catch (Throwable e) {
            fail(e);
            return null;
        }
    }

    /** Lets futura3d keep the held item (a 3D rod) in the model's hand while the line is out, instead of a stick. */
    public static void keepItemWhileFishing(Predicate<EntityPlayer> keeps) {
        lookUp();
        MethodHandle h = setKeepsItemWhileFishing;
        if (h == null) {
            return;
        }
        try {
            h.invokeExact(keeps);
        } catch (Throwable e) {
            fail(e);
        }
    }

    private static void fail(Throwable e) {
        if (!failedOnce) {
            failedOnce = true;
            Kunftig.LOG.error("Kunftig: a call to 3D Futura Engine failed", e);
        }
    }
}
