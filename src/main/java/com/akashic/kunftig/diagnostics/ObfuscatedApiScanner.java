package com.akashic.kunftig.diagnostics;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lists API members whose signatures, <b>as stored in the jar</b>, name classes in the default package: obfuscated
 * Minecraft names ({@code mt}, {@code aji}...). A tool for inspecting server jars, not a defect detector: Thermos and
 * Crucible store every class with notch names and FML renames them while they load, so on those jars nearly everything
 * is listed and is fine at runtime. What matters is the loaded class ({@link BukkitApiSelfCheck}); on TEOF,
 * {@code org.bukkit.World} kept {@code mt} only because a coremod loaded it before FML's deobfuscation (issue #3).
 * <p>
 * Reads class files directly (constant pool, fields, methods) without loading or initializing any class, so it is
 * safe to run inside a live server and needs no library. Runnable on any server jar:
 *
 * <pre>
 * java -cp kunftig-&lt;version&gt;.jar com.akashic.kunftig.diagnostics.ObfuscatedApiScanner server.jar
 * </pre>
 */
public final class ObfuscatedApiScanner {

    /** Public plugin APIs bundled in Bukkit server jars. */
    public static final List<String> DEFAULT_INCLUDES = Collections
        .unmodifiableList(java.util.Arrays.asList("org/bukkit/", "org/spigotmc/", "net/md_5/bungee/"));

    /** Implementation packages, where Minecraft types in signatures are expected. */
    public static final List<String> DEFAULT_EXCLUDES = Collections
        .unmodifiableList(java.util.Arrays.asList("org/bukkit/craftbukkit/"));

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_PRIVATE = 0x0002;
    private static final int ACC_PROTECTED = 0x0004;

    /** One member (or class header) that names default-package classes. */
    public static final class Finding {

        private final String owner;
        private final String member;
        private final String access;
        private final List<String> types;
        private final boolean typesInJar;

        Finding(String owner, String member, String access, List<String> types, boolean typesInJar) {
            this.owner = owner;
            this.member = member;
            this.access = access;
            this.types = Collections.unmodifiableList(types);
            this.typesInJar = typesInJar;
        }

        /** Binary name of the declaring class, e.g. {@code org.bukkit.World}. */
        public String owner() {
            return owner;
        }

        /** Member name and JVM descriptor, e.g. {@code getWorldServer()Lmt;}; {@code <class>} for the header. */
        public String member() {
            return member;
        }

        /** {@code public}, {@code protected}, {@code package} or {@code private}. */
        public String access() {
            return access;
        }

        /** The default-package class names, e.g. {@code [mt]}. */
        public List<String> types() {
            return types;
        }

        /** Whether every named class exists in the scanned jar (if not, resolving the member fails at runtime). */
        public boolean typesInJar() {
            return typesInJar;
        }

        @Override
        public String toString() {
            return access + " " + owner + "#" + member + " -> " + types + (typesInJar ? "" : " (not in this jar)");
        }
    }

    private ObfuscatedApiScanner() {}

    /** Scans the default API packages of a server jar. */
    public static List<Finding> scan(File jar) throws IOException {
        return scan(jar, DEFAULT_INCLUDES, DEFAULT_EXCLUDES);
    }

