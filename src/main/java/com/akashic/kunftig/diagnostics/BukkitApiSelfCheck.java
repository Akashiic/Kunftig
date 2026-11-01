package com.akashic.kunftig.diagnostics;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.List;

import org.apache.logging.log4j.Logger;

/**
 * Runs {@link ObfuscatedApiScanner} on the server jar once at startup, in a background thread, and logs what it finds,
 * so the admin knows whether reflection on the Bukkit API is safe on this build (issue #3). Never loads a Bukkit class.
 * Disabled with {@code -Dkunftig.diagnostics.disable=true}.
 */
public final class BukkitApiSelfCheck {

    private static final int MAX_LISTED = 20;

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
            if (findings.isEmpty()) {
                log.info("Kunftig: no obfuscated types in the Bukkit API signatures of {}", jar.getName());
                return;
            }
            StringBuilder message = new StringBuilder();
            message.append("Kunftig: ")
                .append(findings.size())
                .append(" Bukkit API member(s) in ")
                .append(jar.getName())
                .append(" name obfuscated classes. Class#getMethod/getMethods on these classes throws ")
                .append("NoClassDefFoundError; use MethodHandles (see Kunftig docs/API.md, section 6):");
            for (int i = 0; i < findings.size() && i < MAX_LISTED; i++) {
                message.append("\n  ")
                    .append(findings.get(i));
            }
            if (findings.size() > MAX_LISTED) {
                message.append("\n  ... and ")
                    .append(findings.size() - MAX_LISTED)
                    .append(" more");
            }
            log.warn(message.toString());
        } catch (Throwable e) {
            log.debug("Kunftig: Bukkit API signature check failed", e);
        }
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
