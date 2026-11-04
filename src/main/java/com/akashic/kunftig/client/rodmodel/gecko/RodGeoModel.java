package com.akashic.kunftig.client.rodmodel.gecko;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import com.akashic.kunftig.client.rodmodel.RodModels;

import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.AnimationProcessor;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.geo.render.built.GeoBone;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import software.bernie.geckolib3.molang.MolangRegistrar;

/**
 * GeckoLib's animated model of one 3D rod, with three changes to {@link AnimatedGeoModel}:
 * <ul>
 * <li>its own clock ({@link RodModels#clock()}): GeckoLib's registers a ticker on the FML bus for every new key and
 * never removes it (issue #13, GeckoLib note 8);</li>
 * <li>a pose per key: the bones belong to the cached {@code GeoModel}, shared by everyone, and GeckoLib skips
 * animating a key twice at the same time, so a second draw in a frame (shadow pass, another player in between) would
 * get another player's pose; the pose of each key is saved and put back instead;</li>
 * <li>Molang: only {@code query.tensao}, the fight's tension of the rod being drawn.</li>
 * </ul>
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class RodGeoModel extends AnimatedGeoModel<RodAnimatable> {

    static final String TENSION = "query.tensao";
    private static final int FLOATS_PER_BONE = 9;

    final ResourceLocation geo;
    final ResourceLocation animations;
    ResourceLocation texture;
    private final Map<Integer, Pose> poses = new HashMap<>();
    private GeoModel bonesOf;
    private Map<String, GeoBone> byName = Collections.emptyMap();

    private static final class Pose {

        double seek;
        float[] values;
    }

    RodGeoModel(ResourceLocation geo, ResourceLocation animations, ResourceLocation texture) {
        this.geo = geo;
        this.animations = animations;
        this.texture = texture;
    }

    @Override
    public ResourceLocation getModelLocation(RodAnimatable object) {
        return geo;
    }

    @Override
    public ResourceLocation getTextureLocation(RodAnimatable object) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationFileLocation(RodAnimatable animatable) {
        return animations;
    }

    /** The bones of the model by name (for hiding them). */
    Map<String, GeoBone> bones(GeoModel model) {
        if (model != bonesOf) {
            Map<String, GeoBone> map = new HashMap<>();
            for (GeoBone b : model.topLevelBones) {
                collect(b, map);
            }
            byName = map;
            bonesOf = model;
        }
        return byName;
    }

    private static void collect(GeoBone bone, Map<String, GeoBone> into) {
        into.put(bone.getName(), bone);
        for (GeoBone child : bone.childBones) {
            collect(child, into);
        }
    }

    @Override
    public void setLivingAnimations(RodAnimatable rod, Integer key, AnimationEvent customPredicate) {
        RodModels rods = RodModels.INSTANCE;
        boolean paused = Minecraft.getMinecraft()
            .isGamePaused();
        double seek = rods.clock() + (paused ? 0 : rods.partialTicks());
        seekTime = seek;
        AnimationProcessor processor = getAnimationProcessor();
        List<IBone> bones = processor.getModelRendererList();
        Pose pose = poses.get(key);
        if (pose != null && pose.seek == seek && pose.values.length == bones.size() * FLOATS_PER_BONE) {
            restore(bones, pose.values);
            return;
        }
        AnimationEvent event = customPredicate != null ? customPredicate
            : new AnimationEvent(rod, 0, 0, rods.partialTicks(), false, Collections.emptyList());
        event.animationTick = seek;
        processor.preAnimationSetup(rod, seek);
        if (!bones.isEmpty()) {
            processor.tickAnimation(rod, key, seek, event, MolangRegistrar.getParser(), false);
        }
        for (Object c : rod.getFactory()
            .getOrCreateAnimationData(key)
            .getAnimationControllers()
            .values()) {
            if (c instanceof RodAnimatable.Controller && ((RodAnimatable.Controller) c).pendingOffset) {
                // GeckoLib started the clip now; count its time from the state's start instead.
                ((AnimationController) c).tickOffset = rod.start;
                ((RodAnimatable.Controller) c).pendingOffset = false;
            }
        }
        if (pose == null) {
            pose = new Pose();
            poses.put(key, pose);
        }
        pose.seek = seek;
        pose.values = save(bones, pose.values);
    }

    private static float[] save(List<IBone> bones, float[] into) {
        float[] v = into != null && into.length == bones.size() * FLOATS_PER_BONE ? into
            : new float[bones.size() * FLOATS_PER_BONE];
        int i = 0;
        for (IBone b : bones) {
            v[i++] = b.getRotationX();
            v[i++] = b.getRotationY();
            v[i++] = b.getRotationZ();
            v[i++] = b.getPositionX();
            v[i++] = b.getPositionY();
            v[i++] = b.getPositionZ();
            v[i++] = b.getScaleX();
            v[i++] = b.getScaleY();
            v[i++] = b.getScaleZ();
        }
        return v;
    }

    private static void restore(List<IBone> bones, float[] v) {
        int i = 0;
        for (IBone b : bones) {
            b.setRotationX(v[i++]);
            b.setRotationY(v[i++]);
            b.setRotationZ(v[i++]);
            b.setPositionX(v[i++]);
            b.setPositionY(v[i++]);
            b.setPositionZ(v[i++]);
            b.setScaleX(v[i++]);
            b.setScaleY(v[i++]);
            b.setScaleZ(v[i++]);
        }
    }

    /** Only the tension: the world queries of GeckoLib are not used by the rods. */
    @Override
    public void setMolangQueries(IAnimatable animatable, double currentTick) {
        MolangRegistrar.getParser()
            .setValue(TENSION, ((RodAnimatable) animatable).tension);
    }
}