    /**
     * Scans the classes of {@code jar} whose internal names start with one of {@code includes} and none of
     * {@code excludes} (prefixes like {@code org/bukkit/}).
     */
    public static List<Finding> scan(File jar, List<String> includes, List<String> excludes) throws IOException {
        List<Finding> findings = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class") || !matches(name, includes, excludes)) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    scanClass(in, zip, findings);
                } catch (IOException | RuntimeException e) {
                    throw new IOException("Malformed class file " + name + " in " + jar, e);
                }
            }
        }
        Collections.sort(findings, (a, b) -> (a.owner + a.member).compareTo(b.owner + b.member));
        return findings;
    }

    private static boolean matches(String name, List<String> includes, List<String> excludes) {
        for (String exclude : excludes) {
            if (name.startsWith(exclude)) {
                return false;
            }
        }
        for (String include : includes) {
            if (name.startsWith(include)) {
                return true;
            }
        }
        return false;
    }

    private static void scanClass(InputStream raw, ZipFile zip, List<Finding> findings) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("bad magic");
        }
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        int[] classNameIndex = new int[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: // Utf8
                    utf8[i] = in.readUTF();
                    break;
                case 7: // Class
                    classNameIndex[i] = in.readUnsignedShort();
                    break;
                case 8: // String
                case 16: // MethodType
                case 19: // Module
                case 20: // Package
                    in.readUnsignedShort();
                    break;
                case 15: // MethodHandle
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                    break;
                case 3: // Integer
                case 4: // Float
                case 9: // Fieldref
                case 10: // Methodref
                case 11: // InterfaceMethodref
                case 12: // NameAndType
                case 17: // Dynamic
                case 18: // InvokeDynamic
                    in.readInt();
                    break;
                case 5: // Long
                case 6: // Double
                    in.readLong();
                    i++;
                    break;
                default:
                    throw new IOException("unknown constant pool tag " + tag);
            }
        }
        in.readUnsignedShort(); // access
        String self = utf8[classNameIndex[in.readUnsignedShort()]];
        String owner = self.replace('/', '.');

        List<String> header = new ArrayList<>();
        int superIndex = in.readUnsignedShort();
        if (superIndex != 0) {
            addIfDefaultPackage(utf8[classNameIndex[superIndex]], header);
        }
        int interfaces = in.readUnsignedShort();
        for (int i = 0; i < interfaces; i++) {
            addIfDefaultPackage(utf8[classNameIndex[in.readUnsignedShort()]], header);
        }
        if (!header.isEmpty()) {
            findings.add(new Finding(owner, "<class>", "public", header, allInJar(header, zip)));
        }

        for (int kind = 0; kind < 2; kind++) { // fields, then methods
            int members = in.readUnsignedShort();
            for (int m = 0; m < members; m++) {
                int access = in.readUnsignedShort();
                String name = utf8[in.readUnsignedShort()];
                String descriptor = utf8[in.readUnsignedShort()];
                skipAttributes(in);
                List<String> types = defaultPackageTypes(descriptor);
                if (!types.isEmpty()) {
                    String member = kind == 0 ? name + ":" + descriptor : name + descriptor;
                    findings.add(new Finding(owner, member, accessName(access), types, allInJar(types, zip)));
                }
            }
        }
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int attributes = in.readUnsignedShort();
        for (int a = 0; a < attributes; a++) {
            in.readUnsignedShort();
            long length = in.readInt() & 0xFFFFFFFFL;
            while (length > 0) {
                long skipped = in.skip(length);
                if (skipped <= 0) {
                    throw new IOException("truncated attribute");
                }
                length -= skipped;
            }
        }
    }

    /** Class names without a package in a field or method descriptor, e.g. {@code ()Lmt;} gives {@code [mt]}. */
    static List<String> defaultPackageTypes(String descriptor) {
        Set<String> found = new TreeSet<>();
        int i = 0;
        while ((i = descriptor.indexOf('L', i)) >= 0) {
            int end = descriptor.indexOf(';', i);
            if (end < 0) {
                break;
            }
            addIfDefaultPackage(descriptor.substring(i + 1, end), found);
            i = end + 1;
        }
        return new ArrayList<>(found);
    }

    private static void addIfDefaultPackage(String internalName, java.util.Collection<String> out) {
        if (internalName != null && internalName.indexOf('/') < 0 && !out.contains(internalName)) {
            out.add(internalName);
        }
    }

    private static boolean allInJar(List<String> types, ZipFile zip) {
        for (String type : types) {
            if (zip.getEntry(type + ".class") == null) {
                return false;
            }
        }
        return true;
    }

    private static String accessName(int access) {
        if ((access & ACC_PUBLIC) != 0) {
            return "public";
        }
        if ((access & ACC_PROTECTED) != 0) {
            return "protected";
        }
        return (access & ACC_PRIVATE) != 0 ? "private" : "package";
    }

    /**
     * {@code java -cp kunftig.jar com.akashic.kunftig.diagnostics.ObfuscatedApiScanner <server.jar> [prefix...]}.
     * Delegates to {@link ObfuscatedApiScannerCli}, which the server never loads: FML warns about every class it loads
     * that calls {@code System.exit} (issue #6).
     */
    public static void main(String[] args) throws IOException {
        ObfuscatedApiScannerCli.main(args);
    }
}
