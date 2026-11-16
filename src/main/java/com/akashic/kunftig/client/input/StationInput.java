package com.akashic.kunftig.client.input;

import net.minecraft.util.MovementInput;

/**
 * The local player's movement input while he is at a station (API 14): the keys are read as always, kept for the
 * server ({@link #forward} ...), and none of them reaches the legs.
 */
final class StationInput extends MovementInput {

    final MovementInput real;
    float forward;
    float strafe;
    boolean jumping;
    boolean sneaking;

    StationInput(MovementInput real) {
        this.real = real;
    }

    @Override
    public void updatePlayerMoveState() {
        real.updatePlayerMoveState();
        forward = real.moveForward;
        strafe = real.moveStrafe;
        jumping = real.jump;
        sneaking = real.sneak;
        moveForward = 0;
        moveStrafe = 0;
        jump = false;
        sneak = false;
    }
}
