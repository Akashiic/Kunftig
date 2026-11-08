package com.akashic.kunftig.bukkit.input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.input.KunftigInput;
import com.akashic.kunftig.api.bukkit.input.KunftigInputEvent;
import com.akashic.kunftig.api.bukkit.input.KunftigKeysEvent;
import com.akashic.kunftig.bukkit.fishing.CraftBridge;
import com.akashic.kunftig.input.InputGate;
import com.akashic.kunftig.input.InputWire;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@link KunftigInput} on Thermos/Crucible (API 11). The client's messages come in through Bukkit's messenger (the
 * route plugin messages already take on these servers), which only hands them to a plugin: the first enabled plugin
 * that called {@link KunftigInput#register} lends itself, and the next one takes over if it is disabled. Each message
 * goes through an {@link InputGate} and becomes a {@link KunftigInputEvent}. What a client should report goes out as a
 * raw BIND message, when a plugin changes it and whenever the player has a new connection. Loaded reflectively, only
 * where Bukkit exists.
 */
public final class BukkitInputBridge implements KunftigInput.Provider, PluginMessageListener {

    private final InputGate<UUID> gate = new InputGate<>();
    private final Set<Plugin> lenders = new LinkedHashSet<>();
    private Plugin listening;
    /** The connection each player was last told his bound actions on (null: not told yet). */
    private final Map<UUID, NetHandlerPlayServer> told = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    private long tick;

    private BukkitInputBridge() {}

    /** Called reflectively by the mod at start-up. */
    public static void install() {
        BukkitInputBridge bridge = new BukkitInputBridge();
        FMLCommonHandler.instance()
            .bus()
            .register(bridge);
        KunftigInput.install(bridge);
        Kunftig.LOG.info("Kunftig input: Bukkit API enabled (KunftigInput)");
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("KunftigInput must be used on the main thread");
        }
    }

    @Override
    public void register(Plugin plugin) {
        mainThread();
        lenders.add(plugin);
        ensureListening();
    }

    /** Keeps the messenger registration on an enabled plugin (Bukkit drops a disabled plugin's channels). */
    private void ensureListening() {
        if (listening != null && listening.isEnabled()) {
            return;
        }
        listening = null;
        lenders.removeIf(p -> !p.isEnabled());
        for (Plugin p : lenders) {
            try {
                Bukkit.getMessenger()
                    .registerIncomingPluginChannel(p, InputWire.CHANNEL, this);
                listening = p;
                Kunftig.LOG.info("Kunftig input: receiving on {} through plugin {}", InputWire.CHANNEL, p.getName());
                return;
            } catch (RuntimeException e) {
                Kunftig.LOG.warn("Kunftig input: plugin {} could not register {}", p.getName(), InputWire.CHANNEL, e);
            }
        }
    }

    @Override
    public void bind(Player player, Set<String> actions) {
        mainThread();
        Set<String> before = gate.bound(player.getUniqueId());
        Set<String> now = gate.bind(player.getUniqueId(), actions);
        if (!before.equals(now) || !told.containsKey(player.getUniqueId())) {
            dirty.add(player.getUniqueId());
            flush(player); // right away: the key may be pressed in this very tick
        }
    }

    @Override
    public Set<String> getBound(Player player) {
        mainThread();
        return gate.bound(player.getUniqueId());
    }

    @Override
    public boolean isHeld(Player player, String action) {
        mainThread();
        return gate.isHeld(player.getUniqueId(), action);
    }

    @Override
    public int getKeys(Player player) {
        mainThread();
        return gate.keys(player.getUniqueId());
    }

    @Override
    public long getLastPress(Player player, int key) {
        mainThread();
        return gate.lastPress(player.getUniqueId(), key);
    }

    @Override
    public long getTick() {
        return tick;
    }

    @Override
    public boolean sendsInput(Player player) {
        return player.getListeningPluginChannels()
            .contains(InputWire.CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!InputWire.CHANNEL.equals(channel)) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            // CraftBukkit 1.7.10 handles plugin messages on the main thread; stay safe if a fork does not.
            Plugin owner = listening;
            if (owner != null && owner.isEnabled()) {
                Bukkit.getScheduler()
                    .runTask(owner, () -> onPluginMessageReceived(channel, player, message));
            }
            return;
        }
        InputWire.Keys keys = InputWire.readKeys(message);
        if (keys != null) {
            int old = player.isOnline() ? gate.acceptKeys(player.getUniqueId(), keys, tick) : -1;
            if (old >= 0 && old != keys.keys) {
                Bukkit.getPluginManager()
                    .callEvent(new KunftigKeysEvent(player, old, keys.keys, keys.clientTick));
            }
            return;
        }
        InputWire.Action a = InputWire.readAction(message);
        if (a == null || !player.isOnline() || !gate.accept(player.getUniqueId(), a, tick)) {
            return;
        }
        Bukkit.getPluginManager()
            .callEvent(
                new KunftigInputEvent(
                    player,
                    a.action,
                    a.pressed,
                    a.sequence,
                    a.clientTick,
                    a.movement,
                    a.yaw,
                    a.pitch));
    }

    /** Sends the player his bound actions if they changed or his connection is new, once his client listens. */
    private void flush(Player player) {
        UUID id = player.getUniqueId();
        net.minecraft.entity.Entity handle = CraftBridge.handle(player);
        if (!(handle instanceof EntityPlayerMP)) {
            return;
        }
        NetHandlerPlayServer connection = ((EntityPlayerMP) handle).playerNetServerHandler;
        if (connection == null) {
            return;
        }
        if (told.containsKey(id) && told.get(id) != connection) {
            gate.resetConnection(id); // relogged: a new client, a new sequence
            dirty.add(id);
        }
        if (!dirty.contains(id) || !sendsInput(player)) {
            return;
        }
        connection.sendPacket(new S3FPacketCustomPayload(InputWire.CHANNEL, InputWire.bind(gate.bound(id))));
        told.put(id, connection);
        dirty.remove(id);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        if (!lenders.isEmpty() && (tick & 31) == 0) {
            ensureListening();
        }
        if (gate.players()
            .isEmpty()) {
            return;
        }
        Map<UUID, Player> online = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Player p : world.getPlayers()) {
                online.put(p.getUniqueId(), p);
            }
        }
        for (UUID id : new HashSet<>(gate.players())) {
            Player p = online.get(id);
            if (p == null) {
                gate.remove(id);
                told.remove(id);
                dirty.remove(id);
            } else {
                flush(p);
            }
        }
    }
}
