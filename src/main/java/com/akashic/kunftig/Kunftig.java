package com.akashic.kunftig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
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
        installBukkitFishingBridge();
        proxy.preInit(event);
    }

    /**
     * On Bukkit hybrids (Thermos, Crucible) expose the fishing engine to plugins. Done reflectively so that no
     * Bukkit class is ever loaded where Bukkit does not exist (clients, singleplayer, pure Forge servers).
     */
    private static void installBukkitFishingBridge() {
        try {
            Class.forName("org.bukkit.Bukkit", false, Kunftig.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            Class.forName("com.akashic.kunftig.bukkit.fishing.BukkitFishingBridge")
                .getMethod("install")
                .invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.error("Kunftig fishing: could not enable the Bukkit API", e);
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
