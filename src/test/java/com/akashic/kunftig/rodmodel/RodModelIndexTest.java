package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RodModelIndexTest {

    static String resource(String name) throws IOException {
        try (InputStream in = RodModelIndexTest.class.getResourceAsStream("/rodmodel/" + name)) {
            assertNotNull(in, name);
            byte[] all = new byte[1 << 16];
            int n = 0;
            for (int r; (r = in.read(all, n, all.length - n)) > 0;) {
                n += r;
            }
            return new String(all, 0, n, StandardCharsets.UTF_8);
        }
    }

    static RodModelIndex fixture() throws IOException {
        RodModelIndex index = new RodModelIndex();
        index.add("akashicfishing", resource("item_models_3d.json"), "akashicfishing:" + RodModelIndex.PATH);
        return index;
    }

    @Test
    void readsTheManifest() throws IOException {
        RodModelIndex index = fixture();
        RodModelDef rod = index.models()
            .get("akashicfishing:vara/vara-solar");
        assertNotNull(rod);
        assertEquals("akashicfishing:geo/vara/vara-solar.geo.json", rod.geo);
        assertEquals("akashicfishing:animations/vara/braco.animation.json", rod.arm);
        assertEquals(3, rod.ticksPerFrame);
        assertTrue(rod.states.get("parada").loop);
        assertFalse(rod.states.get("lancar").loop);
        assertEquals("animation.vara-solar.aura_briga", rod.states.get("briga").aura);
        assertNull(rod.states.get("sacar").aura);
        assertNull(rod.states.get("sacar").arm);
        assertEquals("ponta", rod.tipBone);
        assertArrayEquals(new float[] { 0, 32.4F, 0 }, rod.tipRest);
        assertArrayEquals(new float[] { 0, 4.5F, 0 }, rod.grip);
        assertTrue(rod.lightBones.contains("raios"));
        assertTrue(rod.translucentBones.contains("m4_aura_1"));
        assertTrue(rod.hiddenWhileCast.contains("linha"));
        assertEquals(0.4F, rod.displays.get(RodModelDef.FIRST_PERSON).scale);
        assertArrayEquals(new float[] { -7.5F, -10.5F, -15 }, rod.displays.get(RodModelDef.FIRST_PERSON).translation);
        // "contextos" leaves the frame out although it has a display.
        assertTrue(rod.drawsIn(RodModelDef.GROUND));
        assertFalse(rod.drawsIn(RodModelDef.FRAME));
    }

    @Test
    void badModelsAreLeftOutAndReported() throws IOException {
        RodModelIndex index = fixture();
        assertEquals(
            1,
            index.models()
                .size());
        assertEquals(
            2,
            index.errors()
                .size(),
            index.errors()
                .toString());
        assertTrue(
            index.errors()
                .get(0)
                .contains("vara/sem-parada")
                && index.errors()
                    .get(0)
                    .contains("parada"));
        assertTrue(
            index.errors()
                .get(1)
                .contains("vara/Maiuscula"));
    }

    @Test
    void mutationsTexturesAndLineColors() throws IOException {
        RodModelDef rod = fixture().models()
            .get("akashicfishing:vara/vara-solar");
        // Bones show from their lowest stage.
        assertTrue(rod.showsAt("vara", 0));
        assertFalse(rod.showsAt("m2_estrela", 1));
        assertTrue(rod.showsAt("m2_estrela", 2));
        assertFalse(rod.showsAt("m4_aura_1", 3));
        assertTrue(rod.showsAt("m4_aura_1", 6));
        // Textures: stage 0 the base, stages 3-5 fall back to 2, stage 6 cycles every 3 ticks.
        assertEquals("akashicfishing:textures/vara3d/vara-solar.png", rod.textureFor(0, 99));
        assertEquals("akashicfishing:textures/vara3d/vara-solar_m2.png", rod.textureFor(4, 0));
        assertEquals("akashicfishing:textures/vara3d/vara-solar_m6_0.png", rod.textureFor(6, 2));
        assertEquals("akashicfishing:textures/vara3d/vara-solar_m6_1.png", rod.textureFor(6, 3));
        assertEquals("akashicfishing:textures/vara3d/vara-solar_m6_0.png", rod.textureFor(6, 9));
        // Line: 0, then the aura from 1 to 5, white at 6.
        assertEquals(0xffe9a8, rod.lineColor(0));
        assertEquals(0xfff0b0, rod.lineColor(5));
        assertEquals(0xffffff, rod.lineColor(6));
    }

    @Test
    void resolvesTheItemModelWithItsSuffixes() throws IOException {
        RodModelIndex index = fixture();
        RodModelDef rod = index.models()
            .get("akashicfishing:vara/vara-solar");

        RodModelIndex.Resolved plain = index.resolve("akashicfishing:vara/vara-solar");
        assertSame(rod, plain.model);
        assertNull(plain.quality);
        assertEquals(0, plain.stage);
        assertFalse(plain.cast);

        RodModelIndex.Resolved full = index.resolve("akashicfishing:vara/vara-solar.ouro.m3.cast");
        assertSame(rod, full.model);
        assertEquals("ouro", full.quality);
        assertEquals(3, full.stage);
        assertTrue(full.cast);

        RodModelIndex.Resolved stageOnly = index.resolve("akashicfishing:vara/vara-solar.m6");
        assertNull(stageOnly.quality);
        assertEquals(6, stageOnly.stage);
        assertEquals("prata", index.resolve("akashicfishing:vara/vara-solar.prata").quality);

        // Not ours: other rods, other namespaces, a quality on the wrong segment.
        assertNull(index.resolve("akashicfishing:vara/vara-de-osso"));
        assertNull(index.resolve("outro:vara/vara-solar"));
        assertNull(index.resolve("akashicfishing:vara.x/vara-solar"));
        assertNull(index.resolve(null));
        // Cached answers stay the same.
        assertSame(index.resolve("akashicfishing:vara/vara-solar.ouro.m3.cast"), full);
    }

    @Test
    void laterPacksOverrideAndOtherFormatsAreRefused() throws IOException {
        RodModelIndex index = fixture();
        index.add(
            "akashicfishing",
            "{\"_formato\": 1, \"modelos\": {\"vara/vara-solar\": {\"geo\": \"g\", \"animacoes\": \"a\","
                + " \"textura\": \"t\", \"estados\": {\"parada\": {\"vara\": \"p\"}}}}}",
            "override");
        assertEquals(
            "akashicfishing:g",
            index.models()
                .get("akashicfishing:vara/vara-solar").geo);
        int before = index.errors()
            .size();
        index.add("x", "{\"_formato\": 2, \"modelos\": {}}", "future");
        index.add("x", "not json", "broken");
        assertEquals(
            before + 2,
            index.errors()
                .size());
        assertTrue(
            index.errors()
                .get(before)
                .contains("_formato"));
    }

    @Test
    void displayPrintsAsJson() {
        RodModelDef.Display d = new RodModelDef.Display(
            new float[] { 15, -45, 10 },
            new float[] { -7.5F, -10.5F, -15 },
            0.4F);
        assertEquals(
            "{ \"rotation\": [15, -45, 10], \"translation\": [-7.5, -10.5, -15], \"scale\": 0.4 }",
            d.toString());
        assertTrue(Arrays.equals(new float[] { 15, -45, 10 }, d.rotation));
    }
}
