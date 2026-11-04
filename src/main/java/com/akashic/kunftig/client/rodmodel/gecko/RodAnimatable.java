package com.akashic.kunftig.client.rodmodel.gecko;

import org.apache.commons.lang3.tuple.Pair;

import com.akashic.kunftig.client.rodmodel.RodModels;
import com.akashic.kunftig.rodmodel.RodMatrix;
import com.akashic.kunftig.rodmodel.RodModelDef;

import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.core.snapshot.BoneSnapshot;

/**
 * GeckoLib's view of one 3D rod: two controllers per animation key (a key per player, see
 * {@code RodModels#animationKey}), "vara" and "aura", each playing the clip of the state set in {@link #clips} right
 * before the rod is animated. Rendering is sequential on the client thread, so one set of fields serves every key.
 */
final class RodAnimatable implements IAnimatable {

    final RodModelDef rod;
    final RodGeoModel model;
    private final AnimationFactory factory = new AnimationFactory(this);

    /** The state being drawn: its clips, the client tick it started at, and the fight's tension. */
    RodModelDef.Clips clips;
    long start;
    float tension;
    /** The animation key being drawn (its bone snapshots are wrapped before a blend). */
    int key;

    RodAnimatable(RodModelDef rod, RodGeoModel model) {
        this.rod = rod;
        this.model = model;
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new Controller(this, "vara", false));
        data.addAnimationController(new Controller(this, "aura", true));
        data.setResetSpeedInTicks(1);
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    /**
     * Plays the clip of the current state from the state's start. When the clip or the start changes, the animation is
     * restarted (a state that repeats, such as a second bite, plays again) with the controller's blend
     * ({@code transicao_ticks}), from rotations first wrapped to the short way (the continuous spins carry whole turns,
     * which GeckoLib's blend would unwind). A player first seen in the middle of a state skips the blend and enters the
     * clip at the right time ({@link #pendingOffset}, applied by {@link RodGeoModel}): in both cases the clip's time is
     * the state's time minus the blend, as the timeline assumes.
     */
    static final class Controller extends AnimationController<RodAnimatable> {

        private final int blend;
        private String applied;
        private long appliedStart = Long.MIN_VALUE;
        boolean pendingOffset;
        long offset;

        Controller(RodAnimatable rod, String name, boolean aura) {
            super(
                rod,
                name,
                rod.rod.transition(name),
                event -> ((Controller) event.getController()).decide(event, aura));
            this.blend = rod.rod.transition(name);
        }

        private PlayState decide(AnimationEvent<RodAnimatable> event, boolean aura) {
            RodAnimatable a = event.getAnimatable();
            RodModelDef.Clips c = a.clips;
            String clip = c == null ? null : aura ? c.aura : c.rod;
            if (clip == null) {
                applied = null;
                return PlayState.STOP; // no aura in this state: its bones go back to rest
            }
            if (!clip.equals(applied) || a.start != appliedStart) {
                // Seen after the blend would be over (out of view at the change, or arriving mid-state): no blend.
                boolean late = RodModels.INSTANCE.clock() - a.start > blend;
                wrapRotations(a);
                transitionLengthTicks = late ? 0 : blend;
                markNeedsReload();
                setAnimation(
                    new AnimationBuilder().addAnimation(
                        clip,
                        c.loop ? ILoopType.EDefaultLoopTypes.LOOP : ILoopType.EDefaultLoopTypes.PLAY_ONCE));
                applied = clip;
                appliedStart = a.start;
                pendingOffset = late;
                offset = a.start + blend;
            }
            return PlayState.CONTINUE;
        }

        /**
         * The blend starts from the key's bone snapshots (rotation minus rest): bring every one within half a turn of
         * its rest, so that a crank that turned twice does not spin back twice.
         */
        private static void wrapRotations(RodAnimatable a) {
            for (Pair<IBone, BoneSnapshot> p : a.getFactory()
                .getOrCreateAnimationData(a.key)
                .getBoneSnapshotCollection()
                .values()) {
                BoneSnapshot now = p.getRight();
                BoneSnapshot rest = p.getLeft()
                    .getInitialSnapshot();
                if (now == null || rest == null) {
                    continue;
                }
                now.rotationValueX = rest.rotationValueX + RodMatrix.wrap(now.rotationValueX - rest.rotationValueX);
                now.rotationValueY = rest.rotationValueY + RodMatrix.wrap(now.rotationValueY - rest.rotationValueY);
                now.rotationValueZ = rest.rotationValueZ + RodMatrix.wrap(now.rotationValueZ - rest.rotationValueZ);
            }
        }
    }
}
