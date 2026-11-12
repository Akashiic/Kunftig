package com.akashic.kunftig.client.display;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.display.DeckPose;
import com.akashic.kunftig.display.DeckShape;
import com.akashic.kunftig.display.DeckWalker;
import com.akashic.kunftig.display.DisplayMotion;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Walking on displays (API 12): at the end of the local player's tick, after it moved against the world and before
 * its position goes to the server, the {@link DeckWalker} carries it with the display it stands on and keeps it on
 * the deck. The displays' poses advance at the start of the client tick ({@link DisplayClient}), so the player ends
 * the tick where the display is, and both are drawn interpolated over the same interval: no trembling between them.
 * <p>
 * The other players come from the server a few ticks late, while the display is already where it is: one who stands
 * within a short reach of a floor is drawn on it.
 * <p>
 * Disabled with {@code -Dkunftig.decks.disable=true}. Client thread.
 */
public final class DeckClient {

    public static final DeckClient INSTANCE = new DeckClient();
    /** Blocks beyond a deck's farthest corner within which a player is watched. */
    private static final double MARGIN = 6;
    /** How far above or below a floor another player is still drawn on it. */
    private static final double REMOTE_REACH = 0.4;
    /** {@code -Dkunftig.decks.debug=true}: logs each time the local player boards or leaves a display. */
    private static final boolean DEBUG = Boolean.getBoolean("kunftig.decks.debug");

    private final DeckWalker walker = new DeckWalker();
    private int display = -1;
    private boolean wasAboard;
    private final Map<EntityPlayer, Boolean> remoteOnDeck = new WeakHashMap<>();

    private DeckClient() {}

    public static boolean disabled() {
        return Boolean.getBoolean("kunftig.decks.disable");
    }

    /** The display the local player stands on or is carried by, or -1. */
    public int aboard() {
        return walker.aboard() ? display : -1;
    }

    /** Whether the local player stands on a display's floor. */
    public boolean grounded() {
        return walker.grounded();
    }

    public void reset() {
        wasAboard = false;
        walker.reset();
        display = -1;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.CLIENT || DisplayClient.INSTANCE.count() == 0) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == Minecraft.getMinecraft().thePlayer) {
            local(player);
        } else {
            remote(player);
        }
    }

    private static DeckPose pose(DisplayMotion m, float partial) {
        return new DeckPose(
            m.x(partial),
            m.y(partial),
            m.z(partial),
            m.yaw(partial),
            m.pitch(partial),
            m.roll(partial));
    }

    private static boolean near(DisplayClient.Entry e, double x, double y, double z) {
        DeckShape deck = e.deck;
        if (deck.isEmpty()) {
            return false;
        }
        double dx = e.motion.x(1) - x;
        double dy = e.motion.y(1) - y;
        double dz = e.motion.z(1) - z;
        double reach = deck.radius() + MARGIN;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private void local(EntityPlayer player) {
        if (player.capabilities.isFlying || player.ridingEntity != null
            || player.isDead
            || player.isPlayerSleeping()
            || player.noClip) {
            reset();
            return;
        }
        double fx = player.posX;
        double fy = player.boundingBox.minY;
        double fz = player.posZ;
        DisplayClient.Entry target = display < 0 ? null : DisplayClient.INSTANCE.get(display);
        if (target != null && !near(target, fx, fy, fz)) {
            target = null;
        }
        if (target == null || !walker.aboard()) {
            // not carried by one: the nearest deck around
            DisplayClient.Entry nearest = target;
            double best = Double.MAX_VALUE;
            for (DisplayClient.Entry e : DisplayClient.INSTANCE.entries()) {
                if (!near(e, fx, fy, fz)) {
                    continue;
                }
                double dx = e.motion.x(1) - fx;
                double dy = e.motion.y(1) - fy;
                double dz = e.motion.z(1) - fz;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < best) {
                    best = d;
                    nearest = e;
                }
            }
            target = nearest;
        }
        if (target == null) {
            reset();
            return;
        }
        if (target.id != display) {
            walker.reset();
            display = target.id;
        }
        walker.step(
            target.deck,
            pose(target.motion, 0),
            pose(target.motion, 1),
            fx,
            fy,
            fz,
            player.width / 2,
            player.height,
            player.onGround);
        if (walker.aboard() != wasAboard) {
            wasAboard = walker.aboard();
            if (DEBUG) {
                DeckPose now = pose(target.motion, 1);
                Kunftig.LOG.info(
                    "Kunftig decks: {} display {} (feet {}, floor under them {}, pose y {} pitch {} roll {})",
                    wasAboard ? "aboard" : "left",
                    display,
                    fy,
                    DeckWalker.surface(target.deck, now, fx, fy, fz, player.width / 2, 4),
                    now.y,
                    target.motion.pitch(1),
                    target.motion.roll(1));
            }
        }
        if (!walker.moved) {
            return;
        }
        player.setPosition(player.posX + (walker.x - fx), player.posY + (walker.y - fy), player.posZ + (walker.z - fz));
        if (walker.turned != 0) {
            player.rotationYaw += walker.turned;
            player.renderYawOffset += walker.turned;
            player.rotationYawHead += walker.turned;
        }
        if (walker.grounded()) {
            player.onGround = true;
            player.isCollidedVertically = true;
            player.isAirBorne = false;
            player.fallDistance = 0;
            if (player.motionY < 0) {
                player.motionY = 0;
            }
        } else if (walker.bumped && player.motionY > 0) {
            player.motionY = 0;
        }
    }

    /** Another player: drawn on the floor it stands on, wherever the display is this tick. */
    private void remote(EntityPlayer player) {
        double fx = player.posX;
        double fy = player.boundingBox.minY;
        double fz = player.posZ;
        for (DisplayClient.Entry e : DisplayClient.INSTANCE.entries()) {
            if (!near(e, fx, fy, fz)) {
                continue;
            }
            // one already on the deck is followed a little farther than one arriving (the display's own motion)
            double reach = remoteOnDeck.containsKey(player) ? REMOTE_REACH * 2 : REMOTE_REACH;
            double y = DeckWalker.surface(e.deck, pose(e.motion, 1), fx, fy, fz, player.width / 2, reach);
            if (!Double.isNaN(y)) {
                remoteOnDeck.put(player, Boolean.TRUE);
                player.setPosition(player.posX, player.posY + (y - fy), player.posZ);
                return;
            }
        }
        remoteOnDeck.remove(player);
    }
}
