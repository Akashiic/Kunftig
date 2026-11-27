package com.akashic.kunftig.modern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.modern.math.BlockPos;
import com.akashic.kunftig.modern.math.Mth;
import com.akashic.kunftig.modern.math.Vec3;

class MathTest {

    @Test
    void mthMatchesModernSemantics() {
        assertEquals(-1, Mth.floor(-0.5D));
        assertEquals(1, Mth.ceil(0.2F));
        assertEquals(5, Mth.clamp(9, 0, 5));
        assertEquals(-170F, Mth.wrapDegrees(190F), 1e-4);
        assertEquals(2.5D, Mth.lerp(0.5D, 0D, 5D), 1e-9);
        assertEquals(0xFF0000, Mth.hsvToRgb(0F, 1F, 1F));
        assertEquals(0F, Mth.sin(0F), 1e-4);
        assertEquals(1F, Mth.cos(0F), 1e-4);
    }

    @Test
    void vec3Arithmetic() {
        Vec3 a = new Vec3(1, 2, 2);
        assertEquals(3D, a.length(), 1e-9);
        assertEquals(new Vec3(2, 4, 4), a.scale(2));
        assertEquals(new Vec3(0, 0, 0), a.subtract(a));
        assertEquals(
            1D,
            a.normalize()
                .length(),
            1e-9);
        assertEquals(new Vec3(0, 0, 1), new Vec3(1, 0, 0).cross(new Vec3(0, 1, 0)));
        // yRot by 90 degrees turns +X into -Z (modern convention)
        Vec3 rotated = new Vec3(1, 0, 0).yRot((float) (Math.PI / 2));
        assertEquals(0D, rotated.x, 1e-3);
        assertEquals(-1D, rotated.z, 1e-3);
    }

    @Test
    void blockPosNavigation() {
        BlockPos pos = new BlockPos(1, 64, -3);
        assertEquals(new BlockPos(1, 65, -3), pos.above());
        assertEquals(new BlockPos(1, 64, -4), pos.north());
        assertEquals(BlockPos.containing(1.9, 64.2, -2.1), pos);
        assertEquals(pos, new BlockPos(1, 64, -3));
    }
}
