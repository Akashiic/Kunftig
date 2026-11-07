package com.akashic.kunftig.bukkit.blade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.blade.BladeMotion;
import com.akashic.kunftig.api.bukkit.blade.KunftigBlades;
import com.akashic.kunftig.api.bukkit.blade.KunftigEffects;
import com.akashic.kunftig.blade.BladeStateTracker;
import com.akashic.kunftig.blade.BladeTimeline;
import com.akashic.kunftig.blade.BladeWire;
import com.akashic.kunftig.blade.EffectProjectile;
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
    /** Live projectiles: who got them (later messages go to the same players) and when they last changed. */
    private final Map<Integer, Audience> projectiles = new LinkedHashMap<>();
    private int nextProjectileId = 1;
    private long tick;

    private static final class Audience {

        final List<UUID> players;
        long lastChange;

        Audience(List<UUID> players, long lastChange) {
            this.players = players;
            this.lastChange = lastChange;
        }
    }

    private BukkitBladeBridge() {}

    /** Called reflectively by the mod at start-up. */
    public static void install() {
        BukkitBladeBridge bridge = new BukkitBladeBridge();
        FMLCommonHandler.instance()
            .bus()
            .register(bridge);
        KunftigBlades.install(bridge);
        KunftigEffects.install(bridge);
        Kunftig.LOG.info("Kunftig blades: Bukkit API enabled (KunftigBlades, KunftigEffects)");
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
        broadcast(
            at,
            EffectWire.slash(
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
                    lifetime)));
    }

    @Override
    public void judgementCut(String namespace, Location at, float yaw, float pitch, int color, int lifetime) {
        mainThread();
        broadcast(
            at,
            EffectWire.judgementCut(
                new EffectWire.JudgementCut(namespace, at.getX(), at.getY(), at.getZ(), yaw, pitch, color, lifetime)));
    }

    @Override
    public KunftigEffects.Projectile projectile(String namespace, String effect, int kind, Location at, Vector velocity,
        float yaw, float pitch, float roll, int color, float lifetime) {
        mainThread();
        int id = nextProjectileId++;
        if (nextProjectileId == Integer.MAX_VALUE) {
            nextProjectileId = 1;
        }
        List<UUID> got = broadcast(
            at,
            EffectWire.projectile(
                new EffectWire.Projectile(
                    namespace,
                    effect,
                    id,
                    kind,
                    at.getX(),
                    at.getY(),
                    at.getZ(),
                    velocity.getX(),
                    velocity.getY(),
                    velocity.getZ(),
                    yaw,
                    pitch,
                    roll,
                    color,
                    lifetime)));
        projectiles.put(id, new Audience(got, tick));
        return new Handle(id);
    }

    /** A projectile's later messages, to the players that got it. */
    private final class Handle implements KunftigEffects.Projectile {

        private final int id;

        Handle(int id) {
            this.id = id;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public void move(Location at, Vector velocity) {
            float[] rot = KunftigEffects.rotation(velocity);
            move(at, velocity, rot[0], rot[1]);
        }

        @Override
        public void move(Location at, Vector velocity, float yaw, float pitch) {
            mainThread();
            send(
                id,
                EffectWire.move(
                    new EffectWire.Move(
                        id,
                        at.getX(),
                        at.getY(),
                        at.getZ(),
                        velocity.getX(),
                        velocity.getY(),
                        velocity.getZ(),
                        yaw,
                        pitch)),
                false);
        }

        @Override
        public void stick(Entity target, float offsetYaw) {
            mainThread();
            send(id, EffectWire.stick(new EffectWire.Stick(id, target.getEntityId(), offsetYaw)), false);
        }

        @Override
        public void remove(boolean burst) {
            mainThread();
            send(id, EffectWire.remove(new EffectWire.Remove(id, burst)), true);
        }
    }

    private void send(int id, byte[] data, boolean last) {
        Audience a = last ? projectiles.remove(id) : projectiles.get(id);
        if (a == null) {
            return;
        }
        a.lastChange = tick;
        for (UUID u : a.players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                send(p, EffectWire.CHANNEL, data);
            }
        }
    }

    @Override
    public void lockOn(Player owner, String namespace, Entity target, int color) {
        mainThread();
        if (listens(owner)) {
            send(
                owner,
                EffectWire.CHANNEL,
                EffectWire.lock(new EffectWire.Lock(namespace, target == null ? -1 : target.getEntityId(), color)));
        }
    }

    @Override
    public void rank(Player owner, String namespace, int points) {
        mainThread();
        if (listens(owner)) {
            send(owner, EffectWire.CHANNEL, EffectWire.rank(new EffectWire.Rank(namespace, points)));
        }
    }

    @Override
    public void hits(Player owner, String namespace, int hits) {
        mainThread();
        if (listens(owner)) {
            send(owner, EffectWire.CHANNEL, EffectWire.hits(new EffectWire.Hits(namespace, hits)));
        }
    }

    private static boolean listens(Player p) {
        return p.getListeningPluginChannels()
            .contains(EffectWire.CHANNEL);
    }

    /** Sends {@code data} to the players within {@link #EFFECT_RANGE} of {@code at} drawing effects; returns them. */
    private static List<UUID> broadcast(Location at, byte[] data) {
        List<UUID> got = new ArrayList<>();
        for (Player p : at.getWorld()
            .getPlayers()) {
            if (p.getLocation()
                .distanceSquared(at) <= EFFECT_RANGE * EFFECT_RANGE && listens(p)
                && send(p, EffectWire.CHANNEL, data)) {
                got.add(p.getUniqueId());
            }
        }
        return got;
    }

    private static boolean send(Player p, String channel, byte[] data) {
        net.minecraft.entity.Entity handle = CraftBridge.handle(p);
        if (handle instanceof EntityPlayerMP && ((EntityPlayerMP) handle).playerNetServerHandler != null) {
            ((EntityPlayerMP) handle).playerNetServerHandler.sendPacket(new S3FPacketCustomPayload(channel, data));
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        for (Iterator<Audience> it = projectiles.values()
            .iterator(); it.hasNext();) {
            if (it.next().lastChange + EffectProjectile.MAX_AGE < tick) { // the clients forgot it too
                it.remove();
            }
        }
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
