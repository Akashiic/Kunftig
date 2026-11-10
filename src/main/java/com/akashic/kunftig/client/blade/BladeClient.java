package com.akashic.kunftig.client.blade;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.IOUtils;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.blade.BladeIndex;
import com.akashic.kunftig.blade.BladeTimeline;
import com.akashic.kunftig.blade.BladeWire;
import com.akashic.kunftig.blade.BodyPose;
import com.akashic.kunftig.blade.ObjModel;
import com.akashic.kunftig.blade.Vmd;
import com.akashic.kunftig.client.itemmodel.ItemModels;
import com.akashic.kunftig.itemmodel.ItemModelIndex;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client side of the SlashBlade-style blades: the packs' {@code kunftig/blades.json}, their OBJ models and VMD
 * motions (read once, cached), each player's state from the server ({@link BladeWire}), and the clocks the renderer
 * samples. Client thread only. Disabled with {@code -Dkunftig.blades.disable=true}.
 */
public final class BladeClient implements IResourceManagerReloadListener {

    public static final BladeClient INSTANCE = new BladeClient();
    private static final int MAX_LOGGED_ERRORS = 20;

    /** SlashBlade's standby: frames 0 to 1, looping. */
    public static final BladeTimeline.Segment STANDBY = new BladeTimeline.Segment(
        0,
        1,
        true,
        BladeTimeline.Segment.duration(0, 1, 1, 1000));
    private static final BladeTimeline IDLE = new BladeTimeline(
        Collections.<BladeTimeline.Segment>emptyList(),
        STANDBY);

    /** One player's blade as the server last described it. */
    static final class State {

        BladeTimeline timeline = IDLE;
        long chainStart;
        BladeWire.Body body;
        long bodyStart;
        boolean charged;
        /** Changes with every chain the server (or the harness) starts. */
        int serial;
    }

    private volatile BladeIndex index = new BladeIndex();
    private final Map<String, ObjModel> models = new HashMap<>();
    private final Map<String, Vmd> motions = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private final Map<Integer, State> states = new HashMap<>();
    private final Map<String, ResourceLocation> locations = new HashMap<>();
    private long clock;
    private float partialTicks;
    private int loggedErrors;

    private BladeClient() {}

    public static boolean disabled() {
        return Boolean.getBoolean("kunftig.blades.disable");
    }

    public BladeIndex index() {
        return index;
    }

    public boolean active() {
        return !index.isEmpty();
    }

    public long clock() {
        return clock;
    }

    public float partialTicks() {
        return partialTicks;
    }

