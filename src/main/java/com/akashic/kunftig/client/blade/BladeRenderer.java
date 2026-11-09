package com.akashic.kunftig.client.blade;

import java.nio.FloatBuffer;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.blade.BladeHolder;
import com.akashic.kunftig.blade.BladeIndex;
import com.akashic.kunftig.blade.BodyPose;
import com.akashic.kunftig.blade.Mmd;
import com.akashic.kunftig.blade.ObjModel;
import com.akashic.kunftig.blade.Vmd;
import com.akashic.kunftig.client.compat.FuturaModels;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Draws SlashBlade-style blades as SlashBlade: Resharped's {@code LayerMainBlade} and {@code BladeFirstPersonRender}
 * do (MIT code, ported to 1.7.10's fixed pipeline): the blade at the blade rig's {@code hardpointA} and the sheath at
 * {@code hardpointB}, in the player model's space; the blade not in use (Backhand's off hand, or hotbar slot 0) at the
 * hip by its carry type; and in first person from a reset view matrix. Two passes per part, with SlashBlade's GL state:
 * normal (alpha blend, entity lighting, back faces culled, depth written, nearest texels) and luminous (additive, full
 * bright, no culling, no depth write, linear texels). Client thread only.
 */
public final class BladeRenderer {

    public static final BladeRenderer INSTANCE = new BladeRenderer();
    /** 1.7.10 puts the player model 1.5078125 below the neck, 1.20 1.501: the difference. */
    private static final float MODEL_OFFSET_1_7 = 0.0068125F;
    private static final float MOTION_SCALE = (float) BladeHolder.BLOCKS_PER_MMD;
    private static final float MODEL_SCALE = (float) (1 / BladeHolder.OBJ_PER_MMD);
    private static final ResourceLocation CHARGE_TEXTURE = new ResourceLocation(
        "textures/entity/creeper/creeper_armor.png");
    private static final int MAX_LOGGED_ERRORS = 20;

    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private int loggedErrors;

    private BladeRenderer() {}

