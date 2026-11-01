package com.akashic.kunftig.bukkit.net;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.akashic.kunftig.bukkit.api.KunftigClientReadyEvent;
import com.akashic.kunftig.common.protocol.KnpCodec;
import com.akashic.kunftig.common.protocol.KnpDecodeException;
import com.akashic.kunftig.common.protocol.KnpMessage;
import com.akashic.kunftig.common.protocol.KnpProtocol;

/**
 * Server end of the Kunftig Nametag Protocol on {@link KnpProtocol#CHANNEL}.
 * <p>
 * Handshake: the client sends HELLO on every JoinGame (and whenever probed). The server answers HELLO_ACK, which
 * makes the client drop old styles, then sends the current styles. A PROBE is sent when a client registers the
 * channel without having said HELLO (e.g. the plugin was reloaded while the player was online).
 * <p>
 * Works unchanged behind Velocity (it forwards REGISTER and unknown plugin messages, and replays REGISTER on server
 * switch) and through ViaVersion (the channel name is a valid namespaced key, so it is not rewritten).
 */
public final class KunftigChannel implements PluginMessageListener, Listener {

    /** Called when a client completes the handshake, on the main thread. */
    public interface SessionListener {

        void onSessionStarted(Player player, ClientSession session);
    }

    private static final long HELLO_MIN_INTERVAL_MILLIS = 250;

    private final Plugin plugin;
    private final SessionListener listener;
    private final Map<UUID, ClientSession> sessions = new ConcurrentHashMap<UUID, ClientSession>();
    private final Map<UUID, Long> lastHello = new ConcurrentHashMap<UUID, Long>();
    private volatile boolean debug;

    public KunftigChannel(Plugin plugin, SessionListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, KnpProtocol.CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, KnpProtocol.CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Players already online (plugin reload): ask their clients to say HELLO again.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getListeningPluginChannels().contains(KnpProtocol.CHANNEL)) {
                send(player, KnpMessage.Probe.INSTANCE);
            }
        }
    }

    /**
     * Stops listening. Clients are not told: Bukkit marks a plugin disabled before {@code onDisable}, and a disabled
     * plugin cannot send plugin messages. That is harmless: the plugin removes its teams (styles only apply to
     * existing teams), team names are never reused across enables, and the next HELLO_ACK clears the client.
     */
    public void shutdown() {
        sessions.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, KnpProtocol.CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, KnpProtocol.CHANNEL);
    }

    public ClientSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public Map<UUID, ClientSession> sessions() {
        return sessions;
    }

    @Override
    public void onPluginMessageReceived(String channel, final Player player, final byte[] message) {
        if (!KnpProtocol.CHANNEL.equals(channel)) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> handle(player, message));
            return;
        }
        handle(player, message);
    }

    private void handle(Player player, byte[] message) {
        if (!player.isOnline()) {
            return;
        }
        KnpMessage.Serverbound decoded;
        try {
            decoded = KnpCodec.decodeServerbound(message);
        } catch (KnpDecodeException e) {
            plugin.getLogger().warning("Malformed Kunftig message from " + player.getName() + ": " + e.getMessage());
            return;
        }
        if (!(decoded instanceof KnpMessage.Hello)) {
            debug("<- unknown message from " + player.getName() + ", ignored");
            return;
        }
        KnpMessage.Hello hello = (KnpMessage.Hello) decoded;
        long now = System.currentTimeMillis();
        Long previous = lastHello.put(player.getUniqueId(), now);
        if (previous != null && now - previous < HELLO_MIN_INTERVAL_MILLIS) {
            debug("<- HELLO flood from " + player.getName() + ", ignored");
            return;
        }
        debug("<- " + hello + " from " + player.getName());
        int version = KnpProtocol.negotiate(hello.minVersion, hello.maxVersion);
        if (version < 0) {
            plugin.getLogger().warning(player.getName() + " uses Kunftig protocol " + hello.minVersion + "-"
                + hello.maxVersion + ", this plugin speaks " + KnpProtocol.MIN_VERSION + "-" + KnpProtocol.VERSION
                + ": update the older side");
            return;
        }
        ClientSession session = new ClientSession(version, hello.capabilities, hello.clientVersion);
        sessions.put(player.getUniqueId(), session);
        send(player, new KnpMessage.HelloAck(version, plugin.getName() + "/" + plugin.getDescription().getVersion()));
        Bukkit.getPluginManager().callEvent(new KunftigClientReadyEvent(player, session.version(),
            session.clientVersion(), session.capabilities()));
        listener.onSessionStarted(player, session);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        if (!KnpProtocol.CHANNEL.equals(event.getChannel())) {
            return;
        }
        final Player player = event.getPlayer();
        // The client normally sends HELLO right after REGISTER; only probe if it did not within a few ticks.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !sessions.containsKey(player.getUniqueId())) {
                send(player, KnpMessage.Probe.INSTANCE);
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        lastHello.remove(event.getPlayer().getUniqueId());
    }

    /** Sends a message if the client listens on the channel. Returns false when it could not be sent. */
    public boolean send(Player player, KnpMessage.Clientbound message) {
        byte[] payload;
        try {
            payload = KnpCodec.encode(message);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Kunftig message too large, not sent to " + player.getName() + ": "
                + e.getMessage());
            return false;
        }
        try {
            player.sendPluginMessage(plugin, KnpProtocol.CHANNEL, payload);
            debug("-> " + message + " to " + player.getName());
            return true;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Could not send Kunftig message to " + player.getName(), e);
            return false;
        }
    }

    /** Sends to every player with a session. */
    public void broadcast(KnpMessage.Clientbound message) {
        for (UUID id : sessions.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                send(player, message);
            }
        }
    }

    private void debug(String message) {
        if (debug) {
            plugin.getLogger().info("[debug] " + message);
        }
    }
}
