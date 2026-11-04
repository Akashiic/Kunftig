package com.akashic.kunftig.bukkit.rod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.rod.KunftigRods;
import com.akashic.kunftig.bukkit.fishing.CraftBridge;
import com.akashic.kunftig.rodmodel.RodStateTracker;
import com.akashic.kunftig.rodmodel.RodWire;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@link KunftigRods} on Thermos/Crucible (issue #13): keeps the states in a {@link RodStateTracker} and, at the end
 * of every server tick, sends each viewer what changed as raw plugin messages on {@link RodWire#CHANNEL} (straight to
 * the connection, no Bukkit channel registration needed). Loaded reflectively, only where Bukkit exists.
 */
public final class BukkitRodBridge implements KunftigRods.Provider {

    private final RodStateTracker<UUID, EntityPlayerMP> tracker = new RodStateTracker<>();
    private long tick;

    private BukkitRodBridge() {}

    /** Called reflectively by the mod at start-up. */
    public static void install() {
        BukkitRodBridge bridge = new BukkitRodBridge();
        FMLCommonHandler.instance()
            .bus()
            .register(bridge);
        KunftigRods.install(bridge);
        Kunftig.LOG.info("Kunftig 3D rods: Bukkit API enabled (KunftigRods)");
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("KunftigRods must be used on the main thread");
        }
    }

    @Override
    public void setState(Player player, String state) {
        mainThread();
        tracker.setState(player.getUniqueId(), state, tick);
    }

    @Override
    public void setTension(Player player, float tension) {
        mainThread();
        tracker.setTension(player.getUniqueId(), tension);
    }

    @Override
    public String getState(Player player) {
        mainThread();
        return tracker.state(player.getUniqueId());
    }

    @Override
    public boolean drawsRodModels(Player player) {
        return player.getListeningPluginChannels()
            .contains(RodWire.CHANNEL);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        if (tracker.size() == 0) {
            return;
        }
        List<RodStateTracker.Viewer<EntityPlayerMP>> viewers = new ArrayList<>();
        Map<UUID, RodStateTracker.Angler> anglers = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                net.minecraft.entity.Entity handle = CraftBridge.handle(player);
                if (!(handle instanceof EntityPlayerMP) || ((EntityPlayerMP) handle).playerNetServerHandler == null) {
                    continue;
                }
                Location at = player.getLocation();
                viewers.add(
                    new RodStateTracker.Viewer<>(
                        (EntityPlayerMP) handle,
                        world.getName(),
                        at.getX(),
                        at.getY(),
                        at.getZ(),
                        drawsRodModels(player)));
                anglers.put(
                    player.getUniqueId(),
                    new RodStateTracker.Angler(player.getEntityId(), world.getName(), at.getX(), at.getY(), at.getZ()));
            }
        }
        for (RodStateTracker.Out<EntityPlayerMP> out : tracker.update(viewers, anglers, tick)) {
            if (out.connection.playerNetServerHandler != null) {
                out.connection.playerNetServerHandler.sendPacket(new S3FPacketCustomPayload(RodWire.CHANNEL, out.data));
            }
        }
    }
}
