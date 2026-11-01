package com.akashic.kunftig.bukkit.nametag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.akashic.kunftig.bukkit.net.KunftigChannel;
import com.akashic.kunftig.common.protocol.KnpMessage;
import com.akashic.kunftig.common.protocol.KnpProtocol;
import com.akashic.kunftig.common.text.ComponentJson;

/**
 * Owns the nametag of every managed player and keeps two outputs in sync:
 * <ol>
 * <li><b>vanilla teams</b>, with a prefix/suffix that fits what the server and every client accept (16 chars below
 * 1.13), on the main scoreboard and on every scoreboard a player is currently viewing;</li>
 * <li><b>Kunftig styles</b> (full JSON components and name style), sent to Kunftig clients by team name.</li>
 * </ol>
 * Bukkit has no event for "a plugin gave a player another scoreboard", so scoreboards are reconciled on a timer:
 * a board that already received the previous update gets only what changed since; a board seen for the first time
 * (or one that skipped an update) gets everything, including removal of stale Kunftig teams.
 * <p>
 * Main thread only.
 */
public final class NametagService implements KunftigChannel.SessionListener {

    /** Resolves the spec for a player, or {@code null} for an unmanaged player. */
    public interface SpecResolver {

        NametagSpec resolve(Player player);
    }

    private static final class Managed {

        final UUID id;
        final String teamName;
        final NametagSpec spec;
        final String fallbackPrefix;
        final String fallbackSuffix;
        final KnpMessage.TeamStyle style;

        Managed(UUID id, String teamName, NametagSpec spec, String fallbackPrefix, String fallbackSuffix,
            KnpMessage.TeamStyle style) {
            this.id = id;
            this.teamName = teamName;
            this.spec = spec;
            this.fallbackPrefix = fallbackPrefix;
            this.fallbackSuffix = fallbackSuffix;
            this.style = style;
        }
    }

    private final Logger logger;
    private final KunftigChannel channel;
    private final SpecResolver resolver;
    private final FallbackRenderer fallback;
    private final VanillaTeamOptions vanillaOptions;
    private final boolean overrideOtherTeams;
    private final TeamNames teamNames = new TeamNames();

    private final Map<UUID, Managed> managed = new LinkedHashMap<UUID, Managed>();
    /** Styles that other plugins attach to their own teams through the API. */
    private final Map<String, KnpMessage.TeamStyle> externalStyles = new LinkedHashMap<String, KnpMessage.TeamStyle>();

    private final Set<UUID> dirty = new HashSet<UUID>();
    private final Set<String> removedTeams = new HashSet<String>();
    private final Map<Scoreboard, Integer> appliedGeneration = new WeakHashMap<Scoreboard, Integer>();
    private int generation;

    public NametagService(Logger logger, KunftigChannel channel, SpecResolver resolver, ServerEnvironment environment,
        boolean overrideOtherTeams) {
        this.logger = logger;
        this.channel = channel;
        this.resolver = resolver;
        this.fallback = new FallbackRenderer(environment);
        this.vanillaOptions = new VanillaTeamOptions(logger);
        this.overrideOtherTeams = overrideOtherTeams;
    }

    // ------------------------------------------------------------------ desired state

    /** Re-resolves one player's spec; cheap when nothing changed. */
    public void refresh(Player player) {
        NametagSpec spec = resolver.resolve(player);
        UUID id = player.getUniqueId();
        Managed current = managed.get(id);
        if (spec == null || spec.isEmpty()) {
            if (current != null) {
                remove(id);
            }
            return;
        }
        if (current != null && current.spec.equals(spec)) {
            return;
        }
        String teamName = teamNames.nameFor(id, spec.priority());
        KnpMessage.TeamStyle style = styleMessage(teamName, spec);
        Managed next = new Managed(id, teamName, spec, fallback.prefix(spec), fallback.suffix(spec), style);
        managed.put(id, next);
        dirty.add(id);
        if (current != null && !current.teamName.equals(teamName)) {
            removedTeams.add(current.teamName);
            channel.broadcast(new KnpMessage.TeamStyleRemove(current.teamName));
        }
        channel.broadcast(style);
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /** Stops managing a player (quit, or no spec any more). */
    public void remove(UUID id) {
        Managed current = managed.remove(id);
        dirty.remove(id);
        teamNames.forget(id);
        if (current != null) {
            removedTeams.add(current.teamName);
            channel.broadcast(new KnpMessage.TeamStyleRemove(current.teamName));
        }
    }

    /** The protocol message carrying a spec's style for a team. */
    public static KnpMessage.TeamStyle styleMessage(String teamName, NametagSpec spec) {
        return new KnpMessage.TeamStyle(teamName, spec.prefix() == null ? null : ComponentJson.write(spec.prefix()),
            spec.suffix() == null ? null : ComponentJson.write(spec.suffix()), spec.name(), spec.visibility());
    }

    /** Styles attached to other plugins' teams, to carry them over a reload. */
    public List<KnpMessage.TeamStyle> externalStyles() {
        return new ArrayList<KnpMessage.TeamStyle>(externalStyles.values());
    }

    /** Attaches a Kunftig style to a team owned by another plugin. Vanilla team data is left alone. */
    public void setExternalStyle(KnpMessage.TeamStyle style) {
        if (style.team.isEmpty() || style.team.length() > KnpProtocol.MAX_TEAM_NAME) {
            throw new IllegalArgumentException("Team name must be 1-" + KnpProtocol.MAX_TEAM_NAME + " chars");
        }
        externalStyles.put(style.team, style);
        channel.broadcast(style);
    }

    public void removeExternalStyle(String teamName) {
        if (externalStyles.remove(teamName) != null) {
            channel.broadcast(new KnpMessage.TeamStyleRemove(teamName));
        }
    }

    @Override
    public void onSessionStarted(Player player, com.akashic.kunftig.bukkit.net.ClientSession session) {
        // HELLO_ACK made the client drop everything; send the full state.
        for (Managed entry : managed.values()) {
            channel.send(player, entry.style);
        }
        for (KnpMessage.TeamStyle style : externalStyles.values()) {
            channel.send(player, style);
        }
    }

    // ------------------------------------------------------------------ scoreboards

    /** Brings every scoreboard in use up to date. Call every few ticks. */
    public void sync() {
        boolean changed = !dirty.isEmpty() || !removedTeams.isEmpty();
        int previous = generation;
        if (changed) {
            generation++;
        }
        for (Scoreboard board : boardsInUse()) {
            Integer applied = appliedGeneration.get(board);
            try {
                if (applied == null || applied != (changed ? previous : generation)) {
                    applyAll(board);
                } else if (changed) {
                    applyChanges(board);
                }
                appliedGeneration.put(board, generation);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not update nametag teams on a scoreboard", e);
            }
        }
        dirty.clear();
        removedTeams.clear();
    }

    private Set<Scoreboard> boardsInUse() {
        Set<Scoreboard> boards = Collections.newSetFromMap(new IdentityHashMap<Scoreboard, Boolean>());
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player player : Bukkit.getOnlinePlayers()) {
            boards.add(player.getScoreboard());
        }
        return boards;
    }

