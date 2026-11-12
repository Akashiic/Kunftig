package com.akashic.kunftig.bukkit.display;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.display.Display;
import com.akashic.kunftig.api.bukkit.display.DisplayBox;
import com.akashic.kunftig.api.bukkit.display.KunftigDisplays;
import com.akashic.kunftig.bukkit.fishing.CraftBridge;
import com.akashic.kunftig.display.DeckShape;
import com.akashic.kunftig.display.DisplayTracker;
import com.akashic.kunftig.display.DisplayWire;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@link KunftigDisplays} on Thermos/Crucible: keeps the displays in a {@link DisplayTracker} and, at the end of every
 * server tick, sends each viewer what changed as raw plugin messages on {@link DisplayWire#CHANNEL}. Item stacks go
 * as their NBT (uncompressed, {@code CompressedStreamTools#write}), read back by the client. Loaded reflectively, only
 * where Bukkit exists.
 */
public final class BukkitDisplayBridge implements KunftigDisplays.Provider {

    private final DisplayTracker<EntityPlayerMP> tracker = new DisplayTracker<>();
    private Class<?> craftItemStack;
    private MethodHandle asCraftCopy;
    private Field handle;

    private BukkitDisplayBridge() {}

    /** Called reflectively by the mod at start-up. */
    public static void install() {
        if (Boolean.getBoolean("kunftig.displays.disable")) {
            Kunftig.LOG.warn("Kunftig displays disabled by -Dkunftig.displays.disable=true");
            return;
        }
        BukkitDisplayBridge bridge = new BukkitDisplayBridge();
        FMLCommonHandler.instance()
            .bus()
            .register(bridge);
        KunftigDisplays.install(bridge);
        Kunftig.LOG.info("Kunftig displays: Bukkit API enabled (KunftigDisplays)");
    }

    @Override
    public Display spawn(Location location, String model, String variant, String state, List<ItemStack> items,
        double viewRange) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("KunftigDisplays must be used on the main thread");
        }
        DisplayTracker.State<EntityPlayerMP> s = tracker.create(
            location.getWorld()
                .getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            model,
            variant,
            state,
            encode(items),
            viewRange);
        return new BukkitDisplay(s, items);
    }

    @Override
    public boolean drawsDisplays(Player player) {
        return player.getListeningPluginChannels()
            .contains(DisplayWire.CHANNEL);
    }

    @Override
    public boolean drawsExtendedDisplays(Player player) {
        return drawsDisplays(player) && player.getListeningPluginChannels()
            .contains(DisplayWire.CHANNEL_V2);
    }

    @Override
    public boolean walksOnDisplays(Player player) {
        return drawsExtendedDisplays(player) && player.getListeningPluginChannels()
            .contains(DisplayWire.CHANNEL_V3);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || tracker.size() == 0) {
            return;
        }
        List<DisplayTracker.Viewer<EntityPlayerMP>> viewers = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                net.minecraft.entity.Entity h = CraftBridge.handle(player);
                if (!(h instanceof EntityPlayerMP) || ((EntityPlayerMP) h).playerNetServerHandler == null) {
                    continue;
                }
                Location at = player.getLocation();
                viewers.add(
                    new DisplayTracker.Viewer<>(
                        (EntityPlayerMP) h,
                        world.getName(),
                        at.getX(),
                        at.getY(),
                        at.getZ(),
                        drawsDisplays(player)));
            }
        }
        for (DisplayTracker.Out<EntityPlayerMP> out : tracker.update(viewers)) {
            if (out.connection.playerNetServerHandler != null) {
                out.connection.playerNetServerHandler
                    .sendPacket(new S3FPacketCustomPayload(DisplayWire.CHANNEL, out.data));
            }
        }
    }

    // ---- item stacks as NBT -------------------------------------------------------------------------------------

    private synchronized void resolve() {
        if (craftItemStack != null) {
            return;
        }
        try {
            String pkg = Bukkit.getServer()
                .getClass()
                .getPackage()
                .getName();
            Class<?> type = Class.forName(pkg + ".inventory.CraftItemStack");
            Field f = type.getDeclaredField("handle");
            f.setAccessible(true);
            // A method handle, not getMethod (issue #3), as in ItemDataBridge.
            asCraftCopy = MethodHandles.publicLookup()
                .findStatic(type, "asCraftCopy", MethodType.methodType(type, ItemStack.class))
                .asType(MethodType.methodType(ItemStack.class, ItemStack.class));
            handle = f;
            craftItemStack = type;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Kunftig displays: CraftItemStack not found on this server", e);
        }
    }

    private List<byte[]> encode(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<byte[]> out = new ArrayList<>();
        for (ItemStack item : items) {
            out.add(encode(item));
        }
        return out;
    }

    private byte[] encode(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return new byte[0];
        }
        resolve();
        try {
            ItemStack craft = craftItemStack.isInstance(item) ? item : (ItemStack) asCraftCopy.invokeExact(item);
            net.minecraft.item.ItemStack nms = (net.minecraft.item.ItemStack) handle.get(craft);
            if (nms == null) {
                return new byte[0];
            }
            NBTTagCompound tag = new NBTTagCompound();
            nms.writeToNBT(tag);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                CompressedStreamTools.write(tag, out);
            }
            byte[] data = bytes.toByteArray();
            return data.length > DisplayWire.MAX_ITEM_BYTES ? new byte[0] : data;
        } catch (IOException | IllegalAccessException e) {
            Kunftig.LOG.warn("Kunftig displays: cannot send an item stack: {}", e.toString());
            return new byte[0];
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("CraftItemStack.asCraftCopy failed", e);
        }
    }

    /** The plugin's handle on one display. */
    private final class BukkitDisplay implements Display {

        private final DisplayTracker.State<EntityPlayerMP> state;
        private List<ItemStack> items;

        BukkitDisplay(DisplayTracker.State<EntityPlayerMP> state, List<ItemStack> items) {
            this.state = state;
            this.items = copy(items);
        }

        private List<ItemStack> copy(List<ItemStack> list) {
            List<ItemStack> out = new ArrayList<>();
            if (list != null) {
                for (ItemStack i : list) {
                    out.add(i == null ? null : i.clone());
                }
            }
            return out;
        }

        @Override
        public int getId() {
            return state.id();
        }

        @Override
        public Location getLocation() {
            double[] p = state.position();
            return new Location(Bukkit.getWorld(state.world()), p[0], p[1], p[2], (float) p[3], state.rotation()[1]);
        }

        @Override
        public String getModel() {
            return state.model();
        }

        @Override
        public String getVariant() {
            return state.variant();
        }

        @Override
        public String getState() {
            return state.state();
        }

        @Override
        public void setState(String newState) {
            if (newState == null || !KunftigDisplays.STATE_NAME.matcher(newState)
                .matches()) {
                throw new IllegalArgumentException("state must match " + KunftigDisplays.STATE_NAME);
            }
            state.setState(newState, tracker.tick());
        }

        @Override
        public List<ItemStack> getItems() {
            return copy(items);
        }

        @Override
        public void setItems(List<ItemStack> newItems) {
            if (newItems != null && newItems.size() > KunftigDisplays.MAX_ITEMS) {
                throw new IllegalArgumentException("at most " + KunftigDisplays.MAX_ITEMS + " items");
            }
            items = copy(newItems);
            state.setItems(encode(newItems));
        }

        @Override
        public void teleport(Location location) {
            if (location == null || location.getWorld() == null) {
                throw new IllegalArgumentException("location with a world required");
            }
            moveTo(location, 0);
        }

        @Override
        public void moveTo(Location location, int glideTicks) {
            if (location == null || location.getWorld() == null) {
                throw new IllegalArgumentException("location with a world required");
            }
            float[] r = state.rotation();
            state.moveTo(
                location.getWorld()
                    .getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                r[1],
                r[2],
                glideTicks);
        }

        @Override
        public void setScale(double x, double y, double z) {
            for (double v : new double[] { x, y, z }) {
                if (!(v >= DisplayWire.MIN_SCALE && v <= DisplayWire.MAX_SCALE)) {
                    throw new IllegalArgumentException(
                        "scale must be " + DisplayWire.MIN_SCALE + ".." + DisplayWire.MAX_SCALE + ": " + v);
                }
            }
            state.setScale((float) x, (float) y, (float) z);
        }

        @Override
        public void setScale(double scale) {
            setScale(scale, scale, scale);
        }

        @Override
        public double[] getScale() {
            float[] s = state.scale();
            return new double[] { s[0], s[1], s[2] };
        }

        @Override
        public void setBoneVisible(String bone, boolean visible) {
            if (bone == null || bone.isEmpty() || bone.length() > DisplayWire.MAX_NAME) {
                throw new IllegalArgumentException("bone name required (at most " + DisplayWire.MAX_NAME + " chars)");
            }
            state.setBone(bone, visible);
        }

        @Override
        public Map<String, Boolean> getBoneVisibility() {
            return state.bones();
        }

        @Override
        public void resetBones() {
            state.resetBones();
        }

        @Override
        public void setRotation(float yaw, float pitch, float roll) {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
                throw new IllegalArgumentException("rotation must be finite");
            }
            double[] p = state.position();
            double[] v = state.motion();
            state.moveTo(state.world(), p[0], p[1], p[2], yaw, pitch, roll, 1);
            if (v[0] != 0 || v[1] != 0 || v[2] != 0) {
                state.setMotion(v[0], v[1], v[2]); // turning does not stop a flight
            }
        }

        @Override
        public float getPitch() {
            return state.rotation()[1];
        }

        @Override
        public float getRoll() {
            return state.rotation()[2];
        }

        @Override
        public void setMotion(org.bukkit.util.Vector perTick) {
            if (perTick == null) {
                state.setMotion(0, 0, 0);
                return;
            }
            if (!Double.isFinite(perTick.getX()) || !Double.isFinite(perTick.getY())
                || !Double.isFinite(perTick.getZ())) {
                throw new IllegalArgumentException("motion must be finite");
            }
            state.setMotion(perTick.getX(), perTick.getY(), perTick.getZ());
        }

        @Override
        public org.bukkit.util.Vector getMotion() {
            double[] v = state.motion();
            return new org.bukkit.util.Vector(v[0], v[1], v[2]);
        }

        @Override
        public void setBoneRotation(String bone, float x, float y, float z, int glideTicks) {
            if (bone == null || bone.isEmpty() || bone.length() > DisplayWire.MAX_NAME) {
                throw new IllegalArgumentException("bone name required (at most " + DisplayWire.MAX_NAME + " chars)");
            }
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new IllegalArgumentException("rotation must be finite");
            }
            state.setPose(bone, x, y, z, glideTicks);
        }

        @Override
        public float[] getBoneRotation(String bone) {
            return state.pose(bone);
        }

        @Override
        public void setVariant(String variant) {
            if (variant != null && !variant.isEmpty()
                && !KunftigDisplays.STATE_NAME.matcher(variant)
                    .matches()) {
                throw new IllegalArgumentException("variant must match " + KunftigDisplays.STATE_NAME + ": " + variant);
            }
            state.setVariant(variant);
        }

        @Override
        public void setCollision(List<DisplayBox> boxes) {
            double[][] raw = new double[boxes == null ? 0 : boxes.size()][];
            for (int i = 0; i < raw.length; i++) {
                DisplayBox b = boxes.get(i);
                if (b == null) {
                    throw new IllegalArgumentException("null box");
                }
                raw[i] = new double[] { b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ };
            }
            state.setDeck(DeckShape.pack(raw));
        }

        @Override
        public List<DisplayBox> getCollision() {
            short[] p = state.deck();
            List<DisplayBox> out = new ArrayList<>(p.length / 6);
            for (int i = 0; i < p.length; i += 6) {
                out.add(
                    new DisplayBox(
                        p[i] / DeckShape.UNITS,
                        p[i + 1] / DeckShape.UNITS,
                        p[i + 2] / DeckShape.UNITS,
                        p[i + 3] / DeckShape.UNITS,
                        p[i + 4] / DeckShape.UNITS,
                        p[i + 5] / DeckShape.UNITS));
            }
            return out;
        }

        @Override
        public double getViewRange() {
            return state.range();
        }

        @Override
        public void remove() {
            state.remove();
        }

        @Override
        public boolean isRemoved() {
            return state.isRemoved();
        }
    }
}
