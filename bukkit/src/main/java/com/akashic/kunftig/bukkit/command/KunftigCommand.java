package com.akashic.kunftig.bukkit.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import com.akashic.kunftig.bukkit.KunftigPlugin;
import com.akashic.kunftig.bukkit.net.ClientSession;
import com.akashic.kunftig.bukkit.nametag.NametagSpec;
import com.akashic.kunftig.bukkit.nametag.PlayerOverrides;
import com.akashic.kunftig.bukkit.text.TextParseException;
import com.akashic.kunftig.bukkit.text.TextParser;
import com.akashic.kunftig.common.text.ColorMode;
import com.akashic.kunftig.common.text.Component;
import com.akashic.kunftig.common.text.ComponentJson;
import com.akashic.kunftig.common.text.LegacyRenderer;
import com.akashic.kunftig.common.text.LegacyText;

/** {@code /kunftig status|reload|set|clear|preview}. */
public final class KunftigCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = Arrays.asList("status", "reload", "set", "clear", "preview");

    private final KunftigPlugin plugin;

    public KunftigCommand(KunftigPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status":
                return status(sender, args);
            case "reload":
                plugin.reloadKunftig();
                sender.sendMessage(ChatColor.GREEN + "Kunftig reloaded.");
                return true;
            case "set":
                return set(sender, label, args);
            case "clear":
                return clear(sender, label, args);
            case "preview":
                return preview(sender, label, args);
            default:
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <status|reload|set|clear|preview>");
                return true;
        }
    }

    private boolean status(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not online: " + args[1]);
                return true;
            }
            ClientSession session = plugin.channel().session(target);
            sender.sendMessage(ChatColor.GOLD + target.getName() + ChatColor.GRAY + " | client: "
                + (session == null ? ChatColor.RED + "vanilla/no Kunftig" : ChatColor.GREEN + session.toString()));
            String team = plugin.nametags().teamOf(target.getUniqueId());
            String[] fallback = plugin.nametags().fallbackOf(target.getUniqueId());
            if (team == null) {
                sender.sendMessage(ChatColor.GRAY + "Not managed (no group, override or API spec).");
            } else {
                sender.sendMessage(ChatColor.GRAY + "Team " + team + " | vanilla: '" + fallback[0] + ChatColor.RESET
                    + target.getName() + fallback[1] + ChatColor.RESET + ChatColor.GRAY + "'");
            }
            for (String field : PlayerOverrides.FIELDS) {
                String raw = plugin.overrides().raw(target.getUniqueId(), field);
                if (raw != null) {
                    sender.sendMessage(ChatColor.GRAY + "  override " + field + ": " + ChatColor.WHITE + raw);
                }
            }
            return true;
        }
        Map<UUID, ClientSession> sessions = plugin.channel().sessions();
        sender.sendMessage(ChatColor.GOLD + "Kunftig " + plugin.getDescription().getVersion() + ChatColor.GRAY
            + " | server " + plugin.environment().version() + " | vanilla affix limit "
            + (plugin.environment().affixLimit() == Integer.MAX_VALUE ? "none" : plugin.environment().affixLimit()));
        sender.sendMessage(ChatColor.GRAY + "Kunftig clients online: " + sessions.size() + "/"
            + Bukkit.getOnlinePlayers().size() + " | managed nametags: " + plugin.nametags().managedCount()
            + " | external team styles: " + plugin.nametags().externalStyleCount());
        return true;
    }

    private boolean set(CommandSender sender, String label, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " set <player> <" + String.join("|",
                PlayerOverrides.FIELDS) + "> <value...>");
            return true;
        }
        String field = args[2].toLowerCase(Locale.ROOT);
        if (!Arrays.asList(PlayerOverrides.FIELDS).contains(field)) {
            sender.sendMessage(ChatColor.RED + "Unknown field " + args[2]);
            return true;
        }
        UUID target = resolve(args[1]);
        String value = join(args, 3);
        try {
            plugin.overrides().set(target, field, value);
        } catch (TextParseException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            return true;
        }
        plugin.refresh(target);
        sender.sendMessage(ChatColor.GREEN + "Set " + field + " of " + args[1] + ".");
        return true;
    }

    private boolean clear(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " clear <player> [field]");
            return true;
        }
        UUID target = resolve(args[1]);
        if (args.length >= 3) {
            String field = args[2].toLowerCase(Locale.ROOT);
            if (!Arrays.asList(PlayerOverrides.FIELDS).contains(field)) {
                sender.sendMessage(ChatColor.RED + "Unknown field " + args[2]);
                return true;
            }
            try {
                plugin.overrides().set(target, field, null);
            } catch (TextParseException e) {
                sender.sendMessage(ChatColor.RED + e.getMessage());
                return true;
            }
        } else {
            plugin.overrides().clear(target);
        }
        plugin.refresh(target);
        sender.sendMessage(ChatColor.GREEN + "Cleared " + (args.length >= 3 ? args[2] : "overrides") + " of "
            + args[1] + ".");
        return true;
    }

    private boolean preview(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " preview <text...>");
            return true;
        }
        Component component;
        NametagSpec asPrefix;
        try {
            component = TextParser.parse(join(args, 1));
            asPrefix = NametagSpec.builder().prefix(component).build();
        } catch (TextParseException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            return true;
        }
        ClientSession session = sender instanceof Player ? plugin.channel().session((Player) sender) : null;
        ColorMode mode = session != null && session.rendersRgb() ? ColorMode.RGB : ColorMode.LEGACY;
        String vanilla = plugin.nametags().fallbackPrefix(asPrefix);
        sender.sendMessage(ChatColor.GRAY + "Kunftig clients: " + ChatColor.RESET
            + LegacyRenderer.render(component, mode));
        sender.sendMessage(ChatColor.GRAY + "Vanilla (" + LegacyText.strip(vanilla).length() + " visible chars): "
            + ChatColor.RESET + vanilla);
        sender.sendMessage(ChatColor.GRAY + "JSON size: " + ComponentJson.write(component).length() + " chars");
        return true;
    }

    @SuppressWarnings("deprecation")
    private static UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.getUniqueId();
    }

    private static String join(String[] args, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                out.append(' ');
            }
            out.append(args[i]);
        }
        return out.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("status") || sub.equals("set") || sub.equals("clear"))) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && (sub.equals("set") || sub.equals("clear"))) {
            return filter(Arrays.asList(PlayerOverrides.FIELDS), args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                out.add(option);
            }
        }
        return out;
    }
}
