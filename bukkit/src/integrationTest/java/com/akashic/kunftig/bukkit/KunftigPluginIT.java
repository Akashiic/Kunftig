package com.akashic.kunftig.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.akashic.kunftig.bukkit.api.KunftigClientReadyEvent;
import com.akashic.kunftig.bukkit.api.KunftigNametags;
import com.akashic.kunftig.bukkit.nametag.NametagSpec;
import com.akashic.kunftig.bukkit.nametag.TeamNames;
import com.akashic.kunftig.common.protocol.KnpCodec;
import com.akashic.kunftig.common.protocol.KnpMessage;
import com.akashic.kunftig.common.protocol.KnpProtocol;
import com.akashic.kunftig.common.text.ComponentJson;
import com.akashic.kunftig.common.text.LegacyText;

/**
 * The shaded plugin in MockBukkit (Paper 1.21 API), driven like a server would drive it, with players acting as
 * Kunftig clients through the real protocol codec.
 */
class KunftigPluginIT {

    /** A player whose client records the plugin messages it receives and speaks KNP. */
    static final class ClientPlayer extends PlayerMock {

        final List<KnpMessage.Clientbound> received = new ArrayList<>();

        ClientPlayer(ServerMock server, String name) {
            super(server, name, UUID.nameUUIDFromBytes(name.getBytes()));
        }

        @Override
        public void sendPluginMessage(Plugin source, String channel, byte[] message) {
            super.sendPluginMessage(source, channel, message);
            assertEquals(KnpProtocol.CHANNEL, channel);
            try {
                received.add(KnpCodec.decodeClientbound(message, KnpProtocol.VERSION));
            } catch (Exception e) {
                throw new AssertionError("Plugin sent an undecodable message", e);
            }
        }

        /** What the server records when the client sends REGISTER (CraftPlayer#addChannel; not public API). */
        @SuppressWarnings("unchecked")
        void registerChannel() throws ReflectiveOperationException {
            java.lang.reflect.Field field = PlayerMock.class.getDeclaredField("channels");
            field.setAccessible(true);
            ((java.util.Set<String>) field.get(this)).add(KnpProtocol.CHANNEL);
        }

        void hello() {
            byte[] payload = KnpCodec.encode(new KnpMessage.Hello(KnpProtocol.MIN_VERSION, KnpProtocol.VERSION,
                KnpProtocol.CAP_RGB | KnpProtocol.CAP_NAME_STYLE | KnpProtocol.CAP_VISIBILITY, "Kunftig/test"));
            getServer().getMessenger().dispatchIncomingMessage(this, KnpProtocol.CHANNEL, payload);
        }

        <T> List<T> received(Class<T> type) {
            return received.stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
        }

        KnpMessage.TeamStyle lastStyle(String team) {
            List<KnpMessage.TeamStyle> styles = received(KnpMessage.TeamStyle.class).stream()
                .filter(s -> s.team.equals(team)).collect(Collectors.toList());
            return styles.isEmpty() ? null : styles.get(styles.size() - 1);
        }
    }

