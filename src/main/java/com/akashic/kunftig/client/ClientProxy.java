package com.akashic.kunftig.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import com.akashic.kunftig.CommonProxy;
import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.itemmodel.ItemModels;
import com.akashic.kunftig.client.nametag.KunftigClientCommand;
import com.akashic.kunftig.client.nametag.RgbSupport;
import com.akashic.kunftig.client.rodmodel.RodModels;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Before the first items atlas stitch, which happens after preInit and before init.
        if (!ItemModels.disabled()) {
            MinecraftForge.EVENT_BUS.register(ItemModels.INSTANCE);
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        ClientCommandHandler.instance.registerCommand(new KunftigClientCommand());
        com.akashic.kunftig.client.compat.NecroTempusTitleFallback.install();
        if (!Boolean.getBoolean("kunftig.holograms.disable")) {
            MinecraftForge.EVENT_BUS.register(com.akashic.kunftig.client.hologram.HologramClient.INSTANCE);
        }
        if (!com.akashic.kunftig.client.display.DisplayClient.disabled()) {
            // The displays (API 8): their manifests now and on every reload, their clock and the world draw.
            ((IReloadableResourceManager) Minecraft.getMinecraft()
                .getResourceManager())
                    .registerReloadListener(com.akashic.kunftig.client.display.DisplayClient.INSTANCE);
            FMLCommonHandler.instance()
                .bus()
                .register(com.akashic.kunftig.client.display.DisplayClient.INSTANCE);
            MinecraftForge.EVENT_BUS.register(com.akashic.kunftig.client.display.DisplayClient.INSTANCE);
        }
        if (!RodModels.disabled()) {
            // Reads the manifests now and on every resource reload (server resource packs included).
            ((IReloadableResourceManager) Minecraft.getMinecraft()
                .getResourceManager()).registerReloadListener(RodModels.INSTANCE);
            FMLCommonHandler.instance()
                .bus()
                .register(RodModels.INSTANCE);
            MinecraftForge.EVENT_BUS.register(RodModels.INSTANCE);
        }
        if (!com.akashic.kunftig.client.input.InputClient.disabled()) {
            // The input keys (API 9): in Controls, category Kunftig; reported only when a server binds them.
            com.akashic.kunftig.client.input.InputClient.INSTANCE.registerKeys();
            FMLCommonHandler.instance()
                .bus()
                .register(com.akashic.kunftig.client.input.InputClient.INSTANCE);
        }
        if (!Boolean.getBoolean("kunftig.screen.disable")) {
            // The screen HUD and the lock-on (API 9).
            FMLCommonHandler.instance()
                .bus()
                .register(com.akashic.kunftig.client.screen.HudClient.INSTANCE);
            MinecraftForge.EVENT_BUS.register(com.akashic.kunftig.client.screen.HudClient.INSTANCE);
            FMLCommonHandler.instance()
                .bus()
                .register(com.akashic.kunftig.client.screen.CameraClient.INSTANCE);
            MinecraftForge.EVENT_BUS.register(com.akashic.kunftig.client.screen.CameraClient.INSTANCE);
        }
        RgbSupport.refresh();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        installRodModels();
        installDisplays();
        if (new java.io.File(Minecraft.getMinecraft().mcDataDir, "kunftig-rod-harness").isFile()) {
            try {
                // Development only: the class is absent from release jars.
                Class.forName("com.akashic.kunftig.dev.RodRenderHarness")
                    .getMethod("install")
                    .invoke(null);
            } catch (ReflectiveOperationException | LinkageError e) {
                Kunftig.LOG.error("Kunftig: rod harness unavailable", e);
            }
        }
    }

    /**
     * 3D rods (issue #13): GeckoLib's side only when GeckoLib is loaded, reflectively, so that no GeckoLib class is
     * touched without it; after the other mods' init, to keep any fishing rod renderer they registered.
     */
    private static void installRodModels() {
        if (RodModels.disabled()) {
            Kunftig.LOG.warn("Kunftig 3D rods disabled by -Dkunftig.rodModels.disable=true");
            return;
        }
        if (!Loader.isModLoaded("geckolib3")) {
            Kunftig.LOG.info("Kunftig 3D rods: GeckoLib (geckolib3) not installed; rods keep their 2D icons");
            return;
        }
        try {
            Class.forName("com.akashic.kunftig.client.rodmodel.gecko.GeckoRods")
                .getMethod("install")
                .invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            Kunftig.LOG.error("Kunftig 3D rods: could not use GeckoLib; rods keep their 2D icons", e);
        }
    }

    /** Displays (API 8): GeckoLib's side only when GeckoLib is loaded, reflectively, like the rods. */
    private static void installDisplays() {
        if (com.akashic.kunftig.client.display.DisplayClient.disabled()) {
            Kunftig.LOG.warn("Kunftig displays disabled by -Dkunftig.displays.disable=true");
            return;
        }
        if (!Loader.isModLoaded("geckolib3")) {
            Kunftig.LOG.info("Kunftig displays: GeckoLib (geckolib3) not installed; displays are not drawn");
            return;
        }
        try {
            Class.forName("com.akashic.kunftig.client.display.gecko.GeckoDisplays")
                .getMethod("install")
                .invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            Kunftig.LOG.error("Kunftig displays: could not use GeckoLib; displays are not drawn", e);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Angelica's RGB switch can be toggled in game; re-read it once per tick, never per string.
            RgbSupport.refresh();
        }
    }
}
