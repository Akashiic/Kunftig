package com.akashic.kunftig.client.rodmodel.gecko;

import com.akashic.kunftig.rodmodel.RodModelDef;

import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

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
     * Plays the clip of the current state from the state's start: when the clip or the start changes, the animation is
     * restarted (a state that repeats, such as a second bite, plays again) and {@link #pendingOffset} asks
     * {@link RodGeoModel} to count its time from the state's start (a player seen mid-animation).
     */
    static final class Controller extends AnimationController<RodAnimatable> {

        private String applied;
        private long appliedStart = Long.MIN_VALUE;
        boolean pendingOffset;

        Controller(RodAnimatable rod, String name, boolean aura) {
            super(rod, name, 0, event -> ((Controller) event.getController()).decide(event, aura));
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
                markNeedsReload();
                setAnimation(
                    new AnimationBuilder().addAnimation(
                        clip,
                        c.loop ? ILoopType.EDefaultLoopTypes.LOOP : ILoopType.EDefaultLoopTypes.PLAY_ONCE));
                applied = clip;
                appliedStart = a.start;
                pendingOffset = true;
            }
            return PlayState.CONTINUE;
        }
    }
}
