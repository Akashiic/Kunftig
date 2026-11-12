package com.akashic.kunftig.mixin.mixins.fishing;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashic.kunftig.KunftigConfig;
import com.akashic.kunftig.compat.NeoStatesBridge;
import com.akashic.kunftig.fishing.CatchArc;
import com.akashic.kunftig.fishing.FishingEngine;
import com.akashic.kunftig.fishing.FishingParameters;
import com.akashic.kunftig.fishing.HookWorld;
import com.akashic.kunftig.fishing.KunftigFishHook;
import com.akashic.kunftig.fishing.ModernBobber;
import com.akashic.kunftig.fishing.ModernBobberMode;
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
 * <p>
 * Lava fishing (off unless enabled for the hook): the fluid argument of that same {@code isAABBInMaterial} call becomes
 * lava where there is no water, so vanilla's own buoyancy code floats the hook on lava; the engine then runs as in
 * water, with fire effects. The hook and the caught item are fire-proof, and the item starts above the lava surface
 * because {@code EntityItem} throws itself in a random direction while inside a lava block.
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
    private final FishingParameters kunftig$parameters = kunftig$initialParameters();

    @Unique
    private boolean kunftig$bobbing;

    @Unique
    private boolean kunftig$openWater = true;

    @Unique
    private int kunftig$outOfWaterTime;

    @Unique
    private boolean kunftig$inLava;

    /** Whether Kunftig made this hook fire-proof (so it only undoes its own change). */
    @Unique
    private boolean kunftig$fireProof;

    private EntityFishHookMixin(World world) {
        super(world);
    }

    @Unique
    private static FishingParameters kunftig$initialParameters() {
        FishingParameters parameters = new FishingParameters(KunftigConfig.fishingProfile, KunftigConfig.fishingLava);
        parameters.setCatchAimedAtAngler(KunftigConfig.fishingCatchAim);
        return parameters;
    }

    /** Set by {@code KunftigFishing.retrieve}: the plugin itself is reeling in, so the reel lock does not apply. */
    @Unique
    private boolean kunftig$retrieving;

    @Override
    public void kunftig$setRetrieving(boolean retrieving) {
        kunftig$retrieving = retrieving;
    }

    @Unique
    private boolean kunftig$lavaFishing() {
        return KunftigConfig.fishingEnabled && kunftig$parameters.lavaFishing();
    }

    /**
     * Vanilla removes the hook 32 blocks from its angler ({@code getDistanceSqToEntity(angler) > 1024.0D}, the only
     * 1024.0 in {@code onUpdate}, server side); the hook's own range replaces it (API 10).
     */
    @ModifyConstant(method = "onUpdate", constant = @Constant(doubleValue = 1024.0D))
    private double kunftig$maxDistanceSq(double vanilla) {
        double d = kunftig$parameters.maxDistance();
        return d == FishingParameters.DEFAULT_MAX_DISTANCE ? vanilla : d * d;
    }

    /** Before {@code Entity.onEntityUpdate} sets the hook on fire. */
    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void kunftig$fireProofing(CallbackInfo ci) {
        if (worldObj.isRemote) {
            return;
        }
        boolean lava = kunftig$lavaFishing();
        if (lava != kunftig$fireProof) {
            kunftig$fireProof = lava;
            isImmuneToFire = lava;
        }
    }

    /**
     * Lava fishing: vanilla's water-fraction loop counts lava where there is no water, so the same buoyancy code
     * floats the hook. Server side only; the client keeps vanilla physics and follows the server's position updates.
     */
    @ModifyArg(
        method = "onUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;isAABBInMaterial(Lnet/minecraft/util/AxisAlignedBB;Lnet/minecraft/block/material/Material;)Z"),
        index = 1)
    private Material kunftig$fluid(AxisAlignedBB slice, Material material) {
        if (worldObj.isRemote || material != Material.water || !kunftig$lavaFishing()) {
            return material;
        }
        return worldObj.isAABBInMaterial(slice, Material.water) ? Material.water : Material.lava;
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
        boolean skipVanilla = kunftig$runCycle(world);
        // The velocity before vanilla's buoyancy (bite pulls of the engine included), for the modern bobber below.
        kunftig$preBuoyancyX = motionX;
        kunftig$preBuoyancyY = motionY;
        kunftig$preBuoyancyZ = motionZ;
        return skipVanilla;
    }

    @Unique
    private double kunftig$preBuoyancyX;

    @Unique
    private double kunftig$preBuoyancyY;

    @Unique
    private double kunftig$preBuoyancyZ;

    @Unique
    private boolean kunftig$wasInFluid;

    @Unique
    private boolean kunftig$runCycle(World world) {
        if (world.isRemote) {
            return true;
        }
        if (!KunftigConfig.fishingEnabled) {
            return false; // vanilla's block runs
        }
        EntityFishHook self = (EntityFishHook) (Object) this;
        boolean inWater = HookWorld.fluidFraction(self, Material.water) > 0.0D;
        kunftig$inLava = !inWater && kunftig$lavaFishing() && HookWorld.fluidFraction(self, Material.lava) > 0.0D;
        if (inWater || kunftig$inLava) {
            kunftig$bobbing = true;
            kunftig$outOfWaterTime = Math.max(0, kunftig$outOfWaterTime - 1);
            if (field_146045_ax <= 0 && field_146038_az <= 0) {
                kunftig$openWater = true;
            } else {
                kunftig$openWater = kunftig$openWater && kunftig$outOfWaterTime < 10
                    && HookWorld.isOpenFluid(
                        worldObj,
                        MathHelper.floor_double(posX),
                        MathHelper.floor_double(posY),
                        MathHelper.floor_double(posZ),
                        kunftig$inLava ? Material.lava : Material.water);
            }
            FishingEngine.tick(this, KunftigFishing.ENGINE_LISTENER);
        } else {
            kunftig$outOfWaterTime = Math.min(10, kunftig$outOfWaterTime + 1);
        }
        return true; // vanilla's copy of the cycle is skipped
    }

    /**
     * Modern bobber physics ({@link ModernBobber}), when the server turns it on ({@code fishing.modernBobber}):
     * replaces
     * the velocity vanilla's buoyancy just computed, right before the tick's final {@code setPosition}. Runs on the
     * server and, when the server announced it, on Kunftig clients, so both predict the same motion between the
     * tracker's updates.
     */
    @Inject(
        method = "onUpdate",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/projectile/EntityFishHook;setPosition(DDD)V"),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/World;isAABBInMaterial(Lnet/minecraft/util/AxisAlignedBB;Lnet/minecraft/block/material/Material;)Z")))
    private void kunftig$modernBobber(CallbackInfo ci) {
        if (!(worldObj.isRemote ? ModernBobberMode.client()
            : KunftigConfig.fishingEnabled && KunftigConfig.fishingModernBobber)) {
            return;
        }
        Double surface = kunftig$fluidSurface();
        boolean entering = surface != null && !kunftig$wasInFluid;
        kunftig$wasInFluid = surface != null;
        double[] velocity = ModernBobber.step(
            new double[] { kunftig$preBuoyancyX, kunftig$preBuoyancyY, kunftig$preBuoyancyZ },
            posY,
            surface,
            entering,
            rand);
        motionX = velocity[0];
        motionY = velocity[1];
        motionZ = velocity[2];
    }

    /** Absolute height of the drawn fluid surface in the hook's block, or null out of fluid. */
    @Unique
    private Double kunftig$fluidSurface() {
        int x = MathHelper.floor_double(posX);
        int y = MathHelper.floor_double(posY);
        int z = MathHelper.floor_double(posZ);
        // Neo States, when installed, owns the water surface (water held inside a slab or stairs included).
        Double neoSurface = NeoStatesBridge.waterSurface(worldObj, x, y, z);
        if (neoSurface != null) {
            return neoSurface;
        }
        Material material = worldObj.getBlock(x, y, z)
            .getMaterial();
        boolean fluid = material == Material.water || material == Material.lava && kunftig$inLava;
        if (!fluid) {
            return null;
        }
        boolean fluidAbove = worldObj.getBlock(x, y + 1, z)
            .getMaterial() == material;
        return y + ModernBobber.surfaceHeight(worldObj.getBlockMetadata(x, y, z), fluidAbove);
    }

    /**
     * The reel lock (issue #9): every path that reels in (right click in the air or on a block, Backhand's off hand,
     * mods) ends in this method; while locked it returns 0 at once: no rod damage, the hook stays and the bite goes on.
     * Then 1.21's REEL_IN: reeling in with nothing hooked, nothing biting and not stuck in a block.
     */
    @Inject(method = "func_146034_e", at = @At("HEAD"), cancellable = true)
    private void kunftig$reelIn(CallbackInfoReturnable<Integer> cir) {
        if (worldObj.isRemote || !KunftigConfig.fishingEnabled) {
            return;
        }
        if (kunftig$parameters.reelLocked() && !kunftig$retrieving) {
            cir.setReturnValue(0);
            return;
        }
        if (field_146043_c == null && field_146045_ax <= 0
            && !field_146051_au
            && !KunftigFishing.fireReelIn((EntityFishHook) (Object) this)) {
            cir.setReturnValue(0);
        }
    }

    /**
     * The caught item: lava fishing makes it fire-proof and lifts it out of the lava, and the optional aim raises its
     * arc so it lands at the angler (issue #10). Crucible has already fired PlayerFishEvent CAUGHT_FISH and set the
     * vanilla motion by now.
     */
    @ModifyArg(
        method = "func_146034_e",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;spawnEntityInWorld(Lnet/minecraft/entity/Entity;)Z"))
    private Entity kunftig$caughtItem(Entity entity) {
        if (!(entity instanceof EntityItem) || !KunftigConfig.fishingEnabled) {
            return entity;
        }
        if (kunftig$inLava && kunftig$lavaFishing()) {
            kunftig$lavaProof(entity);
        }
        if (kunftig$parameters.catchAimedAtAngler() && field_146042_b != null) {
            entity.motionY = CatchArc.aimedVerticalSpeed(entity.motionY, field_146042_b.boundingBox.minY - entity.posY);
        }
        return entity;
    }

    @Unique
    private void kunftig$lavaProof(Entity entity) {
        ((EntityFireAccessor) entity).kunftig$setImmuneToFire(true);
        int x = MathHelper.floor_double(entity.posX);
        int z = MathHelper.floor_double(entity.posZ);
        int y = MathHelper.floor_double(entity.posY);
        int top = y;
        while (top - y < 3 && worldObj.getBlock(x, top, z)
            .getMaterial() == Material.lava) {
            top++;
        }
        if (top != y) {
            entity.setPosition(entity.posX, top + 0.05D, entity.posZ);
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
        HookWorld.waitingSplash((EntityFishHook) (Object) this, rand, kunftig$inLava);
    }

    @Override
    public void kunftig$approachEffects() {
        HookWorld
            .approachEffects((EntityFishHook) (Object) this, rand, field_146054_aA, field_146038_az, kunftig$inLava);
    }

    @Override
    public void kunftig$biteEffects() {
        HookWorld.biteEffects((EntityFishHook) (Object) this, rand, kunftig$inLava);
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

    @Override
    public boolean kunftig$isInLava() {
        return kunftig$inLava;
    }
}
