package com.akashic.kunftig.bukkit.fishing;

import net.minecraft.entity.projectile.EntityFishHook;

import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

import com.akashic.kunftig.api.bukkit.fishing.ModernFishHook;
import com.akashic.kunftig.fishing.FishingEngine;
import com.akashic.kunftig.fishing.FishingParameters;
import com.akashic.kunftig.fishing.HookWorld;
import com.akashic.kunftig.fishing.KunftigFishHook;
import com.akashic.kunftig.fishing.api.KunftigFishing;

/** {@link ModernFishHook} over a Minecraft hook. A thin view: all state lives on the hook itself. */
final class ModernFishHookImpl implements ModernFishHook {

    private final EntityFishHook hook;
    private final KunftigFishHook access;

    ModernFishHookImpl(EntityFishHook hook) {
        this.hook = hook;
        this.access = (KunftigFishHook) hook;
    }

    private FishingParameters parameters() {
        return access.kunftig$parameters();
    }

    @Override
    public FishHook getEntity() {
        return (FishHook) CraftBridge.bukkitEntity(hook);
    }

    @Override
    public Player getOwner() {
        return (Player) CraftBridge.bukkitEntity(hook.field_146042_b);
    }

    @Override
    public int getMinWaitTime() {
        return parameters().minWaitTime();
    }

    @Override
    public void setMinWaitTime(int minWaitTime) {
        parameters().setMinWaitTime(minWaitTime);
    }

    @Override
    public int getMaxWaitTime() {
        return parameters().maxWaitTime();
    }

    @Override
    public void setMaxWaitTime(int maxWaitTime) {
        parameters().setMaxWaitTime(maxWaitTime);
    }

    @Override
    public void setWaitTime(int min, int max) {
        parameters().setWaitTime(min, max);
    }

    @Override
    public int getMinLureTime() {
        return parameters().minLureTime();
    }

    @Override
    public void setMinLureTime(int minLureTime) {
        parameters().setMinLureTime(minLureTime);
    }

    @Override
    public int getMaxLureTime() {
        return parameters().maxLureTime();
    }

    @Override
    public void setMaxLureTime(int maxLureTime) {
        parameters().setMaxLureTime(maxLureTime);
    }

    @Override
    public void setLureTime(int min, int max) {
        parameters().setLureTime(min, max);
    }

    @Override
    public float getMinLureAngle() {
        return parameters().minLureAngle();
    }

    @Override
    public void setMinLureAngle(float minLureAngle) {
        parameters().setMinLureAngle(minLureAngle);
    }

    @Override
    public float getMaxLureAngle() {
        return parameters().maxLureAngle();
    }

    @Override
    public void setMaxLureAngle(float maxLureAngle) {
        parameters().setMaxLureAngle(maxLureAngle);
    }

    @Override
    public void setLureAngle(float min, float max) {
        parameters().setLureAngle(min, max);
    }

    @Override
    public boolean getApplyLure() {
        return parameters().applyLure();
    }

    @Override
    public void setApplyLure(boolean applyLure) {
        parameters().setApplyLure(applyLure);
    }

    @Override
    public boolean isRainInfluenced() {
        return parameters().rainInfluenced();
    }

    @Override
    public void setRainInfluenced(boolean rainInfluenced) {
        parameters().setRainInfluenced(rainInfluenced);
    }

    @Override
    public boolean isSkyInfluenced() {
        return parameters().skyInfluenced();
    }

    @Override
    public void setSkyInfluenced(boolean skyInfluenced) {
        parameters().setSkyInfluenced(skyInfluenced);
    }

    @Override
    public boolean isInOpenWater() {
        return access.kunftig$isInOpenWater();
    }

    @Override
    public HookState getState() {
        if (hook.field_146043_c != null) {
            return HookState.HOOKED_ENTITY;
        }
        return access.kunftig$isBobbing() ? HookState.BOBBING : HookState.UNHOOKED;
    }

    @Override
    public int getWaitTime() {
        return access.kunftig$waitTime();
    }

    @Override
    public void setWaitTime(int ticks) {
        access.kunftig$setWaitTime(ticks);
    }

    @Override
    public int getTimeUntilBite() {
        return Math.max(0, access.kunftig$approachTime());
    }

    @Override
    public void setTimeUntilBite(int ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("The time until bite must be at least 1 tick, got " + ticks);
        }
        access.kunftig$setApproachTime(ticks);
    }

    @Override
    public void resetFishingState() {
        access.kunftig$setBiteWindow(0);
        access.kunftig$setApproachTime(0);
        access.kunftig$setWaitTime(
            FishingEngine.rollWaitTime(access.kunftig$random(), parameters(), access.kunftig$lureLevel()));
    }

    @Override
    public Entity getHookedEntity() {
        return (Entity) CraftBridge.bukkitEntity(hook.field_146043_c);
    }

    @Override
    public void setHookedEntity(Entity entity) {
        hook.field_146043_c = CraftBridge.handle(entity);
    }

    @Override
    public boolean pullHookedEntity() {
        if (hook.field_146043_c == null) {
            return false;
        }
        HookWorld.pull(hook);
        return true;
    }

    @Override
    public int retrieve(boolean applyRodDamage) {
        return KunftigFishing.retrieve(hook, applyRodDamage);
    }

    @Override
    public boolean isBiting() {
        return access.kunftig$biteWindow() > 0;
    }

    @Override
    public int getBiteWindow() {
        return Math.max(0, access.kunftig$biteWindow());
    }

    @Override
    public void setBiteWindow(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("The bite window cannot be negative, got " + ticks);
        }
        access.kunftig$setBiteWindow(ticks);
    }

    @Override
    public int getMinBiteWindow() {
        return parameters().minBiteWindow();
    }

    @Override
    public int getMaxBiteWindow() {
        return parameters().maxBiteWindow();
    }

    @Override
    public void setBiteWindowRange(int min, int max) {
        parameters().setBiteWindow(min, max);
    }

    @Override
    public boolean getTreasureRequiresOpenWater() {
        return parameters().treasureRequiresOpenWater();
    }

    @Override
    public void setTreasureRequiresOpenWater(boolean value) {
        parameters().setTreasureRequiresOpenWater(value);
    }

    @Override
    public void setLavaFishing(boolean lavaFishing) {
        parameters().setLavaFishing(lavaFishing);
    }

    @Override
    public boolean isLavaFishing() {
        return parameters().lavaFishing();
    }

    @Override
    public void setMaxDistance(double blocks) {
        parameters().setMaxDistance(blocks);
    }

    @Override
    public double getMaxDistance() {
        return parameters().maxDistance();
    }

    @Override
    public boolean isInLava() {
        return access.kunftig$isInLava();
    }

    @Override
    public void setReelLocked(boolean locked) {
        parameters().setReelLocked(locked);
    }

    @Override
    public boolean isReelLocked() {
        return parameters().reelLocked();
    }

    @Override
    public void setCatchAimedAtAngler(boolean aimed) {
        parameters().setCatchAimedAtAngler(aimed);
    }

    @Override
    public boolean isCatchAimedAtAngler() {
        return parameters().catchAimedAtAngler();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModernFishHookImpl && ((ModernFishHookImpl) other).hook == hook;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(hook);
    }
}
