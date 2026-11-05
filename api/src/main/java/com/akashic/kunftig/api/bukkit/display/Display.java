package com.akashic.kunftig.api.bukkit.display;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/**
 * A 3D model standing in the world, like the 1.19.4+ display entities: a GeckoLib model of a resource pack (declared
 * in {@code kunftig/displays.json}) at a point, turned by the location's yaw, playing one of its states, with up to
 * {@link KunftigDisplays#MAX_ITEMS} item stacks drawn at the model's item bones (a reward coming out of a box, a
 * trophy on a stand). Created by {@link KunftigDisplays#spawn}. Main thread only.
 * <p>
 * Players whose client has Kunftig with GeckoLib and the pack see it; everyone else sees nothing (draw something else
 * for them, see {@link KunftigDisplays#drawsDisplays}). The animation runs on the client, at its frame rate: the
 * server only says which state plays and since when.
 */
public interface Display {

    /** Unique while the server runs. */
    int getId();

    /** Where the model's origin is; the yaw turns the model (0: its +Z faces south, like an entity's front). */
    Location getLocation();

    /** The model, {@code namespace:id} of {@code kunftig/displays.json}. */
    String getModel();

    /** The texture variant ({@code variantes} of the manifest), or {@code ""} for the default texture. */
    String getVariant();

    /** The state playing (a name of the manifest's {@code estados}). */
    String getState();

    /**
     * Plays another state from its start (the same state again restarts it). Viewers who arrive later see it from
     * where it is.
     */
    void setState(String state);

    /** The stacks drawn at the item bones, in order (copies). */
    List<ItemStack> getItems();

    /** Replaces the stacks (null or air entries draw nothing in their bone). */
    void setItems(List<ItemStack> items);

    /** Moves it (same world or another), with the new yaw. */
    void teleport(Location location);

    /** Blocks around the model within which players see it. */
    double getViewRange();

    /** Removes it for everyone. Further calls do nothing. */
    void remove();

    boolean isRemoved();
}
