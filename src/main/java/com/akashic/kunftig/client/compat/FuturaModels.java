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
    /** futura3d API 3: the main hand holds a SlashBlade-style blade. */
    public static final String SLASHBLADE_HELD = "slashblade.held";
    /** futura3d API 3: the combo state playing. */
    public static final String SLASHBLADE_COMBO = "slashblade.combo";
    /** futura3d API 4: the rod state the server set ({@code KunftigRods}). */
    public static final String FISHING_STATE = "fishing.state";
    /** futura3d API 4: the fight's tension, 0 to 1 as text. */
    public static final String FISHING_TENSION = "fishing.tension";

    private static final String API = "com.akashic.futura.api.FuturaModels";

    private static volatile boolean looked;
    private static MethodHandle drewModel;
    private static MethodHandle isModelled;
    private static MethodHandle hasLocator;
    private static MethodHandle locatorInLastDraw;
    private static MethodHandle setKeepsItemWhileFishing;
    // API 3 (optional: an older futura3d works without them)
    private static MethodHandle setExternalState;
    private static MethodHandle externalState;
    private static MethodHandle hasAnimation;
    private static MethodHandle playingAnimation;
    // API 4 (optional): the models fish with the rod
    private static MethodHandle fishingPhase;
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
                    try {
                        MethodType nameName = MethodType.methodType(String.class, EntityPlayer.class, String.class);
                        setExternalState = l.findStatic(
                            api,
                            "setExternalState",
                            MethodType.methodType(boolean.class, EntityPlayer.class, String.class, String.class));
                        externalState = l.findStatic(api, "externalState", nameName);
                        hasAnimation = l.findStatic(api, "hasAnimation", boolName);
                        playingAnimation = l.findStatic(api, "playingAnimation", nameName);
                    } catch (NoSuchMethodException e) {
                        setExternalState = externalState = hasAnimation = playingAnimation = null;
                    }
                    try {
                        fishingPhase = l
                            .findStatic(api, "fishingPhase", MethodType.methodType(String.class, EntityPlayer.class));
                    } catch (NoSuchMethodException e) {
                        fishingPhase = null;
                    }
                    Kunftig.LOG.info(
                        "Kunftig: 3D Futura Engine found; rods and blades follow its player models{}",
                        setExternalState == null ? ""
                            : fishingPhase != null ? ", which play the blade combos and fish with the rod"
                                : ", which play the blade combos");
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
        setExternalState = externalState = hasAnimation = playingAnimation = null;
        fishingPhase = null;
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

    /** Whether futura3d takes external states (its API 3): its models can play the blade combos. */
    public static boolean hasExternalStates() {
        lookUp();
        return setExternalState != null;
    }

    /**
     * Sets ({@code value} non-null) or clears a state futura3d's animation runtime reads for the player (API 3; a no-op
     * without it). Setting the value it has changes nothing; clearing and setting it again restarts what it started.
     */
    public static boolean setExternalState(EntityPlayer player, String key, String value) {
        lookUp();
        MethodHandle h = setExternalState;
        if (h == null) {
            return false;
        }
        try {
            return (boolean) h.invokeExact(player, key, value);
        } catch (Throwable e) {
            fail(e);
            return false;
        }
    }

    /**
     * Whether futura3d's models fish with the rod (its API 4): they play the server's rod states told through
     * {@link #FISHING_STATE} and {@link #FISHING_TENSION}.
     */
    public static boolean playsFishing() {
        lookUp();
        return fishingPhase != null && setExternalState != null;
    }

    /** What the player's model does with the rod ({@code wait}, {@code fight}...), or null (API 4). */
    public static String fishingPhase(EntityPlayer player) {
        lookUp();
        MethodHandle h = fishingPhase;
        if (h == null) {
            return null;
        }
        try {
            return (String) h.invokeExact(player);
        } catch (Throwable e) {
            fail(e);
            return null;
        }
    }

    /** A state set with {@link #setExternalState}, or null. */
    public static String externalState(EntityPlayer player, String key) {
        lookUp();
        MethodHandle h = externalState;
        if (h == null) {
            return null;
        }
        try {
            return (String) h.invokeExact(player, key);
        } catch (Throwable e) {
            fail(e);
            return null;
        }
    }

    /** Whether the player's model (or the default model behind it) has an animation of that name (API 3). */
    public static boolean hasAnimation(EntityPlayer player, String animation) {
        lookUp();
        MethodHandle h = hasAnimation;
        if (h == null) {
            return false;
        }
        try {
            return (boolean) h.invokeExact(player, animation);
        } catch (Throwable e) {
            fail(e);
            return false;
        }
    }

    /** The clip a controller of the player's model plays now ({@code player.swing}...), or null (API 3). */
    public static String playingAnimation(EntityPlayer player, String controller) {
        lookUp();
        MethodHandle h = playingAnimation;
        if (h == null) {
            return null;
        }
        try {
            return (String) h.invokeExact(player, controller);
        } catch (Throwable e) {
            fail(e);
            return null;
        }
    }

    /**
     * Whether the player's model plays the SlashBlade animations for the blade he holds: futura3d takes external
     * states, Kunftig told it he holds a blade, and the model has {@code slashblade:idle}. The model's clips then show
     * and hide {@link #BLADE}, {@link #SHEATH} and {@link #LEFT_WAIST} as upstream YSM's do.
     */
    public static boolean playsBladeAnimations(EntityPlayer player) {
        return hasExternalStates() && externalState(player, SLASHBLADE_HELD) != null
            && hasAnimation(player, "slashblade:idle");
    }

    private static void fail(Throwable e) {
        if (!failedOnce) {
            failedOnce = true;
            Kunftig.LOG.error("Kunftig: a call to 3D Futura Engine failed", e);
        }
    }
}
