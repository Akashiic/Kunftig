package com.akashic.kunftig.bukkit.player;

import net.minecraft.entity.player.EntityPlayerMP;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.player.KunftigPlayers;
import com.akashic.kunftig.bukkit.fishing.CraftBridge;
import com.akashic.kunftig.flight.FlightLock;

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
    public void setFlightLocked(Player player, boolean locked) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("KunftigPlayers.setFlightLocked must be used on the main thread");
        }
        net.minecraft.entity.Entity handle = CraftBridge.handle(player);
        if (!(handle instanceof EntityPlayerMP)) {
            throw new IllegalArgumentException("Not an online player: " + player);
        }
        FlightLock.INSTANCE.set((EntityPlayerMP) handle, locked);
    }

    @Override
    public boolean isFlightLocked(Player player) {
        return FlightLock.INSTANCE.isLocked(player.getUniqueId());
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

    private final java.util.Map<java.util.UUID, com.akashic.kunftig.api.bukkit.player.Posture> postures = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void setPosture(Player player, com.akashic.kunftig.api.bukkit.player.Posture posture) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("KunftigPlayers.setPosture must be used on the main thread");
        }
        net.minecraft.entity.Entity handle = CraftBridge.handle(player);
        if (!(handle instanceof EntityPlayerMP)) {
            throw new IllegalArgumentException("Not an online player: " + player);
        }
        EntityPlayerMP p = (EntityPlayerMP) handle;
        if (posture == null) {
            postures.remove(player.getUniqueId());
            com.akashic.kunftig.posture.PostureServer.INSTANCE.set(p, null);
            return;
        }
        com.akashic.kunftig.api.bukkit.display.Display display = posture.getDisplay();
        if (display != null && display.isRemoved()) {
            throw new IllegalArgumentException("The posture's display was removed");
        }
        postures.put(player.getUniqueId(), posture);
        com.akashic.kunftig.posture.PostureServer.INSTANCE.set(
            p,
            new com.akashic.kunftig.posture.PostureWire.Posture(
                p.getEntityId(),
                posture.isLying() ? com.akashic.kunftig.posture.PostureWire.LIE
                    : com.akashic.kunftig.posture.PostureWire.SIT,
                display == null ? com.akashic.kunftig.posture.PostureWire.WORLD : display.getId(),
                posture.getX(),
                posture.getY(),
                posture.getZ(),
                posture.getYaw(),
                posture.getEyes()));
    }

    @Override
    public com.akashic.kunftig.api.bukkit.player.Posture getPosture(Player player) {
        // the mod drops a posture with the player's entity (logout, respawn, dimension): only what it still holds
        if (com.akashic.kunftig.posture.PostureServer.INSTANCE.of(player.getUniqueId()) == null) {
            postures.remove(player.getUniqueId());
            return null;
        }
        return postures.get(player.getUniqueId());
    }

    @Override
    public boolean drawsPostures(Player player) {
        return player.getListeningPluginChannels()
            .contains(com.akashic.kunftig.posture.PostureWire.CHANNEL);
    }
}
