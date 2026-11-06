package com.akashic.kunftig.client.display.gecko;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.geckominecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.akashic.kunftig.client.display.DisplayClient;
import com.akashic.kunftig.display.DisplayModelIndex;

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
 * Draws the displays with GeckoLib, at the end of the world render: the model at its point, turned by its yaw, lit by
 * the light where it stands, in four passes (opaque, full bright, translucent, translucent full bright: the bones the
 * manifest lists in {@code luz} and {@code translucido}), and then each item stack at its item bone, through the bone's
 * matrix (its animation moves, turns and scales the item), drawn like the item in an item frame.
 */
final class DisplayRenderer implements DisplayClient.Renderer, IGeoRenderer<DisplayAnimatable> {

    private static final int FULL_BRIGHT = 240;

    private enum Pass {
        OPAQUE,
        LIGHT,
        TRANSLUCENT,
        TRANSLUCENT_LIGHT
    }

    private final Map<String, DisplayAnimatable> animatables = new HashMap<>();
    private final Map<String, Map<String, Pass>> passMaps = new HashMap<>();
    /**
     * Animation keys are recycled ({@code id % KEYS}): GeckoLib keeps the data of every key forever, and a key that
     * comes back restarts its animation anyway (the state's start differs). Two displays share a key only with
     * {@code KEYS} of the same model alive at once.
     */
    private static final int KEYS = 256;
    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private DisplayAnimatable current;
    private Map<String, Pass> passes;
    private Pass pass = Pass.OPAQUE;
    /** The item bones of the display being drawn (name -> index) and their matrices, captured in the opaque pass. */
    private Map<String, Integer> itemBones;
    private final javax.vecmath.Matrix4f[] itemMatrices = new javax.vecmath.Matrix4f[8];
    private final boolean[] itemVisible = new boolean[8];
    private EntityItem itemEntity;

    @Override
    public void reset() {
        animatables.clear();
        passMaps.clear();
    }

    private DisplayAnimatable animatable(DisplayModelIndex.Model def) {
        DisplayAnimatable a = animatables.get(def.id);
        if (a == null || a.def != def) {
            a = new DisplayAnimatable(
                def,
                new DisplayGeoModel(location(def.geo), location(def.animations), location(def.texture)));
            animatables.put(def.id, a);
            Map<String, Pass> map = new HashMap<>();
            for (String b : def.lightBones) {
                map.put(b, def.translucentBones.contains(b) ? Pass.TRANSLUCENT_LIGHT : Pass.LIGHT);
            }
            for (String b : def.translucentBones) {
                map.putIfAbsent(b, Pass.TRANSLUCENT);
            }
            passMaps.put(def.id, map);
        }
        return a;
    }

    private final Map<String, ResourceLocation> locations = new HashMap<>();

    private ResourceLocation location(String id) {
        ResourceLocation r = locations.get(id);
        if (r == null) {
            int c = id.indexOf(':');
            r = new ResourceLocation(id.substring(0, c), id.substring(c + 1));
            locations.put(id, r);
        }
        return r;
    }

