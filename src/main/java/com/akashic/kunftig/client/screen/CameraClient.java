package com.akashic.kunftig.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.akashic.kunftig.screen.CameraWire;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client side of the lock-on ({@code KunftigCamera}, API 9): every frame turns the local player's view part of the way
 * toward the target (the same speed at any frame rate), keeping last frame's angle in step so the turn shows at once,
 * and marks the target with a turning diamond over its head. Lets go when the target is gone, dead or too far. Paused
 * while a screen is open. Client thread.
 */
public final class CameraClient {

    public static final CameraClient INSTANCE = new CameraClient();

    private int target = Integer.MIN_VALUE;
    private float strength;
    private float maxDistance;
    private boolean marker;
    private long lastFrame;

    private CameraClient() {}

    public boolean locked() {
        return target != Integer.MIN_VALUE;
    }

    public void release() {
        target = Integer.MIN_VALUE;
    }

    public void onPayload(byte[] data) {
        CameraWire.Message m = CameraWire.read(data);
        if (m == null) {
            return;
        }
        if (m.op == CameraWire.UNLOCK) {
            release();
            return;
        }
        target = m.entityId;
        strength = m.strength;
        maxDistance = m.maxDistance;
        marker = m.marker;
        lastFrame = 0;
    }

    /** The target if it is still worth following, or null (and the lock is released). */
    private Entity follow(Minecraft mc) {
        EntityClientPlayerMP player = mc.thePlayer;
        if (!locked() || player == null || mc.theWorld == null) {
            return null;
        }
        Entity e = mc.theWorld.getEntityByID(target);
        if (e == null || e.isDead
            || e instanceof EntityLivingBase && ((EntityLivingBase) e).getHealth() <= 0
            || e.getDistanceSqToEntity(player) > maxDistance * maxDistance) {
            release();
            return null;
        }
        return e;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        float ticks = lastFrame == 0 ? 0 : Math.min(2, (now - lastFrame) / 50_000_000F);
        lastFrame = now;
        Entity e = follow(mc);
        if (e == null || mc.currentScreen != null || mc.isGamePaused()) {
            return;
        }
        EntityClientPlayerMP p = mc.thePlayer;
        float pt = event.renderTickTime;
        double ex = p.prevPosX + (p.posX - p.prevPosX) * pt;
        double ey = p.prevPosY + (p.posY - p.prevPosY) * pt; // the client player's posY is at the eyes in 1.7.10
        double ez = p.prevPosZ + (p.posZ - p.prevPosZ) * pt;
        double tx = e.prevPosX + (e.posX - e.prevPosX) * pt;
        double ty = e.prevPosY + (e.posY - e.prevPosY) * pt + e.height * 0.6 - e.yOffset;
        double tz = e.prevPosZ + (e.posZ - e.prevPosZ) * pt;
        float[] look = CameraWire.look(ex, ey, ez, tx, ty, tz);
        float dyaw = CameraWire.step(CameraWire.wrap(look[0] - p.rotationYaw), strength, ticks);
        float dpitch = CameraWire.step(look[1] - p.rotationPitch, strength, ticks);
        p.rotationYaw += dyaw;
        p.prevRotationYaw += dyaw;
        p.rotationPitch = Math.max(-90, Math.min(90, p.rotationPitch + dpitch));
        p.prevRotationPitch = Math.max(-90, Math.min(90, p.prevRotationPitch + dpitch));
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity e = marker ? follow(mc) : null;
        if (e == null || mc.renderViewEntity == null) {
            return;
        }
        float pt = event.partialTicks;
        EntityLivingBase view = mc.renderViewEntity;
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - cx;
        double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - cy + e.height - e.yOffset + 0.6;
        double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - cz;
        float spin = (mc.theWorld.getTotalWorldTime() % 40 + pt) * 9;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(spin, 0, 1, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(2.5F);
            GL11.glColor4f(1, 0.25F, 0.2F, 0.9F);
            float s = 0.22F;
            for (int face = 0; face < 2; face++) {
                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex3f(0, s, 0);
                GL11.glVertex3f(face == 0 ? s : 0, 0, face == 0 ? 0 : s);
                GL11.glVertex3f(0, -s, 0);
                GL11.glVertex3f(face == 0 ? -s : 0, 0, face == 0 ? 0 : -s);
                GL11.glEnd();
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
            GL11.glColor4f(1, 1, 1, 1);
        }
    }
}
