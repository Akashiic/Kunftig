package com.akashic.kunftig.fishing;

/**
 * Reeling in from the server, in the order of 1.7.10's {@code ItemFishingRod.onItemRightClick} followed by the
 * clean-up {@code ItemInWorldManager.tryUseItem} does after it: reel in ({@code EntityFishHook.func_146034_e}, which
 * fires Bukkit's {@code PlayerFishEvent} on Crucible), damage the rod by what that returned, remove a rod that broke,
 * resend the inventory, swing the arm. No Minecraft classes here, so the order is unit tested.
 */
public final class RodRetrieval {

    /** The world side of a retrieval. */
    public interface Angler {

        /** {@code hook.func_146034_e()}: catches, pulls or reels in; returns the rod damage. */
        int reelIn();

        /** Whether the angler holds a fishing rod (decided before reeling in). */
        boolean holdsRod();

        /** {@code ItemStack.damageItem(amount, angler)}. */
        void damageRod(int amount);

        /** The rod's stack size reached 0 (it broke). */
        boolean rodBroken();

        /** Empty the slot and post {@code PlayerDestroyItemEvent}, as {@code tryUseItem} does. */
        void destroyRod();

        /** {@code sendContainerToPlayer}, as {@code tryUseItem} does after using an item. */
        void syncInventory();

        /** {@code swingItem()}: the other players see the arm move. */
        void swing();
    }

    private RodRetrieval() {}

    /**
     * @return what {@code func_146034_e} returned: 0 nothing (or a cancelled catch on Crucible), 1 a fish, 2 stuck in
     *         the ground, 3 a hooked entity; applied to the rod only if {@code applyRodDamage}
     */
    public static int retrieve(Angler angler, boolean applyRodDamage) {
        boolean rod = angler.holdsRod();
        int damage = angler.reelIn();
        if (!applyRodDamage) {
            return damage;
        }
        if (rod && damage > 0) {
            angler.damageRod(damage);
            if (angler.rodBroken()) {
                angler.destroyRod();
            }
            angler.syncInventory();
        }
        angler.swing();
        return damage;
    }
}