    @Override
    public void render(DisplayClient.Entry e, DisplayModelIndex.Model def, double dx, double dy, double dz,
        float partialTicks) {
        DisplayModelIndex.State st = def.states.get(e.state);
        DisplayAnimatable a = animatable(def);
        loadIntoGecko(a.model);
        a.animation = st == null ? null : st.animation;
        a.loop = st != null && st.loop;
        a.start = e.start;
        a.model.texture = location(def.texture(e.variant));
        GeoModel model = a.model.getModel(a.model.geo); // also registers the bones for animation
        a.model.setLivingAnimations(a, Math.floorMod(e.id, KEYS), null);
        Minecraft mc = Minecraft.getMinecraft();
        int light = mc.theWorld.getLightBrightnessForSkyBlocks(
            MathHelper.floor_double(e.motion.x(partialTicks)),
            MathHelper.floor_double(e.motion.y(partialTicks) + 1.5),
            MathHelper.floor_double(e.motion.z(partialTicks)),
            0);
        float lx = light % 65536;
        float ly = light / 65536f;

        current = a;
        passes = passMaps.get(def.id);
        itemBones = new HashMap<>();
        for (int i = 0; i < def.itemBones.size() && i < itemMatrices.length; i++) {
            itemBones.put(def.itemBones.get(i), i);
            itemVisible[i] = false;
        }
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int blendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        int blendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(dx, dy, dz);
            GL11.glRotatef(-e.motion.yaw(partialTicks), 0, 1, 0);
            // API 9: pitch tips the front (+Z) down, then roll turns about the front
            GL11.glRotatef(e.motion.pitch(partialTicks), 1, 0, 0);
            GL11.glRotatef(e.motion.roll(partialTicks), 0, 0, 1);
            RenderHelper.enableStandardItemLighting();
            GL11.glColor4f(1, 1, 1, 1);
            mc.renderEngine.bindTexture(a.model.texture);
            for (Pass p : Pass.values()) {
                boolean bright = p == Pass.LIGHT || p == Pass.TRANSLUCENT_LIGHT;
                boolean translucent = p == Pass.TRANSLUCENT || p == Pass.TRANSLUCENT_LIGHT;
                if (bright) {
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);
                    GL11.glDisable(GL11.GL_LIGHTING);
                } else {
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
                    GL11.glEnable(GL11.GL_LIGHTING);
                }
                GL11.glDepthMask(!translucent);
                pass = p;
                render(model, a, partialTicks, 1, 1, 1, 1);
            }
            GL11.glDepthMask(true);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
            GL11.glEnable(GL11.GL_LIGHTING);
            drawItems(e.items, def.itemScale, mc);
        } finally {
            pass = Pass.OPAQUE;
            current = null;
            itemBones = null;
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
            GL11.glDepthMask(depthMask);
            setEnabled(GL11.GL_BLEND, blend);
            setEnabled(GL11.GL_CULL_FACE, cull);
            setEnabled(GL12.GL_RESCALE_NORMAL, false);
            GL11.glBlendFunc(blendSrc, blendDst);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glPopMatrix();
        }
    }

    /** Each stack at its bone: the bone's matrix on top of the display's, then drawn as in an item frame. */
    private void drawItems(List<ItemStack> items, float scale, Minecraft mc) {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (itemEntity == null || itemEntity.worldObj != mc.theWorld) {
            itemEntity = new EntityItem(mc.theWorld, 0, 0, 0);
        }
        for (int i = 0; i < items.size() && i < itemMatrices.length; i++) {
            ItemStack stack = items.get(i);
            javax.vecmath.Matrix4f m = itemMatrices[i];
            if (stack == null || m == null || !itemVisible[i]) {
                continue;
            }
            GL11.glPushMatrix();
            try {
                matrix.clear();
                // javax.vecmath is row-major, GL wants column-major
                matrix.put(m.m00)
                    .put(m.m10)
                    .put(m.m20)
                    .put(m.m30)
                    .put(m.m01)
                    .put(m.m11)
                    .put(m.m21)
                    .put(m.m31)
                    .put(m.m02)
                    .put(m.m12)
                    .put(m.m22)
                    .put(m.m32)
                    .put(m.m03)
                    .put(m.m13)
                    .put(m.m23)
                    .put(m.m33);
                matrix.flip();
                GL11.glMultMatrix(matrix);
                GL11.glScalef(scale, scale, scale);
                itemEntity.setEntityItemStack(stack.copy());
                itemEntity.hoverStart = 0;
                RenderItem.renderInFrame = true;
                RenderManager.instance.renderEntityWithPosYaw(itemEntity, 0, 0, 0, 0, 0);
            } finally {
                RenderItem.renderInFrame = false;
                GL11.glPopMatrix();
            }
        }
    }

    private static void setEnabled(int capability, boolean on) {
        if (on) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    /** GeckoLib only scans the packs FML lists at start-up: load the display's files from the resource manager. */
    private static void loadIntoGecko(DisplayGeoModel model) {
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

    // ---- IGeoRenderer -------------------------------------------------------------------------------------------

    /** GeckoLib's traversal, drawing only this pass's cubes and noting where the item bones are. */
    @Override
    public void renderRecursively(Tessellator tess, DisplayAnimatable animatable, GeoBone bone, float red, float green,
        float blue, float alpha) {
        MATRIX_STACK.push();
        MATRIX_STACK.translate(bone);
        MATRIX_STACK.moveToPivot(bone);
        MATRIX_STACK.rotate(bone);
        MATRIX_STACK.scale(bone);
        MATRIX_STACK.moveBackFromPivot(bone);
        if (pass == Pass.OPAQUE && itemBones != null) {
            Integer i = itemBones.get(bone.getName());
            if (i != null) {
                // the item sits at the bone's pivot, in the bone's frame
                MATRIX_STACK.push();
                MATRIX_STACK.moveToPivot(bone);
                itemMatrices[i] = new javax.vecmath.Matrix4f(MATRIX_STACK.getModelMatrix());
                itemVisible[i] = !bone.isHidden() && bone.getScaleX() > 0.01f;
                MATRIX_STACK.pop();
            }
        }
        if (!bone.isHidden()
            && (passes == null ? Pass.OPAQUE : passes.getOrDefault(bone.getName(), Pass.OPAQUE)) == pass) {
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

    /** No particles: GeckoLib's would also create animation data under {@link #getUniqueID}. */
    @Override
    public void renderAfter(GeoModel model, DisplayAnimatable animatable, float ticks, float red, float green,
        float blue, float alpha) {}

    @Override
    @SuppressWarnings("rawtypes")
    public GeoModelProvider getGeoModelProvider() {
        return current != null ? current.model : null;
    }

    @Override
    public ResourceLocation getTextureLocation(DisplayAnimatable instance) {
        return instance.model.texture;
    }
}
