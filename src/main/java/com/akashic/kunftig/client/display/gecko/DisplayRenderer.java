package com.akashic.kunftig.client.display.gecko;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.display.DisplayClient;
import com.akashic.kunftig.display.DisplayModelIndex;
import com.akashic.kunftig.display.StaticBones;

import software.bernie.geckolib3.core.builder.Animation;
import software.bernie.geckolib3.core.keyframe.BoneAnimation;
import software.bernie.geckolib3.core.snapshot.BoneSnapshot;
import software.bernie.geckolib3.file.AnimationFile;
import software.bernie.geckolib3.file.AnimationFileLoader;
import software.bernie.geckolib3.file.GeoModelLoader;
import software.bernie.geckolib3.geo.render.built.GeoBone;
import software.bernie.geckolib3.geo.render.built.GeoCube;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.geo.render.built.GeoQuad;
import software.bernie.geckolib3.geo.render.built.GeoVertex;
import software.bernie.geckolib3.model.provider.GeoModelProvider;
import software.bernie.geckolib3.molang.MolangRegistrar;
import software.bernie.geckolib3.renderers.geo.IGeoRenderer;
import software.bernie.geckolib3.resource.GeckoLibCache;

/**
 * Draws the displays with GeckoLib, at the end of the world render: the model at its point, turned by its yaw, lit by
 * the light where it stands, in four passes (opaque, full bright, translucent, translucent full bright: the bones the
 * manifest lists in {@code luz} and {@code translucido}), and then each item stack at its item bone, through the bone's
 * matrix (its animation moves, turns and scales the item), drawn like the item in an item frame.
 * <p>
 * The bones that never move are compiled once into a vertex buffer ({@link Mesh}): a ship has thousands of cubes,
 * and transforming each of their vertices on the CPU every frame, four times over, is what a display costs.
 * Only the bones that move (animated, turned by the server, item bones, and what hangs on them) are still walked
 * and tessellated each frame; see {@link StaticBones}. {@code -Dkunftig.displayMesh=false} turns this off.
 */
final class DisplayRenderer implements DisplayClient.Renderer, IGeoRenderer<DisplayAnimatable> {

    private static final int FULL_BRIGHT = 240;

    private enum Pass {
        OPAQUE,
        LIGHT,
        TRANSLUCENT,
        TRANSLUCENT_LIGHT
    }

    /** How a walk of the bones treats their cubes. */
    private enum Walk {
        /** Every visible bone is drawn (no compiled mesh). */
        ALL,
        /** Compiling: only the bones that stand still. */
        STILL,
        /** Per frame, beside a compiled mesh: only the bones that move, reached through the still bones above them. */
        MOVING
    }

    /**
     * The compiled still bones of one model as one display shows it: one vertex buffer (texture, normal, position
     * per vertex, the quads of each pass one after the other), built once and drawn with a single call per pass.
     */
    private static final class Mesh {

        final StaticBones<GeoBone> plan;
        int buffer;
        /** Per pass: where its quads start in the buffer and how many vertices they are (0: no still cube in it). */
        final int[] first = new int[Pass.values().length];
        final int[] count = new int[Pass.values().length];
        /** Per pass: the cubes the per-frame walk drew, or -1 before its first walk. */
        final int[] movingCubes = { -1, -1, -1, -1 };
        int stillCubes;

        Mesh(StaticBones<GeoBone> plan) {
            this.plan = plan;
        }

        void free() {
            if (buffer != 0) {
                GL15.glDeleteBuffers(buffer);
                buffer = 0;
            }
        }
    }

    /**
     * What a mesh depends on: the model (its bones and cubes), which bones this display shows (API 9: a ship's level)
     * and how many bones of the model the server has turned so far (API 13: see {@link #turnedBones}). The texture
     * variant is not part of it: the cubes keep their UV.
     */
    private static final class MeshKey {

        final GeoModel model;
        final Map<String, Boolean> bones;
        final int turned;