    // ---- packs -----------------------------------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public void onResourceManagerReload(IResourceManager manager) {
        BladeIndex read = new BladeIndex();
        for (String namespace : (Iterable<String>) manager.getResourceDomains()) {
            List<IResource> files;
            try {
                files = manager.getAllResources(new ResourceLocation(namespace, BladeIndex.PATH));
            } catch (IOException e) {
                continue;
            }
            for (IResource file : files) {
                try (InputStream in = file.getInputStream()) {
                    read.add(
                        namespace,
                        IOUtils.toString(in, StandardCharsets.UTF_8),
                        namespace + ":" + BladeIndex.PATH);
                } catch (IOException | RuntimeException e) {
                    Kunftig.LOG.warn("Kunftig blades: could not read {}:{}", namespace, BladeIndex.PATH, e);
                }
            }
        }
        for (String e : read.errors()) {
            log("Kunftig blades: {}", e);
        }
        synchronized (models) {
            models.clear();
            motions.clear();
            failed.clear();
        }
        index = read;
        if (renderers) {
            BladeItemRenderer.installOn(read.items());
        }
        if (!read.isEmpty()) {
            Kunftig.LOG.info(
                "Kunftig blades: {} blade(s) read",
                read.blades()
                    .size());
        }
    }

    private volatile boolean renderers;

    /** After the other mods' init: the item renderer goes on the blades' items, now and after every reload. */
    public void installRenderers() {
        renderers = true;
        BladeItemRenderer.installOn(index.items());
    }

    public ResourceLocation location(String id) {
        ResourceLocation r = locations.get(id);
        if (r == null) {
            r = new ResourceLocation(id);
            locations.put(id, r);
        }
        return r;
    }

    /** A blade's OBJ (read on first use), or null when it cannot be read. */
    public ObjModel model(String id) {
        synchronized (models) {
            ObjModel m = models.get(id);
            if (m != null || failed.contains(id)) {
                return m;
            }
            try (InputStream in = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location(id))
                .getInputStream()) {
                m = ObjModel.parse(IOUtils.toString(in, StandardCharsets.UTF_8), id);
                models.put(id, m);
            } catch (IOException | ObjModel.FormatException | RuntimeException e) {
                failed.add(id);
                log("Kunftig blades: cannot read model {}: {}", id, e.toString());
            }
            return m;
        }
    }

    /** A VMD motion (read on first use), or null. */
    public Vmd motion(String id) {
        if (id == null) {
            return null;
        }
        synchronized (models) {
            Vmd v = motions.get(id);
            if (v != null || failed.contains(id)) {
                return v;
            }
            try (InputStream in = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location(id))
                .getInputStream()) {
                v = Vmd.parse(IOUtils.toByteArray(in), id);
                motions.put(id, v);
            } catch (IOException | Vmd.FormatException | RuntimeException e) {
                failed.add(id);
                log("Kunftig blades: cannot read motion {}: {}", id, e.toString());
            }
            return v;
        }
    }

    private void log(String format, Object... args) {
        if (loggedErrors++ < MAX_LOGGED_ERRORS) {
            Kunftig.LOG.warn(format, args);
        }
    }

    // ---- stacks ----------------------------------------------------------------------------------------------------

    /** The blade of a stack, or null. */
    public BladeIndex.Blade resolve(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null || index.isEmpty()) {
            return null;
        }
        return index.get(ItemModels.modelOf(stack.stackTagCompound));
    }

    /** Whether the stack's data marks the blade broken. */
    public static boolean broken(ItemStack stack, BladeIndex.Blade blade) {
        if (blade.brokenKey == null || stack == null
            || stack.stackTagCompound == null
            || !stack.stackTagCompound.hasKey(ItemModelIndex.NBT_ROOT, 10)) {
            return false;
        }
        NBTTagCompound values = stack.stackTagCompound.getCompoundTag(ItemModelIndex.NBT_ROOT);
        return values.hasKey(blade.brokenKey, 99) && values.getDouble(blade.brokenKey) > 0;
    }

    // ---- clocks and states -----------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            states.clear();
            return;
        }
        if (!mc.isGamePaused()) {
            clock++;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            partialTicks = event.renderTickTime;
        }
    }

    private State state(Entity e) {
        return states.get(e.getEntityId());
    }

    /** The blade motion frame for an entity now (standby without a state). */
    public double bladeFrame(Entity e, Vmd motion, float partial) {
        State s = state(e);
        BladeTimeline t = s == null ? IDLE : s.timeline;
        long start = s == null ? 0 : s.chainStart;
        double ms = (clock - start + partial) * 50.0;
        return t.frame(ms, motion.maxFrame());
    }

    /**
     * Whether the entity's blade is out: a state the server sent is still playing (a combo, a draw); false once its
     * chain is over and the idle motion (SlashBlade's standby, the blade in its sheath) plays, or without a state.
     */
    public boolean drawn(Entity e, float partial) {
        State s = state(e);
        if (s == null || s.timeline.chain()
            .isEmpty()) {
            return false;
        }
        double ms = (clock - s.chainStart + partial) * 50.0;
        return s.timeline.segment(ms) != s.timeline.idle();
    }

    /**
     * The SlashBlade combo state the entity's chain started with while the chain plays ({@code combo_a1}...), named
     * by its first frame ({@link BladeIndex#comboName}); null when no chain plays or its first frame has no name.
     */
    public String comboName(Entity e, BladeIndex.Blade blade, float partial) {
        State s = state(e);
        if (s == null || !drawn(e, partial)) {
            return null;
        }
        return index.comboName(
            blade,
            s.timeline.chain()
                .get(0).start,
            e.onGround);
    }

    /** Changes with every chain started for the entity (0 without a state): a combo started again is new. */
    public int chainSerial(Entity e) {
        State s = state(e);
        return s == null ? 0 : s.serial;
    }

    public boolean charged(Entity e) {
        State s = state(e);
        return s != null && s.charged;
    }

    /** The body clip playing for an entity, or null (vanilla body). */
    public BladeWire.Body body(Entity e) {
        State s = state(e);
        if (s == null || s.body == null) {
            return null;
        }
        if (clock - s.bodyStart >= s.body.spanTicks()) {
            s.body = null; // over: SlashBlade snaps back to vanilla
            return null;
        }
        return s.body;
    }

    /** The body pose of an entity's clip now, or null. */
    public BodyPose bodyPose(Entity e, Vmd motion, float partial) {
        BladeWire.Body b = body(e);
        if (b == null || motion == null) {
            return null;
        }
        State s = state(e);
        double ms = Math.min(motion.maxFrame() * Vmd.MS_PER_FRAME, (clock - s.bodyStart + partial) * 50.0)
            + b.start * Vmd.MS_PER_FRAME;
        return BodyPose.at(motion, ms / Vmd.MS_PER_FRAME);
    }

    // ---- network ---------------------------------------------------------------------------------------------------

    public void onJoinGame(NetHandlerPlayClient handler) {
        states.clear();
        handler
            .addToSendQueue(new C17PacketCustomPayload("REGISTER", BladeWire.CHANNEL.getBytes(StandardCharsets.UTF_8)));
    }

    public void onRespawn() {
        states.clear();
    }

    public void onPayload(byte[] data) {
        BladeWire.Message m = BladeWire.read(data);
        if (m == null) {
            return;
        }
        State s = states.computeIfAbsent(m.entityId, k -> new State());
        if (m.op == BladeWire.CHARGE) {
            s.charged = m.charged;
            return;
        }
        s.timeline = new BladeTimeline(m.chain, m.idle);
        s.serial++;
        s.chainStart = clock - m.elapsedTicks;
        s.body = m.body;
        s.bodyStart = clock - m.bodyElapsedTicks;
        s.charged = m.charged;
    }

    /** Debug and harness: plays a chain on an entity as if the server had sent it now. */
    public void play(Entity e, List<BladeTimeline.Segment> chain, BladeWire.Body body) {
        State s = states.computeIfAbsent(e.getEntityId(), k -> new State());
        s.timeline = new BladeTimeline(chain, STANDBY);
        s.serial++;
        s.chainStart = clock;
        s.body = body;
        s.bodyStart = clock;
    }
}
