package com.akashic.kunftig.client.blade;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.blade.BladeIndex;
import com.akashic.kunftig.blade.EffectWire;
import com.akashic.kunftig.blade.ObjModel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * SlashBlade-style effects on the client, ported from SlashBlade: Resharped's effect renderers (MIT code). The slash
 * arc ({@code SlashEffectRenderer}): the pack's slash model turned by the effect's yaw, pitch and roll, sweeping
 * 135 degrees over its life, fading as {@code 1 - t^4}, in up to four additive passes chosen by the concentration rank
 * (the colour turns grey below rank C), with the vertices at model height 0 transparent. Client thread only.
 */
public final class BladeEffects {

    public static final BladeEffects INSTANCE = new BladeEffects();
    /** SlashBlade's clients always draw a slash over 10 ticks (the server's lifetime is not synced). */
    private static final int SLASH_LIFETIME = 10;
    private static final int MAX_EFFECTS = 256;
    private static final int MAX_LOGGED_ERRORS = 20;

    private static final class Active {

        final EffectWire.Slash slash;
        int age;

        Active(EffectWire.Slash slash) {
            this.slash = slash;
        }
    }

    private final List<Active> effects = new ArrayList<>();
    private final Random random = new Random();
    private int loggedErrors;

    private BladeEffects() {}

    public int count() {
        return effects.size();
    }

    public void onJoinGame(NetHandlerPlayClient handler) {
        effects.clear();
        handler.addToSendQueue(
            new C17PacketCustomPayload("REGISTER", EffectWire.CHANNEL.getBytes(StandardCharsets.UTF_8)));
    }

    public void clear() {
        effects.clear();
    }

    public void onPayload(byte[] data) {
        EffectWire.Slash s = EffectWire.readSlash(data);
        if (s != null) {
            add(s);
        }
    }

