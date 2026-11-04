package com.akashic.kunftig.client.rodmodel.gecko;

import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.geckominecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.rodmodel.RodModels;
import com.akashic.kunftig.rodmodel.RodDisplays;
import com.akashic.kunftig.rodmodel.RodMatrix;
import com.akashic.kunftig.rodmodel.RodModelDef;
import com.akashic.kunftig.rodmodel.RodModelIndex;
import com.akashic.kunftig.rodmodel.RodSway;
import com.akashic.kunftig.rodmodel.RodTimeline;

import software.bernie.geckolib3.file.AnimationFileLoader;
import software.bernie.geckolib3.file.GeoModelLoader;
import software.bernie.geckolib3.geo.render.built.GeoBone;
import software.bernie.geckolib3.geo.render.built.GeoCube;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.model.provider.GeoModelProvider;
import software.bernie.geckolib3.molang.MolangRegistrar;
import software.bernie.geckolib3.renderers.geo.IGeoRenderer;
import software.bernie.geckolib3.resource.GeckoLibCache;

/**
 * Draws the fishing rods that have a 3D model (issue #13) in the hand (first and third person), on the ground and in
 * item frames, through GeckoLib's {@link IGeoRenderer}; everything else, the inventory included (the 2D icon shows the
 * quality star), goes to the renderer registered before this one, or to vanilla.
 * <p>
 * GeckoLib draws a whole model in one {@code Tessellator} batch, so the bones that need another GL state are drawn in
 * separate passes over the same hierarchy: opaque; light ({@code luz}: full lightmap, no shading); translucent (no
 * depth writes), lit and unlit. Mutation stages and the line hanging from the rod hide bones; stage textures cycle.
 * The line's locator is captured on the way for {@code RenderFish}.
 */
final class RodItemRenderer implements IItemRenderer, IGeoRenderer<RodAnimatable> {

    private enum Pass {
        OPAQUE,
        LIGHT,
        TRANSLUCENT,
        TRANSLUCENT_LIGHT
    }

    private static final float FULL_BRIGHT = 240F;
    private static final int MAX_LOGGED_ERRORS = 10;

    private final IItemRenderer previous;
    private final Map<RodModelDef, RodAnimatable> rods = new IdentityHashMap<>();
    /** Per rod: the bones drawn outside the opaque pass. */
    private final Map<RodModelDef, Map<String, Pass>> passMaps = new IdentityHashMap<>();
    /**
     * Per rod: the bones that may be hidden (mutation stages, the hanging line, quality stones), each with the
     * qualities it belongs to (empty when it is not a quality stone).
     */
    private final Map<RodModelDef, Map<String, Set<String>>> controlled = new IdentityHashMap<>();
    private final Set<RodModelDef> broken = Collections.newSetFromMap(new IdentityHashMap<>());
    private RodModelIndex rodsOf;
    private final FloatBuffer glMatrix = BufferUtils.createFloatBuffer(16);
    private final float[] modelView = new float[16];
    private int loggedErrors;

    // The draw in progress (sequential on the client thread).
    private RodAnimatable current;
    private Pass pass = Pass.OPAQUE;
    private Map<String, Pass> passes = Collections.emptyMap();
    private String tipBone;
    private float[] tipPoint;
    private float[] tipEye;
    /** First person: the inertia of this frame ({@code balanco}), or null. */
    private float[] swayRoot;
    private float[] swayBend;
    private String swayPivot;
    private java.util.List<String> swayBones = Collections.emptyList();
    private final float[] eyeLinear = new float[9];

