package com.akashic.kunftig.bukkit.fishing;

import java.lang.reflect.Method;

/**
 * Moves between Minecraft entities and Bukkit entities on hybrid servers without compiling against CraftBukkit:
 * {@code Entity#getBukkitEntity()} is added to Minecraft's class by Cauldron (Thermos, Crucible), and every
 * {@code CraftEntity} has {@code getHandle()}. Methods are looked up once.
 */
final class CraftBridge {

    private static volatile Method getBukkitEntity;

    private CraftBridge() {}

    static Object bukkitEntity(net.minecraft.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        try {
            Method method = getBukkitEntity;
            if (method == null) {
                method = net.minecraft.entity.Entity.class.getMethod("getBukkitEntity");
                getBukkitEntity = method;
            }
            return method.invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Not a Bukkit hybrid server (no Entity#getBukkitEntity)", e);
        }
    }

    static net.minecraft.entity.Entity handle(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        try {
            Object handle = entity.getClass()
                .getMethod("getHandle")
                .invoke(entity);
            if (!(handle instanceof net.minecraft.entity.Entity)) {
                throw new IllegalArgumentException("Not a server entity: " + entity);
            }
            return (net.minecraft.entity.Entity) handle;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Not a CraftBukkit entity: " + entity, e);
        }
    }
}
