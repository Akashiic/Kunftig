package com.akashic.kunftig.screen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.bukkit.hud.HudElement;

class ScreenWireTest {

    @Test
    void hudElementsRoundTripAndAreSanitized() {
        HudWire.Element e = new HudWire.Element(
            HudWire.RIGHT,
            -12,
            40000,
            99,
            "§6§lSSS\n§712 hits",
            1.5F,
            64,
            0xFFFFAA00,
            0x80000000,
            "overkill:textures/hud/rank_s.png",
            32,
            32,
            100,
            300);
        HudWire.Message m = HudWire.read(HudWire.set("overkill:rank", e));
        assertEquals(HudWire.SET, m.op);
        assertEquals("overkill:rank", m.id);
        assertEquals(HudWire.RIGHT, m.element.anchor);
        assertEquals(-12, m.element.x);
        assertEquals(Short.MAX_VALUE, m.element.y, "clamped");
        assertEquals(8, m.element.scale, 0, "clamped");
        assertEquals("§6§lSSS\n§712 hits", m.element.text);
        assertEquals(1, m.element.progress, 0, "clamped");
        assertEquals(0xFFFFAA00, m.element.barColor);
        assertEquals("overkill:textures/hud/rank_s.png", m.element.texture);
        assertEquals(255, m.element.fadeTicks, "clamped");

        HudWire.Element noBar = new HudWire.Element(0, 0, 0, Float.NaN, null, Float.NaN, 0, 0, 0, "../x", 0, 0, -5, 0);
        HudWire.Message n = HudWire.read(HudWire.set("a:b", noBar));
        assertEquals(-1, n.element.progress, 0);
        assertEquals("", n.element.texture, "not a pack PNG: no picture");
        assertEquals(1, n.element.scale, 0);
        assertEquals(0, n.element.durationTicks);

        assertEquals(HudWire.REMOVE, HudWire.read(HudWire.remove("a:b")).op);
        assertEquals(HudWire.CLEAR, HudWire.read(HudWire.clear()).op);
        assertNull(HudWire.read(HudWire.remove("Not An Id")));
        assertNull(HudWire.read(new byte[] { 9 }));
        assertThrows(IllegalArgumentException.class, () -> HudWire.set("bad", noBar));
    }

    @Test
    void apiAnchorsMatchTheWire() {
        HudElement.Anchor[] anchors = HudElement.Anchor.values();
        assertEquals(HudWire.ANCHORS, anchors.length);
        assertEquals(HudWire.TOP_LEFT, HudElement.Anchor.TOP_LEFT.ordinal());
        assertEquals(HudWire.CENTER, HudElement.Anchor.CENTER.ordinal());
        assertEquals(HudWire.BOTTOM_RIGHT, HudElement.Anchor.BOTTOM_RIGHT.ordinal());
        assertEquals(HudElement.ID.pattern(), HudWire.ID.pattern());
    }

    @Test
    void layoutAlignsToAnchorsAndFades() {
        assertArrayEquals(new int[] { 5, 6 }, HudLayout.corner(HudWire.TOP_LEFT, 400, 300, 40, 20, 5, 6));
        assertArrayEquals(new int[] { 180, 140 }, HudLayout.corner(HudWire.CENTER, 400, 300, 40, 20, 0, 0));
        assertArrayEquals(new int[] { 350, 280 }, HudLayout.corner(HudWire.BOTTOM_RIGHT, 400, 300, 40, 20, -10, 0));
        assertArrayEquals(new int[] { 360, 140 }, HudLayout.corner(HudWire.RIGHT, 400, 300, 40, 20, 0, 0));
        assertEquals(1, HudLayout.alpha(1000, 0, 10), 0, "forever");
        assertEquals(1, HudLayout.alpha(50, 100, 20), 0);
        assertEquals(0.5F, HudLayout.alpha(90, 100, 20), 1e-6);
        assertEquals(0, HudLayout.alpha(100, 100, 20), 0);
    }

    @Test
    void cameraMessagesAndMath() {
        CameraWire.Message m = CameraWire.read(CameraWire.lock(42, 3, 500, true));
        assertEquals(CameraWire.LOCK, m.op);
        assertEquals(42, m.entityId);
        assertEquals(1, m.strength, 0, "clamped");
        assertEquals(CameraWire.MAX_DISTANCE, m.maxDistance, 0, "clamped");
        assertTrue(m.marker);
        assertEquals(CameraWire.UNLOCK, CameraWire.read(CameraWire.unlock()).op);
        assertNull(CameraWire.read(new byte[] { CameraWire.LOCK, 0 }));
        assertNull(CameraWire.read(new byte[] { 7 }));

        // Minecraft's angles: yaw 0 looks south (+Z), 90 west (-X); pitch positive looks down
        float[] south = CameraWire.look(0, 0, 0, 0, 0, 10);
        assertEquals(0, CameraWire.wrap(south[0]), 1e-4);
        float[] west = CameraWire.look(0, 0, 0, -10, 0, 0);
        assertEquals(90, CameraWire.wrap(west[0]), 1e-4);
        float[] down = CameraWire.look(0, 10, 0, 0, 0, 1);
        assertTrue(down[1] > 80);
        assertEquals(-170, CameraWire.wrap(190), 1e-4);

        // the same turn per second at 20 and at 200 frames per second
        float atTicks = 0;
        float left = 90;
        for (int i = 0; i < 20; i++) {
            float d = CameraWire.step(left, 0.3F, 1);
            atTicks += d;
            left -= d;
        }
        float atFrames = 0;
        left = 90;
        for (int i = 0; i < 200; i++) {
            float d = CameraWire.step(left, 0.3F, 0.1F);
            atFrames += d;
            left -= d;
        }
        assertEquals(atTicks, atFrames, 1e-2);
        assertFalse(CameraWire.step(10, 0.5F, 0) != 0, "no time, no turn");
    }
}
