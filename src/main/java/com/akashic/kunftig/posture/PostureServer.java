package com.akashic.kunftig.posture;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.server.MinecraftServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * The postures on the server ({@code KunftigPlayers#setPosture}, API 15): who sits or lies and where, told to every
 * client on {@link PostureWire#CHANNEL} (a client without Kunftig ignores the channel and sees the player standing
 * where his own client holds him). A posture ends when the plugin ends it, or when the player leaves, respawns or
 * changes dimension (his entity is another one). Whoever joins is told the postures there are. Main thread.
 */
public final class PostureServer {

    public static final PostureServer INSTANCE = new PostureServer();

    private final Map<UUID, PostureWire.Posture> postures = new ConcurrentHashMap<>();

    private PostureServer() {}

    /** The player's posture, or null when he stands. */
    public PostureWire.Posture of(UUID player) {
        return postures.get(player);
    }

    /** Sits or lays the player down ({@code posture}'s entity is set here), or stands him up (null). */
    public void set(EntityPlayerMP player, PostureWire.Posture posture) {
        UUID id = player.getUniqueID();
        if (posture == null || posture.kind == PostureWire.STAND) {
            if (postures.remove(id) != null) {
                broadcast(PostureWire.stand(player.getEntityId()));
            }
            return;
        }
        PostureWire.Posture mine = posture.forEntity(player.getEntityId());
        postures.put(id, mine);
        broadcast(mine);
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayerMP> online() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null || server.getConfigurationManager() == null ? java.util.Collections.emptyList()
            : (List<EntityPlayerMP>) server.getConfigurationManager().playerEntityList;
    }

    private static void broadcast(PostureWire.Posture posture) {
        byte[] data = PostureWire.encode(posture);
        for (EntityPlayerMP viewer : online()) {
            send(viewer, data);
        }
    }

    private static void send(EntityPlayerMP viewer, byte[] data) {
        if (viewer.playerNetServerHandler != null) {
            viewer.playerNetServerHandler.sendPacket(new S3FPacketCustomPayload(PostureWire.CHANNEL, data));
        }
    }

    private void drop(net.minecraft.entity.player.EntityPlayer player, int entityBefore) {
        if (postures.remove(player.getUniqueID()) != null) {
            broadcast(PostureWire.stand(entityBefore));
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        drop(event.player, event.player.getEntityId());
    }

    /** A respawn makes a new entity: the old one's posture is dropped by its id, which the clients knew. */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PostureWire.Posture before = postures.get(event.player.getUniqueID());
        if (before != null) {
            drop(event.player, before.entity);
        }
        tellAll(event.player);
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PostureWire.Posture before = postures.get(event.player.getUniqueID());
        if (before != null) {
            drop(event.player, before.entity);
        }
        tellAll(event.player);
    }

    /** A new connection: the client knows nothing, tell it who sits and lies. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        tellAll(event.player);
    }

    private void tellAll(net.minecraft.entity.player.EntityPlayer viewer) {
        if (viewer instanceof EntityPlayerMP) {
            for (PostureWire.Posture p : postures.values()) {
                send((EntityPlayerMP) viewer, PostureWire.encode(p));
            }
        }
    }
}
