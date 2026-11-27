package com.akashic.kunftig.modern.entity;

import java.util.function.Supplier;

import net.minecraft.entity.Entity;

import com.akashic.kunftig.modern.codec.Codec;

/**
 * Typed data on an entity, as NeoForge's data attachments: kept in {@link EntityAttachments}, saved with the entity
 * when it has a codec, optionally copied to the new player after death.
 */
public final class AttachmentType<T> {

    private final String id;
    private final Supplier<T> defaultValue;
    private final Codec<T> codec;
    private final boolean copyOnDeath;

    private AttachmentType(String id, Supplier<T> defaultValue, Codec<T> codec, boolean copyOnDeath) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.codec = codec;
        this.copyOnDeath = copyOnDeath;
    }

    /** @param id unique key, by convention {@code modid:name} */
    public static <T> Builder<T> builder(String id, Supplier<T> defaultValue) {
        return new Builder<>(id, defaultValue);
    }

    public String id() {
        return id;
    }

    public T createDefault() {
        return defaultValue.get();
    }

    /** {@code null} for data that is not saved. */
    public Codec<T> codec() {
        return codec;
    }

    public boolean copyOnDeath() {
        return copyOnDeath;
    }

    /** {@code entity.getData(type)}. */
    public T get(Entity entity) {
        return EntityAttachments.of(entity)
            .get(this);
    }

    /** {@code entity.setData(type, value)}. */
    public void set(Entity entity, T value) {
        EntityAttachments.of(entity)
            .set(this, value);
    }

    @Override
    public String toString() {
        return id;
    }

    public static final class Builder<T> {

        private final String id;
        private final Supplier<T> defaultValue;
        private Codec<T> codec;
        private boolean copyOnDeath;

        private Builder(String id, Supplier<T> defaultValue) {
            this.id = id;
            this.defaultValue = defaultValue;
        }

        public Builder<T> serialize(Codec<T> codec) {
            this.codec = codec;
            return this;
        }

        public Builder<T> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }

        /** Builds and registers the type. Register during pre-init. */
        public AttachmentType<T> build() {
            return EntityAttachments.register(new AttachmentType<>(id, defaultValue, codec, copyOnDeath));
        }
    }
}
