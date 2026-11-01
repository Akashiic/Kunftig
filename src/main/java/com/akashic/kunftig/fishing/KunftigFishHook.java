package com.akashic.kunftig.fishing;

/**
 * Added to {@code EntityFishHook} by Kunftig's mixin: the engine's view of the hook plus the 1.21.1 hook state.
 * Server thread only.
 */
public interface KunftigFishHook extends FishingEngine.Hook {

    /** The hook has touched water at least once (1.21's {@code BOBBING} state). */
    boolean kunftig$isBobbing();

    /** 1.16+'s open water state, kept up to date while a fish approaches and bites. */
    boolean kunftig$isInOpenWater();

    /** The hook is floating in lava (only possible with lava fishing on). */
    boolean kunftig$isInLava();
}
