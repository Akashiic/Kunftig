package com.akashic.kunftig.mixin.mixins.fishing;

import java.util.Random;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashic.kunftig.KunftigConfig;
import com.akashic.kunftig.fishing.FishingEngine;
import com.akashic.kunftig.fishing.FishingParameters;
import com.akashic.kunftig.fishing.HookWorld;
import com.akashic.kunftig.fishing.KunftigFishHook;
import com.akashic.kunftig.fishing.TreasureRule;
import com.akashic.kunftig.fishing.api.KunftigFishing;

/**
 * Runs the bite cycle of {@code EntityFishHook} in {@link FishingEngine}.
 * <p>
 * Anchor: in {@code onUpdate}, the only {@code World.isAABBInMaterial} call is the water-fraction loop, and the first
 * {@code World.isRemote} read after it is vanilla's {@code if (!worldObj.isRemote && waterFraction > 0)} that guards
 * the bite cycle. Redirecting that read lets the engine run at exactly vanilla's point in the tick and skip vanilla's
 * copy; the movement code before and after is untouched. The same holds in Thermos' and Crucible's patched class
 * (their patches touch entity collision and reeling in, not this block). The engine keeps the state in vanilla's own
 * fields, so reeling in, Forge loot and Bukkit's PlayerFishEvent keep working unchanged.
 */
@Mixin(EntityFishHook.class)
public abstract class EntityFishHookMixin extends Entity implements KunftigFishHook {

    /** Ticks until a fish is lured (1.21 {@code timeUntilLured}). */
    @Shadow
    private int field_146040_ay;

    /** Ticks until the lured fish bites (1.21 {@code timeUntilHooked}). */
    @Shadow
    private int field_146038_az;

    /** Remaining bite window (1.21 {@code nibble}). */
    @Shadow
    private int field_146045_ax;

    /** Angle the fish comes from (1.21 {@code fishAngle}). */
    @Shadow
    private float field_146054_aA;

    /** Stuck in a block. */
    @Shadow
    private boolean field_146051_au;

    @Shadow
    public EntityPlayer field_146042_b;

    @Shadow
    public Entity field_146043_c;

    @Unique
    private final FishingParameters kunftig$parameters = new FishingParameters(KunftigConfig.fishingProfile);

    @Unique
    private boolean kunftig$bobbing;

    @Unique
    private boolean kunftig$openWater = true;

    @Unique
    private int kunftig$outOfWaterTime;

    private EntityFishHookMixin(World world) {
        super(world);
    }

    @Redirect(
        method = "onUpdate",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/World;isRemote:Z",
            opcode = Opcodes.GETFIELD,
            ordinal = 0),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/World;isAABBInMaterial(Lnet/minecraft/util/AxisAlignedBB;Lnet/minecraft/block/material/Material;)Z")))
    private boolean kunftig$catchFish(World world) {
        if (world.isRemote) {
            return true;
        }
        if (!KunftigConfig.fishingEnabled) {
            return false; // vanilla's block runs
        }
        EntityFishHook self = (EntityFishHook) (Object) this;
        if (HookWorld.waterFraction(self) > 0.0D) {
            kunftig$bobbing = true;
            kunftig$outOfWaterTime = Math.max(0, kunftig$outOfWaterTime - 1);
            if (field_146045_ax <= 0 && field_146038_az <= 0) {
                kunftig$openWater = true;
            } else {
                kunftig$openWater = kunftig$openWater && kunftig$outOfWaterTime < 10
                    && HookWorld.isOpenWater(
                        worldObj,
                        MathHelper.floor_double(posX),
                        MathHelper.floor_double(posY),
                        MathHelper.floor_double(posZ));
            }
            FishingEngine.tick(this, KunftigFishing.ENGINE_LISTENER);
        } else {
            kunftig$outOfWaterTime = Math.min(10, kunftig$outOfWaterTime + 1);
        }
        return true; // vanilla's copy of the cycle is skipped
    }

