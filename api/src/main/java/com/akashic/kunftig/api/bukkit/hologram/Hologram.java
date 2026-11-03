package com.akashic.kunftig.api.bukkit.hologram;

import java.util.List;

import org.bukkit.Location;

/**
 * Floating text in the world, like the 1.19.4+ text display or the armor stand holograms of later versions: one or
 * more lines centered on a point, always facing the viewer, readable through nothing (blocks hide it, as they hide a
 * nametag). Created by {@link KunftigHolograms#create}. Main thread only.
 * <p>
 * Players whose client has Kunftig see it; everyone else sees nothing (1.7.10 has no way to draw text in the world
 * without an entity).
 */
public interface Hologram {

    /** Unique while the server runs. */
    int getId();

    /** The center of the top line. */
    Location getLocation();

    /** The lines, top first (color codes with {@code §}). */
    List<String> getLines();

    /** Replaces the lines; viewers get the new text right away. */
    void setLines(List<String> lines);

    /** Moves it (in the same world or another one). */
    void teleport(Location location);

    /** Blocks around the hologram within which players see it. */
    double getViewRange();

    /** Removes it for everyone. Further calls do nothing. */
    void remove();

    boolean isRemoved();
}
