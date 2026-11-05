package com.akashic.kunftig.client.display.gecko;

import com.akashic.kunftig.client.display.DisplayClient;
import com.akashic.kunftig.display.DisplayModelIndex;

import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

/**
 * GeckoLib's view of one display model: one controller per animation key (a key per display, its id), playing the
 * animation of the display's state from the client tick the state started at (the server's elapsed time included), so
 * a display seen late is already at the right moment. Rendering is sequential on the client thread: the fields below
 * are set right before each display is animated.
 */
final class DisplayAnimatable implements IAnimatable {

    final DisplayModelIndex.Model def;
    final DisplayGeoModel model;
    private final AnimationFactory factory = new AnimationFactory(this);

    /** The display being drawn: its state's animation, whether it loops, and the client tick it started at. */
    String animation;
    boolean loop;
    long start;

    DisplayAnimatable(DisplayModelIndex.Model def, DisplayGeoModel model) {
        this.def = def;
        this.model = model;
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new Controller(this));
        data.setResetSpeedInTicks(0);
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    /** Starts the animation when the state (or its start) changes; its time then counts from the state's start. */
    static final class Controller extends AnimationController<DisplayAnimatable> {

        private String applied;
        private long appliedStart = Long.MIN_VALUE;
        boolean pendingOffset;
        long offset;

        Controller(DisplayAnimatable a) {
            super(a, "exibicao", 0, event -> ((Controller) event.getController()).decide(event));
        }

        private PlayState decide(AnimationEvent<DisplayAnimatable> event) {
            DisplayAnimatable a = event.getAnimatable();
            if (a.animation == null) {
                applied = null;
                return PlayState.STOP;
            }
            if (!a.animation.equals(applied) || a.start != appliedStart) {
                transitionLengthTicks = 0;
                markNeedsReload();
                setAnimation(
                    new AnimationBuilder().addAnimation(
                        a.animation,
                        a.loop ? ILoopType.EDefaultLoopTypes.LOOP : ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME));
                applied = a.animation;
                appliedStart = a.start;
                pendingOffset = true;
                offset = a.start;
            }
            return PlayState.CONTINUE;
        }
    }

    /** The clock the displays use (client ticks). */
    static long clock() {
        return DisplayClient.INSTANCE.clock();
    }
}
