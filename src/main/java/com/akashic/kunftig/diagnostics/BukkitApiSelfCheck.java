package com.akashic.kunftig.diagnostics;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.Logger;

/**
 * Runs {@link ObfuscatedApiScanner} on the server jar once at startup, in a background thread, and logs what it finds,
 * so the admin knows whether reflection on the Bukkit API is safe on this build (issue #3). Never loads a Bukkit class.
 * Disabled with {@code -Dkunftig.diagnostics.disable=true}.
 */
public final class BukkitApiSelfCheck {

    private BukkitApiSelfCheck() {}

    public static void start(ClassLoader loader, Logger log) {
        if (Boolean.getBoolean("kunftig.diagnostics.disable")) {
            return;
        }
        final File jar = jarOf(loader.getResource("org/bukkit/World.class"));
        if (jar == null) {
            log.debug("Kunftig: Bukkit API jar not found, signature check skipped");
            return;
        }
        Thread thread = new Thread(() -> run(jar, log), "Kunftig Bukkit API check");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void run(File jar, Logger log) {
        try {
            List<ObfuscatedApiScanner.Finding> findings = ObfuscatedApiScanner.scan(jar);
            String message = describe(findings, jar.getName());
            if (pluginApi(findings).isEmpty()) {
                log.info(message);
            } else {
                log.warn(message);
            }
        } catch (Throwable e) {
            log.debug("Kunftig: Bukkit API signature check failed", e);
        }
    }

    /** Findings in the API plugins compile against ({@code org.bukkit}, BungeeCord chat), as opposed to internals. */
    static List<ObfuscatedApiScanner.Finding> pluginApi(List<ObfuscatedApiScanner.Finding> findings) {
        List<ObfuscatedApiScanner.Finding> api = new ArrayList<>();
        for (ObfuscatedApiScanner.Finding finding : findings) {
            if (finding.owner()
                .startsWith("org.bukkit.")
                || finding.owner()
                    .startsWith("net.md_5.bungee.")) {
                api.add(finding);
            }
        }
        return api;
    }

    /**
     * The log message: every plugin-API finding in full (production Crucible has exactly one, World#getWorldServer),
     * and server internals ({@code org.spigotmc}, 30 members there) summarized by class.
     */
    static String describe(List<ObfuscatedApiScanner.Finding> findings, String jarName) {
        if (findings.isEmpty()) {
            return "Kunftig: no obfuscated types in the Bukkit API signatures of " + jarName;
        }
        List<ObfuscatedApiScanner.Finding> api = pluginApi(findings);
        Set<String> internalClasses = new TreeSet<>();
        for (ObfuscatedApiScanner.Finding finding : findings) {
            if (!api.contains(finding)) {
                internalClasses.add(finding.owner());
            }
        }
        StringBuilder message = new StringBuilder("Kunftig: ");
        if (!api.isEmpty()) {
            message.append(api.size())
                .append(" Bukkit API member(s) in ")
                .append(jarName)
                .append(" name obfuscated classes that do not exist at runtime. Plugins calling them directly get ")
                .append("NoSuchMethodError, and Class#getMethod/getMethods on their classes throws ")
                .append("NoClassDefFoundError (see Kunftig docs/API.md, section 6):");
            for (ObfuscatedApiScanner.Finding finding : api) {
                message.append("\n  ")
                    .append(finding);
            }
            message.append(internalClasses.isEmpty() ? "" : "\nAlso ");
        }
        if (!internalClasses.isEmpty()) {
            message.append(findings.size() - api.size())
                .append(" member(s) of server internals in ")
                .append(jarName)
                .append(" name obfuscated classes (")
                .append(String.join(", ", internalClasses))
                .append("); plugins should not use these. Full list: java -cp <kunftig jar> ")
                .append(ObfuscatedApiScanner.class.getName())
                .append(" ")
                .append(jarName);
        }
        return message.toString();
    }

    /** The jar file of a {@code jar:file:...!/entry} resource URL, or null for anything else. */
    static File jarOf(URL resource) {
        if (resource == null || !"jar".equals(resource.getProtocol())) {
            return null;
        }
        String spec = resource.getPath();
        int separator = spec.indexOf("!/");
        if (separator < 0) {
            return null;
        }
        try {
            URI uri = new URI(spec.substring(0, separator));
            return "file".equals(uri.getScheme()) ? new File(uri) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
