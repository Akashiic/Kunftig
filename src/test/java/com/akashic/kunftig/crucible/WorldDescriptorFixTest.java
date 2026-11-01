package com.akashic.kunftig.crucible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.ClassNode;

/**
 * Reproduces production Crucible (Kunftig issue #3) with generated classes: org.bukkit.World declaring
 * {@code getWorldServer()Lmt;}, a CraftWorld returning WorldServer, and a plugin call as Crucible's plugin remapper
 * leaves it ({@code invokeinterface World.getWorldServer()Lnet/minecraft/world/WorldServer;}).
 */
class WorldDescriptorFixTest {

    private static final String WORLD_SERVER = "net/minecraft/world/WorldServer";

    private static byte[] world(String getWorldServerDescriptor) {
        ClassWriter w = new ClassWriter(0);
        w.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            "org/bukkit/World",
            null,
            "java/lang/Object",
            null);
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getName", "()Ljava/lang/String;", null, null)
            .visitEnd();
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getWorldServer", getWorldServerDescriptor, null, null)
            .visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    /** {@code CraftWorld}: compiled with runtime names, so it returns WorldServer. */
    private static byte[] craftWorld() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "test/CraftWorld",
            null,
            "java/lang/Object",
            new String[] { "org/bukkit/World" });
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        MethodVisitor name = w.visitMethod(Opcodes.ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null);
        name.visitCode();
        name.visitLdcInsn("world");
        name.visitInsn(Opcodes.ARETURN);
        name.visitMaxs(0, 0);
        name.visitEnd();
        MethodVisitor handle = w
            .visitMethod(Opcodes.ACC_PUBLIC, "getWorldServer", "()L" + WORLD_SERVER + ";", null, null);
        handle.visitCode();
        handle.visitInsn(Opcodes.ACONST_NULL);
        handle.visitInsn(Opcodes.ARETURN);
        handle.visitMaxs(0, 0);
        handle.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    /** A plugin after Crucible's remapper: {@code static Object call(World w) { return w.getWorldServer(); }}. */
    private static byte[] plugin() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "test/Plugin", null, "java/lang/Object", null);
        MethodVisitor call = w.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "call",
            "(Lorg/bukkit/World;)Ljava/lang/Object;",
            null,
            null);
        call.visitCode();
        call.visitVarInsn(Opcodes.ALOAD, 0);
        call.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            "org/bukkit/World",
            "getWorldServer",
            "()L" + WORLD_SERVER + ";",
            true);
        call.visitInsn(Opcodes.ARETURN);
        call.visitMaxs(0, 0);
        call.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    /** What KunftigMixinPlugin does to the class before it is defined. */
    private static byte[] fixed(byte[] world) {
        ClassNode node = new ClassNode();
        new ClassReader(world).accept(node, 0);
        assertTrue(WorldDescriptorFix.apply(node));
        org.spongepowered.asm.lib.ClassWriter writer = new org.spongepowered.asm.lib.ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    /**
     * Defines the three classes itself (child first); everything else, WorldServer included, from the test classpath.
     */
    private static ClassLoader server(byte[] world) {
        final Map<String, byte[]> classes = new HashMap<>();
        classes.put("org.bukkit.World", world);
        classes.put("test.CraftWorld", craftWorld());
        classes.put("test.Plugin", plugin());
        return new ClassLoader(WorldDescriptorFixTest.class.getClassLoader()) {

            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                return loaded != null ? loaded : defineClass(name, bytes, 0, bytes.length);
            }
        };
    }

    private static Object callPlugin(ClassLoader server) throws Throwable {
        Object craftWorld = server.loadClass("test.CraftWorld")
            .getConstructor()
            .newInstance();
        Method call = server.loadClass("test.Plugin")
            .getMethod("call", server.loadClass("org.bukkit.World"));
        try {
            return call.invoke(null, craftWorld);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void reproducesProductionCrucible() throws Exception {
        ClassLoader broken = server(world(WorldDescriptorFix.BROKEN));
        Class<?> world = broken.loadClass("org.bukkit.World");
        assertThrows(NoClassDefFoundError.class, world::getMethods);
        assertThrows(NoClassDefFoundError.class, () -> world.getMethod("getName"));
        // 'net.minecraft.world.WorldServer org.bukkit.World.getWorldServer()', as seen on the server.
        assertThrows(NoSuchMethodError.class, () -> callPlugin(broken));
    }

    @Test
    void afterTheFixPluginsAndReflectionWork() throws Throwable {
        ClassLoader patched = server(fixed(world(WorldDescriptorFix.BROKEN)));
        Class<?> world = patched.loadClass("org.bukkit.World");
        assertEquals(2, world.getMethods().length);
        assertEquals(
            WORLD_SERVER.replace('/', '.'),
            world.getMethod("getWorldServer")
                .getReturnType()
                .getName());
        assertNull(callPlugin(patched), "the call reaches CraftWorld#getWorldServer");
    }

    @Test
    void touchesNothingElse() {
        ClassNode alreadyFixed = new ClassNode();
        new ClassReader(world(WorldDescriptorFix.FIXED)).accept(alreadyFixed, 0);
        assertFalse(WorldDescriptorFix.apply(alreadyFixed), "a fixed Crucible is left alone");

        ClassNode other = new ClassNode();
        new ClassReader(craftWorld()).accept(other, 0);
        assertFalse(WorldDescriptorFix.apply(other));

        ClassNode broken = new ClassNode();
        new ClassReader(world(WorldDescriptorFix.BROKEN)).accept(broken, 0);
        assertTrue(WorldDescriptorFix.apply(broken));
        assertFalse(WorldDescriptorFix.apply(broken), "idempotent");
    }

    @Test
    void preconditionsUseResourcesOnly() {
        ClassLoader empty = new ClassLoader(null) {};
        assertFalse(WorldDescriptorFix.applies(empty), "no Bukkit: nothing to fix");
    }
}
