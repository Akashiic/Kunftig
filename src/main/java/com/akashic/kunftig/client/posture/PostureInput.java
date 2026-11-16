package com.akashic.kunftig.client.posture;

import net.minecraft.util.MovementInput;

/**
 * The local player's movement input while he sits or lies (API 15): the keys do not move him; sneaking goes through,
 * for it is how he gets up (the server sees it).
 */
final class PostureInput extends MovementInput {

    final MovementInput real;

    PostureInput(MovementInput real) {
        this.real = real;
    }

    @Override
    public void updatePlayerMoveState() {
        real.updatePlayerMoveState();
        moveForward = 0;
        moveStrafe = 0;
        jump = false;
        sneak = real.sneak;
    }
}
