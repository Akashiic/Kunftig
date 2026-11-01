package com.akashic.kunftig.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ObfuscatedApiScannerTest {

    @TempDir
    Path dir;

    /** Crucible's defect: {@code public interface World { mt getWorldServer(); String getName(); }} */
    private static byte[] world() {
        ClassWriter w = new ClassWriter(0);
        w.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            "org/bukkit/World",
            null,
            "java/lang/Object",
            null);
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getWorldServer", "()Lmt;", null, null)
            .visitEnd();
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getName", "()Ljava/lang/String;", null, null)
            .visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    /** Fields, 8-byte constants, a protected method with two obfuscated types and an obfuscated superclass. */
    private static byte[] mixed() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "org/bukkit/Mixed",
            null,
            "abc",
            new String[] { "java/io/Serializable" });
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "BIG", "J", null, 1234567890123L)
            .visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "PI", "D", null, 3.14)
            .visitEnd();
        w.visitField(Opcodes.ACC_PRIVATE, "block", "[Laji;", null, null)
            .visitEnd();
        MethodVisitor m = w
            .visitMethod(Opcodes.ACC_PROTECTED, "convert", "(Laji;Ljava/util/List;Laji;)Ladd;", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.ACONST_NULL);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private File jar(Object... entries) throws IOException {
        File file = dir.resolve("server.jar")
            .toFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < entries.length; i += 2) {
                zip.putNextEntry(new ZipEntry((String) entries[i]));
                zip.write((byte[]) entries[i + 1]);
                zip.closeEntry();
            }
        }
        return file;
    }

    private static List<String> names(List<ObfuscatedApiScanner.Finding> findings) {
        return findings.stream()
            .map(f -> f.owner() + "#" + f.member())
            .collect(Collectors.toList());
    }

    @Test
    void findsCruciblesGetWorldServer() throws IOException {
        List<ObfuscatedApiScanner.Finding> findings = ObfuscatedApiScanner.scan(jar("org/bukkit/World.class", world()));
        assertEquals(1, findings.size());
        ObfuscatedApiScanner.Finding finding = findings.get(0);
        assertEquals("org.bukkit.World", finding.owner());
        assertEquals("getWorldServer()Lmt;", finding.member());
        assertEquals("public", finding.access());
        assertEquals(Arrays.asList("mt"), finding.types());
        assertFalse(finding.typesInJar());
    }

    @Test
    void reportsFieldsMethodsAndHeadersWithEveryType() throws IOException {
        List<ObfuscatedApiScanner.Finding> findings = ObfuscatedApiScanner
            .scan(jar("org/bukkit/Mixed.class", mixed(), "aji.class", new byte[] { 0 }));
        assertEquals(
            Arrays.asList(
                "org.bukkit.Mixed#<class>",
                "org.bukkit.Mixed#block:[Laji;",
                "org.bukkit.Mixed#convert(Laji;Ljava/util/List;Laji;)Ladd;"),
            names(findings));
        ObfuscatedApiScanner.Finding field = findings.get(1);
        assertEquals("private", field.access());
        assertTrue(field.typesInJar());
        ObfuscatedApiScanner.Finding method = findings.get(2);
        assertEquals("protected", method.access());
        assertEquals(Arrays.asList("add", "aji"), method.types());
        assertFalse(method.typesInJar());
    }

    @Test
    void skipsImplementationPackagesAndOtherLibraries() throws IOException {
        byte[] craft = world();
        List<ObfuscatedApiScanner.Finding> findings = ObfuscatedApiScanner.scan(
            jar(
                "org/bukkit/craftbukkit/v1_7_R4/CraftWorld.class",
                craft,
                "net/minecraft/world/WorldServer.class",
                craft,
                "mt.class",
                craft));
        assertTrue(findings.isEmpty());
    }

    @Test
    void parsesRealClassFiles() throws IOException {
        // javac output with lambdas: InvokeDynamic, MethodHandle and BootstrapMethods entries.
        List<ObfuscatedApiScanner.Finding> findings = ObfuscatedApiScanner.scan(
            jar(
                "org/bukkit/A.class",
                classBytes(ObfuscatedApiScanner.class),
                "org/bukkit/B.class",
                classBytes(ObfuscatedApiScannerTest.class),
                "org/spigotmc/C.class",
                classBytes(BukkitApiSelfCheck.class)));
        assertTrue(findings.isEmpty(), findings::toString);
    }

    @Test
    void rejectsGarbage() throws IOException {
        File file = jar("org/bukkit/Broken.class", new byte[] { (byte) 0xCA, (byte) 0xFE, 0, 0 });
        assertThrows(IOException.class, () -> ObfuscatedApiScanner.scan(file));
    }

    @Test
    void descriptorParsing() {
        assertEquals(Arrays.asList("mt"), ObfuscatedApiScanner.defaultPackageTypes("()Lmt;"));
        assertEquals(Arrays.asList("a", "b"), ObfuscatedApiScanner.defaultPackageTypes("(I[[Lb;JLa;)V"));
        assertTrue(
            ObfuscatedApiScanner.defaultPackageTypes("(Ljava/lang/String;ILorg/bukkit/World;)Z")
                .isEmpty());
    }

    /** A defective World: its descriptor names {@code mt}, as when a coremod loads it before deobfuscation. */
    private static ClassLoader withWorld(String getWorldServerDescriptor) {
        ClassWriter w = new ClassWriter(0);
        w.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            "org/bukkit/World",
            null,
            "java/lang/Object",
            null);
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getWorldServer", getWorldServerDescriptor, null, null)
            .visitEnd();
        w.visitEnd();
        final byte[] bytes = w.toByteArray();
        return new ClassLoader(ObfuscatedApiScannerTest.class.getClassLoader()) {

            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals("org.bukkit.World")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                return loaded != null ? loaded : defineClass(name, bytes, 0, bytes.length);
            }
        };
    }

    @Test
    void startupCheckJudgesTheLoadedWorld() {
        assertEquals("mt", BukkitApiSelfCheck.missingType(withWorld("()Lmt;")));
        assertNull(BukkitApiSelfCheck.missingType(withWorld("()Ljava/lang/Object;")), "deobfuscated as usual");
        assertNull(BukkitApiSelfCheck.missingType(new ClassLoader(null) {}), "no Bukkit");
    }

    @Test
    void startupWarningNamesTheCauseWithoutClaimingAFix() {
        String message = BukkitApiSelfCheck.describe("mt");
        assertTrue(message.contains("obfuscated class mt"), message);
        assertTrue(message.contains("NoSuchMethodError"), message);
        assertTrue(message.contains("PlayerContainerFix"), message);
        assertFalse(
            message.toLowerCase(java.util.Locale.ROOT)
                .contains("fixed"),
            message);
    }
}
