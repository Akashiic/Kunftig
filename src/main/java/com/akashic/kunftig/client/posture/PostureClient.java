package com.akashic.kunftig.client.posture;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.MathHelper;

import com.akashic.kunftig.client.compat.FuturaModels;
import com.akashic.kunftig.client.display.DisplayClient;
import com.akashic.kunftig.display.DeckPose;
import com.akashic.kunftig.display.DisplayMotion;
import com.akashic.kunftig.posture.PostureWire;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Postures on the client (API 15): at the end of each client tick, after the entities moved, every player the server
 * sat or laid down is put at his point (of the world, or of a display's frame, wherever the display is this tick: the
 * same interval the display is drawn over, so he does not tremble on a moving ship), with no speed of his own. The
 * local player's keys do not move him ({@link PostureInput}), the deck does not push him ({@code DeckClient} skips
 * him) and, lying, he is lowered so that his eyes are near the pillow, his model drawn as much higher. The biped is
 * posed by {@code ModelBipedPostureMixin} and laid down by {@code RenderPlayerPostureMixin}; a model of 3D Futura
 * Engine by its {@code posture} state (futura3d API 7). Client thread.
 */
public final class PostureClient {

    public static final PostureClient INSTANCE = new PostureClient();
    /** futura3d's external state. */
    private static final String FUTURA_KEY = "posture";

    /** A posture and where it is in the world this tick. */
    public static final class Held {

        public final PostureWire.Posture posture;
        /** The yaw in the world: where one who sits faces, where the head of one who lies points. */
        public float yaw;
        /** How much higher than the entity the model is drawn (the local player lying). */
        public float rise;
        /** False until the point was found once (the display may not have arrived). */
        public boolean placed;
        private float displayYaw = Float.NaN;

        Held(PostureWire.Posture posture) {
            this.posture = posture;
        }

        public boolean sits() {
            return posture.kind == PostureWire.SIT;
        }

        public boolean lies() {
            return posture.kind == PostureWire.LIE;
        }
    }

    private final Map<Integer, Held> held = new HashMap<>();
    /** Players futura3d was told a posture of: told to stand when theirs ends. */
    private final Set<UUID> told = new HashSet<>();

    private PostureClient() {}

    public static boolean disabled() {
        return Boolean.getBoolean("kunftig.postures.disable");
    }

    /** A fresh connection: nobody sits; register the channel. */
    public void onJoinGame(NetHandlerPlayClient handler) {
        held.clear();
        handler.addToSendQueue(new C17PacketCustomPayload("REGISTER", PostureWire.registration()));
    }

    public void onPayload(byte[] data) {
        PostureWire.Posture p = PostureWire.decode(data);
        if (p == null) {
            return;
        }
        if (p.kind == PostureWire.STAND) {
            held.remove(p.entity);
        } else {
            held.put(p.entity, new Held(p));
        }
    }

    /** The posture the entity is held and drawn in, or null. */
    public Held of(Entity entity) {
        if (entity == null || held.isEmpty()) {
            return null;
        }
        Held h = held.get(entity.getEntityId());
        return h != null && h.placed && entity instanceof EntityPlayer ? h : null;
    }

    /** Whether the local player sits or lies (the deck leaves him alone). */
    public boolean local() {
        EntityPlayer me = Minecraft.getMinecraft().thePlayer;
        return me != null && held.containsKey(me.getEntityId());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            held.clear();
            told.clear();
            return;
        }
        lock(mc.thePlayer, held.containsKey(mc.thePlayer.getEntityId()));
        if (!held.isEmpty()) {
            for (Iterator<Map.Entry<Integer, Held>> it = held.entrySet()
                .iterator(); it.hasNext();) {
                Map.Entry<Integer, Held> e = it.next();
                Entity entity = mc.theWorld.getEntityByID(e.getKey());
                if (entity instanceof EntityPlayer && !entity.isDead) {
                    place((EntityPlayer) entity, e.getValue(), entity == mc.thePlayer);
                }
            }
        }
        if (!told.isEmpty()) {
            for (Object o : mc.theWorld.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                if (!held.containsKey(p.getEntityId()) && told.remove(p.getUniqueID())) {
                    FuturaModels.setExternalState(p, FUTURA_KEY, null);
                }
            }
        }
    }

    private static void lock(EntityPlayerSP player, boolean on) {
        if (on && player.movementInput != null && !(player.movementInput instanceof PostureInput)) {
            player.movementInput = new PostureInput(player.movementInput);
        } else if (!on && player.movementInput instanceof PostureInput) {
            player.movementInput = ((PostureInput) player.movementInput).real;
        }
    }

    private void place(EntityPlayer player, Held h, boolean local) {
        PostureWire.Posture p = h.posture;
        double x = p.x, y = p.y, z = p.z;
        float yaw = p.yaw;
        if (p.display != PostureWire.WORLD) {
            DisplayClient.Entry display = DisplayClient.INSTANCE.get(p.display);
            if (display == null) {
                return; // not here yet (or gone): he stays where the server has him
            }
            DisplayMotion m = display.motion;
            double[] w = new DeckPose(m.x(1), m.y(1), m.z(1), m.yaw(1), m.pitch(1), m.roll(1)).toWorld(x, y, z);
            x = w[0];
            y = w[1];
            z = w[2];
            float displayYaw = m.yaw(1);
            yaw += displayYaw;
            if (local && !Float.isNaN(h.displayYaw)) {
                // the ship turned under him: his view turns with it, as when he stands on the deck
                float turned = MathHelper.wrapAngleTo180_float(displayYaw - h.displayYaw);
                player.rotationYaw += turned;
                player.prevRotationYaw += turned;
                player.rotationYawHead += turned;
            }
            h.displayYaw = displayYaw;
        }
        float drop = local ? PostureWire.drop(p.kind, p.eye) : 0;
        double feet = player.boundingBox.minY;
        player.setPosition(x, player.posY + (y - drop - feet), z);
        player.motionX = 0;
        player.motionY = 0;
        player.motionZ = 0;
        player.fallDistance = 0;
        player.onGround = true;
        player.renderYawOffset = yaw;
        player.limbSwingAmount = 0;
        h.yaw = yaw;
        h.rise = drop;
        h.placed = true;
        if (FuturaModels.setExternalState(player, FUTURA_KEY, h.sits() ? "sit" : "lie:" + yaw + ":" + drop)) {
            told.add(player.getUniqueID());
        }
    }
}
