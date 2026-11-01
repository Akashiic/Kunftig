package com.akashic.kunftig.client;

import net.minecraftforge.client.ClientCommandHandler;

import com.akashic.kunftig.CommonProxy;
import com.akashic.kunftig.client.nametag.KunftigClientCommand;
import com.akashic.kunftig.client.nametag.RgbSupport;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        ClientCommandHandler.instance.registerCommand(new KunftigClientCommand());
        RgbSupport.refresh();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Angelica's RGB switch can be toggled in game; re-read it once per tick, never per string.
            RgbSupport.refresh();
        }
    }
}