    RodItemRenderer(IItemRenderer previous) {
        this.previous = previous;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // IItemRenderer

    /** The manifest context of a render type, or null. */
    private static String context(ItemRenderType type) {
        switch (type) {
            case EQUIPPED_FIRST_PERSON:
                return RodModelDef.FIRST_PERSON;
            case EQUIPPED:
                return RodModelDef.THIRD_PERSON;
            case ENTITY:
                return RenderItem.renderInFrame ? RodModelDef.FRAME : RodModelDef.GROUND;
            default:
                return null; // the inventory keeps the 2D icon
        }
    }

    private RodModelIndex.Resolved ours(ItemStack stack, ItemRenderType type) {
        String context = context(type);
        if (context == null) {
            return null;
        }
        RodModelIndex.Resolved r = RodModels.INSTANCE.resolve(stack);
        return r != null && r.model.drawsIn(context) && !broken.contains(r.model) ? r : null;
    }

    @Override
    public boolean handleRenderType(ItemStack stack, ItemRenderType type) {
        if (ours(stack, type) != null) {
            return true;
        }
        return previous != null && previous.handleRenderType(stack, type);
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) {
        if (ours(stack, type) == null) {
            return previous != null && previous.shouldUseRenderHelper(type, stack, helper);
        }
        switch (helper) {
            case ENTITY_ROTATION:
            case ENTITY_BOBBING:
                return !RenderItem.renderInFrame; // spins and bobs on the ground like any item, still in a frame
            default:
                return false; // never the block transforms: RodDisplays undoes the flat item's
        }
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        RodModelIndex.Resolved r = ours(stack, type);
        if (r == null) {
            if (previous != null) {
                previous.renderItem(type, stack, data);
            }
            return;
        }
        EntityLivingBase holder = (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON)
            && data.length > 1
            && data[1] instanceof EntityLivingBase ? (EntityLivingBase) data[1] : null;
        if (holder != null && !RodModels.isMainHand(holder, stack)) {
            flat(stack, holder); // Backhand's off hand: its arm transforms are not the ones undone here
            return;
        }
        try {
            draw(r, context(type), holder);
        } catch (RuntimeException | LinkageError e) {
            broken.add(r.model);
            if (loggedErrors++ < MAX_LOGGED_ERRORS) {
                Kunftig.LOG.error("Kunftig 3D rods: cannot draw {}; it keeps its 2D icon", r.model.id, e);
            }
        }
    }

    /** The vanilla flat item, in the transforms 1.7.10 and Forge already set up for it. */
    private static void flat(ItemStack stack, EntityLivingBase holder) {
        IIcon icon = holder instanceof EntityPlayer ? ((EntityPlayer) holder).getItemIcon(stack, 0)
            : stack.getIconIndex();
        if (icon == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(
                mc.getTextureManager()
                    .getResourceLocation(stack.getItemSpriteNumber()));
        ItemRenderer.renderItemIn2D(
            Tessellator.instance,
            icon.getMaxU(),
            icon.getMinV(),
            icon.getMinU(),
            icon.getMaxV(),
            icon.getIconWidth(),
            icon.getIconHeight(),
            0.0625F);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Drawing

    private RodAnimatable rod(RodModelDef def) {
        RodModels models = RodModels.INSTANCE;
        if (rodsOf != models.index()) {
            rods.clear(); // resources reloaded: new rods, new GeckoLib models
            passMaps.clear();
            controlled.clear();
            broken.clear();
            rodsOf = models.index();
        }
        RodAnimatable a = rods.get(def);
        if (a == null) {
            RodGeoModel model = new RodGeoModel(
                models.location(def.geo),
                models.location(def.animations),
                models.location(def.texture));
            a = new RodAnimatable(def, model);
            rods.put(def, a);
            Map<String, Pass> map = new HashMap<>();
            for (String name : def.lightBones) {
                map.put(name, passOf(def, name));
            }
            for (String name : def.translucentBones) {
                map.put(name, passOf(def, name));
            }
            passMaps.put(def, map);
            Map<String, Set<String>> hideable = new HashMap<>();
            for (String bone : def.boneStages.keySet()) {
                hideable.put(bone, new HashSet<>());
            }
            for (String bone : def.hiddenWhileCast) {
                hideable.put(bone, new HashSet<>());
            }
            for (Map.Entry<String, java.util.List<String>> q : def.qualityBones.entrySet()) {
                for (String bone : q.getValue()) {
                    hideable.computeIfAbsent(bone, k -> new HashSet<>())
                        .add(q.getKey());
                }
            }
            controlled.put(def, hideable);
        }
        return a;
    }

    private void draw(RodModelIndex.Resolved r, String context, EntityLivingBase holder) {
        RodModels models = RodModels.INSTANCE;
        RodModelDef def = r.model;
        RodAnimatable rod = rod(def);
        loadIntoGecko(rod.model);
        GeoModel model = rod.model.getModel(rod.model.geo); // also registers the bones for animation

        EntityPlayer player = holder instanceof EntityPlayer ? (EntityPlayer) holder : null;
        RodTimeline timeline = player != null ? models.timeline(player) : null;
        String state = timeline != null && timeline.state() != null ? timeline.state() : RodModelDef.IDLE;
        rod.clips = def.clips(state);
        rod.start = timeline != null ? timeline.start() : 0;
        rod.tension = timeline != null ? timeline.tension() : 0;
        int key = player != null ? models.animationKey(player) : RodModels.SHARED_KEY;
        rod.key = key;
        // The hanging line goes while the line is out, except while casting: the cast animation whips it away.
        boolean lineOut = player != null && player.fishEntity != null && !RodModelDef.CAST.equals(state);

        RodModelDef.Display display = models.display(def, context);
        RodMatrix transform;
        if (RodModelDef.FIRST_PERSON.equals(context)) {
            float swing = holder == null ? 0 : holder.getSwingProgress(models.partialTicks());
            transform = RodDisplays.firstPerson(display, def.grip, swing);
        } else if (RodModelDef.THIRD_PERSON.equals(context)) {
            transform = RodDisplays.thirdPerson(display, def.grip);
        } else {
            transform = RodDisplays.entity(display, def.grip);
        }

        GL11.glPushMatrix();
        try {
            glMatrix.clear();
            glMatrix.put(transform.values());
            glMatrix.flip();
            GL11.glMultMatrix(glMatrix);

            rod.model.texture = models.location(def.textureFor(r.stage, models.clock()));
            rod.model.setLivingAnimations(rod, key, null);
            boolean[] used = hide(rod.model.bones(model), def, r.stage, r.quality, lineOut);

            boolean capture = player != null && def.tipBone != null
                && (RodModelDef.FIRST_PERSON.equals(context) || RodModelDef.THIRD_PERSON.equals(context));
            tipBone = capture ? def.tipBone : null;
            tipPoint = capture ? RodDisplays.geckoPoint(def.tipRest) : null;
            tipEye = null;
            float[][] sway = RodModelDef.FIRST_PERSON.equals(context) && player == Minecraft.getMinecraft().thePlayer
                ? models.firstPersonSway(def)
                : null;
            if (sway != null) {
                swayRoot = sway[0];
                swayBones = def.sway.bend;
                swayBend = swayBones.isEmpty() ? null : scaled(sway[1], 1F / swayBones.size());
                swayPivot = def.sway.pivot;
            }
            if (capture || sway != null) {
                models.readMatrix(GL11.GL_MODELVIEW_MATRIX, modelView);
            }
            Minecraft.getMinecraft().renderEngine.bindTexture(rod.model.texture);
            drawPasses(model, rod, def, used);
            if (tipEye != null) {
                models.captureTip(player, RodModelDef.FIRST_PERSON.equals(context), tipEye);
            }
        } finally {
            current = null;
            tipBone = null;
            swayRoot = swayBend = null;
            swayPivot = null;
            swayBones = Collections.emptyList();
            GL11.glPopMatrix();
        }
    }

    private static float[] scaled(float[] v, float f) {
        return new float[] { v[0] * f, v[1] * f, v[2] * f };
    }

    /**
     * The inertia ({@link RodSway}): an eye-space rotation about the bone's pivot, turned into the bone's own frame
     * through the GL model-view and GeckoLib's matrix so far (mirrored or not), added before the bone's animation.
     */
    private void sway(GeoBone bone) {
        float[] eye = null;
        if (swayRoot != null && bone.getName()
            .equals(swayPivot)) {
            eye = swayRoot;
        } else if (swayBend != null && swayBones.contains(bone.getName())) {
            eye = swayBend;
        }
        if (eye == null) {
            return;
        }
        javax.vecmath.Matrix4f m = MATRIX_STACK.getModelMatrix();
        float[] b = { m.m00, m.m01, m.m02, m.m10, m.m11, m.m12, m.m20, m.m21, m.m22 };
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                // GL's model-view is column-major
                eyeLinear[r * 3 + c] = modelView[r] * b[c] + modelView[4 + r] * b[3 + c] + modelView[8 + r] * b[6 + c];
            }
        }
        float[] local = RodSway.toLocal(eyeLinear, eye);
        MATRIX_STACK.rotateZ(local[2]);
        MATRIX_STACK.rotateY(local[1]);
        MATRIX_STACK.rotateX(local[0]);
    }

    /**
     * Puts the rod's geometry and animations in GeckoLib's cache. GeckoLib 1.0.4 only scans the packs FML lists at
     * start-up (vanilla's and the mods'), never the resource packs a player or a server selects, where the rods come
     * from; read through the resource manager, they come from whichever pack has them. Checked on every draw: each
     * resource reload gives GeckoLib new maps.
     */
    private static void loadIntoGecko(RodGeoModel model) {
        GeckoLibCache cache = GeckoLibCache.getInstance();
        IResourceManager resources = Minecraft.getMinecraft()
            .getResourceManager();
        if (!cache.getGeoModels()
            .containsKey(model.geo)) {
            cache.getGeoModels()
                .put(
                    model.geo,
                    GeoModelLoader.getInstance()
                        .loadModel(resources, model.geo));
        }
        if (!cache.getAnimations()
            .containsKey(model.animations)) {
            cache.getAnimations()
                .put(
                    model.animations,
                    AnimationFileLoader.getInstance()
                        .loadAllAnimations(MolangRegistrar.getParser(), model.animations, resources));
        }
    }

    /**
     * Hides the bones of higher mutation stages, the quality stones of other qualities (an item without a quality
     * suffix shows {@code normal}'s; hiding a bone hides its children) and the hanging line while it is out; returns
     * which passes have something to draw.
     */
    private boolean[] hide(Map<String, GeoBone> bones, RodModelDef def, int stage, String quality, boolean lineOut) {
        String shown = def.shownQuality(quality);
        for (Map.Entry<String, Set<String>> e : controlled.get(def)
            .entrySet()) {
            GeoBone b = bones.get(e.getKey());
            if (b == null) {
                continue;
            }
            Set<String> qualities = e.getValue();
            b.setHidden(
                !def.showsAt(e.getKey(), stage) || lineOut && def.hiddenWhileCast.contains(e.getKey())
                    || !qualities.isEmpty() && !qualities.contains(shown));
        }
        boolean[] used = new boolean[Pass.values().length];
        used[Pass.OPAQUE.ordinal()] = true;
        for (Map.Entry<String, Pass> e : passMaps.get(def)
            .entrySet()) {
            GeoBone b = bones.get(e.getKey());
            if (b != null && !b.isHidden()) {
                used[e.getValue()
                    .ordinal()] = true;
            }
        }
        return used;
    }

    private static Pass passOf(RodModelDef def, String bone) {
        boolean light = def.lightBones.contains(bone);
        if (def.translucentBones.contains(bone)) {
            return light ? Pass.TRANSLUCENT_LIGHT : Pass.TRANSLUCENT;
        }
        return light ? Pass.LIGHT : Pass.OPAQUE;
    }

    private void drawPasses(GeoModel model, RodAnimatable rod, RodModelDef def, boolean[] used) {
        passes = passMaps.get(def);
        current = rod;
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean rescale = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int blendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        int blendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
        float lightX = OpenGlHelper.lastBrightnessX;
        float lightY = OpenGlHelper.lastBrightnessY;
        try {
            for (Pass p : Pass.values()) {
                if (!used[p.ordinal()]) {
                    continue;
                }
                boolean light = p == Pass.LIGHT || p == Pass.TRANSLUCENT_LIGHT;
                boolean translucent = p == Pass.TRANSLUCENT || p == Pass.TRANSLUCENT_LIGHT;
                if (light) {
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);
                    GL11.glDisable(GL11.GL_LIGHTING);
                } else {
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
                    setEnabled(GL11.GL_LIGHTING, lighting);
                }
                GL11.glDepthMask(depthMask && !translucent);
                pass = p;
                render(model, rod, RodModels.INSTANCE.partialTicks(), 1, 1, 1, 1);
            }
        } finally {
            pass = Pass.OPAQUE;
            // GeckoLib's own GL state cache: leave it in sync with the state put back below.
            GlStateManager.disableRescaleNormal();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GL11.glDepthMask(depthMask);
            setEnabled(GL11.GL_LIGHTING, lighting);
            setEnabled(GL11.GL_CULL_FACE, cull);
            setEnabled(GL11.GL_BLEND, blend);
            setEnabled(GL12.GL_RESCALE_NORMAL, rescale);
            GL11.glBlendFunc(blendSrc, blendDst);
        }
    }

