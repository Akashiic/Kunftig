package com.akashic.kunftig.bukkit.blade;

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
import com.akashic.kunftig.api.bukkit.blade.BladeMotion;
import com.akashic.kunftig.api.bukkit.blade.KunftigBlades;
import com.akashic.kunftig.api.bukkit.blade.KunftigEffects;
import com.akashic.kunftig.blade.BladeStateTracker;
import com.akashic.kunftig.blade.BladeTimeline;
import com.akashic.kunftig.blade.BladeWire;
import com.akashic.kunftig.blade.EffectWire;
import com.akashic.kunftig.bukkit.fishing.CraftBridge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@link KunftigBlades} on Thermos/Crucible: keeps the states in a {@link BladeStateTracker} and, at the end of every
 * server tick, sends each viewer what changed as raw plugin messages on {@link BladeWire#CHANNEL}. Loaded
 * reflectively, only where Bukkit exists.
 */
public final class BukkitBladeBridge implements KunftigBlades.Provider, KunftigEffects.Provider {

    /** Blocks within which players get an effect (SlashBlade's effect entities track at 4 chunks). */
    private static final double EFFECT_RANGE = 64;

    private final BladeStateTracker<UUID, EntityPlayerMP> tracker = new BladeStateTracker<>();
    private long tick;

    private BukkitBladeBridge() {}

    /** Called reflectively by the mod at start-up. */
    public static void install() {
        BukkitBladeBridge bridge = new BukkitBladeBridge();
        FMLCommonHandler.instance()
            .bus()
            .register(bridge);
        KunftigBlades.install(bridge);
        KunftigEffects.install(bridge);
        Kunftig.LOG.info("Kunftig blades: Bukkit API enabled (KunftigBlades)");
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("KunftigBlades must be used on the main thread");
        }
    }

    private static BladeTimeline.Segment segment(BladeMotion.State s) {
        return new BladeTimeline.Segment(s.getStart(), s.getEnd(), s.isLoop(), s.getDurationMs());
    }

    @Override
    public void play(Player player, BladeMotion motion) {
        mainThread();
        List<BladeTimeline.Segment> chain = new ArrayList<>();
        for (BladeMotion.State s : motion.getChain()) {
            chain.add(segment(s));
        }
        BladeWire.Body body = motion.hasBody()
            ? new BladeWire.Body(motion.getBodyStart(), motion.getBodyEnd(), motion.isBlendLegs())
            : null;
        tracker.play(player.getUniqueId(), chain, segment(motion.getIdle()), body, tick);
    }

    @Override
    public void setCharged(Player player, boolean charged) {
        mainThread();
        tracker.setCharged(player.getUniqueId(), charged);
    }

    @Override
    public boolean drawsBlades(Player player) {
        return player.getListeningPluginChannels()
            .contains(BladeWire.CHANNEL);
    }

    @Override
    public void slash(String namespace, Location at, float yaw, float pitch, float roll, float rotationOffset,
        int color, float rank, float baseSize, int lifetime) {
        mainThread();
        byte[] data = EffectWire.slash(
            new EffectWire.Slash(
                namespace,
                at.getX(),
                at.getY(),
                at.getZ(),
                yaw,
                pitch,
                roll,
                rotationOffset,
                color,
                rank,
                baseSize,
                lifetime));
        for (Player p : at.getWorld()
            .getPlayers()) {
            if (p.getLocation()
                .distanceSquared(at) > EFFECT_RANGE * EFFECT_RANGE
                || !p.getListeningPluginChannels()
                    .contains(EffectWire.CHANNEL)) {
                continue;
            }
            net.minecraft.entity.Entity handle = CraftBridge.handle(p);
            if (handle instanceof EntityPlayerMP && ((EntityPlayerMP) handle).playerNetServerHandler != null) {
                ((EntityPlayerMP) handle).playerNetServerHandler
                    .sendPacket(new S3FPacketCustomPayload(EffectWire.CHANNEL, data));
            }
        }
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
        List<BladeStateTracker.Viewer<EntityPlayerMP>> viewers = new ArrayList<>();
        Map<UUID, BladeStateTracker.Holder> holders = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                net.minecraft.entity.Entity handle = CraftBridge.handle(player);
                if (!(handle instanceof EntityPlayerMP) || ((EntityPlayerMP) handle).playerNetServerHandler == null) {
                    continue;
                }
                Location at = player.getLocation();
                viewers.add(
                    new BladeStateTracker.Viewer<>(
                        (EntityPlayerMP) handle,
                        world.getName(),
                        at.getX(),
                        at.getY(),
                        at.getZ(),
                        drawsBlades(player)));
                holders.put(
                    player.getUniqueId(),
                    new BladeStateTracker.Holder(
                        player.getEntityId(),
                        world.getName(),
                        at.getX(),
                        at.getY(),
                        at.getZ()));
            }
        }
        for (BladeStateTracker.Out<EntityPlayerMP> out : tracker.update(viewers, holders, tick)) {
            if (out.connection.playerNetServerHandler != null) {
                out.connection.playerNetServerHandler
                    .sendPacket(new S3FPacketCustomPayload(BladeWire.CHANNEL, out.data));
            }
        }
    }
}
