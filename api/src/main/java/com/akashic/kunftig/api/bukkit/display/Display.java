package com.akashic.kunftig.api.bukkit.display;

import java.util.List;
import java.util.Map;

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

    /** Moves it (same world or another), with the new yaw, at once. */
    void teleport(Location location);

    /**
     * Moves it, gliding there over {@code ticks} client ticks (0 to 200; 0 is {@link #teleport(Location)}), position
     * and yaw (the short way round). Moving it every tick with {@code ticks} 1 to 3 makes it turn or travel smoothly
     * (a display circling a point, a model following a path). Another world always jumps. API 9: an API 8 client jumps
     * at once.
     *
     * @since API 9
     */
    void teleport(Location location, int ticks);

    /**
     * Scales the model on its own X, Y and Z (Z is where it faces), on top of the scale its manifest declares
     * ({@code escala}): {@code setScale(1, 1, 2)} stretches it to twice its length. Each axis 0.01 to 64. API 9: an
     * API 8 client draws it at the manifest's size.
     *
     * @since API 9
     */
    void setScale(double x, double y, double z);

    /** {@link #setScale(double, double, double)} with the same value on all three axes. @since API 9 */
    void setScale(double scale);

    /** The scale set by {@link #setScale}: {@code {x, y, z}}, {@code {1, 1, 1}} by default. @since API 9 */
    double[] getScale();

    /**
     * Shows or hides a bone of the model, and everything under it, whatever the manifest says (it can list bones that
     * start hidden, {@code ocultos}). A weak point burning out, a crystal breaking, a part swapped for another. At most
     * 512 bones set per display. API 9: an API 8 client draws every bone.
     *
     * @param bone a bone name of the model's geometry (unknown names do nothing on the client)
     * @since API 9
     */
    void setBoneVisible(String bone, boolean visible);

    /** The bones set by {@link #setBoneVisible} (bone -> visible). @since API 9 */
    Map<String, Boolean> getBoneVisibility();

    /** Every bone back to the manifest's default. @since API 9 */
    void resetBones();

    /** Blocks around the model within which players see it. */
    double getViewRange();

    /** Removes it for everyone. Further calls do nothing. */
    void remove();

    boolean isRemoved();
}
