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

    /**
     * Moves it (same world or another), with the new yaw; the pitch and roll stay. Stops a flight
     * ({@link #setMotion}).
     */
    void teleport(Location location);

    /**
     * API 9: moves it like {@link #teleport}, but viewers in the same world see it glide there over
     * {@code glideTicks} ticks (0: a jump, up to 255), in a straight line, turning along. For something that follows
     * a target (a homing sword): call it every few ticks with a glide of as many ticks. Viewers with an older Kunftig
     * see it jump. Stops a flight.
     */
    void moveTo(Location location, int glideTicks);

    /**
     * API 9: the full rotation in degrees: yaw turns about the vertical axis (as {@link #teleport}), then pitch tips
     * the model's front (+Z) down for positive values (as an entity's pitch), then roll turns it about its front. A
     * blade flying edge first, a slash arc tilted 45 degrees. Viewers see it turn over one tick. An older Kunftig
     * draws yaw only.
     */
    void setRotation(float yaw, float pitch, float roll);

    /** API 9: degrees, see {@link #setRotation}. */
    float getPitch();

    /** API 9: degrees, see {@link #setRotation}. */
    float getRoll();

    /**
     * API 9: from now on it flies on its own, {@code perTick} blocks every tick (each axis within 10), on the server
     * ({@link #getLocation} follows) and on every client, with no message while it flies: a projectile. Null or zero
     * stops it where it is. Collisions are the plugin's: check the path each tick and {@link #remove} it when it
     * hits. Viewers with an older Kunftig see it stand where the flight started.
     */
    void setMotion(org.bukkit.util.Vector perTick);

    /** API 9: blocks per tick (zero when it does not fly). */
    org.bukkit.util.Vector getMotion();

    /** Blocks around the model within which players see it. */
    double getViewRange();

    /** Removes it for everyone. Further calls do nothing. */
    void remove();

    boolean isRemoved();
}
