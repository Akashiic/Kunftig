package com.akashic.kunftig.api.bukkit.fishing;

import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * The fishing states 1.7.10's {@code PlayerFishEvent} lacks, with 1.21.1's names and meaning. Fired on the main
 * thread by Kunftig's fishing engine.
 * <p>
 * The other 1.21.1 states come from Bukkit's own {@code PlayerFishEvent}, which behaves the same on 1.7.10:
 * {@code FISHING}, {@code CAUGHT_FISH}, {@code CAUGHT_ENTITY}, {@code IN_GROUND}. <b>Careful:</b> 1.7.10's
 * {@code PlayerFishEvent.State.FAILED_ATTEMPT} means what 1.21.1 calls {@code REEL_IN}; it is fired right after this
 * event's {@link State#REEL_IN}.
 */
public final class KunftigFishEvent extends PlayerEvent implements Cancellable {

    public enum State {
        /** A fish appeared and swims toward the bobber. Cancel: it does not come; the hook waits again. */
        LURED,
        /** The fish bites: the bobber dips, reeling in now catches it. Cancel: no bite, no splash; waits again. */
        BITE,
        /** The bite window ran out: the player was too slow. Cannot be cancelled. */
        FAILED_ATTEMPT,
        /** The player reels in with nothing on the hook. Cancel: the hook stays in the water. */
        REEL_IN
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final ModernFishHook hook;
    private final State state;
    private boolean cancelled;

    public KunftigFishEvent(Player player, ModernFishHook hook, State state) {
        super(player);
        this.hook = hook;
        this.state = state;
    }

    public State getState() {
        return state;
    }

    /** The hook with 1.21.1's API (and Kunftig's bite window). */
    public ModernFishHook getHook() {
        return hook;
    }

    /** The hook as a Bukkit entity. */
    public FishHook getHookEntity() {
        return hook.getEntity();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /** Ignored for {@link State#FAILED_ATTEMPT}, which reports something that already happened. */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