    /** Adds a slash as if the server had sent it (also the harness). */
    public void add(EffectWire.Slash s) {
        if (effects.size() >= MAX_EFFECTS) {
            effects.remove(0);
        }
        effects.add(new Active(s));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.isGamePaused()) {
            return;
        }
        if (mc.theWorld == null) {
            effects.clear();
            return;
        }
        for (Iterator<Active> it = effects.iterator(); it.hasNext();) {
            Active a = it.next();
            a.age++;
            if (a.age > SLASH_LIFETIME) {
                it.remove();
                continue;
            }
            if (EffectWire.rankCode(a.slash.rank) > 5 && (a.age % 2 == 0 || a.age < 5)) {
                crits(mc, a);
            }
        }
    }

    /** SlashBlade's slash crits at rank SS and SSS: two crit particles along the sweep. */
    private void crits(Minecraft mc, Active a) {
        EffectWire.Slash s = a.slash;
        float progress = a.age / (float) SLASH_LIFETIME;
        double[] normal = { 1, 0, 0 };
        double[] dir = { 0, 0, 1 };
        for (double[] v : new double[][] { normal, dir }) {
            rotY(v, 60 + s.rotationOffset - 200F * progress);
            rotZ(v, s.roll);
            rotX(v, s.pitch);
            rotY(v, -s.yaw);
        }
        double px = s.x + normal[0] * s.baseSize * 2.5;
        double py = s.y + normal[1] * s.baseSize * 2.5;
        double pz = s.z + normal[2] * s.baseSize * 2.5;
        double vx = dir[0] + normal[0];
        double vy = dir[1] + normal[1];
        double vz = dir[2] + normal[2];
        mc.theWorld.spawnParticle("crit", px, py, pz, vx, vy, vz);
        float r = random.nextFloat() + 0.5F;
        mc.theWorld.spawnParticle("crit", px + dir[0] * r, py + dir[1] * r, pz + dir[2] * r, vx, vy, vz);
    }

    private static void rotX(double[] v, double deg) {
        double a = Math.toRadians(deg);
        double y = v[1] * Math.cos(a) - v[2] * Math.sin(a);
        double z = v[1] * Math.sin(a) + v[2] * Math.cos(a);
        v[1] = y;
        v[2] = z;
    }

    private static void rotY(double[] v, double deg) {
        double a = Math.toRadians(deg);
        double x = v[0] * Math.cos(a) + v[2] * Math.sin(a);
        double z = -v[0] * Math.sin(a) + v[2] * Math.cos(a);
        v[0] = x;
        v[2] = z;
    }

    private static void rotZ(double[] v, double deg) {
        double a = Math.toRadians(deg);
        double x = v[0] * Math.cos(a) - v[1] * Math.sin(a);
        double y = v[0] * Math.sin(a) + v[1] * Math.cos(a);
        v[0] = x;
        v[1] = y;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (effects.isEmpty() || mc.renderViewEntity == null) {
            return;
        }
        EntityLivingBase view = mc.renderViewEntity;
        float pt = event.partialTicks;
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        for (Active a : effects) {
            try {
                renderSlash(a, a.slash.x - cx, a.slash.y - cy, a.slash.z - cz, pt);
            } catch (RuntimeException e) {
                if (loggedErrors++ < MAX_LOGGED_ERRORS) {
                    Kunftig.LOG.error("Kunftig blades: cannot draw a slash", e);
                }
            }
        }
    }

    /** SlashBlade's {@code SlashEffectRenderer.render}, pass for pass. */
    private void renderSlash(Active a, double dx, double dy, double dz, float pt) {
        EffectWire.Slash s = a.slash;
        BladeClient c = BladeClient.INSTANCE;
        BladeIndex.Effect fx = c.index()
            .effect(s.namespace, BladeIndex.SLASH);
        ObjModel model = fx == null ? null : c.model(fx.obj);
        if (model == null) {
            return;
        }
        List<ObjModel.Group> base = model.groups("base");
        ResourceLocation texture = c.location(fx.texture);
        int life = SLASH_LIFETIME;
        float progress = Math.min(life, a.age + pt) / life;
        double baseAlpha = Math.min(life, Math.max(0, life - a.age - pt)) / (double) life;
        baseAlpha = -Math.pow(baseAlpha - 1, 4.0) + 1.0;
        int alpha = 0xFF & (int) (0xFF * baseAlpha);
        int rank = EffectWire.rankCode(s.rank);
        int color = rank < 2 ? 0x555555 : s.color & 0xFFFFFF;
        float yscale = 0.03F;
        float scale = s.baseSize * lerp(progress, 0.03F, 0.035F);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(dx, dy, dz);
            GL11.glRotatef(-s.yaw - 90F, 0, 1, 0);
            GL11.glRotatef(s.pitch, 0, 0, 1);
            GL11.glRotatef(s.roll, 1, 0, 0);
            GL11.glRotatef(s.rotationOffset - 135F * progress, 0, 1, 0);
            GL11.glScalef(1, 0.25F, 1);
            GL11.glScalef(1.2F, 1.2F, 1.2F);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            if (rank >= 5) { // black alpha inside
                pass(
                    base,
                    s.baseSize * lerp(progress, 0.035F, 0.03F),
                    yscale,
                    -0.8F + progress * 0.3F,
                    0x222222,
                    alpha,
                    true);
            }
            if (rank >= 1) { // colour alpha base
                pass(base, scale, yscale, -0.35F + progress * -0.15F, color, alpha, true);
            }
            if (rank >= 3) { // white add outside
                pass(
                    base,
                    s.baseSize * lerp(progress, 0.03F, 0.0375F),
                    yscale,
                    -0.5F + progress * -0.2F,
                    0x404040,
                    alpha,
                    false);
            }
            pass(base, scale, yscale, -0.35F + progress * -0.15F, color, alpha, false); // colour add base
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    /**
     * One pass: scaled, the UV operator {@code (1, 1, 0, vOffset)}, the colour with the pass alpha (0 at model height
     * 0). {@code colorWrite}: SlashBlade's ColorWrite state (additive, culled, nearest); otherwise Luminous (additive,
     * no culling, linear).
     */
    private static void pass(List<ObjModel.Group> groups, float s, float ys, float vOffset, int rgb, int alpha,
        boolean colorWrite) {
        GL11.glPushMatrix();
        GL11.glScalef(s, ys, s);
        effectState(colorWrite, () -> tessellateFaded(groups, 1, 1, 0, vOffset, rgb, alpha));
        GL11.glPopMatrix();
    }

    /** The additive effect states (fullbright, no depth write), culled and nearest, or not culled and linear. */
    static void effectState(boolean culled, Runnable draw) {
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            if (culled) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
            GL11.glDisable(GL11.GL_LIGHTING);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            int filter = culled ? GL11.GL_NEAREST : GL11.GL_LINEAR;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            draw.run();
        } finally {
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        }
    }

    /** Triangles with a per-vertex colour whose alpha is 0 where the model's y is exactly 0. */
    static void tessellateFaded(List<ObjModel.Group> groups, float uScale, float vScale, float uOffset, float vOffset,
        int rgb, int alpha) {
        Tessellator t = Tessellator.instance;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        t.startDrawing(GL11.GL_TRIANGLES);
        for (ObjModel.Group grp : groups) {
            for (ObjModel.Face f : grp.faces) {
                int[] order = f.size() == 3 ? new int[] { 0, 1, 2 } : new int[] { 0, 1, 2, 0, 2, 3 };
                for (int i : order) {
                    float[] p = f.positions[i];
                    t.setColorRGBA(r, g, b, p[1] == 0 ? 0 : alpha);
                    float[] uv = f.uv(i, uScale, vScale, uOffset, vOffset);
                    t.addVertexWithUV(p[0], p[1], p[2], uv[0], uv[1]);
                }
            }
        }
        t.draw();
    }
}