    private static void setEnabled(int capability, boolean on) {
        if (on) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // IGeoRenderer

    /**
     * GeckoLib's traversal (every bone's transform, always, since children depend on it), drawing only the cubes of
     * this pass, and noting where the line's locator is.
     */
    @Override
    public void renderRecursively(Tessellator tess, RodAnimatable animatable, GeoBone bone, float red, float green,
        float blue, float alpha) {
        MATRIX_STACK.push();
        MATRIX_STACK.translate(bone);
        MATRIX_STACK.moveToPivot(bone);
        sway(bone);
        MATRIX_STACK.rotate(bone);
        MATRIX_STACK.scale(bone);
        MATRIX_STACK.moveBackFromPivot(bone);
        if (tipBone != null && pass == Pass.OPAQUE && tipBone.equals(bone.getName())) {
            captureTip();
        }
        if (!bone.isHidden() && passes.getOrDefault(bone.getName(), Pass.OPAQUE) == pass) {
            for (GeoCube cube : bone.childCubes) {
                MATRIX_STACK.push();
                try {
                    renderCube(tess, cube, red, green, blue, alpha);
                } finally {
                    MATRIX_STACK.pop();
                }
            }
        }
        if (!bone.childBonesAreHiddenToo()) {
            for (GeoBone child : bone.childBones) {
                renderRecursively(tess, animatable, child, red, green, blue, alpha);
            }
        }
        MATRIX_STACK.pop();
    }

    /** The locator through the bone's matrix, then the GL model-view: a point in eye space. */
    private void captureTip() {
        javax.vecmath.Matrix4f m = MATRIX_STACK.getModelMatrix();
        float x = tipPoint[0];
        float y = tipPoint[1];
        float z = tipPoint[2];
        float mx = m.m00 * x + m.m01 * y + m.m02 * z + m.m03;
        float my = m.m10 * x + m.m11 * y + m.m12 * z + m.m13;
        float mz = m.m20 * x + m.m21 * y + m.m22 * z + m.m23;
        tipEye = RodMatrix.of(modelView)
            .transform(mx, my, mz);
    }

    /** No particles: GeckoLib's would also create animation data under {@link #getUniqueID}. */
    @Override
    public void renderAfter(GeoModel model, RodAnimatable animatable, float ticks, float red, float green, float blue,
        float alpha) {}

    @Override
    @SuppressWarnings("rawtypes")
    public GeoModelProvider getGeoModelProvider() {
        return current != null ? current.model : null;
    }

    @Override
    public ResourceLocation getTextureLocation(RodAnimatable instance) {
        return instance.model.texture;
    }
}