    private ServerMock server;
    private KunftigPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(KunftigPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ClientPlayer join(String name, String... permissions) {
        ClientPlayer player = new ClientPlayer(server, name);
        for (String permission : permissions) {
            player.addAttachment(plugin, permission, true);
        }
        server.addPlayer(player);
        server.getScheduler().performTicks(20);
        return player;
    }

    private Team managedTeam(Scoreboard board, PlayerMock player) {
        Team team = board.getPlayerTeam(player);
        return team != null && TeamNames.isManaged(team.getName()) ? team : null;
    }

    @Test
    void groupPlayersGetAVanillaTeamOnModernServersWithoutTruncation() {
        ClientPlayer admin = join("Admin", "kunftig.group.admin");
        ClientPlayer guest = join("Guest");

        Team team = managedTeam(Bukkit.getScoreboardManager().getMainScoreboard(), admin);
        assertNotNull(team, "admin is managed");
        // MockBukkit reports 1.21: no 16-char limit and §x colors are allowed in the vanilla prefix.
        // The group sets vanilla-prefix, so clients without Kunftig get the short form...
        assertEquals("ADM | ", LegacyText.strip(team.getPrefix()));
        // From 1.13 on the member name is colored by the team color, not by the prefix's last code (which the
        // server drops when it turns the string into a component). The gradient starts at #ffaa00 = gold.
        assertEquals(org.bukkit.ChatColor.GOLD, team.getColor(), "team color set reflectively");
        assertNull(managedTeam(Bukkit.getScoreboardManager().getMainScoreboard(), guest), "no group, no team");
        assertTrue(admin.received.isEmpty(), "nothing on the channel before the client says HELLO");
    }

    @Test
    void handshakeSendsAckThenTheFullStateAndFiresTheReadyEvent() throws Exception {
        List<KunftigClientReadyEvent> events = new ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {

            @EventHandler
            public void onReady(KunftigClientReadyEvent event) {
                events.add(event);
            }
        }, plugin);
        ClientPlayer admin = join("Admin", "kunftig.group.admin");
        ClientPlayer vip = join("Vip", "kunftig.group.vip");

        vip.hello();

        assertTrue(vip.received.get(0) instanceof KnpMessage.HelloAck, "ACK comes first: " + vip.received);
        assertEquals(1, ((KnpMessage.HelloAck) vip.received.get(0)).version);
        String adminTeam = managedTeam(Bukkit.getScoreboardManager().getMainScoreboard(), admin).getName();
        KnpMessage.TeamStyle style = vip.lastStyle(adminTeam);
        assertNotNull(style, "the admin's style is part of the snapshot");
        // ...while Kunftig clients get the full one. #ff5555 is exactly legacy red, so the gradient's first stop is serialized as "red".
        assertEquals("Administrador | ", ComponentJson.parse(style.prefixJson).plainText());
        assertTrue(style.prefixJson.contains("\"color\":\"red\"") && style.prefixJson.contains("#ff5c4e"),
            style.prefixJson);
        assertEquals(2, style.nameStyle.colors().size(), "gradient name");
        assertEquals(1, events.size());
        assertEquals("Kunftig/test", events.get(0).getClientVersion());
        assertTrue(KunftigNametags.api().isKunftigClient(vip));
        assertFalse(KunftigNametags.api().isKunftigClient(admin));
    }

    @Test
    void commandOverridesAreLayeredPersistedAndPushed() throws Exception {
        ClientPlayer vip = join("Vip", "kunftig.group.vip");
        vip.hello();
        vip.received.clear();

        server.dispatchCommand(server.getConsoleSender(), "kunftig set Vip suffix <red> [AFK]");
        server.getScheduler().performTicks(1);

        Team team = managedTeam(Bukkit.getScoreboardManager().getMainScoreboard(), vip);
        KnpMessage.TeamStyle style = vip.lastStyle(team.getName());
        assertNotNull(style);
        assertEquals(" [AFK]", ComponentJson.parse(style.suffixJson).plainText());
        assertEquals("[VIP] ", ComponentJson.parse(style.prefixJson).plainText(), "group prefix kept");
        assertTrue(new java.io.File(plugin.getDataFolder(), "players.yml").isFile());

        server.dispatchCommand(server.getConsoleSender(), "kunftig set Vip name not-a-color");
        assertEquals("[VIP] ", ComponentJson.parse(vip.lastStyle(team.getName()).prefixJson).plainText(),
            "an invalid value changes nothing");
    }

    @Test
    void scoreboardsSwappedByOtherPluginsReceiveTheTeams() {
        ClientPlayer admin = join("Admin", "kunftig.group.admin");
        Scoreboard sidebarBoard = Bukkit.getScoreboardManager().getNewScoreboard();
        admin.setScoreboard(sidebarBoard);

        server.getScheduler().performTicks(20);

        assertNotNull(managedTeam(sidebarBoard, admin), "team re-created on the new scoreboard");
    }