    private void applyAll(Scoreboard board) {
        Set<String> wanted = new HashSet<String>();
        for (Managed entry : managed.values()) {
            wanted.add(entry.teamName);
        }
        for (Team team : new ArrayList<Team>(board.getTeams())) {
            if (TeamNames.isManaged(team.getName()) && !wanted.contains(team.getName())) {
                team.unregister();
            }
        }
        for (Managed entry : managed.values()) {
            upsert(board, entry);
        }
    }

    private void applyChanges(Scoreboard board) {
        for (String name : removedTeams) {
            Team team = board.getTeam(name);
            if (team != null) {
                team.unregister();
            }
        }
        for (UUID id : dirty) {
            Managed entry = managed.get(id);
            if (entry != null) {
                upsert(board, entry);
            }
        }
    }

    private void upsert(Scoreboard board, Managed entry) {
        Player player = Bukkit.getPlayer(entry.id);
        if (player == null) {
            return;
        }
        Team team = board.getTeam(entry.teamName);
        if (team == null) {
            team = board.registerNewTeam(entry.teamName);
        }
        setAffixes(team, entry);
        vanillaOptions.applyColor(team, entry.spec.name() == null ? null : entry.spec.name().primaryColor());
        vanillaOptions.applyVisibility(team, entry.spec.visibility());
        if (!team.hasPlayer(player)) {
            Team other = board.getPlayerTeam(player);
            if (other != null && !overrideOtherTeams && !TeamNames.isManaged(other.getName())) {
                return;
            }
            team.addPlayer(player);
        }
    }

    private void setAffixes(Team team, Managed entry) {
        // Each set sends a team update packet, so only write what differs.
        try {
            if (!entry.fallbackPrefix.equals(team.getPrefix())) {
                team.setPrefix(entry.fallbackPrefix);
            }
            if (!entry.fallbackSuffix.equals(team.getSuffix())) {
                team.setSuffix(entry.fallbackSuffix);
            }
        } catch (IllegalArgumentException e) {
            // A server-side limit lower than expected (fallback.max-length too high): stay safe and empty.
            logger.warning("Server refused a nametag prefix/suffix (" + e.getMessage()
                + "); lower nametags.fallback.max-length");
            team.setPrefix("");
            team.setSuffix("");
        }
    }

    /** Unregisters every Kunftig team from every board in use. Called on disable. */
    public void clearAll() {
        for (Scoreboard board : boardsInUse()) {
            for (Team team : new ArrayList<Team>(board.getTeams())) {
                if (TeamNames.isManaged(team.getName())) {
                    team.unregister();
                }
            }
        }
        managed.clear();
        dirty.clear();
        removedTeams.clear();
        appliedGeneration.clear();
    }

    // ------------------------------------------------------------------ diagnostics

    public String teamOf(UUID id) {
        Managed entry = managed.get(id);
        return entry == null ? null : entry.teamName;
    }

    public NametagSpec specOf(UUID id) {
        Managed entry = managed.get(id);
        return entry == null ? null : entry.spec;
    }

    public String[] fallbackOf(UUID id) {
        Managed entry = managed.get(id);
        return entry == null ? null : new String[] { entry.fallbackPrefix, entry.fallbackSuffix };
    }

    public int managedCount() {
        return managed.size();
    }

    public int externalStyleCount() {
        return externalStyles.size();
    }

    /** The vanilla prefix this server would write for a spec (for previews). */
    public String fallbackPrefix(NametagSpec spec) {
        return fallback.prefix(spec);
    }
}
