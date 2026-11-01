package com.akashic.kunftig.client.particle;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S2APacketParticles;

import com.akashic.kunftig.api.particle.KunftigParticle;
import com.akashic.kunftig.api.particle.KunftigParticle.DataKind;
import com.akashic.kunftig.particle.ParticleWire;

/**
 * Client side of Kunftig particles: announces {@link ParticleWire#CHANNEL} so the server sends Kunftig names, and
 * draws {@code kunftig:*} particle packets with Et Futurum Requiem, or with the vanilla fallback when Et Futurum is
 * missing. Expands {@code count}, offsets and speed exactly like {@code NetHandlerPlayClient#handleParticles}, and
 * applies the same particle setting and 16-block range as {@code RenderGlobal#doSpawnParticle}. Client thread only.
 */
public final class ParticleClient {

    /** 1.7.10 draws particles up to 16 blocks from the camera. */
    private static final double MAX_DISTANCE_SQUARED = 16 * 16;

    private static final Random RANDOM = new Random();

    private ParticleClient() {}

    /** At the end of {@code handleJoinGame}: every new connection (server or proxy switch) starts unregistered. */
    public static void onJoinGame(NetHandlerPlayClient handler) {
        handler.addToSendQueue(
            new C17PacketCustomPayload("REGISTER", ParticleWire.CHANNEL.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Handles a particle packet.
     *
     * @return true if it was a Kunftig particle (vanilla must then ignore it)
     */
    public static boolean onParticles(S2APacketParticles packet) {
        String name = packet.func_149228_c();
        if (!ParticleWire.isKunftig(name)) {
            return false;
        }
        ParticleWire.Decoded decoded = ParticleWire.decode(name);
        WorldClient world = Minecraft.getMinecraft().theWorld;
        if (decoded == null || world == null) {
            return true; // a particle of a newer Kunftig, or no world: nothing to draw
        }
        int count = packet.func_149222_k();
        double x = packet.func_149220_d();
        double y = packet.func_149226_e();
        double z = packet.func_149225_f();
        float offsetX = packet.func_149221_g();
        float offsetY = packet.func_149224_h();
        float offsetZ = packet.func_149223_i();
        float speed = packet.func_149227_j();
        if (count == 0) {
            spawn(world, decoded, x, y, z, speed * offsetX, speed * offsetY, speed * offsetZ);
        } else {
            for (int i = 0; i < count; i++) {
                double px = x + RANDOM.nextGaussian() * offsetX;
                double py = y + RANDOM.nextGaussian() * offsetY;
                double pz = z + RANDOM.nextGaussian() * offsetZ;
                spawn(
                    world,
                    decoded,
                    px,
                    py,
                    pz,
                    RANDOM.nextGaussian() * speed,
                    RANDOM.nextGaussian() * speed,
                    RANDOM.nextGaussian() * speed);
            }
        }
        return true;
    }

    private static void spawn(WorldClient world, ParticleWire.Decoded decoded, double x, double y, double z, double vx,
        double vy, double vz) {
        KunftigParticle particle = decoded.particle;
        if (particle.isModern() && EfrParticles.isInstalled()) {
            if (!visible(world, x, y, z)) {
                return;
            }
            if (EfrParticles.spawn(particle, world, x, y, z, vx, vy, vz, decoded.color)) {
                return;
            }
        }
        // Vanilla path (RenderGlobal applies the particle setting and the range itself).
        KunftigParticle target = particle.fallback();
        int tint = ParticleWire.fallbackColor(particle, decoded.color);
        if (target.dataKind() == DataKind.COLOR && tint != 0) {
            float[] rgb = ParticleWire.colorAsVelocity(target, tint);
            world.spawnParticle(target.vanillaName(), x, y, z, rgb[0], rgb[1], rgb[2]);
        } else if (target.dataKind() == DataKind.NONE || target.dataKind() == DataKind.COLOR) {
            world.spawnParticle(target.vanillaName(), x, y, z, vx, vy, vz);
        }
        // ITEM/BLOCK particles are never sent with a Kunftig name.
    }

    /** {@code RenderGlobal#doSpawnParticle}'s checks, which Et Futurum's spawn methods skip. */
    private static boolean visible(WorldClient world, double x, double y, double z) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityLivingBase camera = minecraft.renderViewEntity;
        if (camera == null || minecraft.effectRenderer == null) {
            return false;
        }
        int setting = minecraft.gameSettings.particleSetting;
        if (setting == 1 && world.rand.nextInt(3) == 0) {
            setting = 2;
        }
        if (setting > 1) {
            return false;
        }
        double dx = camera.posX - x;
        double dy = camera.posY - y;
        double dz = camera.posZ - z;
        return dx * dx + dy * dy + dz * dz <= MAX_DISTANCE_SQUARED;
    }
}
