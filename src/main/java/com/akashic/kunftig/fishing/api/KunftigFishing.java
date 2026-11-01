package com.akashic.kunftig.fishing.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.entity.projectile.EntityFishHook;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.KunftigConfig;
import com.akashic.kunftig.fishing.FishingEngine;
import com.akashic.kunftig.fishing.FishingParameters;
import com.akashic.kunftig.fishing.KunftigFishHook;

/**
 * Forge-side entry point of Kunftig's modern fishing: per-hook settings and listeners. Server thread only.
 */
public final class KunftigFishing {

    private static final List<FishingListener> LISTENERS = new CopyOnWriteArrayList<FishingListener>();

    /** Engine view of the listeners, handed to {@link FishingEngine#tick}. */
    public static final FishingEngine.Listener ENGINE_LISTENER = new FishingEngine.Listener() {

        @Override
        public boolean lured(FishingEngine.Hook hook) {
            boolean allowed = true;
            for (FishingListener listener : LISTENERS) {
                allowed &= safe(listener, hook, Event.LURED);
            }
            return allowed;
        }

        @Override
        public boolean bite(FishingEngine.Hook hook) {
            boolean allowed = true;
            for (FishingListener listener : LISTENERS) {
                allowed &= safe(listener, hook, Event.BITE);
            }
            return allowed;
        }

        @Override
        public void failedAttempt(FishingEngine.Hook hook) {
            for (FishingListener listener : LISTENERS) {
                safe(listener, hook, Event.FAILED_ATTEMPT);
            }
        }
    };

    private enum Event {
        LURED,
        BITE,
        FAILED_ATTEMPT,
        REEL_IN
    }

    private KunftigFishing() {}

    /** Every listener sees every event; the event is cancelled when any of them cancels it. */
    public static void register(FishingListener listener) {
        LISTENERS.add(listener);
    }

    public static void unregister(FishingListener listener) {
        LISTENERS.remove(listener);
    }

    /** Whether the engine replaces vanilla's fishing cycle (config on, mixin applied). */
    public static boolean isActive() {
        return KunftigConfig.fishingEnabled && KunftigFishHook.class.isAssignableFrom(EntityFishHook.class);
    }

    /** The modern settings of a hook (wait/lure ranges, lure angle, influences, bite window, treasure rule). */
    public static FishingParameters parameters(EntityFishHook hook) {
        return access(hook).kunftig$parameters();
    }

    /** Full access to a hook's fishing state. */
    public static KunftigFishHook access(EntityFishHook hook) {
        if (!(hook instanceof KunftigFishHook)) {
            throw new IllegalStateException("Kunftig fishing is not installed (mixin disabled?)");
        }
        return (KunftigFishHook) hook;
    }

    /** Called by the mixin when a player reels in with nothing; false = keep the hook in the water. */
    public static boolean fireReelIn(EntityFishHook hook) {
        boolean allowed = true;
        for (FishingListener listener : LISTENERS) {
            allowed &= safe(listener, (FishingEngine.Hook) hook, Event.REEL_IN);
        }
        return allowed;
    }

    /** A failing listener must never break fishing for everyone else (nor crash the tick). */
    private static boolean safe(FishingListener listener, FishingEngine.Hook hook, Event event) {
        EntityFishHook entity = (EntityFishHook) hook;
        try {
            switch (event) {
                case LURED:
                    return listener.onLured(entity);
                case BITE:
                    return listener.onBite(entity);
                case REEL_IN:
                    return listener.onReelIn(entity);
                case FAILED_ATTEMPT:
                default:
                    listener.onFailedAttempt(entity);
                    return true;
            }
        } catch (RuntimeException | LinkageError e) {
            Kunftig.LOG.error(
                "Kunftig fishing listener {} failed on {}",
                listener.getClass()
                    .getName(),
                event,
                e);
            return true;
        }
    }
}
