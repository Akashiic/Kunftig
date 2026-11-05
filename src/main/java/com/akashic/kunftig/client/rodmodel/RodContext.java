package com.akashic.kunftig.client.rodmodel;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * The world around whoever holds a rod, as the client sees it, for the manifest's {@code contextos_estados}: thunder or
 * rain falling on them, a snowy biome, the night. Computed where it is drawn (no packet: the client knows the weather
 * and the time), cheap enough for every frame.
 */
public final class RodContext {

    private RodContext() {}

    /** The contexts active for {@code holder} (any order; the manifest's order decides which one wins). */
    public static List<String> of(EntityLivingBase holder) {
        List<String> out = new ArrayList<>(2);
        if (holder == null || holder.worldObj == null) {
            return out;
        }
        World w = holder.worldObj;
        int x = MathHelper.floor_double(holder.posX);
        int y = MathHelper.floor_double(holder.boundingBox.maxY);
        int z = MathHelper.floor_double(holder.posZ);
        // rain on them: raining, under the open sky, in a biome where it rains (canLightningStrikeAt checks all three)
        boolean rainHere = w.isRaining() && w.canLightningStrikeAt(x, y, z);
        if (rainHere && w.isThundering()) {
            out.add("trovoada");
        }
        if (rainHere) {
            out.add("chuva");
        }
        BiomeGenBase biome = w.getBiomeGenForCoords(x, z);
        if (biome != null && biome.getEnableSnow()) {
            out.add("neve");
        }
        if (w.provider.isSurfaceWorld()) {
            long t = w.getWorldTime() % 24000L;
            if (t >= 13000L && t < 23000L) {
                out.add("noite");
            }
        }
        return out;
    }
}
