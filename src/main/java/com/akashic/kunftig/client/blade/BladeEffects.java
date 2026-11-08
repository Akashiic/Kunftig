package com.akashic.kunftig.client.blade;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.blade.BladeIndex;
import com.akashic.kunftig.blade.EffectProjectile;
import com.akashic.kunftig.blade.EffectWire;
import com.akashic.kunftig.blade.ObjModel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * SlashBlade-style effects on the client, ported from SlashBlade: Resharped's effect renderers (MIT code), with the
 * models and textures of the pack namespace each effect names:
 * <ul>
 * <li>the slash arc ({@code SlashEffectRenderer}): the slash model turned by the effect's yaw, pitch and roll, sweeping
 * 135 degrees over its life, fading as {@code 1 - t^4}, in up to four additive passes chosen by the concentration rank
 * (the colour turns grey below rank C), with the vertices at model height 0 transparent;</li>
 * <li>the Judgement Cut ({@code JudgementCutRenderer}): five nested and three pulsing spheres that darken what is
 * behind them (reverse-subtract blending) in the colour's complement, and five crescents of wind spinning through
 * them, with SlashBlade's alpha bug that hides each wind for half of its cycle; crits when it ends;</li>
 * <li>drives ({@code DriveRenderer}): a crescent along the flight, fading as {@code 0.75 - t^4} by whole ticks;</li>
 * <li>summoned swords ({@code SummonedSwordRenderer}): an opaque additive sword along the flight, or stuck in an
 * entity, turning with it; crits when they shatter.</li>
 * </ul>
 * Projectiles move here between server messages ({@link EffectProjectile}). Client thread only.
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

    private static final class Cut {

        final EffectWire.JudgementCut cut;
        /** SlashBlade's JC seed: {@code random.nextInt(360)} on each client. */
        final int seed;
        int age;

        Cut(EffectWire.JudgementCut cut, int seed) {
            this.cut = cut;
            this.seed = seed;
        }
    }

    private final List<Active> effects = new ArrayList<>();
    private final List<Cut> cuts = new ArrayList<>();
    private final Map<Integer, EffectProjectile> projectiles = new LinkedHashMap<>();
    private final Random random = new Random();
    private int loggedErrors;

    private BladeEffects() {}

    public int count() {
        return effects.size() + cuts.size() + projectiles.size();
    }

    public void onJoinGame(NetHandlerPlayClient handler) {
        clear();
        BladeHud.INSTANCE.clear();
        handler.addToSendQueue(
            new C17PacketCustomPayload("REGISTER", EffectWire.CHANNEL.getBytes(StandardCharsets.UTF_8)));
    }

    public void clear() {
        effects.clear();
        cuts.clear();
        projectiles.clear();
    }

    public void onPayload(byte[] data) {
        Object m = EffectWire.read(data);
        if (m instanceof EffectWire.Slash) {
            add((EffectWire.Slash) m);
        } else if (m instanceof EffectWire.JudgementCut) {
            add((EffectWire.JudgementCut) m);
        } else if (m instanceof EffectWire.Projectile) {
            add((EffectWire.Projectile) m);
        } else if (m instanceof EffectWire.Move) {
            EffectProjectile p = projectiles.get(((EffectWire.Move) m).id);
            if (p != null) {
                p.move((EffectWire.Move) m);
            }
        } else if (m instanceof EffectWire.Stick) {
            EffectProjectile p = projectiles.get(((EffectWire.Stick) m).id);
            if (p != null) {
                p.stick((EffectWire.Stick) m);
            }
        } else if (m instanceof EffectWire.Remove) {
            remove(((EffectWire.Remove) m).id, ((EffectWire.Remove) m).burst);
        } else if (m instanceof EffectWire.Lock) {
            BladeHud.INSTANCE.lock((EffectWire.Lock) m);
        } else if (m instanceof EffectWire.Rank) {
            BladeHud.INSTANCE.rank((EffectWire.Rank) m);
        } else if (m instanceof EffectWire.Hits) {
            BladeHud.INSTANCE.hits((EffectWire.Hits) m);
        }
    }

    /** Adds a Judgement Cut as if the server had sent it (also the harness). */
    public void add(EffectWire.JudgementCut c) {
        if (cuts.size() >= MAX_EFFECTS) {
            cuts.remove(0);
        }
        cuts.add(new Cut(c, random.nextInt(360)));
    }

    /** Adds a drive or summoned sword as if the server had sent it (also the harness). */
    public void add(EffectWire.Projectile p) {
        if (projectiles.size() >= MAX_EFFECTS && !projectiles.containsKey(p.id)) {
            Iterator<Integer> first = projectiles.keySet()
                .iterator();
            first.next();
            first.remove();
        }
        projectiles.put(p.id, new EffectProjectile(p));
    }

    public EffectProjectile projectile(int id) {
        return projectiles.get(id);
    }

    /** Ends a projectile, shattering it (crits) if {@code burst}. */
    public void remove(int id, boolean burst) {
        EffectProjectile p = projectiles.remove(id);
        Minecraft mc = Minecraft.getMinecraft();
        if (p != null && burst && mc.theWorld != null) {
            burstParticles(mc, p.pos[0], p.pos[1], p.pos[2]);
        }
    }

    /** SlashBlade's {@code sendParticles(CRIT, x, y, z, 16, 0.5, 0.5, 0.5, 0.25)}. */
    private void burstParticles(Minecraft mc, double x, double y, double z) {
        for (int i = 0; i < 16; i++) {
            mc.theWorld.spawnParticle(
                "crit",
                x + random.nextGaussian() * 0.5,
                y + random.nextGaussian() * 0.5,
                z + random.nextGaussian() * 0.5,
                random.nextGaussian() * 0.25,
                random.nextGaussian() * 0.25,
                random.nextGaussian() * 0.25);
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
            clear();
            return;
        }
        for (Iterator<Cut> it = cuts.iterator(); it.hasNext();) {
            Cut c = it.next();
            c.age++;
            if (c.age > c.cut.lifetime) { // SlashBlade's burst: removed on the server's tick 11
                burstParticles(mc, c.cut.x, c.cut.y, c.cut.z);
                it.remove();
            }
        }
        EffectProjectile.BlockClip clip = (from, to) -> {
            MovingObjectPosition hit = mc.theWorld.func_147447_a(
                Vec3.createVectorHelper(from[0], from[1], from[2]),
                Vec3.createVectorHelper(to[0], to[1], to[2]),
                false,
                true,
                false);
            return hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK ? null
                : new double[] { hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord };
        };
        for (Iterator<EffectProjectile> it = projectiles.values()
            .iterator(); it.hasNext();) {
            EffectProjectile p = it.next();
            if (p.stuckIn >= 0) {
                Entity e = mc.theWorld.getEntityByID(p.stuckIn);
                if (e == null || e.isDead) { // SlashBlade: the target died, the sword bursts
                    burstParticles(mc, p.pos[0], p.pos[1], p.pos[2]);
                    it.remove();
                    continue;
                }
                double[] at = stuckAt(e, 1);
                System.arraycopy(at, 0, p.pos, 0, 3);
            }
            p.tick(clip);
            if (p.dead) {
                it.remove();
            }
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
        if (count() == 0 || mc.renderViewEntity == null) {
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
                logError("a slash", e);
            }
        }
        for (Cut c : cuts) {
            try {
                renderJudgementCut(c, c.cut.x - cx, c.cut.y - cy, c.cut.z - cz, pt);
            } catch (RuntimeException e) {
                logError("a Judgement Cut", e);
            }
        }
        for (EffectProjectile p : projectiles.values()) {
            try {
                renderProjectile(mc, p, cx, cy, cz, pt);
            } catch (RuntimeException e) {
                logError("a projectile", e);
            }
        }
    }

    private void logError(String what, RuntimeException e) {
        if (loggedErrors++ < MAX_LOGGED_ERRORS) {
            Kunftig.LOG.error("Kunftig blades: cannot draw " + what, e);
        }
    }

    /** Where a sword stuck in {@code e} sits: SlashBlade's {@code (x, y + eyeHeight * 0.5, z)}, interpolated. */
    static double[] stuckAt(Entity e, float pt) {
        double feet = e.posY - e.boundingBox.minY;
        float eye = e instanceof EntityPlayer ? 1.62F : e.getEyeHeight();
        return new double[] { e.lastTickPosX + (e.posX - e.lastTickPosX) * pt,
            e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - feet + eye * 0.5,
            e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt };
    }

    /** SlashBlade's {@code JudgementCutRenderer.render}, pass for pass. */
    private void renderJudgementCut(Cut c, double dx, double dy, double dz, float pt) {
        EffectWire.JudgementCut j = c.cut;
        BladeClient bc = BladeClient.INSTANCE;
        BladeIndex.Effect fx = bc.index()
            .effect(j.namespace, BladeIndex.JUDGEMENT_CUT);
        ObjModel model = fx == null ? null : bc.model(fx.obj);
        if (model == null) {
            return;
        }
        List<ObjModel.Group> base = model.groups("base");
        List<ObjModel.Group> wind = model.groups("wind");
        int life = j.lifetime;
        double baseAlpha = Math.min(life, Math.max(0, life - c.age - pt)) / (double) life;
        baseAlpha = -Math.pow(baseAlpha - 1, 4.0) + 1.0;
        int color = j.color & 0xFFFFFF;
        float[] hsb = java.awt.Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        int baseColor = java.awt.Color.HSBtoRGB(0.5F + hsb[0], hsb[1], 0.2F) & 0xFFFFFF;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(bc.location(fx.texture));
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(dx, dy, dz);
            GL11.glRotatef(j.yaw - 90F, 0, 1, 0);
            GL11.glRotatef(j.pitch, 0, 0, 1);
            GL11.glRotatef(c.seed, 0, 1, 0);
            GL11.glScalef(0.01F, 0.01F, 0.01F);
            if (reverseSubtract()) {
                GL11.glPushMatrix();
                for (int l = 0; l < 5; l++) {
                    GL11.glScalef(0.95F, 0.95F, 0.95F);
                    int a = 0xFF & (int) (0x66 * baseAlpha);
                    reverseState(() -> tessellate(base, baseColor, a, false));
                }
                GL11.glPopMatrix();
                int loop = 3;
                float cycle = 15;
                for (int l = 0; l < loop; l++) {
                    GL11.glPushMatrix();
                    float wave = (c.age + (cycle / loop * l) + pt) % cycle;
                    float waveScale = 1.0F + 0.03F * wave;
                    GL11.glScalef(waveScale, waveScale, waveScale);
                    int a = 0xFF & (int) (0x88 * ((cycle - wave) / cycle) * baseAlpha);
                    reverseState(() -> tessellate(base, baseColor, a, false));
                    GL11.glPopMatrix();
                }
            }
            int windCount = 5;
            for (int l = 0; l < windCount; l++) {
                GL11.glPushMatrix();
                GL11.glRotatef(360F / windCount * l, 1, 0, 0);
                GL11.glRotatef(30F, 0, 1, 0);
                double offsetBase = 7;
                double motionLen = offsetBase * (windCount - 1);
                double offsetTicks = c.age + pt + c.seed + l * offsetBase;
                double progress = (offsetTicks % motionLen) / motionLen;
                float windScale = (float) (0.4 + progress);
                GL11.glScalef(windScale, windScale, windScale);
                GL11.glRotatef((float) (360.0 / 20.0 * offsetTicks), 0, 0, 1);
                // SlashBlade's bug, kept: min(0, ...) hides the wind while sin > 0, and wraps negative values
                int a = 0xFF & (int) Math.min(0, 0xFF * Math.sin(Math.PI * 2 * progress) * baseAlpha);
                effectState(true, () -> tessellate(wind, color, a, false));
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static Boolean subtractSupported;

    private static boolean reverseSubtract() {
        if (subtractSupported == null) {
            subtractSupported = GLContext.getCapabilities().OpenGL14;
            if (!subtractSupported) {
                Kunftig.LOG.warn("Kunftig blades: no OpenGL 1.4, the Judgement Cut is drawn without its dark spheres");
            }
        }
        return subtractSupported;
    }

    /**
     * SlashBlade's ReverseLuminous state: {@code dst - src.rgb * src.a} (reverse subtract), culled, linear, no depth
     * write, fullbright.
     */
    private static void reverseState(Runnable draw) {
        effectState(true, () -> {
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            try {
                draw.run();
            } finally {
                GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            }
        });
    }

    /** SlashBlade's {@code DriveRenderer} and {@code SummonedSwordRenderer}. */
    private void renderProjectile(Minecraft mc, EffectProjectile p, double cx, double cy, double cz, float pt) {
        BladeClient bc = BladeClient.INSTANCE;
        BladeIndex.Effect fx = bc.index()
            .effect(p.namespace, p.effect);
        ObjModel model = fx == null ? null : bc.model(fx.obj);
        if (model == null) {
            return;
        }
        boolean drive = p.kind == EffectWire.KIND_DRIVE;
        Entity hit = p.stuckIn >= 0 ? mc.theWorld.getEntityByID(p.stuckIn) : null;
        double x;
        double y;
        double z;
        if (hit != null) {
            double[] at = stuckAt(hit, pt);
            x = at[0];
            y = at[1];
            z = at[2];
        } else {
            x = p.prevPos[0] + (p.pos[0] - p.prevPos[0]) * pt;
            y = p.prevPos[1] + (p.pos[1] - p.prevPos[1]) * pt;
            z = p.prevPos[2] + (p.pos[2] - p.prevPos[2]) * pt;
        }
        mc.getTextureManager()
            .bindTexture(bc.location(fx.texture));
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x - cx, y - cy, z - cz);
            if (hit != null) {
                GL11.glRotatef(EffectProjectile.rotLerp(pt, hit.prevRotationYaw, hit.rotationYaw) - 90F, 0, -1, 0);
                GL11.glRotatef(p.offsetYaw, 0, -1, 0);
            } else {
                GL11.glRotatef(EffectProjectile.rotLerp(pt, p.prevYaw, p.yaw) - 90F, 0, 1, 0);
            }
            GL11.glRotatef(EffectProjectile.rotLerp(pt, p.prevPitch, p.pitch), 0, 0, 1);
            GL11.glRotatef(p.roll, 1, 0, 0);
            float scale = drive ? 0.015F : 0.0075F;
            GL11.glScalef(scale, scale, scale);
            GL11.glRotatef(90F, 0, 1, 0);
            if (hit != null) {
                GL11.glTranslatef(0, 0, -100);
            }
            List<ObjModel.Group> groups = model.groups(drive ? "base" : "ss");
            int alpha = drive ? 0xFF & (int) (0xFF * p.driveAlpha()) : 0xFF;
            effectState(false, () -> tessellate(groups, p.color, alpha, false));
        } finally {
            GL11.glPopMatrix();
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

    /** Triangles in one colour (SlashBlade's {@code renderOverrided} without a UV operator), optionally faded. */
    static void tessellate(List<ObjModel.Group> groups, int rgb, int alpha, boolean fadeZeroY) {
        tessellate(groups, 1, 1, 0, 0, rgb, alpha, fadeZeroY);
    }

    /** Triangles with a per-vertex colour whose alpha is 0 where the model's y is exactly 0. */
    static void tessellateFaded(List<ObjModel.Group> groups, float uScale, float vScale, float uOffset, float vOffset,
        int rgb, int alpha) {
        tessellate(groups, uScale, vScale, uOffset, vOffset, rgb, alpha, true);
    }

    /**
     * Triangles with a per-vertex colour; with {@code fadeZeroY}, alpha 0 where the model's y is exactly 0
     * (SlashBlade's
     * {@code alphaOverrideYZZ}).
     */
    static void tessellate(List<ObjModel.Group> groups, float uScale, float vScale, float uOffset, float vOffset,
        int rgb, int alpha, boolean fadeZeroY) {
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
                    t.setColorRGBA(r, g, b, fadeZeroY && p[1] == 0 ? 0 : alpha);
                    float[] uv = f.uv(i, uScale, vScale, uOffset, vOffset);
                    t.addVertexWithUV(p[0], p[1], p[2], uv[0], uv[1]);
                }
            }
        }
        t.draw();
    }
}
