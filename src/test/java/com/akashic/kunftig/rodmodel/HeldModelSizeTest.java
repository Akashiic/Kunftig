package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/** Models that are not rods (a fish): items, size, size bands, quality textures and "modelo" (manifest fields). */
class HeldModelSizeTest {

    static final String FISH = "{\"_formato\": 1, \"modelos\": {\"pirarucu\": {"
        + "\"geo\": \"geo/peixe/pirarucu.geo.json\", \"animacoes\": \"animations/peixe/peixe.animation.json\","
        + "\"braco\": \"animations/peixe/braco.animation.json\", \"textura\": \"textures/peixe3d/pirarucu.png\","
        + "\"texturas_qualidade\": {\"ouro\": \"textures/peixe3d/pirarucu_ouro.png\","
        + "  \"iridio\": [\"textures/peixe3d/pirarucu_iridio_0.png\", \"textures/peixe3d/pirarucu_iridio_1.png\"]},"
        + "\"ticks_por_quadro\": 2,"
        + "\"itens\": [\"minecraft:fish\"],"
        + "\"tamanho\": {\"chave\": \"akashicfishing:tamanho-cm\", \"unidades_por_bloco\": 100, \"comprimento\": 32,"
        + "  \"minimo\": 0.4, \"maximo\": 5, \"padrao\": 150},"
        + "\"estados\": {"
        + "  \"parada\": {\"modelo\": \"animation.peixe.parada\", \"braco\": \"animation.braco.peixe_mao\", \"laco\": true},"
        + "  \"sacar\": {\"modelo\": \"animation.peixe.sacar\", \"braco\": \"animation.braco.peixe_mao_sacar\"},"
        + "  \"parada_duas_maos\": {\"modelo\": \"animation.peixe.parada\", \"braco\": \"animation.braco.peixe_duas\","
        + "    \"laco\": true, \"duas_maos\": true},"
        + "  \"parada_acima\": {\"modelo\": \"animation.peixe.parada\", \"braco\": \"animation.braco.peixe_acima\","
        + "    \"laco\": true, \"duas_maos\": true}},"
        + "\"faixas\": ["
        + "  {\"nome\": \"mao\", \"ate\": 60},"
        + "  {\"nome\": \"duas_maos\", \"ate\": 160, \"estados\": {\"parada\": \"parada_duas_maos\", \"sacar\": \"parada_duas_maos\"},"
        + "    \"display\": {\"terceira_pessoa\": {\"rotation\": [0, 90, 0], \"scale\": 1}}},"
        + "  {\"nome\": \"acima\", \"pega\": [0, -6, 2], \"estados\": {\"parada\": \"parada_acima\", \"sacar\": \"parada_acima\"}}],"
        + "\"display\": {\"primeira_pessoa\": {\"scale\": 0.5}, \"terceira_pessoa\": {\"scale\": 0.8}},"
        + "\"contextos\": [\"primeira_pessoa\", \"terceira_pessoa\"]"
        + "}}}";

    static RodModelDef fish(String json) {
        RodModelIndex index = new RodModelIndex();
        index.add("akashicfishing", json, "test");
        assertEquals(Collections.emptyList(), index.errors());
        assertEquals(Collections.emptyList(), index.ignored());
        return index.models()
            .get("akashicfishing:pirarucu");
    }

    @Test
    void readsTheFishFields() {
        RodModelDef f = fish(FISH);
        assertEquals(Arrays.asList("minecraft:fish"), f.items);
        assertEquals("animation.peixe.parada", f.states.get("parada").rod, "\"modelo\" is the model's animation");
        assertEquals("akashicfishing:tamanho-cm", f.size.key);
        assertEquals(3, f.bands.size());
        assertEquals(Double.POSITIVE_INFINITY, f.bands.get(2).upTo);
    }

    @Test
    void aRodDrawsOnTheFishingRodOnly() throws Exception {
        RodModelDef rod = RodModelIndexTest.fixture()
            .models()
            .get("akashicfishing:vara/vara-solar");
        assertEquals(RodModelDef.DEFAULT_ITEMS, rod.items);
        assertNull(rod.size);
        assertNull(rod.band(10));
        assertSame(rod.clips("briga"), rod.clips("briga", null));
    }

