package com.akashic.kunftig.api.bukkit.persistence;

/**
 * Bukkit 1.14+'s {@code PersistentDataType}: how a complex value {@code C} is stored as a primitive {@code P}.
 * Custom types (UUID, enums, records) implement this on top of the primitive types below.
 * <p>
 * The primitive types are the ones 1.7.10's NBT can hold. {@code LONG_ARRAY} does not exist here: NBT long arrays
 * were added in Minecraft 1.12 (store two {@code LONG}s, or a {@code BYTE_ARRAY}, instead).
 */
public interface PersistentDataType<P, C> {

    PersistentDataType<Byte, Byte> BYTE = new Primitive<Byte>(Byte.class);
    PersistentDataType<Short, Short> SHORT = new Primitive<Short>(Short.class);
    PersistentDataType<Integer, Integer> INTEGER = new Primitive<Integer>(Integer.class);
    PersistentDataType<Long, Long> LONG = new Primitive<Long>(Long.class);
    PersistentDataType<Float, Float> FLOAT = new Primitive<Float>(Float.class);
    PersistentDataType<Double, Double> DOUBLE = new Primitive<Double>(Double.class);
    PersistentDataType<String, String> STRING = new Primitive<String>(String.class);
    PersistentDataType<byte[], byte[]> BYTE_ARRAY = new Primitive<byte[]>(byte[].class);
    PersistentDataType<int[], int[]> INTEGER_ARRAY = new Primitive<int[]>(int[].class);
    PersistentDataType<PersistentDataContainer, PersistentDataContainer> TAG_CONTAINER = new Primitive<PersistentDataContainer>(
        PersistentDataContainer.class);
    PersistentDataType<PersistentDataContainer[], PersistentDataContainer[]> TAG_CONTAINER_ARRAY = new Primitive<PersistentDataContainer[]>(
        PersistentDataContainer[].class);

    /** Stored as a byte (1 or 0), as Bukkit 1.19.3+ does. */
    PersistentDataType<Byte, Boolean> BOOLEAN = new PersistentDataType<Byte, Boolean>() {

        @Override
        public Class<Byte> getPrimitiveType() {
            return Byte.class;
        }

        @Override
        public Class<Boolean> getComplexType() {
            return Boolean.class;
        }

        @Override
        public Byte toPrimitive(Boolean complex, PersistentDataAdapterContext context) {
            return (byte) (complex ? 1 : 0);
        }

        @Override
        public Boolean fromPrimitive(Byte primitive, PersistentDataAdapterContext context) {
            return primitive != 0;
        }
    };

    Class<P> getPrimitiveType();

    Class<C> getComplexType();

    P toPrimitive(C complex, PersistentDataAdapterContext context);

    C fromPrimitive(P primitive, PersistentDataAdapterContext context);

    /** A type stored as itself. */
    final class Primitive<T> implements PersistentDataType<T, T> {

        private final Class<T> type;

        Primitive(Class<T> type) {
            this.type = type;
        }

        @Override
        public Class<T> getPrimitiveType() {
            return type;
        }

        @Override
        public Class<T> getComplexType() {
            return type;
        }

        @Override
        public T toPrimitive(T complex, PersistentDataAdapterContext context) {
            return complex;
        }

        @Override
        public T fromPrimitive(T primitive, PersistentDataAdapterContext context) {
            return primitive;
        }
    }
}
