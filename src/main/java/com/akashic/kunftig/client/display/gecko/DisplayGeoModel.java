package com.akashic.kunftig.client.display.gecko;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import com.akashic.kunftig.client.display.DisplayClient;

import software.bernie.geckolib3.core.AnimationState;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.AnimationProcessor;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import software.bernie.geckolib3.molang.MolangRegistrar;

/**
 * GeckoLib's animated model of one display model, with the same two changes the rods' model has
 * ({@code RodGeoModel}): Kunftig's own clock (GeckoLib's registers a ticker per key that is never removed), and a pose
 * per key (the bones belong to the shared {@code GeoModel}; a second draw in the same frame puts the saved pose back
 * instead of animating again). The texture is set per display (its variant) right before it is drawn.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class DisplayGeoModel extends AnimatedGeoModel<DisplayAnimatable> {

    private static final int FLOATS_PER_BONE = 9;

    final ResourceLocation geo;
    final ResourceLocation animations;
    ResourceLocation texture;
    private final Map<Integer, Pose> poses = new HashMap<>();

    private static final class Pose {

        double seek;
        float[] values;
    }

    DisplayGeoModel(ResourceLocation geo, ResourceLocation animations, ResourceLocation texture) {
        this.geo = geo;
        this.animations = animations;
        this.texture = texture;
    }

    @Override
    public ResourceLocation getModelLocation(DisplayAnimatable object) {
        return geo;
    }

    @Override
    public ResourceLocation getTextureLocation(DisplayAnimatable object) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationFileLocation(DisplayAnimatable animatable) {
        return animations;
    }

    @Override
    public void setLivingAnimations(DisplayAnimatable a, Integer key, AnimationEvent customPredicate) {
        DisplayClient displays = DisplayClient.INSTANCE;
        boolean paused = Minecraft.getMinecraft()
            .isGamePaused();
        double seek = displays.clock() + (paused ? 0 : displays.partialTicks());
        seekTime = seek;
        AnimationProcessor processor = getAnimationProcessor();
        List<IBone> bones = processor.getModelRendererList();
        Pose pose = poses.get(key);
        if (pose != null && pose.seek == seek && pose.values.length == bones.size() * FLOATS_PER_BONE) {
            restore(bones, pose.values);
            return;
        }
        AnimationEvent event = customPredicate != null ? customPredicate
            : new AnimationEvent(a, 0, 0, displays.partialTicks(), false, Collections.emptyList());
        event.animationTick = seek;
        processor.preAnimationSetup(a, seek);
        if (!bones.isEmpty()) {
            processor.tickAnimation(a, key, seek, event, MolangRegistrar.getParser(), false);
        }
        for (Object c : a.getFactory()
            .getOrCreateAnimationData(key)
            .getAnimationControllers()
            .values()) {
            if (c instanceof DisplayAnimatable.Controller && ((DisplayAnimatable.Controller) c).pendingOffset
                && ((AnimationController) c).getAnimationState() == AnimationState.Running) {
                // GeckoLib started the animation now (or at the end of the blend); count its time from the state's
                // start instead, as the server does.
                ((AnimationController) c).tickOffset = ((DisplayAnimatable.Controller) c).offset;
                ((DisplayAnimatable.Controller) c).pendingOffset = false;
                processor.preAnimationSetup(a, seek);
                processor.tickAnimation(a, key, seek, event, MolangRegistrar.getParser(), false);
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

    /** No Molang queries: the displays' animations do not read any. */
    @Override
    public void setMolangQueries(IAnimatable animatable, double currentTick) {}
}
