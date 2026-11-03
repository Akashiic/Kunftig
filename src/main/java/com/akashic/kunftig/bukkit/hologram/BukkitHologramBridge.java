package com.akashic.kunftig.bukkit.hologram;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.hologram.Hologram;
import com.akashic.kunftig.api.bukkit.hologram.KunftigHolograms;
import com.akashic.kunftig.bukkit.fishing.CraftBridge;
import com.akashic.kunftig.hologram.HologramTracker;
import com.akashic.kunftig.hologram.HologramWire;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@link KunftigHolograms} on Thermos/Crucible: keeps the holograms in a {@link HologramTracker} and, at the end of
 * every server tick, sends each viewer what changed as raw plugin messages on {@link HologramWire#CHANNEL} (straight to
 * the connection, no Bukkit channel registration needed). Loaded reflectively, only where Bukkit exists.
 */
public final class BukkitHologramBridge implements KunftigHolograms.Provider {

    private final HologramTracker<EntityPlayerMP> tracker = new HologramTracker<>();

    private BukkitHologramBridge() {}

    /** Called reflectively by the mod at start-up. */
    public static void install() {
        if (Boolean.getBoolean("kunftig.holograms.disable")) {
            Kunftig.LOG.warn("Kunftig holograms disabled by -Dkunftig.holograms.disable=true");
            return;
        }
        BukkitHologramBridge bridge = new BukkitHologramBridge();
        FMLCommonHandler.instance()
            .bus()
            .register(bridge);
        KunftigHolograms.install(bridge);
        Kunftig.LOG.info("Kunftig holograms: Bukkit API enabled (KunftigHolograms)");
    }

    @Override
    public Hologram create(Location location, List<String> lines, double viewRange) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("KunftigHolograms must be used on the main thread");
        }
        HologramTracker.State<EntityPlayerMP> state = tracker.create(
            location.getWorld()
                .getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            lines,
            viewRange);
        return new BukkitHologram(state);
    }

    @Override
    public boolean drawsHolograms(Player player) {
        return player.getListeningPluginChannels()
            .contains(HologramWire.CHANNEL);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || tracker.size() == 0) {
            return;
        }
        List<HologramTracker.Viewer<EntityPlayerMP>> viewers = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                net.minecraft.entity.Entity handle = CraftBridge.handle(player);
                if (!(handle instanceof EntityPlayerMP) || ((EntityPlayerMP) handle).playerNetServerHandler == null) {
                    continue;
                }
                Location at = player.getLocation();
                viewers.add(
                    new HologramTracker.Viewer<>(
                        (EntityPlayerMP) handle,
                        world.getName(),
                        at.getX(),
                        at.getY(),
                        at.getZ(),
                        drawsHolograms(player)));
            }
        }
        for (HologramTracker.Out<EntityPlayerMP> out : tracker.update(viewers)) {
            if (out.connection.playerNetServerHandler != null) {
                out.connection.playerNetServerHandler
                    .sendPacket(new S3FPacketCustomPayload(HologramWire.CHANNEL, out.data));
            }
        }
    }

    /** The plugin's handle on one hologram. */
    private static final class BukkitHologram implements Hologram {

        private final HologramTracker.State<EntityPlayerMP> state;

        BukkitHologram(HologramTracker.State<EntityPlayerMP> state) {
            this.state = state;
        }

        @Override
        public int getId() {
            return state.id();
        }

        @Override
        public Location getLocation() {
            double[] p = state.position();
            return new Location(Bukkit.getWorld(state.world()), p[0], p[1], p[2]);
        }

        @Override
        public List<String> getLines() {
            return state.lines();
        }

        @Override
        public void setLines(List<String> lines) {
            state.setLines(lines);
        }

        @Override
        public void teleport(Location location) {
            if (location == null || location.getWorld() == null) {
                throw new IllegalArgumentException("location with a world required");
            }
            state.moveTo(
                location.getWorld()
                    .getName(),
                location.getX(),
                location.getY(),
                location.getZ());
        }

        @Override
        public double getViewRange() {
            return state.range();
        }

        @Override
        public void remove() {
            state.remove();
        }

        @Override
        public boolean isRemoved() {
            return state.isRemoved();
        }
    }
}
