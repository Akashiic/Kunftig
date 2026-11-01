package com.akashic.kunftig.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Issue #6: FML's TerminalTransformer prints a "THIS IS NOT ALLOWED" warning for every class it loads that calls
 * System.exit, Runtime.exit or Runtime.halt. Only command-line classes (never loaded by the mod) may do it.
 */
class NoProcessExitTest {

    private static boolean exits(MethodInsnNode call) {
        return call.owner.equals("java/lang/System") && call.name.equals("exit")
            || call.owner.equals("java/lang/Runtime") && (call.name.equals("exit") || call.name.equals("halt"));
    }

    private static List<Path> modClasses() throws IOException, URISyntaxException {
        Path root = Paths.get(
            ObfuscatedApiScanner.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(
                p -> p.toString()
                    .endsWith(".class"))
                .collect(Collectors.toList());
        }
    }

    private static ClassNode read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
            return node;
        }
    }

    @Test
    void onlyCommandLineClassesExitTheProcess() throws Exception {
        List<Path> classes = modClasses();
        assertTrue(classes.size() > 50, "mod classes found: " + classes.size());
        List<String> exiting = new ArrayList<>();
        List<String> cliUsers = new ArrayList<>();
        for (Path file : classes) {
            ClassNode node = read(file);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (!(insn instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (exits(call)) {
                        exiting.add(node.name + "." + method.name);
                    }
                    if (call.owner.endsWith("Cli") && !node.name.endsWith("Cli")) {
                        cliUsers.add(node.name + "." + method.name);
                    }
                }
            }
        }
        assertEquals(1, exiting.size(), exiting.toString());
        assertTrue(
            exiting.get(0)
                .endsWith("Cli.main"),
            exiting.toString());
        // Reached only from the scanner's own main, which the mod never calls.
        assertEquals(
            java.util.Collections.singletonList("com/akashic/kunftig/diagnostics/ObfuscatedApiScanner.main"),
            cliUsers);
    }

    private static Path jar(Path dir, String name, byte[] bytes) throws IOException {
        Path file = dir.resolve("server-" + (bytes == null ? "clean" : "defective") + ".jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(bytes == null ? "README" : name));
            zip.write(bytes == null ? new byte[] { 'x' } : bytes);
            zip.closeEntry();
        }
        return file;
    }

    @Test
    void cliReportsExitCodes(@TempDir Path dir) throws IOException {
        ClassWriter world = new ClassWriter(0);
        world.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            "org/bukkit/World",
            null,
            "java/lang/Object",
            null);
        world.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getWorldServer", "()Lmt;", null, null)
            .visitEnd();
        world.visitEnd();

        assertEquals(2, ObfuscatedApiScannerCli.run(new String[0]), "usage");
        assertEquals(0, ObfuscatedApiScannerCli.run(new String[] { jar(dir, null, null).toString() }), "clean");
        assertEquals(
            1,
            ObfuscatedApiScannerCli
                .run(new String[] { jar(dir, "org/bukkit/World.class", world.toByteArray()).toString() }),
            "findings");
    }
}
