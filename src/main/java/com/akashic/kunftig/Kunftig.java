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
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
