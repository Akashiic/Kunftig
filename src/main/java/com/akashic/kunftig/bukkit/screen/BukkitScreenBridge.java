package com.akashic.kunftig.bukkit.screen;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.camera.KunftigCamera;
import com.akashic.kunftig.api.bukkit.hud.HudElement;
import com.akashic.kunftig.api.bukkit.hud.KunftigHud;
import com.akashic.kunftig.bukkit.fishing.CraftBridge;
import com.akashic.kunftig.screen.CameraWire;
import com.akashic.kunftig.screen.HudWire;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@link KunftigHud} and {@link KunftigCamera} on Thermos/Crucible (API 11): raw plugin messages to one player, sent
 * right away. Remembers what each player's screen shows, to skip sending an element that did not change, and forgets
 * it when his connection changes (a relog starts with an empty screen). Loaded reflectively, only where Bukkit exists.
 */
public final class BukkitScreenBridge implements KunftigHud.Provider, KunftigCamera.Provider {

    private static final class Screen {

        NetHandlerPlayServer connection;
        final Map<String, byte[]> hud = new LinkedHashMap<>();
        Entity target;
    }

    private final Map<UUID, Screen> screens = new HashMap<>();
    private long tick;

    private BukkitScreenBridge() {}

    /** Called reflectively by the mod at start-up. */
    public static void install() {
        BukkitScreenBridge bridge = new BukkitScreenBridge();
        FMLCommonHandler.instance()
            .bus()
            .register(bridge);
        KunftigHud.install(bridge);
        KunftigCamera.install(bridge);
        Kunftig.LOG.info("Kunftig screen: Bukkit API enabled (KunftigHud, KunftigCamera)");
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("KunftigHud and KunftigCamera must be used on the main thread");
        }
    }

    /** The player's screen state, reset if his connection changed; null when he is not connected. */
    private Screen screen(Player player) {
        net.minecraft.entity.Entity handle = CraftBridge.handle(player);
        if (!(handle instanceof EntityPlayerMP) || ((EntityPlayerMP) handle).playerNetServerHandler == null) {
            return null;
        }
        NetHandlerPlayServer connection = ((EntityPlayerMP) handle).playerNetServerHandler;
        Screen s = screens.computeIfAbsent(player.getUniqueId(), k -> new Screen());
        if (s.connection != connection) {
            s.connection = connection;
            s.hud.clear();
            s.target = null;
        }
        return s;
    }

    private static void send(Screen s, String channel, byte[] data) {
        s.connection.sendPacket(new S3FPacketCustomPayload(channel, data));
    }

    // ---- HUD ------------------------------------------------------------------------------------------------------

    @Override
    public void show(Player player, HudElement e) {
        mainThread();
        if (!drawsHud(player)) {
            return;
        }
        Screen s = screen(player);
        if (s == null) {
            return;
        }
        byte[] data = HudWire.set(
            e.getId(),
            new HudWire.Element(
                e.getAnchor()
                    .ordinal(),
                e.getX(),
                e.getY(),
                e.getScale(),
                e.getText(),
                e.getProgress(),
                e.getBarWidth(),
                e.getBarColor(),
                e.getBarBackground(),
                e.getTexture(),
                e.getTextureWidth(),
                e.getTextureHeight(),
                e.getDurationTicks(),
                e.getFadeTicks()));
        // an element with a duration is sent again (it restarts); one that stays is skipped when unchanged
        if (e.getDurationTicks() == 0 && Arrays.equals(data, s.hud.get(e.getId()))) {
            return;
        }
        s.hud.put(e.getId(), data);
        send(s, HudWire.CHANNEL, data);
    }

    @Override
    public void remove(Player player, String id) {
        mainThread();
        Screen s = screen(player);
        if (s != null && s.hud.remove(id) != null) {
            send(s, HudWire.CHANNEL, HudWire.remove(id));
        }
    }

    @Override
    public void clear(Player player) {
        mainThread();
        Screen s = screen(player);
        if (s != null && drawsHud(player)) {
            s.hud.clear();
            send(s, HudWire.CHANNEL, HudWire.clear());
        }
    }

    @Override
    public boolean drawsHud(Player player) {
        return player.getListeningPluginChannels()
            .contains(HudWire.CHANNEL);
    }

    // ---- camera ---------------------------------------------------------------------------------------------------

    @Override
    public void lockOn(Player player, Entity target, float strength, float maxDistance, boolean marker) {
        mainThread();
        Screen s = screen(player);
        if (s == null) {
            return;
        }
        s.target = target;
        if (followsLockOn(player)) {
            send(s, CameraWire.CHANNEL, CameraWire.lock(target.getEntityId(), strength, maxDistance, marker));
        }
    }

    @Override
    public void release(Player player) {
        mainThread();
        Screen s = screen(player);
        if (s == null || s.target == null) {
            return;
        }
        s.target = null;
        if (followsLockOn(player)) {
            send(s, CameraWire.CHANNEL, CameraWire.unlock());
        }
    }

    @Override
    public Entity getTarget(Player player) {
        mainThread();
        Screen s = screens.get(player.getUniqueId());
        return s == null ? null : s.target;
    }

    @Override
    public boolean followsLockOn(Player player) {
        return player.getListeningPluginChannels()
            .contains(CameraWire.CHANNEL);
    }

    /** Every few seconds: forget players who left. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 100 != 0 || screens.isEmpty()) {
            return;
        }
        Set<UUID> online = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Player p : world.getPlayers()) {
                online.add(p.getUniqueId());
            }
        }
        screens.keySet()
            .retainAll(online);
    }
}
