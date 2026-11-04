package com.akashic.kunftig;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.akashic.kunftig.diagnostics.BukkitApiSelfCheck;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Kunftig: backports of features from recent Minecraft versions to 1.7.10.
 * <p>
 * Every feature is client-side and talks to the server through plugin messages, so the mod is accepted by any
 * server ({@code acceptableRemoteVersions = "*"}) and never appears as a requirement in the FML handshake.
 */
@Mod(modid = Kunftig.MODID, name = Kunftig.NAME, version = Tags.VERSION, acceptableRemoteVersions = "*")
public final class Kunftig {

    public static final String MODID = "kunftig";
    public static final String NAME = "Kunftig";
    public static final Logger LOG = LogManager.getLogger(NAME);

    @SidedProxy(clientSide = "com.akashic.kunftig.client.ClientProxy", serverSide = "com.akashic.kunftig.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        KunftigConfig.load(event.getSuggestedConfigurationFile());
        installBukkitBridges();
        // Tells joining Kunftig clients which bobber physics to predict (also in singleplayer's integrated server).
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new com.akashic.kunftig.fishing.ModernBobberAnnouncer());
        if (Boolean.getBoolean("kunftig.devHarness")) {
            install("com.akashic.kunftig.dev.FishingHarness"); // development only, absent from release jars
        }
        proxy.preInit(event);
    }

    /**
     * On Bukkit hybrids (Thermos, Crucible) implement kunftig-api for plugins. Done reflectively so that no Bukkit
     * class is ever loaded where Bukkit does not exist (clients, singleplayer, pure Forge servers).
     */
    private static boolean bukkitPresent() {
        try {
            Class.forName("org.bukkit.Bukkit", false, Kunftig.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void install(String bridge) {
        try {
            // MethodHandles, never getMethod, for anything that may touch Bukkit classes (issue #3).
            MethodHandles.publicLookup()
                .findStatic(Class.forName(bridge), "install", MethodType.methodType(void.class))
                .invoke();
        } catch (Throwable e) {
            LOG.error("Kunftig: could not enable {}", bridge, e);
        }
    }

    private static void installBukkitBridges() {
        if (bukkitPresent()) {
            BukkitApiSelfCheck.start(Kunftig.class.getClassLoader(), LOG);
            install("com.akashic.kunftig.bukkit.fishing.BukkitFishingBridge");
            install("com.akashic.kunftig.bukkit.data.ItemDataBridge");
            install("com.akashic.kunftig.bukkit.particle.BukkitParticleBridge");
            install("com.akashic.kunftig.bukkit.player.BukkitPlayerBridge");
            install("com.akashic.kunftig.bukkit.hologram.BukkitHologramBridge");
            install("com.akashic.kunftig.bukkit.rod.BukkitRodBridge");
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
