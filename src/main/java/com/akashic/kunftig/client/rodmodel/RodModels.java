package com.akashic.kunftig.client.rodmodel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.apache.commons.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.itemmodel.ItemModels;
import com.akashic.kunftig.itemmodel.ItemModelIndex;
import com.akashic.kunftig.rodmodel.BedrockAnimations;
import com.akashic.kunftig.rodmodel.RodMatrix;
import com.akashic.kunftig.rodmodel.RodModelDef;
import com.akashic.kunftig.rodmodel.RodModelIndex;
import com.akashic.kunftig.rodmodel.RodSway;
import com.akashic.kunftig.rodmodel.RodTimeline;
import com.akashic.kunftig.rodmodel.RodWire;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client side of the 3D rods (issue #13), everything that does not touch GeckoLib: the manifests of the resource
 * packs, each player's {@link RodTimeline}, the {@link RodWire} channel, the arm pose, and where the fishing line
 * starts. The drawing itself is in {@code client.rodmodel.gecko}, installed only when GeckoLib is present; until it is,
 * {@link #active()} is false and every hook here does nothing. Client thread only.
 * <p>
 * Disabled with {@code -Dkunftig.rodModels.disable=true}: the rods keep their 2D icons.
 */
public final class RodModels implements IResourceManagerReloadListener {

    public static final RodModels INSTANCE = new RodModels();

    /** The arm bones of the arm animations: the right arm holds the grip, the left one (two hands) the rod. */
    public static final String ARM_BONE = "braco_direito";
    public static final String LEFT_ARM_BONE = "braco_esquerdo";
    /** Animation key of entities that are not players (rods on the ground, in frames, held by mobs). */
    public static final int SHARED_KEY = 1;
    private static final int FIRST_PLAYER_KEY = 2;
    private static final int MAX_LOGGED_ERRORS = 20;
    /** Ticks a player may stay unseen before his timeline is dropped. */
    private static final int FORGET_AFTER = 100;
    /** A captured line tip is used for this many frames. */
    private static final int TIP_FRAMES = 2;
    /** A tip farther than this from its player is a bad capture. */
    private static final double MAX_TIP_DISTANCE = 4;

    private volatile boolean installed;
    private RodModelIndex index = new RodModelIndex();
    private Map<String, BedrockAnimations> animations = Collections.emptyMap();
    private final Map<String, ResourceLocation> locations = new HashMap<>();
    private final Map<Integer, RodTimeline> timelines = new HashMap<>();
    private final Map<Integer, Integer> keys = new HashMap<>();
    private final ArrayDeque<Integer> freeKeys = new ArrayDeque<>();
    private int nextKey = FIRST_PLAYER_KEY;
    private final Map<String, RodModelDef.Display> displayOverrides = new HashMap<>();
    private final Map<Integer, Tip> firstPersonTips = new HashMap<>();
    private final Map<Integer, Tip> thirdPersonTips = new HashMap<>();
    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private final float[] playerView = new float[16];

    /**
     * The local player's rod inertia in first person ({@code balanco}); -Dkunftig.rodSway.disable=true turns it off.
     */
    private static final boolean SWAY_DISABLED = Boolean.getBoolean("kunftig.rodSway.disable");
    private final RodSway sway = new RodSway();
    private RodModelDef swayRod;
    private float swayYaw;
    private float swayPitch;
    private double swayX;
    private double swayY;
    private double swayZ;
    private int playerViewEntity = Integer.MIN_VALUE;
    private long clock;
    private long frame;
    private float partialTicks;

    /** A captured line tip: eye space and the hand's projection (first person), or an offset from the player. */
    private static final class Tip {

        final double x;
        final double y;
        final double z;
        final float projectionX;
        final float projectionY;
        final long frame;

        Tip(double x, double y, double z, float projectionX, float projectionY, long frame) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.projectionX = projectionX;
            this.projectionY = projectionY;
            this.frame = frame;
        }
    }

    private RodModels() {}

    public static boolean disabled() {
        return Boolean.getBoolean("kunftig.rodModels.disable");
    }

    /** Called by the GeckoLib side once it can draw. */
    public void markInstalled() {
        installed = true;
    }

    /** Whether 3D rods are drawn: GeckoLib side installed and a pack declares at least one rod. */
    public boolean active() {
        return installed && !index.isEmpty();
    }

    public boolean installed() {
        return installed;
    }

    public RodModelIndex index() {
        return index;
    }

    /** Client ticks since start, not counting pauses. */
    public long clock() {
        return clock;
    }

    public float partialTicks() {
        return partialTicks;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Resources

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        RodModelIndex read = new RodModelIndex();
        for (String namespace : domains(manager)) {
            List<IResource> files;
            try {
                files = manager.getAllResources(new ResourceLocation(namespace, RodModelIndex.PATH));
            } catch (IOException e) {
                continue; // this namespace declares no rods
            }
            for (IResource file : files) {
                String source = namespace + ":" + RodModelIndex.PATH;
                try (InputStream in = file.getInputStream()) {
                    read.add(namespace, IOUtils.toString(in, StandardCharsets.UTF_8), source);
                } catch (IOException | RuntimeException e) {
                    Kunftig.LOG.warn("Kunftig 3D rods: could not read {}", source, e);
                }
            }
        }
        Map<String, BedrockAnimations> anims = new HashMap<>();
        int logged = 0;
        for (String e : read.errors()) {
            if (logged++ < MAX_LOGGED_ERRORS) {
                Kunftig.LOG.warn("Kunftig 3D rods: {}", e);
            }
        }
        for (RodModelDef rod : read.models()
            .values()) {
            for (String file : new String[] { rod.animations, rod.arm }) {
                if (file != null && !anims.containsKey(file)) {
                    try (InputStream in = manager.getResource(location(file))
                        .getInputStream()) {
                        anims.put(file, BedrockAnimations.read(IOUtils.toString(in, StandardCharsets.UTF_8)));
                    } catch (IOException | RuntimeException ex) {
                        if (logged++ < MAX_LOGGED_ERRORS) {
                            Kunftig.LOG.warn("Kunftig 3D rods: {}: could not read {}: {}", rod.id, file, ex.toString());
                        }
                    }
                }
            }
            for (Map.Entry<String, RodModelDef.Clips> s : rod.states.entrySet()) {
                BedrockAnimations a = anims.get(rod.animations);
                if (a != null && a.clip(s.getValue().rod) == null && logged++ < MAX_LOGGED_ERRORS) {
                    Kunftig.LOG.warn(
                        "Kunftig 3D rods: {}: state {}: no animation {} in {}",
                        rod.id,
                        s.getKey(),
                        s.getValue().rod,
                        rod.animations);
                }
            }
        }
        index = read;
        animations = anims;
        forgetAll(); // states and keys refer to the old rods
        java.util.function.Consumer<java.util.Set<String>> listener = itemsListener;
        if (listener != null) {
            listener.accept(items());
        }
        if (!read.isEmpty()) {
            Kunftig.LOG.info(
                "Kunftig 3D rods: {} rod(s) read{}",
                read.models()
                    .size(),
                installed ? "" : " (not drawn: GeckoLib missing or disabled)");
        }
    }

    /** Called with {@link #items()} now and after every reload (the renderer registers itself on new items). */
    private java.util.function.Consumer<java.util.Set<String>> itemsListener;

    public void onItems(java.util.function.Consumer<java.util.Set<String>> listener) {
        itemsListener = listener;
        listener.accept(items());
    }

    /** Registry names of every item some 3D model is drawn on ({@code itens}; the fishing rod at least). */
    public java.util.Set<String> items() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>(RodModelDef.DEFAULT_ITEMS);
        for (RodModelDef rod : index.models()
            .values()) {
            out.addAll(rod.items);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Iterable<String> domains(IResourceManager manager) {
        return (Iterable<String>) manager.getResourceDomains();
    }

    /** A {@code namespace:path} as a cached {@link ResourceLocation}. */
    public ResourceLocation location(String id) {
        ResourceLocation r = locations.get(id);
        if (r == null) {
            r = new ResourceLocation(id);
            locations.put(id, r);
        }
        return r;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Stacks, states and timing

    /** The 3D rod of a stack, or null (no model, or 3D rods not drawn). */
    public RodModelIndex.Resolved resolve(ItemStack stack) {
        if (!active() || stack == null || stack.stackTagCompound == null) {
            return null;
        }
        return index.resolve(ItemModels.modelOf(stack.stackTagCompound));
    }

    /** The value of the model's size ({@code tamanho}) in a stack's data, or null (no size, or none stored). */
    public static Double sizeOf(ItemStack stack, RodModelDef rod) {
        if (rod == null || rod.size == null
            || stack == null
            || stack.stackTagCompound == null
            || !stack.stackTagCompound.hasKey(ItemModelIndex.NBT_ROOT, 10)) {
            return null;
        }
        NBTTagCompound values = stack.stackTagCompound.getCompoundTag(ItemModelIndex.NBT_ROOT);
        return values.hasKey(rod.size.key, 99) ? values.getDouble(rod.size.key) : null;
    }

    /** The size band of a stack's model (null: the model has no bands). */
    public static RodModelDef.Band band(ItemStack stack, RodModelDef rod) {
        return rod == null || rod.bands.isEmpty() ? null : rod.band(rod.size.valueOr(sizeOf(stack, rod)));
    }

    /**
     * The states a holder's model plays: its size band (a fish) or, for a model without bands, the swaps of the world
     * around the holder ({@code contextos_estados}: thunder, rain, snow, night); null when neither applies.
     */
    public static RodModelDef.Band band(net.minecraft.entity.EntityLivingBase holder, ItemStack stack,
        RodModelDef rod) {
        if (rod == null) {
            return null;
        }
        if (!rod.bands.isEmpty()) {
            return band(stack, rod);
        }
        return rod.contextBands.isEmpty() || holder == null ? null : rod.contextBand(RodContext.of(holder));
    }

    /** The level of the model's {@code variante} stored in a stack (0 without one or without the value). */
    public static int variantOf(ItemStack stack, RodModelDef rod) {
        if (rod == null || rod.variant == null
            || stack == null
            || stack.stackTagCompound == null
            || !stack.stackTagCompound.hasKey(ItemModelIndex.NBT_ROOT, 10)) {
            return 0;
        }
        NBTTagCompound values = stack.stackTagCompound.getCompoundTag(ItemModelIndex.NBT_ROOT);
        return values.hasKey(rod.variant.key, 99) ? Math.max(0, (int) values.getDouble(rod.variant.key)) : 0;
    }

    /** The factor a stack's model is scaled by for its size (1 without {@code tamanho}). */
    public static float sizeScale(ItemStack stack, RodModelDef rod) {
        return rod == null || rod.size == null ? 1F : rod.size.scale(rod.size.valueOr(sizeOf(stack, rod)));
    }

    /** The lengths and loops of a rod's states, for its timeline. */
    public RodTimeline.Clips clips(final RodModelDef rod) {
        return clips(rod, null);
    }

    /** {@link #clips(RodModelDef)} with the states of a size band. */
    public RodTimeline.Clips clips(final RodModelDef rod, final RodModelDef.Band band) {
        final BedrockAnimations anims = rod == null ? null : animations.get(rod.animations);
        return new RodTimeline.Clips() {

            private String name(String state) {
                return band == null ? state : band.state(state);
            }

            @Override
            public boolean has(String state) {
                return rod != null && rod.states.containsKey(name(state));
            }

            @Override
            public boolean loops(String state) {
                RodModelDef.Clips c = rod == null ? null : rod.states.get(name(state));
                return c != null && c.loop;
            }

            @Override
            public String attack(String state, long elapsedTicks) {
                RodModelDef.Clips c = rod == null ? null : rod.states.get(name(state));
                return c == null || c.attack == null || !c.attack.accepts(elapsedTicks) ? null : c.attack.next;
            }

            /** The clip plus the blend into it: the clip starts once the blend is over. */
            @Override
            public int lengthTicks(String state) {
                RodModelDef.Clips c = rod == null ? null : rod.states.get(name(state));
                BedrockAnimations.Clip clip = c == null || anims == null ? null : anims.clip(c.rod);
                return (clip == null ? 20 : clip.lengthTicks()) + rod.transition(RodModelDef.ROD_CONTROLLER);
            }
        };
    }

    /** The timeline of a player (created on demand), or null when 3D rods are off. */
    public RodTimeline timeline(EntityPlayer player) {
        return active() ? timelines.computeIfAbsent(player.getEntityId(), k -> new RodTimeline()) : null;
    }

    /**
     * A stable, small animation key for a player (GeckoLib keeps state per key): reused once he is gone, so keys never
     * grow with every entity id of a long session.
     */
    public int animationKey(EntityPlayer player) {
        Integer key = keys.get(player.getEntityId());
        if (key == null) {
            key = freeKeys.isEmpty() ? nextKey++ : freeKeys.poll();
            keys.put(player.getEntityId(), key);
        }
        return key;
    }

    /** Whether {@code stack} is what the holder has in the main hand (Backhand's off hand is not). */
    public static boolean isMainHand(Entity holder, ItemStack stack) {
        if (!(holder instanceof EntityPlayer)) {
            return true;
        }
        ItemStack held = ((EntityPlayer) holder).getHeldItem();
        if (held == stack) {
            return true;
        }
        // First person draws a copy kept by ItemRenderer, briefly stale after the server updates the rod.
        return held != null && stack != null
            && held.getItem() == stack.getItem()
            && sameRod(ItemModels.modelOf(held.stackTagCompound), ItemModels.modelOf(stack.stackTagCompound));
    }

    private static boolean sameRod(String a, String b) {
        RodModelIndex.Resolved ra = INSTANCE.index.resolve(a);
        RodModelIndex.Resolved rb = INSTANCE.index.resolve(b);
        return ra != null && rb != null && ra.model == rb.model;
    }

    /** A display changed in game ({@code /kunftigclient rods display}), for every rod; null clears it. */
    public void overrideDisplay(String context, RodModelDef.Display display) {
        if (display == null) {
            displayOverrides.remove(context);
        } else {
            displayOverrides.put(context, display);
        }
    }

    public RodModelDef.Display display(RodModelDef rod, String context) {
        return display(rod, context, null);
    }

    /** The display of a context for a size band (null: the model's), unless one was set in game. */
    public RodModelDef.Display display(RodModelDef rod, String context, RodModelDef.Band band) {
        RodModelDef.Display d = displayOverrides.get(context);
        return d != null ? d : rod.display(context, band);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            partialTicks = event.renderTickTime;
            frame++;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            if (!timelines.isEmpty()) {
                forgetAll();
            }
            return;
        }
        if (mc.isGamePaused()) {
            return;
        }
        clock++;
        for (Object o : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            RodModelIndex.Resolved r = resolve(p.getHeldItem());
            RodModelDef rod = r == null ? null : r.model;
            RodTimeline t = timelines.get(p.getEntityId());
            if (t == null && rod == null) {
                continue;
            }
            if (t == null) {
                t = timeline(p);
            }
            // A size band is another way of holding the model: taking a bigger fish of the same species draws again.
            RodModelDef.Band band = band(p, p.getHeldItem(), rod);
            t.observe(
                clock,
                rod == null ? null : band == null ? rod.id : rod.id + "#" + band.name,
                p.fishEntity != null,
                clips(rod, band));
        }
        tickSway(mc.thePlayer);
        for (Iterator<Map.Entry<Integer, RodTimeline>> it = timelines.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<Integer, RodTimeline> e = it.next();
            if (clock - e.getValue()
                .lastSeen() > FORGET_AFTER) {
                it.remove();
                forgetKey(e.getKey());
            }
        }
    }

    /**
     * The local player's camera turn and movement this tick drive the rod's inertia. A rod just taken (or another rod)
     * starts at rest: the first tick only samples.
     */
    private void tickSway(EntityPlayer player) {
        RodModelIndex.Resolved r = player == null || SWAY_DISABLED ? null : resolve(player.getHeldItem());
        RodModelDef rod = r == null || r.model.sway == null ? null : r.model;
        if (rod != swayRod) {
            swayRod = rod;
            sway.reset();
        } else if (rod != null) {
            float yaw = (float) Math.toRadians(player.rotationYaw);
            double dx = player.posX - swayX;
            double dz = player.posZ - swayZ;
            // the camera's right in the world: facing (-sin yaw, 0, cos yaw), right (-cos yaw, 0, -sin yaw)
            float right = (float) (-Math.cos(yaw) * dx - Math.sin(yaw) * dz);
            sway.tick(
                RodSway.turn(player.rotationYaw, swayYaw),
                player.rotationPitch - swayPitch,
                right,
                (float) (player.posY - swayY),
                rod.sway.strength);
        }
        if (player != null) {
            swayYaw = player.rotationYaw;
            swayPitch = player.rotationPitch;
            swayX = player.posX;
            swayY = player.posY;
            swayZ = player.posZ;
        }
    }

    /**
     * The first-person inertia of this rod for the frame, or null: {root, bend} rotation vectors in eye space
     * (radians).
     */
    public float[][] firstPersonSway(RodModelDef rod) {
        if (rod == null || rod != swayRod || sway.resting()) {
            return null;
        }
        float partial = partialTicks();
        return new float[][] { sway.root(partial), sway.bend(partial) };
    }

    private void forgetKey(int entityId) {
        Integer key = keys.remove(entityId);
        if (key != null) {
            freeKeys.add(key);
        }
        firstPersonTips.remove(entityId);
        thirdPersonTips.remove(entityId);
    }

    private void forgetAll() {
        timelines.clear();
        for (Integer id : keys.keySet()
            .toArray(new Integer[0])) {
            forgetKey(id);
        }
    }

    /** {@code ItemFishingRod#onItemRightClick} on the client: the local player casts now (before the bobber spawns). */
    public void onLocalRightClick(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active() || player != mc.thePlayer || player.fishEntity != null) {
            return;
        }
        RodModelIndex.Resolved r = resolve(player.getHeldItem());
        if (r != null) {
            timeline(player).localCast(clock, clips(r.model));
        }
    }

    /**
     * {@code EntityClientPlayerMP#swingItem}: the local player swings, and the server is told in the same instant
     * (an arm swing packet). Starts the next attack of the held model's combo ({@code ao_atacar}), if any.
     */
    public void onLocalSwing(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active() || player != mc.thePlayer) {
            return;
        }
        RodModelIndex.Resolved r = resolve(player.getHeldItem());
        if (r != null) {
            timeline(player).localAttack(clock, clips(r.model, band(player, player.getHeldItem(), r.model)));
        }
    }

    /** Debug ({@code /kunftigclient rods state}): plays a state on the local player's rod. */
    public boolean forceLocalState(String state, float tension) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active() || mc.thePlayer == null) {
            return false;
        }
        RodTimeline t = timeline(mc.thePlayer);
        t.server(state, clock);
        t.setTension(tension);
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Network

    /**
     * At the end of {@code handleJoinGame}: a new connection, so new entities; and ask for rod states. Whenever
     * GeckoLib
     * can draw, even before any pack declares a rod: a server's resource pack arrives after the join.
     */
    public void onJoinGame(NetHandlerPlayClient handler) {
        forgetAll();
        if (installed) {
            handler.addToSendQueue(
                new C17PacketCustomPayload("REGISTER", RodWire.CHANNEL.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /** Respawn or dimension change: the server tells the states again. */
    public void onRespawn() {
        forgetAll();
    }

    /** A message on {@link RodWire#CHANNEL}. */
    public void onPayload(byte[] data) {
        RodWire.Message m = RodWire.read(data);
        if (m == null || !active()) {
            return;
        }
        RodTimeline t = timelines.computeIfAbsent(m.entityId, k -> new RodTimeline());
        t.touch(clock); // the player may spawn on this client right after
        if (m.op == RodWire.STATE) {
            t.fromServer(m.state, clock - m.elapsedTicks, clock);
        }
        t.setTension(m.tension);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Arm

    /**
     * The arm rotation (radians, added to {@code bipedRightArm}) of a player holding a 3D rod with an arm animation for
     * its state, or null to leave the arm alone.
     */
    public float[] armRotation(EntityPlayer player) {
        return armRotation(player, ARM_BONE);
    }

    /**
     * The rotation (radians) of an arm bone ({@link #ARM_BONE} or {@link #LEFT_ARM_BONE}) for a player holding a 3D
     * rod,
     * or null when the rod has no arm animation for its state or the clip leaves that arm alone.
     */
    public float[] armRotation(EntityPlayer player, String bone) {
        RodModelIndex.Resolved r = resolve(player.getHeldItem());
        if (r == null || r.model.arm == null || !r.model.drawsIn(RodModelDef.THIRD_PERSON)) {
            return null; // no arm pose, or the rod stays flat in third person
        }
        RodTimeline t = timelines.get(player.getEntityId());
        BedrockAnimations arm = animations.get(r.model.arm);
        boolean line = player.fishEntity != null;
        RodModelDef.Band band = band(player, player.getHeldItem(), r.model);
        float[] deg = armPose(r.model, band, arm, t, bone, line);
        if (deg == null) {
            return null;
        }
        // The line went out or came back: the arms move from the other pose (one hand to both, or back).
        int blend = r.model.transition(RodModelDef.ROD_CONTROLLER);
        double sinceLine = t == null ? blend : clock - t.lineSince() + partialTicks;
        if (t != null && sinceLine < blend) {
            float[] from = armPose(r.model, band, arm, t, bone, !line);
            if (from != null) {
                float f = (float) (sinceLine / blend);
                for (int i = 0; i < 3; i++) {
                    deg[i] = from[i] + (deg[i] - from[i]) * f;
                }
            }
        }
        return new float[] { (float) Math.toRadians(deg[0]), (float) Math.toRadians(deg[1]),
            (float) Math.toRadians(deg[2]) };
    }

    /** The arm bone's pose (degrees) in the player's state, with the line out or not, blended from the state before. */
    private float[] armPose(RodModelDef rod, RodModelDef.Band band, BedrockAnimations arm, RodTimeline t, String bone,
        boolean line) {
        String state = t == null || t.state() == null ? RodModelDef.IDLE : t.state();
        BedrockAnimations.Clip clip = armClip(rod, band, arm, state, line);
        if (clip == null || !clip.animates(bone)) {
            return null;
        }
        // Like the rod: the clip starts once the blend into it is over, and the blend starts from the previous pose.
        int blend = rod.transition(RodModelDef.ROD_CONTROLLER);
        double ticks = t == null ? blend : Math.max(0, clock - t.start() + partialTicks);
        float[] deg = clip.rotation(bone, Math.max(0, ticks - blend) / 20);
        BedrockAnimations.Clip before = t == null ? null : armClip(rod, band, arm, t.previous(), line);
        if (before != null && ticks < blend && before.animates(bone)) {
            float[] from = before.rotation(bone, Math.max(0, t.start() - t.previousStart() - blend) / 20.0);
            float f = (float) (ticks / blend);
            for (int i = 0; i < 3; i++) {
                deg[i] = from[i] + (deg[i] - from[i]) * f;
            }
        }
        return deg;
    }

    /**
     * Whether the player's rod is held with both hands in its current state ({@code duas_maos}): while it is, the
     * walking swing leaves the arms (the hands would let go of the rod).
     */
    public boolean twoHanded(EntityPlayer player) {
        RodModelIndex.Resolved r = resolve(player.getHeldItem());
        if (r == null) {
            return false;
        }
        RodTimeline t = timelines.get(player.getEntityId());
        String state = t == null || t.state() == null ? RodModelDef.IDLE : t.state();
        RodModelDef.Clips c = r.model.clips(state, band(player, player.getHeldItem(), r.model));
        return c != null && c.twoHands(player.fishEntity != null);
    }

    private static volatile java.lang.invoke.MethodHandle backhandOffhand;
    private static volatile boolean backhandLooked;

    /**
     * Whether the player holds something in Backhand's off hand (then the left arm is Backhand's and the rod is held
     * with one hand). Resolved once, by reflection; false without Backhand or if its API changed.
     */
    public static boolean offhandBusy(EntityPlayer player) {
        if (!backhandLooked) {
            synchronized (RodModels.class) {
                if (!backhandLooked) {
                    try {
                        Class<?> utils = Class.forName("xonin.backhand.api.core.BackhandUtils");
                        backhandOffhand = java.lang.invoke.MethodHandles.publicLookup()
                            .findStatic(
                                utils,
                                "getOffhandItem",
                                java.lang.invoke.MethodType.methodType(ItemStack.class, EntityPlayer.class));
                    } catch (Throwable ignored) {
                        backhandOffhand = null; // no Backhand, or another API: only heldItemLeft counts
                    }
                    backhandLooked = true;
                }
            }
        }
        java.lang.invoke.MethodHandle h = backhandOffhand;
        if (h == null) {
            return false;
        }
        try {
            return (ItemStack) h.invoke(player) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    private static BedrockAnimations.Clip armClip(RodModelDef rod, RodModelDef.Band band, BedrockAnimations arm,
        String state, boolean line) {
        RodModelDef.Clips c = state == null ? null : rod.clips(state, band);
        return c == null || arm == null ? null : arm.clip(c.arm(line));
    }

    /** Whether a cast player keeps his 3D rod in third person (vanilla swaps it for a stick). */
    public boolean keepsRodWhileCast(EntityPlayer player) {
        RodModelIndex.Resolved r = resolve(player.getHeldItem());
        return r != null && r.model.drawsIn(RodModelDef.THIRD_PERSON);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Line tip

    /** {@code RenderPlayerEvent.Pre}: the view matrix the player (and his rod) is drawn with. */
    private final float[] viewMatrix = new float[16];
    private boolean viewKnown;

    /**
     * The camera's view matrix of the frame (column-major), read when the world is done (before the hand): entities
     * and the hand drawn in this frame use it (the entities, the one of the frame before). Null before the first frame.
     */
    public float[] viewMatrix() {
        return viewKnown ? viewMatrix : null;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (installed) {
            readMatrix(GL11.GL_MODELVIEW_MATRIX, viewMatrix);
            viewKnown = true;
        }
    }

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (resolve(event.entityPlayer.getHeldItem()) == null) {
            return;
        }
        readMatrix(GL11.GL_MODELVIEW_MATRIX, playerView);
        playerViewEntity = event.entityPlayer.getEntityId();
    }

    /**
     * Where the line's locator is, from the rod renderer: {@code eye} is the point in the current GL eye space. First
     * person keeps it in eye space (with the hand's projection); third person turns it into an offset from the player.
     */
    public void captureTip(EntityPlayer player, boolean firstPerson, float[] eye) {
        if (firstPerson) {
            float[] projection = new float[16];
            readMatrix(GL11.GL_PROJECTION_MATRIX, projection);
            firstPersonTips
                .put(player.getEntityId(), new Tip(eye[0], eye[1], eye[2], projection[0], projection[5], frame));
            return;
        }
        if (playerViewEntity != player.getEntityId()) {
            return;
        }
        float[] rel = RodMatrix.of(playerView)
            .inverse()
            .transform(eye[0], eye[1], eye[2]);
        double[] pos = interpolated(player, partialTicks);
        double ox = rel[0] + RenderManager.renderPosX - pos[0];
        double oy = rel[1] + RenderManager.renderPosY - pos[1];
        double oz = rel[2] + RenderManager.renderPosZ - pos[2];
        if (Double.isFinite(ox) && Double.isFinite(oy)
            && Double.isFinite(oz)
            && ox * ox + oy * oy + oz * oz <= MAX_TIP_DISTANCE * MAX_TIP_DISTANCE) {
            thirdPersonTips.put(player.getEntityId(), new Tip(ox, oy, oz, 0, 0, frame));
        }
    }

    /**
     * Draws the fishing line from the rod's tip, in place of vanilla's line ({@code RenderFish#doRender}, where it
     * starts drawing the line; texture and lighting are already off). False to let vanilla draw it.
     */
    public boolean drawLine(EntityFishHook hook, double x, double y, double z, float partial) {
        EntityPlayer angler = hook.field_146042_b;
        if (angler == null) {
            return false;
        }
        RodModelIndex.Resolved r = resolve(angler.getHeldItem());
        if (r == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        double[] start = mc.gameSettings.thirdPersonView == 0 && angler == mc.thePlayer ? firstPersonStart(angler)
            : thirdPersonStart(angler, partial);
        if (start == null) {
            return false;
        }
        double dx = start[0] - x;
        double dy = start[1] - y - 0.25;
        double dz = start[2] - z;
        int color = r.model.lineColor(r.stage);
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINE_STRIP);
        tess.setColorOpaque_I(color < 0 ? 0 : color);
        int segments = 16;
        for (int i = 0; i <= segments; ++i) {
            float f = (float) i / segments;
            tess.addVertex(x + dx * f, y + dy * (f * f + f) * 0.5 + 0.25, z + dz * f);
        }
        tess.draw();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        return true;
    }

    /** The tip relative to the camera ({@code RenderManager.renderPos}), from the hand's last frame. */
    private double[] firstPersonStart(EntityPlayer player) {
        Tip tip = firstPersonTips.get(player.getEntityId());
        if (tip == null || frame - tip.frame > TIP_FRAMES) {
            return null;
        }
        // The hand is drawn with its own field of view: the same point on screen, at the same depth, in the world's.
        float[] projection = new float[16];
        readMatrix(GL11.GL_PROJECTION_MATRIX, projection);
        float[] view = new float[16];
        readMatrix(GL11.GL_MODELVIEW_MATRIX, view);
        if (projection[0] == 0 || projection[5] == 0) {
            return null;
        }
        float ex = (float) tip.x * tip.projectionX / projection[0];
        float ey = (float) tip.y * tip.projectionY / projection[5];
        float[] rel = RodMatrix.of(view)
            .inverse()
            .transform(ex, ey, (float) tip.z);
        return finite(rel[0], rel[1], rel[2]);
    }

    private double[] thirdPersonStart(EntityPlayer player, float partial) {
        Tip tip = thirdPersonTips.get(player.getEntityId());
        if (tip == null || frame - tip.frame > TIP_FRAMES) {
            return null;
        }
        double[] pos = interpolated(player, partial);
        return finite(
            pos[0] + tip.x - RenderManager.renderPosX,
            pos[1] + tip.y - RenderManager.renderPosY,
            pos[2] + tip.z - RenderManager.renderPosZ);
    }

    private static double[] finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) ? new double[] { x, y, z } : null;
    }

    private static double[] interpolated(Entity e, float partial) {
        return new double[] { e.lastTickPosX + (e.posX - e.lastTickPosX) * partial,
            e.lastTickPosY + (e.posY - e.lastTickPosY) * partial,
            e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partial };
    }

    /** Reads a GL matrix (column-major) into {@code out}. */
    public void readMatrix(int which, float[] out) {
        matrix.clear();
        GL11.glGetFloat(which, matrix);
        matrix.get(out, 0, 16);
    }

    /** For {@code /kunftigclient rods}. */
    public String status() {
        return (disabled() ? "disabled (-Dkunftig.rodModels.disable=true)"
            : installed ? "GeckoLib ready" : "off (GeckoLib not installed)") + " | rods: "
            + index.models()
                .size()
            + " | manifest problems: "
            + index.errors()
                .size()
            + " | players tracked: "
            + timelines.size();
    }
}