        MeshKey(GeoModel model, Map<String, Boolean> bones, int turned) {
            this.model = model;
            this.bones = bones;
            this.turned = turned;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MeshKey)) {
                return false;
            }
            MeshKey k = (MeshKey) o;
            return k.model == model && k.turned == turned && k.bones.equals(bones);
        }

        @Override
        public int hashCode() {
            return (System.identityHashCode(model) * 31 + bones.hashCode()) * 31 + turned;
        }
    }

    /** At most this many meshes are kept (displays of one model at one level share theirs). */
    private static final int MESHES = 24;
    private static boolean meshesOn = !"false".equalsIgnoreCase(System.getProperty("kunftig.displayMesh"));
    private final LinkedHashMap<MeshKey, Mesh> meshes = new LinkedHashMap<MeshKey, Mesh>(16, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<MeshKey, Mesh> eldest) {
            if (size() > MESHES) {
                eldest.getValue()
                    .free(); // only ever called while drawing, on the render thread
                return true;
            }
            return false;
        }
    };
    /** Set by {@link #reset} (which may run off the render thread): the buffers are freed at the next draw. */
    private volatile boolean dropMeshes;
    /**
     * Per model id: every bone the server has turned on some display of the model. They move on all of them from then
     * on: a ship's rudder, wheel and doors are learnt the first time each turns (one new mesh each time), instead of
     * every ship keeping a mesh for its own set of open doors.
     */
    private final Map<String, Set<String>> turnedBones = new HashMap<>();
    /** Per model id: the bones its animation file has a track for. */
    private final Map<String, Set<String>> animatedBones = new HashMap<>();
    private Walk walk = Walk.ALL;
    private StaticBones<GeoBone> plan;
    private int drawnCubes;

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
    /** The display being drawn (its bones hidden or shown by the server, API 9). */
    private DisplayClient.Entry currentEntry;
    private DisplayModelIndex.Model currentDef;
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
        animatedBones.clear();
        turnedBones.clear();
        flatBones.clear();
        dropMeshes = true;
    }

    /** The bones some animation of the model's file moves: they are never compiled. */
    private Set<String> animatedBones(DisplayModelIndex.Model def, DisplayGeoModel model) {
        Set<String> names = animatedBones.get(def.id);
        if (names == null) {
            names = new HashSet<>();
            AnimationFile file = GeckoLibCache.getInstance()
                .getAnimations()
                .get(model.animations);
            if (file != null) {
                for (Animation animation : file.getAllAnimations()) {
                    for (BoneAnimation track : animation.boneAnimations) {
                        names.add(track.boneName);
                    }
                }
            }
            animatedBones.put(def.id, names);
        }
        return names;
    }

    /**
     * The compiled mesh of this display's still bones, built the first time a display needs it. Null when meshes are
     * off or one could not be compiled: the display is then drawn the old way, every cube every frame.
     */
    private Mesh mesh(DisplayClient.Entry e, DisplayModelIndex.Model def, DisplayAnimatable a, GeoModel model) {
        if (dropMeshes) {
            dropMeshes = false;
            for (Mesh m : meshes.values()) {
                m.free();
            }
            meshes.clear();
        }
        if (!meshesOn) {
            return null;
        }
        Set<String> turnedNow = turnedBones.get(def.id);
        if (turnedNow == null) {
            turnedNow = new HashSet<>();
            turnedBones.put(def.id, turnedNow);
        }
        if (!e.turns.isEmpty() && turnedNow.addAll(e.turns.keySet())) {
            // a bone turned for the first time: the meshes that have it compiled are of no use any more
            for (java.util.Iterator<Map.Entry<MeshKey, Mesh>> it = meshes.entrySet()
                .iterator(); it.hasNext();) {
                Map.Entry<MeshKey, Mesh> old = it.next();
                if (old.getKey().model == model) {
                    old.getValue()
                        .free();
                    it.remove();
                }
            }
        }
        MeshKey key = new MeshKey(model, e.bones, turnedNow.size());
        Mesh mesh = meshes.get(key);
        if (mesh != null) {
            return mesh;
        }
        Set<String> animated = animatedBones(def, a.model);
        Set<String> flat = flatBones(def, model);
        Set<String> turned = turnedNow;
        mesh = new Mesh(
            StaticBones.plan(
                model.topLevelBones,
                b -> b.childBones,
                GeoBone::getName,
                n -> animated.contains(n) || turned.contains(n) || flat.contains(n) || def.itemBones.contains(n),
                n -> e.boneVisible(n, def)));
        if (!compile(mesh, model, a)) {
            mesh.free();
            meshesOn = false;
            Kunftig.LOG
                .warn("Kunftig displays: could not compile {}; drawing every cube each frame from now on", def.id);
            return null;
        }
        meshes.put(key, mesh);
        Kunftig.LOG.info(
            "Kunftig displays: {} compiled: {} cubes in {} still bones, {} bones walked each frame",
            def.id,
            mesh.stillCubes,
            mesh.plan.stillBones(),
            mesh.plan.movingBones());
        return mesh;
    }

    /** The vertices being collected for a mesh: 8 floats each (u, v, normal, position). */
    private float[] sink = new float[1 << 16];
    private int sunk;
    private final javax.vecmath.Vector3f normal = new javax.vecmath.Vector3f();
    private final javax.vecmath.Vector4f corner = new javax.vecmath.Vector4f();

    /** Transforms the still bones' cubes once, pass by pass, into one vertex buffer. */
    private boolean compile(Mesh mesh, GeoModel model, DisplayAnimatable a) {
        if (!GLContext.getCapabilities().OpenGL15) {
            return false;
        }
        Pass before = pass;
        plan = mesh.plan;
        walk = Walk.STILL;
        sunk = 0;
        try {
            for (Pass p : Pass.values()) {
                pass = p;
                drawnCubes = 0;
                mesh.first[p.ordinal()] = sunk / 8;
                for (GeoBone bone : model.topLevelBones) {
                    renderRecursively(null, a, bone, 1, 1, 1, 1);
                }
                mesh.count[p.ordinal()] = sunk / 8 - mesh.first[p.ordinal()];
                mesh.stillCubes += drawnCubes;
            }
        } catch (RuntimeException ex) {
            Kunftig.LOG.warn("Kunftig displays: compiling a mesh failed", ex);
            return false;
        } finally {
            pass = before;
            walk = Walk.ALL;
            plan = null;
        }
        if (sunk == 0) {
            return true; // every bone moves: nothing to keep
        }
        for (int i = 0; i < sunk; i++) {
            if (Float.isNaN(sink[i]) || Float.isInfinite(sink[i])) {
                Kunftig.LOG.warn("Kunftig displays: a mesh came out with a vertex that is not a number");
                return false;
            }
        }
        FloatBuffer data = BufferUtils.createFloatBuffer(sunk);
        data.put(sink, 0, sunk)
            .flip();
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // someone else's errors are not ours
        }
        mesh.buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.buffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return mesh.buffer != 0 && GL11.glGetError() == GL11.GL_NO_ERROR;
    }

    /**
     * One cube into the mesh: what {@link IGeoRenderer#renderCube} hands the tessellator (the cube's own rotation,
     * each quad's normal through the normal matrix and each corner through the model matrix), kept instead of drawn.
     * A cube with no thickness never comes here ({@link #flatBones}): GeckoLib draws those with a polygon offset.
     */
    private void sinkCube(GeoCube cube) {
        MATRIX_STACK.moveToPivot(cube);
        MATRIX_STACK.rotate(cube);
        MATRIX_STACK.moveBackFromPivot(cube);
        for (GeoQuad quad : cube.quads) {
            if (quad == null) {
                continue;
            }
            normal.set(quad.normal.getX(), quad.normal.getY(), quad.normal.getZ());
            MATRIX_STACK.getNormalMatrix()
                .transform(normal);
            for (GeoVertex vertex : quad.vertices) {
                corner.set(vertex.position.x, vertex.position.y, vertex.position.z, 1);
                MATRIX_STACK.getModelMatrix()
                    .transform(corner);
                if (sunk + 8 > sink.length) {
                    sink = java.util.Arrays.copyOf(sink, sink.length * 2);
                }
                sink[sunk++] = vertex.textureU;
                sink[sunk++] = vertex.textureV;
                sink[sunk++] = normal.x;
                sink[sunk++] = normal.y;
                sink[sunk++] = normal.z;
                sink[sunk++] = corner.x;
                sink[sunk++] = corner.y;
                sink[sunk++] = corner.z;
            }
        }
    }

    /** Per model id: the bones with a cube of no thickness (drawn the old way, with GeckoLib's polygon offset). */
    private final Map<String, Set<String>> flatBones = new HashMap<>();

    private Set<String> flatBones(DisplayModelIndex.Model def, GeoModel model) {
        Set<String> names = flatBones.get(def.id);
        if (names == null) {
            names = new HashSet<>();
            for (GeoBone bone : model.topLevelBones) {
                collectFlat(bone, names);
            }
            flatBones.put(def.id, names);
        }
        return names;
    }

    private static void collectFlat(GeoBone bone, Set<String> names) {
        for (GeoCube cube : bone.childCubes) {
            if (cube.size.x == 0 || cube.size.y == 0 || cube.size.z == 0) {
                names.add(bone.getName());
                break;
            }
        }
        for (GeoBone child : bone.childBones) {
            collectFlat(child, names);
        }
    }

    /**
     * One pass of a display with a mesh: the buffer of its still bones, then a walk of the ones that move, inside the
     * state {@link IGeoRenderer#render} sets around its cubes.
     */
    private void drawPass(GeoModel model, DisplayAnimatable a, Mesh mesh) {
        int i = pass.ordinal();
        boolean walkNow = mesh.movingCubes[i] != 0 || pass == Pass.OPAQUE && !itemBones.isEmpty();
        if (mesh.count[i] == 0 && !walkNow) {
            return;
        }
        GlStateManager.disableCull();
        GlStateManager.enableRescaleNormal();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (mesh.count[i] != 0) {
            // texture, normal, position from the buffer; the colour and the light map are the current ones
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.buffer);
            GL11.glInterleavedArrays(GL11.GL_T2F_N3F_V3F, 0, 0L);
            GL11.glDrawArrays(GL11.GL_QUADS, mesh.first[i], mesh.count[i]);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        }
        if (walkNow) {
            Tessellator tess = Tessellator.instance;
            plan = mesh.plan;
            walk = Walk.MOVING;
            drawnCubes = 0;
            tess.startDrawingQuads();
            try {
                for (GeoBone bone : model.topLevelBones) {
                    renderRecursively(tess, a, bone, 1, 1, 1, 1);
                }
            } finally {
                tess.draw();
                walk = Walk.ALL;
                plan = null;
            }
            mesh.movingCubes[i] = drawnCubes;
        }
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
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
        a.transition = def.transitionTicks;
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
        currentEntry = e;
        currentDef = def;
        passes = passMaps.get(def.id);
        itemBones = new HashMap<>();
        for (int i = 0; i < def.itemBones.size() && i < itemMatrices.length; i++) {
            itemBones.put(def.itemBones.get(i), i);
            itemVisible[i] = false;
        }
        Mesh mesh = mesh(e, def, a, model);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int blendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        int blendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
        boolean normalize = GL11.glIsEnabled(GL11.GL_NORMALIZE);
        float[] sc = e.scale;
        float sx = def.scale * sc[0];
        float sy = def.scale * sc[1];
        float sz = def.scale * sc[2];
        boolean scaled = sx != 1 || sy != 1 || sz != 1;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(dx, dy, dz);
            GL11.glRotatef(-e.motion.yaw(partialTicks), 0, 1, 0);
            // API 9: pitch tips the front (+Z) down, then roll turns about the front
            GL11.glRotatef(e.motion.pitch(partialTicks), 1, 0, 0);
            GL11.glRotatef(e.motion.roll(partialTicks), 0, 0, 1);
            if (scaled) {
                // API 9: the model's scale and the display's; normals renormalised (the lighting would darken)
                GL11.glScalef(sx, sy, sz);
                GL11.glEnable(GL11.GL_NORMALIZE);
            }
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
                if (mesh != null) {
                    drawPass(model, a, mesh);
                } else {
                    render(model, a, partialTicks, 1, 1, 1, 1);
                }
            }
            GL11.glDepthMask(true);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
            GL11.glEnable(GL11.GL_LIGHTING);
            drawItems(e.items, def.itemScale, mc);
        } finally {
            pass = Pass.OPAQUE;
            walk = Walk.ALL;
            plan = null;
            current = null;
            currentEntry = null;
            currentDef = null;
            itemBones = null;
            setEnabled(GL11.GL_NORMALIZE, normalize);
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
        if (currentEntry != null && currentDef != null && !currentEntry.boneVisible(bone.getName(), currentDef)) {
            return; // hidden by the manifest or the server: the bone and everything under it (API 9)
        }
        if (walk == Walk.MOVING && !plan.walk(bone) || walk == Walk.STILL && !plan.still(bone)) {
            return; // beside a mesh: nothing under here moves; compiling one: this bone and all under it move
        }
        // API 13: what the server turned this bone by, on top of the animation. The model file's degrees: GeckoLib
        // keeps X and Y negated.
        DisplayClient.BoneTurn turn = currentEntry == null || walk == Walk.STILL ? null
            : currentEntry.turns.get(bone.getName());
        float rx = bone.getRotationX();
        float ry = bone.getRotationY();
        float rz = bone.getRotationZ();
        if (turn != null) {
            float t = DisplayClient.INSTANCE.partialTicks();
            bone.setRotationX(rx - (float) Math.toRadians(turn.get(0, t)));
            bone.setRotationY(ry - (float) Math.toRadians(turn.get(1, t)));
            bone.setRotationZ(rz + (float) Math.toRadians(turn.get(2, t)));
        }
        // A still bone is in its rest pose by definition, and that is what the mesh and the walk beside it use:
        // not what the animation processor happens to have left in it this frame (on the first frame a display is
        // animated, GeckoLib's reset of the bones without a track divides by its reset speed, which is 0 here).
        boolean rest = walk != Walk.ALL && plan.still(bone);
        float px = 0, py = 0, pz = 0, sx = 1, sy = 1, sz = 1;
        if (rest) {
            BoneSnapshot pose = bone.getInitialSnapshot();
            px = bone.getPositionX();
            py = bone.getPositionY();
            pz = bone.getPositionZ();
            sx = bone.getScaleX();
            sy = bone.getScaleY();
            sz = bone.getScaleZ();
            bone.setRotationX(pose.rotationValueX);
            bone.setRotationY(pose.rotationValueY);
            bone.setRotationZ(pose.rotationValueZ);
            bone.setPositionX(pose.positionOffsetX);
            bone.setPositionY(pose.positionOffsetY);
            bone.setPositionZ(pose.positionOffsetZ);
            bone.setScaleX(pose.scaleValueX);
            bone.setScaleY(pose.scaleValueY);
            bone.setScaleZ(pose.scaleValueZ);
        }
        MATRIX_STACK.push();
        MATRIX_STACK.translate(bone);
        MATRIX_STACK.moveToPivot(bone);
        MATRIX_STACK.rotate(bone);
        MATRIX_STACK.scale(bone);
        MATRIX_STACK.moveBackFromPivot(bone);
        if (turn != null || rest) {
            bone.setRotationX(rx);
            bone.setRotationY(ry);
            bone.setRotationZ(rz);
        }
        if (rest) {
            bone.setPositionX(px);
            bone.setPositionY(py);
            bone.setPositionZ(pz);
            bone.setScaleX(sx);
            bone.setScaleY(sy);
            bone.setScaleZ(sz);
        }
        if (pass == Pass.OPAQUE && itemBones != null && walk != Walk.STILL) {
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
        if (!bone.isHidden() && (walk != Walk.MOVING || !plan.still(bone))
            && (passes == null ? Pass.OPAQUE : passes.getOrDefault(bone.getName(), Pass.OPAQUE)) == pass) {
            drawnCubes += bone.childCubes.size();
            for (GeoCube cube : bone.childCubes) {
                MATRIX_STACK.push();
                try {
                    if (walk == Walk.STILL) {
                        sinkCube(cube);
                    } else {
                        renderCube(tess, cube, red, green, blue, alpha);
                    }
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
