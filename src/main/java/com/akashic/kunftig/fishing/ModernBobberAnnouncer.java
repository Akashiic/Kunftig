package com.akashic.kunftig.fishing;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import com.akashic.kunftig.KunftigConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Server side of {@link ModernBobberMode}: tells each joining player's client to predict the modern bobber. Sent as a
 * raw plugin message (no Bukkit channel registration needed); clients without Kunftig ignore unknown channels.
 */
public final class ModernBobberAnnouncer {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (KunftigConfig.fishingEnabled && KunftigConfig.fishingModernBobber
            && event.player instanceof EntityPlayerMP
            && ((EntityPlayerMP) event.player).playerNetServerHandler != null) {
            ((EntityPlayerMP) event.player).playerNetServerHandler.sendPacket(
                new S3FPacketCustomPayload(ModernBobberMode.CHANNEL, new byte[] { ModernBobberMode.MODERN }));
        }
    }
}