    /** 1.21's REEL_IN: reeling in with nothing hooked, nothing biting and not stuck in a block. */
    @Inject(method = "func_146034_e", at = @At("HEAD"), cancellable = true)
    private void kunftig$reelIn(CallbackInfoReturnable<Integer> cir) {
        if (!worldObj.isRemote && KunftigConfig.fishingEnabled
            && field_146043_c == null
            && field_146045_ax <= 0
            && !field_146051_au
            && !KunftigFishing.fireReelIn((EntityFishHook) (Object) this)) {
            cir.setReturnValue(0);
        }
    }

    @ModifyArg(
        method = "func_146033_f",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/common/FishingHooks;getFishableCategory(FII)Lnet/minecraftforge/common/FishingHooks$FishableCategory;",
            remap = false), // Forge class: same names in every environment
        index = 0)
    private float kunftig$treasureForStat(float chance) {
        return kunftig$treasureRule(chance);
    }

    @ModifyArg(
        method = "func_146033_f",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/common/FishingHooks;getRandomFishable(Ljava/util/Random;FII)Lnet/minecraft/item/ItemStack;",
            remap = false), // Forge class: same names in every environment
        index = 1)
    private float kunftig$treasureForLoot(float chance) {
        return kunftig$treasureRule(chance);
    }

    /** Applied to both calls with the same draw, so the statistic and the item always agree. */
    @Unique
    private float kunftig$treasureRule(float chance) {
        if (!KunftigConfig.fishingEnabled || !kunftig$parameters.treasureRequiresOpenWater() || kunftig$openWater) {
            return chance;
        }
        return TreasureRule.withoutTreasure(
            chance,
            EnchantmentHelper.func_151386_g(field_146042_b),
            EnchantmentHelper.func_151387_h(field_146042_b));
    }

    // ------------------------------------------------------------------ KunftigFishHook

    @Override
    public Random kunftig$random() {
        return rand;
    }

    @Override
    public FishingParameters kunftig$parameters() {
        return kunftig$parameters;
    }

    @Override
    public int kunftig$waitTime() {
        return field_146040_ay;
    }

    @Override
    public void kunftig$setWaitTime(int ticks) {
        field_146040_ay = ticks;
    }

    @Override
    public int kunftig$approachTime() {
        return field_146038_az;
    }

    @Override
    public void kunftig$setApproachTime(int ticks) {
        field_146038_az = ticks;
    }

    @Override
    public int kunftig$biteWindow() {
        return field_146045_ax;
    }

    @Override
    public void kunftig$setBiteWindow(int ticks) {
        field_146045_ax = ticks;
    }

    @Override
    public float kunftig$fishAngle() {
        return field_146054_aA;
    }

    @Override
    public void kunftig$setFishAngle(float degrees) {
        field_146054_aA = degrees;
    }

    @Override
    public int kunftig$lureLevel() {
        return EnchantmentHelper.func_151387_h(field_146042_b);
    }

    @Override
    public boolean kunftig$isRainingAbove() {
        return worldObj.canLightningStrikeAt(
            MathHelper.floor_double(posX),
            MathHelper.floor_double(posY) + 1,
            MathHelper.floor_double(posZ));
    }

    @Override
    public boolean kunftig$canSeeSkyAbove() {
        return worldObj.canBlockSeeTheSky(
            MathHelper.floor_double(posX),
            MathHelper.floor_double(posY) + 1,
            MathHelper.floor_double(posZ));
    }

    @Override
    public void kunftig$waitingSplash() {
        HookWorld.waitingSplash((EntityFishHook) (Object) this, rand);
    }

    @Override
    public void kunftig$approachEffects() {
        HookWorld.approachEffects((EntityFishHook) (Object) this, rand, field_146054_aA, field_146038_az);
    }

    @Override
    public void kunftig$biteEffects() {
        HookWorld.biteEffects((EntityFishHook) (Object) this, rand);
    }

    @Override
    public void kunftig$nibbleTug() {
        HookWorld.nibbleTug((EntityFishHook) (Object) this, rand);
    }

    @Override
    public boolean kunftig$isBobbing() {
        return kunftig$bobbing;
    }

    @Override
    public boolean kunftig$isInOpenWater() {
        return kunftig$openWater;
    }
}
