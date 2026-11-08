package com.akashic.kunftig.mixin.mixins.screen;

import java.nio.charset.StandardCharsets;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.screen.CameraClient;
import com.akashic.kunftig.client.screen.HudClient;
import com.akashic.kunftig.screen.CameraWire;
import com.akashic.kunftig.screen.HudWire;

/**
 * Screen HUD and lock-on (API 11): register both channels on join (a new server starts with an empty HUD and a free
 * camera), let go of the target on respawn, and take their messages.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientScreenMixin {

    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void kunftig$screenJoinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        HudClient.INSTANCE.clear();
        CameraClient.INSTANCE.release();
        NetHandlerPlayClient self = (NetHandlerPlayClient) (Object) this;
        self.addToSendQueue(
            new C17PacketCustomPayload(
                "REGISTER",
                (HudWire.CHANNEL + "\0" + CameraWire.CHANNEL).getBytes(StandardCharsets.UTF_8)));
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void kunftig$screenRespawn(S07PacketRespawn packet, CallbackInfo ci) {
        CameraClient.INSTANCE.release();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void kunftig$screenPayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
        String channel = packet.func_149169_c();
        if (HudWire.CHANNEL.equals(channel)) {
            HudClient.INSTANCE.onPayload(packet.func_149168_d());
            ci.cancel();
        } else if (CameraWire.CHANNEL.equals(channel)) {
            CameraClient.INSTANCE.onPayload(packet.func_149168_d());
            ci.cancel();
        }
    }
}
