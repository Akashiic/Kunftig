package com.akashic.kunftig.bukkit.fishing;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.bukkit.Bukkit;

/**
 * Moves between Minecraft entities and Bukkit entities on hybrid servers without compiling against CraftBukkit:
 * {@code Entity#getBukkitEntity()} is added to Minecraft's class by Cauldron (Thermos, Crucible), and every
 * {@code CraftEntity} has {@code getHandle()}.
 * <p>
 * Uses {@link MethodHandles} on purpose, not {@code Class#getMethod}: {@code getMethod} resolves the signature of
 * every public method of the class and its interfaces, and on production Crucible some Bukkit signatures name
 * obfuscated classes that do not exist at runtime ({@code World#getWorldServer()} returns {@code mt}), which throws
 * {@code NoClassDefFoundError}. A method handle lookup resolves only the method asked for (issue #3).
 */
public final class CraftBridge {

    private static volatile MethodHandle getBukkitEntity;
    private static volatile MethodHandle getHandle;

    private CraftBridge() {}

    private static void resolve() {
        if (getHandle != null) {
            return;
        }
        synchronized (CraftBridge.class) {
            if (getHandle != null) {
                return;
            }
            try {
                String craftPackage = Bukkit.getServer()
                    .getClass()
                    .getPackage()
                    .getName();
                Class<?> craftEntity = Class.forName(craftPackage + ".entity.CraftEntity");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                getBukkitEntity = lookup
                    .findVirtual(
                        net.minecraft.entity.Entity.class,
                        "getBukkitEntity",
                        MethodType.methodType(craftEntity))
                    .asType(MethodType.methodType(Object.class, net.minecraft.entity.Entity.class));
                getHandle = lookup
                    .findVirtual(craftEntity, "getHandle", MethodType.methodType(net.minecraft.entity.Entity.class))
                    .asType(MethodType.methodType(net.minecraft.entity.Entity.class, org.bukkit.entity.Entity.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                    "Not a Bukkit hybrid server (Entity#getBukkitEntity or CraftEntity#getHandle missing)",
                    e);
            }
        }
    }

    /** The Bukkit entity of a Minecraft entity, or null. */
    public static Object bukkitEntity(net.minecraft.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        resolve();
        try {
            return (Object) getBukkitEntity.invokeExact(entity);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /** The Minecraft entity behind a Bukkit entity, or null. */
    public static net.minecraft.entity.Entity handle(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        resolve();
        try {
            return (net.minecraft.entity.Entity) getHandle.invokeExact(entity);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Not a CraftBukkit entity: " + entity, e);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
}
