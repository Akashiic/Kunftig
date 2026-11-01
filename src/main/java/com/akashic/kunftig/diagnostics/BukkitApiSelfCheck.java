package com.akashic.kunftig.diagnostics;

import org.apache.logging.log4j.Logger;

/**
 * Tells the admin, once at startup, whether {@code org.bukkit.World} was loaded without FML's runtime deobfuscation
 * (Kunftig issue #3).
 * <p>
 * On Thermos/Crucible the server jar is obfuscated (notch names, Minecraft itself comes from the vanilla jar on the
 * Class-Path) and FML renames everything while classes load, through {@code DeobfuscationTransformer}. FML registers
 * that transformer last ({@code FMLDeobfTweaker}), after the coremods are injected. A coremod that probes for Bukkit
 * with {@code Class.forName("org.bukkit.World")} at that point (PlayerContainerFix 2.0.0 does) loads {@code World}
 * with its notch names for good: {@code getWorldServer()} keeps returning {@code mt}, plugins calling it get
 * {@code NoSuchMethodError} and reflection on {@code World} throws {@code NoClassDefFoundError: mt}.
 * <p>
 * Only the loaded class tells: on disk every class of the jar has notch names, so a static scan
 * ({@link ObfuscatedApiScanner}) reports them all. Runs in a background thread; {@code World} is loaded without
 * initialization if nobody has yet (FML's transformers are active by then). Disabled with
 * {@code -Dkunftig.diagnostics.disable=true}.
 */
public final class BukkitApiSelfCheck {

    private BukkitApiSelfCheck() {}

    public static void start(final ClassLoader loader, final Logger log) {
        if (Boolean.getBoolean("kunftig.diagnostics.disable") || loader.getResource("org/bukkit/World.class") == null) {
            return;
        }
        Thread thread = new Thread(() -> run(loader, log), "Kunftig Bukkit API check");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void run(ClassLoader loader, Logger log) {
        try {
            String missing = missingType(loader);
            if (missing == null) {
                log.debug("Kunftig: org.bukkit.World resolves all its signatures");
            } else {
                log.warn(describe(missing));
            }
        } catch (Throwable e) {
            log.debug("Kunftig: Bukkit API check failed", e);
        }
    }

    /**
     * The class {@code org.bukkit.World}'s signatures name but that does not exist ({@code mt} when it escaped
     * deobfuscation), or null if they all resolve or there is no Bukkit.
     */
    static String missingType(ClassLoader loader) {
        try {
            Class.forName("org.bukkit.World", false, loader)
                .getMethods();
            return null;
        } catch (NoClassDefFoundError e) {
            return e.getMessage() == null ? "?" : e.getMessage();
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    static String describe(String missing) {
        return "Kunftig: org.bukkit.World was loaded without FML's runtime deobfuscation: its methods still name the "
            + "obfuscated class "
            + missing
            + ". Plugins calling World#getWorldServer() get NoSuchMethodError and "
            + "reflection on World throws NoClassDefFoundError. Usual cause: a coremod probing for Bukkit with "
            + "Class.forName(\"org.bukkit.World\") while coremods load (PlayerContainerFix 2.0.0 does), before FML "
            + "registers its deobfuscation transformer. See Kunftig docs/API.md, section 6.";
    }
}
