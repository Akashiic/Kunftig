package com.akashic.kunftig.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Reproduces Kunftig issue #3 without a server: production Crucible's {@code org.bukkit.World} declares
 * {@code mt getWorldServer()}, and {@code mt} does not exist at runtime. This builds a class with the same defect and
 * shows why Kunftig only uses method handles for reflection on Bukkit types.
 */
class ObfuscatedSignatureReflectionTest {

    /** {@code public class Defective { public mt getWorldServer(); public String getName() { return "world"; } }} */
    private static Class<?> defectiveClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "test/Defective", null, "java/lang/Object", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        MethodVisitor broken = writer.visitMethod(Opcodes.ACC_PUBLIC, "getWorldServer", "()Lmt;", null, null);
        broken.visitCode();
        broken.visitInsn(Opcodes.ACONST_NULL);
        broken.visitInsn(Opcodes.ARETURN);
        broken.visitMaxs(0, 0);
        broken.visitEnd();
        MethodVisitor fine = writer.visitMethod(Opcodes.ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null);
        fine.visitCode();
        fine.visitLdcInsn("world");
        fine.visitInsn(Opcodes.ARETURN);
        fine.visitMaxs(0, 0);
        fine.visitEnd();
        writer.visitEnd();
        final byte[] bytes = writer.toByteArray();
        ClassLoader loader = new ClassLoader(ObfuscatedSignatureReflectionTest.class.getClassLoader()) {

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals("test.Defective")) {
                    return defineClass(name, bytes, 0, bytes.length);
                }
                throw new ClassNotFoundException(name);
            }
        };
        try {
            return loader.loadClass("test.Defective");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void getMethodFailsEvenForAHealthyMethod() {
        Class<?> defective = defectiveClass();
        assertThrows(NoClassDefFoundError.class, () -> defective.getMethod("getName"));
        assertThrows(NoClassDefFoundError.class, defective::getMethods);
    }

    @Test
    void methodHandlesResolveOnlyWhatIsAskedFor() throws Throwable {
        Class<?> defective = defectiveClass();
        Object instance = defective.getConstructor()
            .newInstance();
        Object name = MethodHandles.publicLookup()
            .findVirtual(defective, "getName", MethodType.methodType(String.class))
            .invoke(instance);
        assertEquals("world", name);
    }
}