    // ---- third person ----------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderSpecials(RenderPlayerEvent.Specials.Post event) {
        BladeClient c = BladeClient.INSTANCE;
        if (!c.active()) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        try {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, MODEL_OFFSET_1_7, 0);
            renderStandby(player, false);
            ItemStack held = player.getHeldItem();
            BladeIndex.Blade blade = c.resolve(held);
            if (blade != null) {
                renderMain(player, held, blade, event.partialRenderTick);
            }
        } catch (RuntimeException e) {
            if (loggedErrors++ < MAX_LOGGED_ERRORS) {
                Kunftig.LOG.error("Kunftig blades: cannot draw a blade", e);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * The blade in the main hand and its sheath, moved by the blade rig (SlashBlade's {@code LayerMainBlade.render}).
     */
    void renderMain(EntityLivingBase entity, ItemStack stack, BladeIndex.Blade blade, float partial) {
        BladeClient c = BladeClient.INSTANCE;
        ObjModel model = c.model(blade.obj);
        Vmd motion = c.motion(blade.bladeMotion);
        if (model == null || motion == null) {
            return;
        }
        double frame = c.bladeFrame(entity, motion, partial);
        double[] hardpointA = BladeHolder.blade(motion, frame);
        double[] hardpointB = BladeHolder.sheath(motion, frame);
        ResourceLocation texture = c.location(blade.texture);
        boolean broken = BladeClient.broken(stack, blade);
        GL11.glPushMatrix();
        try {
            setUserPose(entity, partial);
            GL11.glTranslatef(0, 1.5F, 0);
            GL11.glScalef(MOTION_SCALE, MOTION_SCALE, MOTION_SCALE);
            GL11.glRotatef(180, 0, 0, 1); // MMD to Minecraft
            GL11.glPushMatrix();
            hardpoint(hardpointA);
            GL11.glScalef(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
            String part = broken ? "blade_damaged" : "blade";
            drawNormal(model, part, texture);
            drawLuminous(model, part + "_luminous", texture);
            GL11.glPopMatrix();
            GL11.glPushMatrix();
            hardpoint(hardpointB);
            GL11.glScalef(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
            drawNormal(model, "sheath", texture);
            drawLuminous(model, "sheath_luminous", texture);
            if (c.charged(entity)) {
                drawCharge(model, "effect", entity.ticksExisted + partial);
            }
            GL11.glPopMatrix();
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * SlashBlade's {@code setUserPose}: undoes, roughly as SlashBlade does, the whole-body motion the model inherited,
     * since the blade motion already contains it.
     */
    private static void setUserPose(EntityLivingBase entity, float partial) {
        BladeClient c = BladeClient.INSTANCE;
        BladeIndex.Blade blade = c.resolve(entity.getHeldItem());
        BodyPose pose = blade == null ? null : c.bodyPose(entity, c.motion(blade.playerMotion), partial);
        if (pose == null) {
            return;
        }
        GL11.glTranslatef(-pose.bodyPosition[0], pose.bodyPosition[1] + BodyPose.BODY_PIVOT, -pose.bodyPosition[2]);
        GL11.glRotatef((float) Math.toDegrees(pose.bodyRotation[2]), 0, 0, 1);
        GL11.glRotatef((float) Math.toDegrees(pose.bodyRotation[1]), 0, 1, 0);
        GL11.glRotatef((float) Math.toDegrees(pose.bodyRotation[0]), 1, 0, 0);
        GL11.glTranslatef(0, -BodyPose.BODY_PIVOT, 0);
    }

    /** {@code scale(-1,1,1) · M · scale(-1,1,1)}: the MMD matrix, in MMD order, is already column-major for GL. */
    private void hardpoint(double[] m) {
        GL11.glScalef(-1, 1, 1);
        matrix.clear();
        matrix.put(Mmd.gl(m));
        matrix.flip();
        GL11.glMultMatrix(matrix);
        GL11.glScalef(-1, 1, 1);
    }

    /** The blade not in use: Backhand's off hand, or hotbar slot 0 while another slot is selected; null if none. */
    private static ItemStack standbyStack(EntityPlayer player) {
        ItemStack stack = offhandBlade(player);
        if (stack == null && player.inventory.currentItem != 0) {
            ItemStack slot0 = player.inventory.getStackInSlot(0);
            if (BladeClient.INSTANCE.resolve(slot0) != null) {
                stack = slot0;
            }
        }
        return stack;
    }

    /** The blade not in use, at the hip by its carry type (SlashBlade's {@code renderStandbyBlade}). */
    void renderStandby(EntityPlayer player, boolean ownFirstPerson) {
        BladeClient c = BladeClient.INSTANCE;
        ItemStack stack = standbyStack(player);
        BladeIndex.Blade blade = c.resolve(stack);
        if (blade == null) {
            return;
        }
        ObjModel model = c.model(blade.obj);
        if (model == null) {
            return;
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0, 1.5F, 0);
            switch (blade.carry) {
                case PSO2:
                    if (ownFirstPerson) {
                        return;
                    }
                    GL11.glTranslatef(1F, -1.125F, 0.20F);
                    rotateZYX(-0.122173F, 0, 0);
                    break;
                case KATANA:
                    GL11.glTranslatef(0.25F, -0.875F, -0.55F);
                    rotateZYX(3.1415927F, 1.570796F, 0.261799F);
                    break;
                case DEFAULT:
                    GL11.glTranslatef(0.25F, -0.875F, -0.55F);
                    rotateZYX(0F, 1.570796F, 0.261799F);
                    break;
                case NINJA:
                    if (ownFirstPerson) {
                        return;
                    }
                    GL11.glTranslatef(-0.5F, -2F, 0.20F);
                    rotateZYX(-2.094395F, 0F, 3.1415927F);
                    break;
                case RNINJA:
                    if (ownFirstPerson) {
                        return;
                    }
                    GL11.glTranslatef(0.5F, -2F, 0.20F);
                    rotateZYX(-1.047198F, 0, 0);
                    break;
                default:
                    return;
            }
            GL11.glScalef(MOTION_SCALE, MOTION_SCALE, MOTION_SCALE);
            GL11.glScalef(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
            ResourceLocation texture = c.location(blade.texture);
            String part = BladeClient.broken(stack, blade) ? "blade_damaged" : "blade";
            drawNormal(model, part, texture);
            drawLuminous(model, part + "_luminous", texture);
            drawNormal(model, "sheath", texture);
            drawLuminous(model, "sheath_luminous", texture);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** JOML's {@code rotateZYX(z, y, x)} = Rz·Ry·Rx (radians). */
    private static void rotateZYX(float z, float y, float x) {
        GL11.glRotatef((float) Math.toDegrees(z), 0, 0, 1);
        GL11.glRotatef((float) Math.toDegrees(y), 0, 1, 0);
        GL11.glRotatef((float) Math.toDegrees(x), 1, 0, 0);
    }

    private static volatile java.lang.invoke.MethodHandle backhandOffhand;
    private static volatile boolean backhandLooked;

    /** The blade in Backhand's off hand, or null (no Backhand, or not a blade). */
    private static ItemStack offhandBlade(EntityPlayer player) {
        if (!backhandLooked) {
            synchronized (BladeRenderer.class) {
                if (!backhandLooked) {
                    try {
                        backhandOffhand = java.lang.invoke.MethodHandles.publicLookup()
                            .findStatic(
                                Class.forName("xonin.backhand.api.core.BackhandUtils"),
                                "getOffhandItem",
                                java.lang.invoke.MethodType.methodType(ItemStack.class, EntityPlayer.class));
                    } catch (Throwable ignored) {
                        backhandOffhand = null;
                    }
                    backhandLooked = true;
                }
            }
        }
        java.lang.invoke.MethodHandle h = backhandOffhand;
        if (h == null) {
            return null;
        }
        try {
            ItemStack s = (ItemStack) h.invoke(player);
            return BladeClient.INSTANCE.resolve(s) != null ? s : null;
        } catch (Throwable e) {
            return null;
        }
    }

    // ---- player models (3D Futura Engine) ----------------------------------------------------------------------

    /** Upstream YSM's {@code BladeLocator}: the locator at the middle of the hilt, the blade forward. */
    private static final int AT_HILT = 0;
    /**
     * Upstream YSM's {@code SheathLocator} and, with the blade locators, {@code LeftWaistLocator}: the sheath's end.
     */
    private static final int AT_SHEATH_END = 1;
    /** Upstream YSM's waist locators for models without blade locators (the "old" SlashBlade layout). */
    private static final int AT_WAIST = 2;

    /**
     * A player drawn with a 3D Futura Engine model: {@code RenderPlayerEvent.Specials} does not fire (the biped's
     * {@code renderEquippedItems} does not run) and the blade rig's hardpoints are in the biped's space, so the blade
     * goes on the model's locators, after the model is drawn, as upstream YSM's SlashBlade compatibility places it
     * ({@code SlashBladeRender}). The body poses of the blade ({@link BladePose}) are the biped's and do not apply.
     */
    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        BladeClient c = BladeClient.INSTANCE;
        EntityPlayer player = event.entityPlayer;
        if (!c.active() || player.isInvisible() || !FuturaModels.drewModel(player)) {
            return;
        }
        try {
            renderOnModel(player, event.partialRenderTick);
        } catch (RuntimeException e) {
            if (loggedErrors++ < MAX_LOGGED_ERRORS) {
                Kunftig.LOG.error("Kunftig blades: cannot draw a blade on a player model", e);
            }
        }
    }

    /**
     * The held blade and its sheath on the model's locators. A model with {@code BladeLocator}, {@code SheathLocator}
     * and {@code LeftWaistLocator} (YSM 2.x's convention): while a combo plays, the blade at {@code BladeLocator} and
     * the sheath at {@code SheathLocator}; otherwise the sheathed blade at {@code LeftWaistLocator}. Upstream lets the
     * model's {@code slashblade:*} animations hide the locators that are not in use; Kunftig does not play those
     * animations yet, so its combo state chooses, and since models keep {@code BladeLocator} and {@code SheathLocator}
     * hidden outside those animations (the built-in ones in a {@code pre_parallel}), a hidden one is replaced by
     * {@code RightHandLocator} (where the built-in models put {@code BladeLocator}) and {@code LeftWaistLocator}
     * (where they put {@code SheathLocator}). A model without them: the blade at {@code RightHandLocator} (where
     * the built-in models put {@code BladeLocator}) while a combo plays, sheathed at {@code LeftWaistLocator} when
     * idle, the sheath at {@code LeftWaistLocator} during a combo; without a waist locator the blade stays in the hand
     * and the sheath is not drawn. The blade not in use goes, sheathed, to {@code RightWaistLocator} (upstream's off
     * hand), or nowhere. A locator the pose hides (scale 0) hides what is drawn there.
     */
    void renderOnModel(EntityPlayer player, float partial) {
        BladeClient c = BladeClient.INSTANCE;
        ItemStack standby = standbyStack(player);
        BladeIndex.Blade standbyBlade = c.resolve(standby);
        if (standbyBlade != null) {
            onLocator(player, FuturaModels.RIGHT_WAIST, AT_WAIST, standby, standbyBlade, true, true, false, partial);
        }
        ItemStack held = player.getHeldItem();
        BladeIndex.Blade blade = c.resolve(held);
        if (blade == null) {
            return;
        }
        boolean drawn = c.drawn(player, partial);
        boolean bladeLocators = FuturaModels.hasLocator(player, FuturaModels.BLADE)
            && FuturaModels.hasLocator(player, FuturaModels.SHEATH)
            && FuturaModels.hasLocator(player, FuturaModels.LEFT_WAIST);
        if (bladeLocators) {
            if (drawn) {
                // Models hide these two until a slashblade:* animation shows them (the built-in ones in a
                // pre_parallel): without those animations, the hand and the waist stand in.
                if (!onLocator(player, FuturaModels.BLADE, AT_HILT, held, blade, true, false, false, partial)) {
                    onLocator(player, FuturaModels.RIGHT_HAND, AT_HILT, held, blade, true, false, false, partial);
                }
                if (!onLocator(player, FuturaModels.SHEATH, AT_SHEATH_END, held, blade, false, true, true, partial)) {
                    onLocator(player, FuturaModels.LEFT_WAIST, AT_SHEATH_END, held, blade, false, true, true, partial);
                }
            } else {
                onLocator(player, FuturaModels.LEFT_WAIST, AT_SHEATH_END, held, blade, true, true, true, partial);
            }
        } else if (drawn
            || !onLocator(player, FuturaModels.LEFT_WAIST, AT_WAIST, held, blade, true, true, true, partial)) {
                onLocator(player, FuturaModels.RIGHT_HAND, AT_HILT, held, blade, true, false, false, partial);
                if (drawn) {
                    onLocator(player, FuturaModels.LEFT_WAIST, AT_WAIST, held, blade, false, true, true, partial);
                }
            }
    }

    /**
     * Draws parts of a blade at a locator of the model draw that just ended, in upstream YSM's frame for that locator
     * ({@code SlashBladeRender}: 1/100 block per OBJ unit, turned from the OBJ's axes).
     *
     * @return false when the locator is missing or hidden (nothing drawn)
     */
    private boolean onLocator(EntityPlayer player, String locator, int frame, ItemStack stack, BladeIndex.Blade blade,
        boolean bladePart, boolean sheath, boolean charge, float partial) {
        BladeClient c = BladeClient.INSTANCE;
        ObjModel model = c.model(blade.obj);
        float[] at = model == null ? null : FuturaModels.locatorInLastDraw(player, locator);
        if (at == null) {
            return false;
        }
        GL11.glPushMatrix();
        try {
            matrix.clear();
            matrix.put(at);
            matrix.flip();
            GL11.glMultMatrix(matrix);
            switch (frame) {
                case AT_HILT:
                    GL11.glTranslatef(0, 0.035F, 0);
                    GL11.glScalef(0.01F, 0.01F, 0.01F);
                    GL11.glRotatef(-90, 0, 1, 0);
                    GL11.glRotatef(180, 1, 0, 0);
                    break;
                case AT_SHEATH_END:
                    GL11.glTranslatef(0, 0.025F, -0.6F);
                    GL11.glScalef(0.01F, 0.01F, 0.01F);
                    GL11.glRotatef(-90, 0, 1, 0);
                    GL11.glRotatef(180, 0, 0, 1);
                    break;
                default:
                    GL11.glTranslatef(0, 0, -0.7F);
                    GL11.glScalef(0.01F, 0.01F, 0.01F);
                    GL11.glRotatef(-90, 0, 1, 0);
                    GL11.glRotatef(180, 0, 0, 1);
                    break;
            }
            ResourceLocation texture = c.location(blade.texture);
            if (bladePart) {
                String part = BladeClient.broken(stack, blade) ? "blade_damaged" : "blade";
                drawNormal(model, part, texture);
                drawLuminous(model, part + "_luminous", texture);
            }
            if (sheath) {
                drawNormal(model, "sheath", texture);
                drawLuminous(model, "sheath_luminous", texture);
                if (charge && c.charged(player)) {
                    drawCharge(model, "effect", player.ticksExisted + partial);
                }
            }
        } finally {
            GL11.glPopMatrix();
        }
        return true;
    }

    // ---- first person ----------------------------------------------------------------------------------------------

    /**
     * SlashBlade's first person: the view-space matrix reset, the body pivot half a block ahead, the model space
     * emulated ({@code rotZ 180}, {@code scale 1.2, 1, 1}), the pitch kept body-horizontal, then the third-person draw.
     * Called from the item renderer (EQUIPPED_FIRST_PERSON); no arm is drawn.
     */
    void renderFirstPerson(EntityPlayer player, ItemStack stack, BladeIndex.Blade blade) {
        float partial = BladeClient.INSTANCE.partialTicks();
        GL11.glPushMatrix();
        boolean normalize = GL11.glIsEnabled(GL11.GL_NORMALIZE);
        try {
            GL11.glLoadIdentity();
            GL11.glEnable(GL11.GL_NORMALIZE); // the 1.2 X scale is not uniform
            GL11.glTranslatef(0, 0, -0.5F);
            GL11.glRotatef(180, 0, 0, 1);
            GL11.glScalef(1.2F, 1, 1);
            float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partial;
            GL11.glRotatef(-pitch, 1, 0, 0);
            renderStandby(player, true);
            renderMain(player, stack, blade, partial);
        } finally {
            if (!normalize) {
                GL11.glDisable(GL11.GL_NORMALIZE);
            }
            GL11.glPopMatrix();
        }
    }

    // ---- passes ----------------------------------------------------------------------------------------------------

    /** The normal pass: alpha blend, entity lighting, back faces culled, depth written, nearest texels. */
    static void drawNormal(ObjModel model, String part, ResourceLocation texture) {
        List<ObjModel.Group> groups = model.groups(part);
        if (groups.isEmpty()) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            filter(GL11.GL_NEAREST);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1, 1, 1, 1);
            tessellate(groups, 1, 1, 0, 0);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** The luminous pass: additive, full bright, no culling, no depth write, linear texels. */
    static void drawLuminous(ObjModel model, String part, ResourceLocation texture) {
        List<ObjModel.Group> groups = model.groups(part);
        if (groups.isEmpty()) {
            return;
        }
        luminous(() -> {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            filter(GL11.GL_LINEAR);
            tessellate(groups, 1, 1, 0, 0);
        });
    }

    /** The charge glow: the {@code effect} group, additive, with the creeper charge texture scrolling. */
    private static void drawCharge(ObjModel model, String part, float time) {
        List<ObjModel.Group> groups = model.groups(part);
        if (groups.isEmpty()) {
            return;
        }
        luminous(() -> {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(CHARGE_TEXTURE);
            filter(GL11.GL_NEAREST);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glTranslatef(time * 0.1F % 1.0F, time * 0.01F % 1.0F, 0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            try {
                tessellate(groups, 1, 1, 0, 0);
            } finally {
                GL11.glMatrixMode(GL11.GL_TEXTURE);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
            }
        });
    }

    /** Runs a draw in the luminous state and restores everything after. */
    static void luminous(Runnable draw) {
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glColor4f(1, 1, 1, 1);
            draw.run();
        } finally {
            GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        }
    }

    private static void filter(int mode) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mode);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mode);
    }

    /** The faces of the groups as triangles, with per-vertex normals and SlashBlade's UVs. */
    static void tessellate(List<ObjModel.Group> groups, float uScale, float vScale, float uOffset, float vOffset) {
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLES);
        for (ObjModel.Group g : groups) {
            for (ObjModel.Face f : g.faces) {
                if (f.size() == 3) {
                    vertex(t, f, 0, uScale, vScale, uOffset, vOffset);
                    vertex(t, f, 1, uScale, vScale, uOffset, vOffset);
                    vertex(t, f, 2, uScale, vScale, uOffset, vOffset);
                } else {
                    for (int i : new int[] { 0, 1, 2, 0, 2, 3 }) {
                        vertex(t, f, i, uScale, vScale, uOffset, vOffset);
                    }
                }
            }
        }
        t.draw();
    }

    private static void vertex(Tessellator t, ObjModel.Face f, int i, float uScale, float vScale, float uOffset,
        float vOffset) {
        float[] n = f.normal(i);
        t.setNormal(n[0], n[1], n[2]);
        float[] uv = f.uv(i, uScale, vScale, uOffset, vOffset);
        float[] p = f.positions[i];
        t.addVertexWithUV(p[0], p[1], p[2], uv[0], uv[1]);
    }
}
