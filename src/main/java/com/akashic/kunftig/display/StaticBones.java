package com.akashic.kunftig.display;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Which bones of a display model stand still, so the client can compile their cubes once instead of transforming every
 * vertex again each frame (a ship has thousands of cubes and four or five bones that move).
 * <p>
 * A bone <em>moves</em> when something may change its matrix from one frame to the next: an animation of the model's
 * file has a track for it, the server turned it (API 13), an item hangs on it, or a bone above it moves. Every other
 * visible bone is <em>still</em>: its cubes go into the compiled mesh. A hidden bone takes everything under it out of
 * both, as in the renderer.
 * <p>
 * The per-frame walk only needs the bones that move and the still bones above them (for their matrices):
 * {@link #walk}. Pure logic over any bone type, so it is tested without GeckoLib.
 */
public final class StaticBones<B> {

    private final Set<B> still = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<B> walk = Collections.newSetFromMap(new IdentityHashMap<>());
    private int stillBones;
    private int movingBones;

    private StaticBones() {}

    /**
     * @param roots    the model's top-level bones
     * @param children a bone's child bones
     * @param name     a bone's name
     * @param moves    whether the bone of this name moves on its own (animated, turned by the server, an item bone)
     * @param visible  whether the bone of this name is drawn at all
     */
    public static <B> StaticBones<B> plan(List<B> roots, Function<B, List<B>> children, Function<B, String> name,
        Predicate<String> moves, Predicate<String> visible) {
        StaticBones<B> plan = new StaticBones<>();
        for (B root : roots) {
            plan.visit(root, false, children, name, moves, visible);
        }
        return plan;
    }

    /** Returns whether this bone or one under it has to be walked every frame. */
    private boolean visit(B bone, boolean parentMoves, Function<B, List<B>> children, Function<B, String> name,
        Predicate<String> moves, Predicate<String> visible) {
        String n = name.apply(bone);
        if (!visible.test(n)) {
            return false;
        }
        boolean moving = parentMoves || moves.test(n);
        boolean walked = moving;
        for (B child : children.apply(bone)) {
            walked |= visit(child, moving, children, name, moves, visible);
        }
        if (moving) {
            movingBones++;
        } else {
            still.add(bone);
            stillBones++;
        }
        if (walked) {
            walk.add(bone);
        }
        return walked;
    }

    /** The bone stands still: its cubes are in the compiled mesh, not drawn by the per-frame walk. */
    public boolean still(B bone) {
        return still.contains(bone);
    }

    /** The per-frame walk has to enter this bone: it moves, or a bone under it does. */
    public boolean walk(B bone) {
        return walk.contains(bone);
    }

    public int stillBones() {
        return stillBones;
    }

    public int movingBones() {
        return movingBones;
    }
}
