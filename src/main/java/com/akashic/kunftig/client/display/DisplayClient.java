package com.akashic.kunftig.client.display;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.apache.commons.io.IOUtils;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.display.DeckShape;
import com.akashic.kunftig.display.DisplayModelIndex;
import com.akashic.kunftig.display.DisplayMotion;
import com.akashic.kunftig.display.DisplayWire;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client side of the displays ({@code KunftigDisplays}, API 8): registers {@link DisplayWire#CHANNEL} on join, keeps
 * what the server sent, reads the packs' {@code kunftig/displays.json} on every resource reload and, at the end of the
 * world render, hands each display in range to the {@link Renderer} (GeckoLib's, installed only when GeckoLib is
 * loaded; without it nothing is drawn and nothing else happens). Every connection, respawn or dimension change starts
 * empty: the server sends them again. Client thread only.
 * <p>
 * Disabled with {@code -Dkunftig.displays.disable=true}.
 */
public final class DisplayClient implements IResourceManagerReloadListener {

    public static final DisplayClient INSTANCE = new DisplayClient();
    private static final int MAX_LOGGED_ERRORS = 20;
    private static final long NBT_LIMIT = 2_097_152L;

    /** One display as the server described it. */
    public static final class Entry {

        public final int id;
        public final String model;
        /** The texture variant; the server may change it (API 12). */
        public volatile String variant;
        /** Where it is, tick by tick, and how it moves (API 9: glides and flights). */
        public final DisplayMotion motion = new DisplayMotion();
        public volatile String state;
        /** The client tick the state started at (the server's elapsed time already counted). */
        public volatile long start;
        public volatile List<ItemStack> items;
        /** API 9: the scale the server set ({x, y, z}), on top of the manifest's. */
        public volatile float[] scale = { 1, 1, 1 };
        /** API 9: the bones whose visibility the server set. */
        public volatile Map<String, Boolean> bones = Collections.emptyMap();
        /** API 13: the bones the server turned. */
        public final Map<String, BoneTurn> turns = new ConcurrentHashMap<>();
        /** API 12: the boxes players walk on (empty: none). */
        public volatile DeckShape deck = DeckShape.EMPTY;

        Entry(int id, String model, String variant) {
            this.id = id;
            this.model = model;
            this.variant = variant;
        }

        /** Whether a bone is drawn: what the server set, else hidden only if the manifest starts it hidden. */
        public boolean boneVisible(String bone, DisplayModelIndex.Model model) {
            Boolean set = bones.get(bone);
            return set != null ? set : !model.hiddenBones.contains(bone);
        }
    }

    /** A bone turning to where the server put it (API 13): degrees added to its rotation, glided tick by tick. */
    public static final class BoneTurn {

        private final float[] now = new float[3];
        private final float[] last = new float[3];
        private final float[] to = new float[3];
        private int glide;

        void set(float[] target, int ticks) {
            System.arraycopy(target, 0, to, 0, 3);
            glide = ticks;
            if (ticks <= 0) {
                System.arraycopy(target, 0, now, 0, 3);
                System.arraycopy(target, 0, last, 0, 3);
            }
        }

        void tick() {
            System.arraycopy(now, 0, last, 0, 3);
            if (glide > 0) {
                for (int i = 0; i < 3; i++) {
                    now[i] += (to[i] - now[i]) / glide;
                }
                glide--;
            }
        }

        /** Degrees on axis 0 (X), 1 (Y) or 2 (Z) at this frame. */
        public float get(int axis, float partial) {
            return last[axis] + (now[axis] - last[axis]) * partial;
        }
    }

    /** Draws one display (GeckoLib's, see {@code client.display.gecko}). */
    public interface Renderer {

        void render(Entry entry, DisplayModelIndex.Model model, double dx, double dy, double dz, float partialTicks);

        /** The packs were reloaded: drop whatever was built from the old ones. */
        void reset();
    }

    private final Map<Integer, Entry> displays = new ConcurrentHashMap<>();
    private volatile DisplayModelIndex index = new DisplayModelIndex();
    private volatile Renderer renderer;
    private long clock;
    private float partialTicks;
    private int loggedErrors;

    private DisplayClient() {}

    public static boolean disabled() {
        return Boolean.getBoolean("kunftig.displays.disable");
    }

    /** Called by GeckoLib's side when it is installed. */
    public void install(Renderer r) {
        renderer = r;
    }

    public boolean installed() {
        return renderer != null;
    }

    /** The displays this client knows now (a live view). */
    public java.util.Collection<Entry> entries() {
        return displays.values();
    }

    public Entry get(int id) {
        return displays.get(id);
    }

    /** Displays this client knows now. */
    public int count() {
        return displays.size();
    }

    public DisplayModelIndex index() {
        return index;
    }

    /** Client ticks since the start (the displays' animation clock). */
    public long clock() {
        return clock;
    }

    public float partialTicks() {
        return partialTicks;
    }

    // ---- network ------------------------------------------------------------------------------------------------

    /**
     * At the end of {@code handleJoinGame}: a fresh connection knows no display and asks for them. Only a client that
     * can draw them registers the channel (without GeckoLib the server draws the plugin's fallback instead).
     */
    public void onJoinGame(NetHandlerPlayClient handler) {
        displays.clear();
        DeckClient.INSTANCE.reset();
        if (renderer == null) {
            return;
        }
        handler.addToSendQueue(
            new C17PacketCustomPayload(
                "REGISTER",
                // the second channel only says this client draws scale, hidden bones and blends (API 9); the third,
                // that its player walks on displays (API 12)
                (DisplayWire.CHANNEL + "\0"
                    + DisplayWire.CHANNEL_V2
                    + (DeckClient.disabled() ? "" : "\0" + DisplayWire.CHANNEL_V3)).getBytes(StandardCharsets.UTF_8)));
    }

    /** Respawn or dimension change: the server shows them again where they apply. */
    public void clear() {
        displays.clear();
        DeckClient.INSTANCE.reset();
    }

    /** A message on the display channel. */
    public void onPayload(byte[] data) {
        DisplayWire.Message m = DisplayWire.read(data);
        if (m == null) {
            return;
        }
        switch (m.op) {
            case DisplayWire.SHOW: {
                Entry e = new Entry(m.id, m.model, m.variant);
                e.motion.jump(m.x, m.y, m.z, m.yaw, m.pitch, m.roll);
                if (m.vx != 0 || m.vy != 0 || m.vz != 0) {
                    e.motion.fly(m.x, m.y, m.z, m.vx, m.vy, m.vz);
                }
                e.state = m.state;
                e.start = clock - m.elapsed;
                e.items = decode(m.items);
                e.scale = m.scale;
                e.bones = m.bones;
                displays.put(m.id, e);
                break;
            }
            case DisplayWire.STATE: {
                Entry e = displays.get(m.id);
                if (e != null) {
                    e.state = m.state;
                    e.start = clock - m.elapsed;
                }
                break;
            }
            case DisplayWire.ITEMS: {
                Entry e = displays.get(m.id);
                if (e != null) {
                    e.items = decode(m.items);
                }
                break;
            }
            case DisplayWire.MOVE: {
                Entry e = displays.get(m.id);
                if (e != null) {
                    e.motion.glide(m.x, m.y, m.z, m.yaw, m.pitch, m.roll, m.ticks);
                }
                break;
            }
            case DisplayWire.MOTION: {
                Entry e = displays.get(m.id);
                if (e != null) {
                    e.motion.fly(m.x, m.y, m.z, m.vx, m.vy, m.vz);
                }
                break;
            }
            case DisplayWire.SCALE: {
                Entry e = displays.get(m.id);
                if (e != null) {
                    e.scale = m.scale;
                }
                break;
            }
            case DisplayWire.BONES: {
                Entry e = displays.get(m.id);
                if (e != null) {
                    e.bones = m.bones;
                }
                break;
            }
            case DisplayWire.POSE: {
                Entry e = displays.get(m.id);
                if (e != null && (e.turns.size() < DisplayWire.MAX_POSES || e.turns.containsKey(m.bone))) {
                    BoneTurn t = e.turns.get(m.bone);
                    if (t == null) {
                        t = new BoneTurn();
                        e.turns.put(m.bone, t);
                    }
                    t.set(m.turn, m.ticks);
                }
                break;
            }
            case DisplayWire.VARIANT: {
                Entry e = displays.get(m.id);
                if (e != null) {
                    e.variant = m.variant;
                }
                break;
            }
            case DisplayWire.DECK: {
                Entry e = displays.get(m.id);
                if (e != null) {
                    e.deck = m.deck;
                }
                break;
            }
            case DisplayWire.HIDE:
                displays.remove(m.id);
                break;
            default:
                break;
        }
    }

    /** The stacks from their NBT (an empty or unreadable entry draws nothing in its bone). */
    private List<ItemStack> decode(List<byte[]> items) {
        List<ItemStack> out = new ArrayList<>(items.size());
        for (byte[] data : items) {
            ItemStack stack = null;
            if (data.length > 0) {
                try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
                    NBTTagCompound tag = CompressedStreamTools.func_152456_a(in, new NBTSizeTracker(NBT_LIMIT));
                    stack = ItemStack.loadItemStackFromNBT(tag);
                } catch (IOException | RuntimeException e) {
                    if (loggedErrors++ < MAX_LOGGED_ERRORS) {
                        Kunftig.LOG.warn("Kunftig displays: unreadable item stack: {}", e.toString());
                    }
                }
            }
            out.add(stack);
        }
        return Collections.unmodifiableList(out);
    }

    // ---- packs --------------------------------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public void onResourceManagerReload(IResourceManager manager) {
        DisplayModelIndex read = new DisplayModelIndex();
        for (String namespace : (Iterable<String>) manager.getResourceDomains()) {
            List<IResource> files;
            try {
                files = manager.getAllResources(new ResourceLocation(namespace, DisplayModelIndex.PATH));
            } catch (IOException e) {
                continue; // this namespace declares no displays
            }
            for (IResource file : files) {
                String source = namespace + ":" + DisplayModelIndex.PATH;
                try (InputStream in = file.getInputStream()) {
                    read.add(namespace, IOUtils.toString(in, StandardCharsets.UTF_8), source);
                } catch (IOException | RuntimeException e) {
                    Kunftig.LOG.warn("Kunftig displays: could not read {}", source, e);
                }
            }
        }
        int logged = 0;
        for (String e : read.errors()) {
            if (logged++ < MAX_LOGGED_ERRORS) {
                Kunftig.LOG.warn("Kunftig displays: {}", e);
            }
        }
        index = read;
        Renderer r = renderer;
        if (r != null) {
            r.reset();
        }
        if (!read.models()
            .isEmpty()) {
            Kunftig.LOG.info(
                "Kunftig displays: {} display(s) read{}",
                read.models()
                    .size(),
                r != null ? "" : " (no renderer yet: GeckoLib missing, disabled, or not installed so far)");
        }
    }

    // ---- ticking and drawing ------------------------------------------------------------------------------------

    /**
     * At the start of the client tick, before the world's: a player standing on a display (API 12) is moved, at the
     * end of its own tick, to where the display is now, so both are drawn together between this tick and the next.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !Minecraft.getMinecraft()
            .isGamePaused()) {
            clock++;
            for (Entry e : displays.values()) {
                e.motion.tick();
                for (BoneTurn t : e.turns.values()) {
                    t.tick();
                }
            }
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            partialTicks = event.renderTickTime;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Renderer r = renderer;
        Minecraft mc = Minecraft.getMinecraft();
        if (r == null || displays.isEmpty() || mc.theWorld == null || mc.renderViewEntity == null) {
            return;
        }
        EntityLivingBase view = mc.renderViewEntity;
        float t = event.partialTicks;
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * t;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * t;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * t;
        DisplayModelIndex idx = index;
        long began = System.nanoTime();
        mc.mcProfiler.startSection("kunftigDisplays"); // shows in the debug pie (F3 + Shift) under "root.render"
        try {
            drawAll(r, idx, cx, cy, cz, t);
        } finally {
            mc.mcProfiler.endSection();
            drawNanos += System.nanoTime() - began;
            drawFrames++;
        }
    }

    /** What drawing the displays has cost since the last {@link #resetDrawCost}: {nanoseconds, frames}. */
    public long[] drawCost() {
        return new long[] { drawNanos, drawFrames };
    }

    public void resetDrawCost() {
        drawNanos = 0;
        drawFrames = 0;
    }

    private long drawNanos;
    private long drawFrames;

    private void drawAll(Renderer r, DisplayModelIndex idx, double cx, double cy, double cz, float t) {
        for (Entry e : displays.values()) {
            DisplayModelIndex.Model model = idx.models()
                .get(e.model);
            if (model == null) {
                continue; // the pack does not declare it
            }
            double dx = e.motion.x(t) - cx;
            double dy = e.motion.y(t) - cy;
            double dz = e.motion.z(t) - cz;
            if (dx * dx + dy * dy + dz * dz > model.maxDistance * model.maxDistance) {
                continue;
            }
            try {
                r.render(e, model, dx, dy, dz, t);
            } catch (RuntimeException | LinkageError ex) {
                if (loggedErrors++ < MAX_LOGGED_ERRORS) {
                    Kunftig.LOG.error("Kunftig displays: cannot draw {}", e.model, ex);
                }
            }
        }
    }
}