    @Test
    void bandsPickStatesAndDisplays() {
        RodModelDef f = fish(FISH);
        assertEquals("mao", f.band(6).name);
        assertEquals("mao", f.band(60).name, "ate is inclusive");
        assertEquals("duas_maos", f.band(60.1).name);
        assertEquals("acima", f.band(2400).name);
        RodModelDef.Band two = f.band(100);
        assertEquals("animation.braco.peixe_duas", f.clips("parada", two).arm);
        assertTrue(f.clips("parada", two).twoHands);
        assertEquals("animation.braco.peixe_mao", f.clips("parada", f.band(20)).arm);
        // a state the band does not swap: the model's own, and an unknown one falls back to parada
        assertEquals("animation.peixe.parada", f.clips("briga", two).rod);
        // the band's display for its context, the model's for the others
        assertEquals(90, f.display(RodModelDef.THIRD_PERSON, two).rotation[1]);
        assertEquals(0.5F, f.display(RodModelDef.FIRST_PERSON, two).scale);
        assertEquals(0.8F, f.display(RodModelDef.THIRD_PERSON, f.band(20)).scale);
        // a band can hold the model elsewhere (pega): the giant by the belly, not by the mouth
        assertEquals(-6F, f.grip(f.band(500))[1]);
        assertSame(f.grip, f.grip(f.band(20)));
    }

    @Test
    void sizeScalesTheModel() {
        RodModelDef.Size s = fish(FISH).size;
        // 32 pixels long as modeled: 1 m (100 cm) is one block, 16 pixels: half the model
        assertEquals(0.5F, s.scale(100), 1e-6);
        assertEquals(0.4F * 16 / 32, s.scale(6), 1e-6, "a tiny fish still shows (minimo, in blocks)");
        assertEquals(5F * 16 / 32, s.scale(2400), 1e-6, "a leviathan is capped (maximo)");
        assertEquals(150, s.valueOr(null));
        assertEquals(150, s.valueOr(Double.NaN));
        assertEquals(42, s.valueOr(42.0));
        RodModelDef.Display d = new RodModelDef.Display(new float[3], new float[3], 0.8F);
        assertEquals(0.4F, RodModelDef.scaled(d, 0.5F).scale, 1e-6);
        assertSame(d, RodModelDef.scaled(d, 1F));
    }

    @Test
    void qualityTextures() {
        RodModelDef f = fish(FISH);
        assertEquals("akashicfishing:textures/peixe3d/pirarucu.png", f.textureFor(null, 0, 0));
        assertEquals("akashicfishing:textures/peixe3d/pirarucu.png", f.textureFor("prata", 0, 0), "no texture: base");
        assertEquals("akashicfishing:textures/peixe3d/pirarucu_ouro.png", f.textureFor("ouro", 0, 5));
        assertEquals("akashicfishing:textures/peixe3d/pirarucu_iridio_0.png", f.textureFor("iridio", 0, 1));
        assertEquals("akashicfishing:textures/peixe3d/pirarucu_iridio_1.png", f.textureFor("iridio", 0, 2));
        assertEquals("akashicfishing:textures/peixe3d/pirarucu_iridio_0.png", f.textureFor("iridio", 0, 4));
        // "normal" (an item without a quality suffix) can have its own animated texture too (Peixe-Akasha)
        RodModelDef akasha = fish(
            FISH.replace(
                "\"ouro\": \"textures",
                "\"normal\": [\"textures/peixe3d/a_0.png\", " + "\"textures/peixe3d/a_1.png\"], \"ouro\": \"textures"));
        assertEquals("akashicfishing:textures/peixe3d/a_1.png", akasha.textureFor(null, 0, 2));
        assertEquals("akashicfishing:textures/peixe3d/a_0.png", akasha.textureFor("normal", 0, 0));
    }

    @Test
    void rejectsBrokenFishFields() {
        assertProblem(FISH.replace("\"tamanho\":", "\"tamanho_x\":"), "faixas needs tamanho");
        assertProblem(
            FISH.replace("\"parada\": \"parada_duas_maos\"", "\"parada\": \"nenhum\""),
            "must name a state of estados");
        assertProblem(FISH.replace("\"ate\": 160", "\"ate\": 50"), "must grow");
        assertProblem(FISH.replace("{\"nome\": \"mao\", \"ate\": 60}", "{\"nome\": \"mao\"}"), "needs \"ate\"");
        assertProblem(FISH.replace("[\"minecraft:fish\"]", "[\"fish\"]"), "itens: invalid item");
        assertProblem(FISH.replace("\"maximo\": 5", "\"maximo\": 0.1"), "minimo <= maximo");
    }

    private static void assertProblem(String json, String expected) {
        RodModelIndex index = new RodModelIndex();
        index.add("akashicfishing", json, "test");
        assertTrue(
            index.models()
                .isEmpty(),
            "should be rejected: " + expected);
        assertTrue(
            index.errors()
                .stream()
                .anyMatch(e -> e.contains(expected)),
            index.errors() + " should mention " + expected);
    }
}
