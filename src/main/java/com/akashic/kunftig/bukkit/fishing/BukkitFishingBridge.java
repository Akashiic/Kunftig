package com.akashic.kunftig.bukkit.fishing;

import net.minecraft.entity.projectile.EntityFishHook;

import org.bukkit.Bukkit;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.fishing.KunftigFishEvent;
import com.akashic.kunftig.api.bukkit.fishing.KunftigFishingBukkit;
import com.akashic.kunftig.api.bukkit.fishing.ModernFishHook;
import com.akashic.kunftig.fishing.KunftigFishHook;
import com.akashic.kunftig.fishing.api.FishingListener;
import com.akashic.kunftig.fishing.api.KunftigFishing;

/**
 * Turns engine events into {@link KunftigFishEvent}s for Bukkit plugins. Loaded reflectively, only when Bukkit is on
 * the classpath (Thermos/Crucible), so singleplayer and pure Forge servers never touch Bukkit classes.
 */
public final class BukkitFishingBridge implements FishingListener, KunftigFishingBukkit.Provider {

    private BukkitFishingBridge() {}

    /** Called reflectively by the mod at start-up. */
    public static void install() {
        BukkitFishingBridge bridge = new BukkitFishingBridge();
        KunftigFishing.register(bridge);
        KunftigFishingBukkit.install(bridge);
        Kunftig.LOG.info("Kunftig fishing: Bukkit API enabled (KunftigFishEvent, KunftigFishingBukkit)");
    }

    @Override
    public boolean isActive() {
        return KunftigFishing.isActive();
    }

    @Override
    public ModernFishHook of(FishHook hook) {
        net.minecraft.entity.Entity handle = CraftBridge.handle(hook);
        if (!(handle instanceof EntityFishHook) || !(handle instanceof KunftigFishHook)) {
            throw new IllegalArgumentException("Not a fishing hook handled by Kunftig: " + hook);
        }
        return new ModernFishHookImpl((EntityFishHook) handle);
    }

    private static boolean fire(EntityFishHook hook, KunftigFishEvent.State state) {
        if (Bukkit.getServer() == null || hook.field_146042_b == null) {
            return true;
        }
        Object owner = CraftBridge.bukkitEntity(hook.field_146042_b);
        if (!(owner instanceof Player)) {
            return true;
        }
        KunftigFishEvent event = new KunftigFishEvent((Player) owner, new ModernFishHookImpl(hook), state);
        Bukkit.getPluginManager()
            .callEvent(event);
        return state == KunftigFishEvent.State.FAILED_ATTEMPT || !event.isCancelled();
    }

    @Override
    public boolean onLured(EntityFishHook hook) {
        return fire(hook, KunftigFishEvent.State.LURED);
    }

    @Override
    public boolean onBite(EntityFishHook hook) {
        return fire(hook, KunftigFishEvent.State.BITE);
    }

    @Override
    public void onFailedAttempt(EntityFishHook hook) {
        fire(hook, KunftigFishEvent.State.FAILED_ATTEMPT);
    }

    @Override
    public boolean onReelIn(EntityFishHook hook) {
        return fire(hook, KunftigFishEvent.State.REEL_IN);
    }
}
