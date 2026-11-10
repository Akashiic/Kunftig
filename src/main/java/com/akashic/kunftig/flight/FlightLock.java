package com.akashic.kunftig.flight;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The flight lock on the server ({@code KunftigPlayers#setFlightLocked}, API 9): a locked player (not in creative)
 * cannot fly, whatever gives the flight. At the end of each of their ticks, after rings, armour, baubles and spells
 * switched flying on during the tick, it is switched off again and the client told. Every mod that flies through the
 * player's capabilities (the creative flight: Extra Utilities' angel ring, Draconic Evolution's armour, Botania's
 * tiara, Avaritia, Ars Magica's flight, Thaumcraft's harness, Witchery's bat...) is covered by this one rule.
 * <p>
 * The client (Kunftig) is told on {@link #CHANNEL} and does the same on its side, plus cutting thrust that does not
 * come from a jump or a push ({@link FlightEnvelope}): jetpacks and gliders move the player on the client. The lock is
 * a protection layer, not an anticheat: a modified client ignores it, so the plugin should also check that players do
 * not stay in the air. Main thread.
 */
public final class FlightLock {

    public static final FlightLock INSTANCE = new FlightLock();
    /** Server to client: one byte, 1 locked, 0 free. Kunftig clients register it on join. */
    public static final String CHANNEL = "kunftig:flight";

    private final Set<UUID> locked = ConcurrentHashMap.newKeySet();
    /** Who could fly when the lock came (a one-time grant, like a /fly): given back when it goes. */
    private final Set<UUID> hadFlight = ConcurrentHashMap.newKeySet();

    private FlightLock() {}

    public boolean isLocked(UUID player) {
        return locked.contains(player);
    }

    /** Locks or frees the player, and tells their client. */
    public void set(EntityPlayerMP player, boolean lock) {
        UUID id = player.getUniqueID();
        boolean changed = lock ? locked.add(id) : locked.remove(id);
        if (changed) {
            if (lock) {
                if (player.capabilities.allowFlying && !player.capabilities.isCreativeMode) {
                    hadFlight.add(id);
                }
                enforce(player);
            } else if (hadFlight.remove(id) && !player.capabilities.isCreativeMode) {
                player.capabilities.allowFlying = true;
                player.sendPlayerAbilities();
            }
            tell(player, lock);
        }
    }

    /**
     * Leaving the server drops the lock: it lived only in memory, and a player who quit in the middle of a fight stayed
     * locked on every later login until the server restarted (the plugin locks them again if they come back into the
     * fight).
     */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.player.getUniqueID();
        locked.remove(id);
        hadFlight.remove(id);
    }

    private static void tell(EntityPlayerMP player, boolean lock) {
        if (player.playerNetServerHandler != null) {
            player.playerNetServerHandler
                .sendPacket(new S3FPacketCustomPayload(CHANNEL, new byte[] { (byte) (lock ? 1 : 0) }));
        }
    }

    private static void enforce(EntityPlayer player) {
        if (player.capabilities.isCreativeMode) {
            return;
        }
        if (player.capabilities.allowFlying || player.capabilities.isFlying) {
            player.capabilities.allowFlying = false;
            player.capabilities.isFlying = false;
            player.sendPlayerAbilities();
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.worldObj.isRemote
            && locked.contains(event.player.getUniqueID())) {
            enforce(event.player);
        }
    }

    /** A new connection or a respawn: the client forgot, tell it again. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP && locked.contains(event.player.getUniqueID())) {
            tell((EntityPlayerMP) event.player, true);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP && locked.contains(event.player.getUniqueID())) {
            tell((EntityPlayerMP) event.player, true);
        }
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP && locked.contains(event.player.getUniqueID())) {
            tell((EntityPlayerMP) event.player, true);
        }
    }

    /** The channel name as the client registers it. */
    public static byte[] registration() {
        return CHANNEL.getBytes(StandardCharsets.UTF_8);
    }
}
