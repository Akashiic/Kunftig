package com.akashic.kunftig.bukkit.particle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.network.play.server.S2APacketParticles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.particle.KunftigParticles;
import com.akashic.kunftig.api.bukkit.particle.ParticleBuilder;
import com.akashic.kunftig.api.particle.KunftigParticle;
import com.akashic.kunftig.api.particle.KunftigParticle.DataKind;
import com.akashic.kunftig.bukkit.fishing.CraftBridge;
import com.akashic.kunftig.particle.ParticlePlan;
import com.akashic.kunftig.particle.ParticleWire;

/**
 * {@link KunftigParticles} on Thermos/Crucible: builds the packets once per effect ({@link ParticlePlan}) and sends
 * them straight to each receiver's connection, so it needs neither {@code org.bukkit.Particle} (absent from Thermos
 * and from the 1.7.10 API) nor {@code World#spawnParticle} (absent from production Crucible). Loaded reflectively,
 * only where Bukkit exists.
 */
public final class BukkitParticleBridge implements KunftigParticles.Provider {

    /** {@code -Dkunftig.particles.disable=true}: send only vanilla particles (the client mixin is off too). */
    private static final boolean MODERN_DISABLED = Boolean.getBoolean("kunftig.particles.disable");

    private final Random random = new Random();

    private BukkitParticleBridge() {}

    /** Called reflectively by the mod at start-up. */
    public static void install() {
        KunftigParticles.install(new BukkitParticleBridge());
        Kunftig.LOG.info(
            "Kunftig particles: Bukkit API enabled (KunftigParticles{})",
            MODERN_DISABLED ? ", modern particles disabled by -Dkunftig.particles.disable" : "");
    }

    @Override
    public boolean drawsModernParticles(Player player) {
        return !MODERN_DISABLED && player.getListeningPluginChannels()
            .contains(ParticleWire.CHANNEL);
    }

    @Override
    public void spawn(ParticleBuilder effect) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("KunftigParticles must be used on the main thread");
        }
        KunftigParticle particle = effect.particle();
        Location at = effect.location();
        int id = effect.dataId();
        int meta = effect.dataMeta();
        // 1.7.10 clients fail on unknown ids (null Item/Block in the particle constructor): use stone, as Crucible
        // does.
        if (particle.dataKind() == DataKind.ITEM && Item.getItemById(id) == null
            || particle.dataKind() == DataKind.BLOCK && Block.getBlockById(id) == Blocks.air) {
            id = 1;
            meta = 0;
        }
        ParticlePlan plan = new ParticlePlan(
            particle,
            at.getX(),
            at.getY(),
            at.getZ(),
            effect.count(),
            effect.offsetX(),
            effect.offsetY(),
            effect.offsetZ(),
            effect.extra(),
            effect.color(),
            id,
            meta,
            random);
        for (Player player : receivers(effect, at)) {
            net.minecraft.entity.Entity handle = CraftBridge.handle(player);
            if (!(handle instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP target = (EntityPlayerMP) handle;
            if (target.playerNetServerHandler == null) {
                continue;
            }
            List<ParticlePlan.Packet> packets = drawsModernParticles(player) ? plan.forKunftigClient()
                : plan.forVanillaClient();
            for (ParticlePlan.Packet packet : packets) {
                target.playerNetServerHandler.sendPacket(
                    new S2APacketParticles(
                        packet.name,
                        (float) packet.x,
                        (float) packet.y,
                        (float) packet.z,
                        packet.offsetX,
                        packet.offsetY,
                        packet.offsetZ,
                        packet.speed,
                        packet.count));
            }
        }
    }

    private static List<Player> receivers(ParticleBuilder effect, Location at) {
        List<Player> explicit = effect.receivers();
        List<Player> players = new ArrayList<>();
        if (explicit != null) {
            for (Player player : explicit) {
                if (player != null && player.isOnline()) {
                    players.add(player);
                }
            }
            return players;
        }
        double maxSquared = effect.radius() * effect.radius();
        Location scratch = new Location(at.getWorld(), 0, 0, 0);
        for (Player player : at.getWorld()
            .getPlayers()) {
            if (player.getLocation(scratch)
                .distanceSquared(at) <= maxSquared) {
                players.add(player);
            }
        }
        return players;
    }
}
