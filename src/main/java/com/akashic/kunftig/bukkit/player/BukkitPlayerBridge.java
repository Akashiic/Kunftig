package com.akashic.kunftig.bukkit.player;

import net.minecraft.entity.player.EntityPlayerMP;

import org.bukkit.entity.Player;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.player.KunftigPlayers;
import com.akashic.kunftig.bukkit.fishing.CraftBridge;

/** {@link KunftigPlayers} on Thermos/Crucible. Loaded reflectively, only where Bukkit exists. */
public final class BukkitPlayerBridge implements KunftigPlayers.Provider {

    private BukkitPlayerBridge() {}

    /** Called reflectively by the mod at start-up. */
    public static void install() {
        KunftigPlayers.install(new BukkitPlayerBridge());
        Kunftig.LOG.info("Kunftig players: Bukkit API enabled (KunftigPlayers)");
    }

    @Override
    public int getPing(Player player) {
        net.minecraft.entity.Entity handle = CraftBridge.handle(player);
        if (!(handle instanceof EntityPlayerMP)) {
            throw new IllegalArgumentException("Not an online player: " + player);
        }
        return ((EntityPlayerMP) handle).ping;
    }

    @Override
    public void pushVelocity(Player player, double x, double y, double z) {
        net.minecraft.entity.Entity handle = CraftBridge.handle(player);
        if (!(handle instanceof EntityPlayerMP)) {
            throw new IllegalArgumentException("Not an online player: " + player);
        }
        EntityPlayerMP p = (EntityPlayerMP) handle;
        p.motionX = x;
        p.motionY = y;
        p.motionZ = z;
        if (p.playerNetServerHandler != null) {
            p.playerNetServerHandler
                .sendPacket(new net.minecraft.network.play.server.S12PacketEntityVelocity(p.getEntityId(), x, y, z));
        }
    }
}
