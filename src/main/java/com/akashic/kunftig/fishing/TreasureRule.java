package com.akashic.kunftig.fishing;

/**
 * Applies "treasure only in open water" (1.16+) to 1.7.10 Forge loot.
 * <p>
 * Forge picks the loot category from one uniform draw {@code chance}: junk in {@code [0, J)}, treasure in
 * {@code [J, J+T)}, fish in {@code [J+T, 1)}, with J and T depending on Luck of the Sea and Lure exactly as in
 * {@code FishingHooks.getRandomFishable}. Outside open water a draw that falls on treasure is moved, uniformly, into
 * junk or fish in proportion to their sizes. That is what a loot table does when a conditional entry is excluded:
 * the remaining weights are renormalised. Draws outside the treasure interval are untouched.
 */
public final class TreasureRule {

    private TreasureRule() {}

    public static float junkChance(int luck, int lure) {
        return clamp(0.1F - luck * 0.025F - lure * 0.01F);
    }

    public static float treasureChance(int luck, int lure) {
        return clamp(0.05F + luck * 0.01F - lure * 0.01F);
    }

    /** The draw to use instead of {@code chance} when the hook is not in open water. */
    public static float withoutTreasure(float chance, int luck, int lure) {
        float junk = junkChance(luck, lure);
        float treasure = treasureChance(luck, lure);
        if (chance < junk || chance >= junk + treasure || treasure <= 0.0F) {
            return chance;
        }
        float rest = 1.0F - treasure; // junk + fish
        if (rest <= 0.0F) {
            return chance;
        }
        // u is uniform in [0, 1) inside the treasure interval; spread it over [0, junk) U [junk + treasure, 1).
        float scaled = (chance - junk) / treasure * rest;
        float remapped = scaled < junk ? scaled : scaled + treasure;
        // Float rounding must never land back on treasure or reach 1.
        if (remapped >= junk && remapped < junk + treasure) {
            remapped = junk + treasure;
        }
        return Math.min(remapped, Math.nextDown(1.0F));
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