    @Test
    void priorityChangeRenamesTheTeamAndCleansUp() {
        ClientPlayer vip = join("Vip", "kunftig.group.vip");
        ClientPlayer watcher = join("Watcher");
        watcher.hello();
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        String before = managedTeam(main, vip).getName();

        vip.addAttachment(plugin, "kunftig.group.admin", true);
        server.getScheduler().performTicks(20);

        String after = managedTeam(main, vip).getName();
        assertFalse(before.equals(after), "priority is part of the team name (tab order)");
        assertTrue(after.compareTo(before) < 0, "higher priority sorts first");
        assertNull(main.getTeam(before), "old team unregistered");
        assertTrue(watcher.received(KnpMessage.TeamStyleRemove.class).stream().anyMatch(r -> r.team.equals(before)));
    }

    @Test
    void quittingRemovesTeamAndStyle() {
        ClientPlayer admin = join("Admin", "kunftig.group.admin");
        ClientPlayer watcher = join("Watcher");
        watcher.hello();
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        String team = managedTeam(main, admin).getName();

        admin.disconnect();
        server.getScheduler().performTicks(20);

        assertNull(main.getTeam(team));
        assertTrue(watcher.received(KnpMessage.TeamStyleRemove.class).stream().anyMatch(r -> r.team.equals(team)));
    }

    @Test
    void externalTeamStylesReachCurrentAndFutureClients() throws Exception {
        ClientPlayer early = join("Early");
        early.hello();

        KunftigNametags.api().setTeamStyle("tab_owner_team",
            NametagSpec.builder().prefix("<gradient:#00ffff:#0000ff>Very long staff prefix</gradient> ").build());
        ClientPlayer late = join("Late");
        late.hello();

        assertNotNull(early.lastStyle("tab_owner_team"));
        assertNotNull(late.lastStyle("tab_owner_team"), "new sessions get existing external styles");

        late.received.clear();
        server.dispatchCommand(server.getConsoleSender(), "kunftig reload");
        // A reload resets connected clients and pushes everything again, without a new HELLO.
        assertTrue(late.received.get(0) instanceof KnpMessage.Reset, late.received.toString());
        assertNotNull(late.lastStyle("tab_owner_team"), "external styles survive a reload");
    }

    @Test
    void disablingRemovesTeamsAndReEnablingNeverReusesTheirNames() {
        ClientPlayer admin = join("Admin", "kunftig.group.admin");
        admin.hello();
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        String team = managedTeam(main, admin).getName();

        server.getPluginManager().disablePlugin(plugin);
        assertNull(main.getTeam(team), "teams removed");
        assertFalse(plugin.isEnabled());

        // A disabled plugin cannot message clients, so a client may still hold the old style: the next enable
        // must not hand the same team name to someone else, and its PROBE/HELLO_ACK clears the client.
        server.getPluginManager().enablePlugin(plugin);
        server.getScheduler().performTicks(20);
        String renewed = managedTeam(main, admin).getName();
        assertFalse(team.equals(renewed), team + " reused");
    }

    @Test
    void enablingProbesClientsThatAlreadyRegisteredTheChannel() throws Exception {
        ClientPlayer admin = join("Admin", "kunftig.group.admin");
        admin.registerChannel();
        server.getPluginManager().disablePlugin(plugin);
        admin.received.clear();

        server.getPluginManager().enablePlugin(plugin);

        assertEquals(1, admin.received(KnpMessage.Probe.class).size(), "a reloaded plugin asks for HELLO again");
    }

    @Test
    void malformedAndFloodingClientsAreHarmless() {
        ClientPlayer player = join("Noisy", "kunftig.group.vip");
        server.getMessenger().dispatchIncomingMessage(player, KnpProtocol.CHANNEL, new byte[] { 0, (byte) 0xFF });
        server.getMessenger().dispatchIncomingMessage(player, KnpProtocol.CHANNEL, new byte[] { 0x55 });
        assertTrue(player.received.isEmpty());
        player.hello();
        player.hello();
        player.hello();
        assertEquals(1, player.received(KnpMessage.HelloAck.class).size(), "HELLO is rate limited");
    }
}
