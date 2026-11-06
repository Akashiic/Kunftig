package com.akashic.kunftig.client.blade;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;

import org.lwjgl.opengl.GL11;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.blade.BladeIndex;
import com.akashic.kunftig.blade.ConcentrationRank;
import com.akashic.kunftig.blade.EffectProjectile;
import com.akashic.kunftig.blade.EffectWire;
import com.akashic.kunftig.blade.ObjModel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * SlashBlade's lock-on and rank on the client, ported from SlashBlade: Resharped (MIT code). While the local player
 * holds a blade and the Sneak key with a live lock target (sent by the server, {@link EffectWire.Lock}):
 * <ul>
 * <li>the view is pulled toward the target's feet plus half its eye height every frame
 * ({@code LockOnManager.onEntityUpdate}): {@code 0.125 * partialTicks} of the way, less inside 2 degrees of yaw, on the
 * real rotation only, so the turn shows through the frame's interpolation;</li>
 * <li>the target carries the reticle ({@code LockonCircleRender}): halfway to the camera, facing it, brackets and a
 * health ring that a depth mask shortens with the target's health, in the blade's colour; drawn on both the Pre and
 * Post living-render events, as SlashBlade's listener on the abstract event is.</li>
 * </ul>
 * The rank HUD ({@code RankRenderer}): the rank's letter filling with the progress and a gauge, at two thirds of the
 * width and a fifth of the height, for 6 seconds after each change, the letter's text variant for 1 second after a
 * rise. Client thread only.
 */
public final class BladeHud {

    public static final BladeHud INSTANCE = new BladeHud();
    private static final int MAX_LOGGED_ERRORS = 20;

    private String lockNamespace;
    private int lockTarget = -1;
    private int lockColor;
    private String rankNamespace;
    private final ConcentrationRank rank = new ConcentrationRank();
    private int loggedErrors;

    private BladeHud() {}

    public void clear() {
        lockTarget = -1;
        rankNamespace = null;
        rank.reset();
    }

    /** Forgets the lock target (entity ids change with the world). */
    public void unlock() {
        lockTarget = -1;
    }

    public void lock(EffectWire.Lock l) {
        lockNamespace = l.namespace;
        lockTarget = l.entityId;
        lockColor = l.color;
    }

