package com.akashic.kunftig.bukkit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.akashic.kunftig.bukkit.api.KunftigNametags;
import com.akashic.kunftig.bukkit.api.NametagApi;
import com.akashic.kunftig.bukkit.command.KunftigCommand;
import com.akashic.kunftig.bukkit.net.KunftigChannel;
import com.akashic.kunftig.bukkit.nametag.NametagConfig;
import com.akashic.kunftig.bukkit.nametag.NametagGroup;
import com.akashic.kunftig.bukkit.nametag.NametagService;
import com.akashic.kunftig.bukkit.nametag.NametagSpec;
import com.akashic.kunftig.bukkit.nametag.PlayerOverrides;
import com.akashic.kunftig.bukkit.nametag.ServerEnvironment;
import com.akashic.kunftig.common.protocol.KnpMessage;

/**
 * Kunftig server plugin. One jar for Thermos/Crucible (1.7.10) and Paper/Spigot 1.8 to 1.21+: it is compiled
 * against the oldest API and checked against the 1.7.10 one, and newer features are reached reflectively.
 */
public class KunftigPlugin extends JavaPlugin implements Listener, NametagApi {

    private KunftigChannel channel;
    private NametagService nametags;
    private NametagConfig config;
    private ServerEnvironment environment;
    private PlayerOverrides overrides;
    private BukkitTask syncTask;
    private final Map<UUID, NametagSpec> apiOverrides = new HashMap<UUID, NametagSpec>();
    private final List<Permission> registeredPermissions = new ArrayList<Permission>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        overrides = new PlayerOverrides(new File(getDataFolder(), "players.yml"), getLogger());
        channel = new KunftigChannel(this, (player, session) -> nametags.onSessionStarted(player, session));
        start();
        channel.register();
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("kunftig");
        KunftigCommand executor = new KunftigCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        KunftigNametags.install(this);
        getLogger().info("Kunftig nametags " + (config.enabled ? "enabled" : "disabled") + " on Minecraft "
            + environment.version() + " (vanilla prefix limit: "
            + (environment.affixLimit() == ServerEnvironment.UNLIMITED ? "none" : environment.affixLimit()) + ")");
    }

    @Override
    public void onDisable() {
        KunftigNametags.install(null);
        stop();
        if (channel != null) {
            channel.shutdown();
        }
    }

    /** (Re)reads configuration and starts the nametag service. */
    private void start() {
        reloadConfig();
        config = NametagConfig.load(getConfig(), getLogger());
        channel.setDebug(getConfig().getBoolean("debug", false));
        environment = new ServerEnvironment(Bukkit.getBukkitVersion(), config.fallbackMaxLength);
        overrides.load();
        registerGroupPermissions();
        nametags = new NametagService(getLogger(), channel, this::resolve, environment, config.overrideOtherTeams);
        if (config.enabled) {
            nametags.refreshAll();
            nametags.sync();
            syncTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                nametags.refreshAll();
                nametags.sync();
            }, config.syncIntervalTicks, config.syncIntervalTicks);
        }
    }

    private void stop() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        if (nametags != null) {
            nametags.clearAll();
            // Only possible on reload: during onDisable the plugin is already marked disabled and cannot send.
            if (isEnabled()) {
                channel.broadcast(KnpMessage.Reset.INSTANCE);
            }
        }
        PluginManager manager = getServer().getPluginManager();
        for (Permission permission : registeredPermissions) {
            manager.removePermission(permission);
        }
        registeredPermissions.clear();
    }

    public void reloadKunftig() {
        List<KnpMessage.TeamStyle> external = nametags.externalStyles();
        stop();
        start();
        for (KnpMessage.TeamStyle style : external) {
            nametags.setExternalStyle(style);
        }
    }

    /**
     * Group permissions are registered with default FALSE: an unregistered permission defaults to OP in Bukkit,
     * which would put every operator in every group.
     */
    private void registerGroupPermissions() {
        PluginManager manager = getServer().getPluginManager();
        for (NametagGroup group : config.groups) {
            String node = group.permission();
            if (node != null && manager.getPermission(node) == null) {
                Permission permission = new Permission(node, "Kunftig nametag group " + group.name(),
                    PermissionDefault.FALSE);
                manager.addPermission(permission);
                registeredPermissions.add(permission);
            }
        }
    }

    /** Group (highest priority the player has) <- /kunftig set override <- API override, field by field. */
    private NametagSpec resolve(Player player) {
        NametagSpec spec = null;
        for (NametagGroup group : config.groups) {
            if (group.permission() == null || player.hasPermission(group.permission())) {
                spec = group.spec();
                break;
            }
        }
        NametagSpec personal = overrides.get(player.getUniqueId());
        if (personal != null) {
            spec = personal.over(spec);
        }
        NametagSpec api = apiOverrides.get(player.getUniqueId());
        if (api != null) {
            spec = api.over(spec);
        }
        return spec;
    }

    /** Re-resolves one player now (after a command or API call) instead of waiting for the next cycle. */
    public void refresh(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null && config.enabled) {
            nametags.refresh(player);
            nametags.sync();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        apiOverrides.remove(id);
        if (config.enabled) {
            nametags.remove(id);
            // The player is still listed as online during this event; unregister after it.
            Bukkit.getScheduler().runTask(this, nametags::sync);
        }
    }

    // ------------------------------------------------------------------ accessors for the command

    public KunftigChannel channel() {
        return channel;
    }

    public NametagService nametags() {
        return nametags;
    }

    public PlayerOverrides overrides() {
        return overrides;
    }

    public ServerEnvironment environment() {
        return environment;
    }

    // ------------------------------------------------------------------ NametagApi

    @Override
    public boolean isKunftigClient(Player player) {
        return channel.session(player) != null;
    }

    @Override
    public void setOverride(Player player, NametagSpec spec) {
        if (spec == null || spec.isEmpty()) {
            apiOverrides.remove(player.getUniqueId());
        } else {
            apiOverrides.put(player.getUniqueId(), spec);
        }
        refresh(player.getUniqueId());
    }

    @Override
    public void clearOverride(Player player) {
        setOverride(player, null);
    }

    @Override
    public void setTeamStyle(String teamName, NametagSpec style) {
        nametags.setExternalStyle(NametagService.styleMessage(teamName, style));
    }

    @Override
    public void removeTeamStyle(String teamName) {
        nametags.removeExternalStyle(teamName);
    }
}
