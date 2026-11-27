package com.akashic.kunftig.modern.entity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * All {@link AttachmentType} values of one entity, stored as one {@link IExtendedEntityProperties} ({@code kunftig}).
 * Players get it when constructed, so their saved data is read with them; other entities get it on first use.
 * Saved data of a type that is not registered (a mod was removed) is kept as it is.
 */
public final class EntityAttachments implements IExtendedEntityProperties {

    public static final String KEY = "kunftig_attachments";

    private static final Logger LOG = LogManager.getLogger("Kunftig");
    private static final Map<String, AttachmentType<?>> TYPES = new LinkedHashMap<>();

    private final Map<AttachmentType<?>, Object> values = new HashMap<>();
    private NBTTagCompound pending = new NBTTagCompound();

    static synchronized <T> AttachmentType<T> register(AttachmentType<T> type) {
        if (TYPES.containsKey(type.id())) {
            throw new IllegalStateException("Attachment " + type.id() + " is already registered");
        }
        TYPES.put(type.id(), type);
        return type;
    }

    public static EntityAttachments of(Entity entity) {
        EntityAttachments data = (EntityAttachments) entity.getExtendedProperties(KEY);
        if (data == null) {
            data = new EntityAttachments();
            entity.registerExtendedProperties(KEY, data);
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(AttachmentType<T> type) {
        Object value = values.get(type);
        if (value == null) {
            value = load(type);
            values.put(type, value);
        }
        return (T) value;
    }

    public <T> void set(AttachmentType<T> type, T value) {
        values.put(type, value);
    }

    public boolean has(AttachmentType<?> type) {
        return values.containsKey(type) || pending.hasKey(type.id());
    }

    private <T> T load(AttachmentType<T> type) {
        if (type.codec() != null && pending.hasKey(type.id())) {
            Optional<T> parsed = type.codec()
                .parse(pending.getTag(type.id()));
            pending.removeTag(type.id());
            if (parsed.isPresent()) {
                return parsed.get();
            }
            LOG.warn("Discarding malformed attachment data {}", type.id());
        }
        return type.createDefault();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void saveNBTData(NBTTagCompound compound) {
        NBTTagCompound tag = (NBTTagCompound) pending.copy();
        for (Map.Entry<AttachmentType<?>, Object> entry : values.entrySet()) {
            AttachmentType type = entry.getKey();
            if (type.codec() != null) {
                tag.setTag(
                    type.id(),
                    type.codec()
                        .encode(entry.getValue()));
            }
        }
        compound.setTag(KEY, tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound compound) {
        values.clear();
        pending = compound.getCompoundTag(KEY);
    }

    @Override
    public void init(Entity entity, World world) {}

    private void copyFrom(EntityAttachments old, boolean death) {
        NBTTagCompound tag = new NBTTagCompound();
        old.saveNBTData(tag);
        NBTTagCompound saved = tag.getCompoundTag(KEY);
        if (death) {
            for (AttachmentType<?> type : TYPES.values()) {
                if (!type.copyOnDeath()) {
                    saved.removeTag(type.id());
                }
            }
        }
        values.clear();
        pending = saved;
        // Unsaved values survive a dimension change as live objects.
        if (!death) {
            for (Map.Entry<AttachmentType<?>, Object> entry : old.values.entrySet()) {
                if (entry.getKey()
                    .codec() == null) {
                    values.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /** Registered on the Forge bus by Kunftig. */
    public static final class Events {

        @SubscribeEvent
        public void onEntityConstructing(EntityEvent.EntityConstructing event) {
            if (event.entity instanceof EntityPlayer && event.entity.getExtendedProperties(KEY) == null) {
                event.entity.registerExtendedProperties(KEY, new EntityAttachments());
            }
        }

        @SubscribeEvent
        public void onPlayerClone(PlayerEvent.Clone event) {
            of(event.entityPlayer).copyFrom(of(event.original), event.wasDeath);
        }
    }
}