    public void rank(EffectWire.Rank r) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }
        rankNamespace = r.namespace;
        rank.sync(r.points, mc.theWorld.getTotalWorldTime());
    }

    public int lockTarget() {
        return lockTarget;
    }

    public ConcentrationRank concentration() {
        return rank;
    }

    /** The live lock target while the local player holds a blade and Sneak, else null. */
    private EntityLivingBase target(Minecraft mc) {
        EntityClientPlayerMP p = mc.thePlayer;
        if (lockTarget < 0 || p == null
            || mc.theWorld == null
            || !mc.gameSettings.keyBindSneak.getIsKeyPressed()
            || BladeClient.INSTANCE.resolve(p.getHeldItem()) == null) {
            return null;
        }
        Entity e = mc.theWorld.getEntityByID(lockTarget);
        return e instanceof EntityLivingBase && e.isEntityAlive() ? (EntityLivingBase) e : null;
    }

    /** SlashBlade's {@code LockOnManager.onEntityUpdate} (render tick, start). */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase t = target(mc);
        if (t == null || mc.isGamePaused()) {
            return;
        }
        EntityClientPlayerMP p = mc.thePlayer;
        float eye = t instanceof EntityPlayer ? 1.62F : t.getEyeHeight();
        double dx = t.posX - p.posX;
        double dy = t.boundingBox.minY + eye / 2.0 - p.posY; // the local player's posY is its eyes
        double dz = t.posZ - p.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float pitch = MathHelper.wrapAngleTo180_float((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
        float yaw = MathHelper.wrapAngleTo180_float((float) Math.toDegrees(Math.atan2(dz, dx)) - 90F);
        float step = 0.125F * event.renderTickTime;
        step *= Math.min(1.0F, Math.abs(MathHelper.wrapAngleTo180_float(p.rotationYaw - yaw) * 0.5F));
        p.rotationPitch = EffectProjectile.rotLerp(step, p.rotationPitch, pitch);
        p.rotationYaw = EffectProjectile.rotLerp(step, p.rotationYaw, yaw);
        p.rotationYawHead = EffectProjectile.rotLerp(step, p.rotationYawHead, yaw);
    }

    @SubscribeEvent
    public void onRenderLivingPre(RenderLivingEvent.Pre event) {
        reticle(event);
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post event) {
        reticle(event);
    }

    /** SlashBlade's {@code LockonCircleRender.onRenderLiving}. */
    private void reticle(RenderLivingEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.entity.getEntityId() != lockTarget) {
            return;
        }
        EntityLivingBase t = target(mc);
        if (t != event.entity) {
            return;
        }
        BladeClient bc = BladeClient.INSTANCE;
        BladeIndex.Effect fx = bc.index()
            .effect(lockNamespace, BladeIndex.LOCKON);
        ObjModel model = fx == null ? null : bc.model(fx.obj);
        if (model == null) {
            return;
        }
        try {
            float health = t.getHealth() / t.getMaxHealth();
            float f = t.height * 0.5F;
            double cx = event.x;
            double cy = event.y + f;
            double cz = event.z;
            List<ObjModel.Group> base = model.groups("lockonBase");
            List<ObjModel.Group> mask = model.groups("lockonHealthMask");
            List<ObjModel.Group> value = model.groups("lockonHealth");
            mc.getTextureManager()
                .bindTexture(bc.location(fx.texture));
            GL11.glPushMatrix();
            try {
                // halfway between the target's middle and the camera
                GL11.glTranslated(
                    cx + (ActiveRenderInfo.objectX - cx) * 0.5,
                    cy + (ActiveRenderInfo.objectY - cy) * 0.5,
                    cz + (ActiveRenderInfo.objectZ - cz) * 0.5);
                GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
                GL11.glRotatef(RenderManager.instance.playerViewX, 1, 0, 0);
                GL11.glScalef(0.0025F, -0.0025F, 0.0025F);
                int alpha = 0xAA;
                BladeEffects.effectState(false, () -> BladeEffects.tessellate(base, lockColor, alpha, false));
                GL11.glPushMatrix();
                GL11.glTranslatef(0, 0, health * 10.0F);
                depthWriteState(() -> BladeEffects.tessellate(mask, 0x000000, 0x20, false));
                GL11.glPopMatrix();
                depthWriteState(() -> BladeEffects.tessellate(value, lockColor, alpha, false));
            } finally {
                GL11.glPopMatrix();
            }
        } catch (RuntimeException e) {
            if (loggedErrors++ < MAX_LOGGED_ERRORS) {
                Kunftig.LOG.error("Kunftig blades: cannot draw the lock-on reticle", e);
            }
        }
    }

    /** SlashBlade's LuminousDepthWrite state: additive, culled, linear, fullbright, writing depth. */
    private static void depthWriteState(Runnable draw) {
        BladeEffects.effectState(true, () -> {
            GL11.glDepthMask(true);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            draw.run();
        });
    }

    /** SlashBlade's {@code RankRenderer.renderTick}, once per frame. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.hideGUI || rankNamespace == null) {
            return;
        }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
            return;
        }
        long now = mc.theWorld.getTotalWorldTime();
        if (!rank.visible(now)) {
            return;
        }
        BladeIndex.Effect fx = BladeClient.INSTANCE.index()
            .effect(rankNamespace, BladeIndex.RANK);
        if (fx == null) {
            return;
        }
        ScaledResolution res = event.resolution;
        int level = rank.rank(now);
        int rankOffset = 32 * (level - 1);
        int textOffset = rank.showText(now) ? 128 : 0;
        float progress = rank.progress(now);
        int gauge = (int) (33 * progress);
        int icon = (int) (18 * progress);
        int iconInv = 17 - icon;
        mc.getTextureManager()
            .bindTexture(BladeClient.INSTANCE.location(fx.texture));
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glTranslatef(res.getScaledWidth() * 2 / 3F, res.getScaledHeight() / 5F, 0);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.0F);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glColor4f(1, 1, 1, 1);
            quad(0, 0, textOffset + 64, rankOffset, 64, 32); // letter frame
            quad(0, iconInv + 7, textOffset, rankOffset + iconInv + 7, 64, icon); // letter, filling upward
            quad(0, 32, 0, 256 - 16, 64, 16); // gauge frame
            quad(16, 32, 16, 256 - 32, gauge, 16); // gauge fill
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    /** SlashBlade's {@code drawTexturedQuad} on a 256x256 texture. */
    private static void quad(int x, int y, int u, int v, int w, int h) {
        float k = 1 / 256F;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, 0, u * k, (v + h) * k);
        t.addVertexWithUV(x + w, y + h, 0, (u + w) * k, (v + h) * k);
        t.addVertexWithUV(x + w, y, 0, (u + w) * k, v * k);
        t.addVertexWithUV(x, y, 0, u * k, v * k);
        t.draw();
    }
}
