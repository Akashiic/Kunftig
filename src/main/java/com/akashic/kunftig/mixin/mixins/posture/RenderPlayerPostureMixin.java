package com.akashic.kunftig.mixin.mixins.posture;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.posture.PostureClient;
import com.akashic.kunftig.posture.PostureWire;

/**
 * Postures (API 15) on the vanilla biped: one who lies is laid down as vanilla lays a sleeper ({@code rotateCorpse}'s
 * bed turn), the head towards the posture's yaw and the middle of the body on the point; the local player's model is
 * drawn as much higher as he was lowered.
 */
@Mixin(RenderPlayer.class)
public abstract class RenderPlayerPostureMixin {

    @Inject(
        method = "rotateCorpse(Lnet/minecraft/client/entity/AbstractClientPlayer;FFF)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private void kunftig$posture(AbstractClientPlayer player, float age, float yaw, float partial, CallbackInfo ci) {
        PostureClient.Held h = PostureClient.INSTANCE.of(player);
        if (h == null || !h.lies() || !player.isEntityAlive() || player.isPlayerSleeping()) {
            return;
        }
        double rad = Math.toRadians(h.yaw);
        // the head points to (-sin, cos) of the yaw: back by half a body, so that the middle is on the point
        GL11.glTranslatef(
            (float) (Math.sin(rad) * PostureWire.HALF_BODY),
            h.rise,
            (float) (-Math.cos(rad) * PostureWire.HALF_BODY));
        GL11.glRotatef(90 - h.yaw, 0, 1, 0);
        GL11.glRotatef(90, 0, 0, 1);
        GL11.glRotatef(270, 0, 1, 0);
        ci.cancel();
    }
}
