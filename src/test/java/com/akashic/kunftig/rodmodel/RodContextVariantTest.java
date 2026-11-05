package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/** contextos_estados (the world swaps states) and variante (bones by a level stored in the item). */
class RodContextVariantTest {

    static final String EXTRA = "\"contextos_estados\": {\"chuva\": {\"parada\": \"briga\"}, "
        + "\"noite\": {\"parada\": \"lancar\", \"briga\": \"lancar\"}}, "
        + "\"variante\": {\"chave\": \"akashicfishing:vara-afeto\", "
        + "\"ossos\": {\"1\": [\"flor\"], \"2\": [\"coroa\", \"flor\"], \"3\": [\"estrela\"]}}, ";

    static RodModelDef solar(String extra) throws IOException {
        String json = RodModelIndexTest.resource("item_models_3d.json")
            .replaceFirst("\"estados\":", java.util.regex.Matcher.quoteReplacement(extra) + "\"estados\":");
        RodModelIndex index = new RodModelIndex();
        index.add("akashicfishing", json, "test");
        return index.models()
            .get("akashicfishing:vara/vara-solar");
    }

    static RodModelIndex index(String extra) throws IOException {
        String json = RodModelIndexTest.resource("item_models_3d.json")
            .replaceFirst("\"estados\":", java.util.regex.Matcher.quoteReplacement(extra) + "\"estados\":");
        RodModelIndex index = new RodModelIndex();
        index.add("akashicfishing", json, "test");
        return index;
    }

    @Test
    void contextsSwapStatesInTheManifestsOrder() throws IOException {
        RodModelDef rod = solar(EXTRA);
        assertEquals(Arrays.asList("chuva", "noite"), new java.util.ArrayList<>(rod.contextBands.keySet()));
        assertNull(rod.contextBand(Collections.<String>emptyList()));
        assertNull(rod.contextBand(Collections.singletonList("neve")), "a context the rod does not declare");
        assertEquals(
            "lancar",
            rod.contextBand(Collections.singletonList("noite"))
                .state("parada"));
        // rain and night at once: rain is first in the manifest
        RodModelDef.Band both = rod.contextBand(Arrays.asList("noite", "chuva"));
        assertEquals("briga", both.state("parada"));
        assertEquals("captura", both.state("captura"), "a state the context does not swap stays");
        // what the renderer plays: parada at night plays the lancar clips
        assertEquals(
            rod.states.get("lancar").rod,
            rod.clips("parada", rod.contextBand(Collections.singletonList("noite"))).rod);
    }

    @Test
    void variantGatesBonesByLevel() throws IOException {
        RodModelDef rod = solar(EXTRA);
        assertEquals("akashicfishing:vara-afeto", rod.variant.key);
        assertFalse(rod.showsAtVariant("flor", 0));
        assertTrue(rod.showsAtVariant("flor", 1), "the lowest level a bone is listed at");
        assertFalse(rod.showsAtVariant("coroa", 1));
        assertTrue(rod.showsAtVariant("coroa", 2));
        assertTrue(rod.showsAtVariant("estrela", 9));
        assertTrue(rod.showsAtVariant("asa_dir", 0), "bones not listed always show");
    }

    @Test
    void withoutTheFieldsNothingChanges() throws IOException {
        RodModelDef rod = solar("");
        assertTrue(rod.contextBands.isEmpty());
        assertNull(rod.variant);
        assertTrue(rod.showsAtVariant("flor", 0));
        assertNull(rod.contextBand(Collections.singletonList("noite")));
    }

    @Test
    void rejectsBrokenFields() throws IOException {
        assertProblem("\"contextos_estados\": {\"lua\": {\"parada\": \"briga\"}}, ", "unknown context");
        assertProblem("\"contextos_estados\": {\"noite\": {\"parada\": \"nenhum\"}}, ", "must swap a state");
        assertProblem("\"variante\": {\"chave\": \"afeto\", \"ossos\": {\"1\": [\"flor\"]}}, ", "variante.chave");
        assertProblem("\"variante\": {\"chave\": \"a:b\", \"ossos\": {\"zero\": [\"flor\"]}}, ", "whole number");
        assertProblem("\"variante\": {\"chave\": \"a:b\", \"ossos\": {}}, ", "needs ossos");
    }

    private static void assertProblem(String extra, String expected) throws IOException {
        RodModelIndex index = index(extra);
        assertNull(
            index.models()
                .get("akashicfishing:vara/vara-solar"),
            "should be rejected: " + expected);
        assertTrue(
            index.errors()
                .stream()
                .anyMatch(e -> e.contains(expected)),
            index.errors() + " should mention " + expected);
    }
}
