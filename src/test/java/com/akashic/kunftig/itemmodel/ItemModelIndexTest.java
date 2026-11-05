package com.akashic.kunftig.itemmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ItemModelIndexTest {

    @Test
    void modelsAreNamespacedByTheirPackDomain() {
        ItemModelIndex index = new ItemModelIndex();
        index.add(
            "akashicfishing",
            "{\"tucunare\": \"akashicfishing:fish/tucunare\", \"bota_velha\": \"junk/boot\", \"x/y.z-1\": \"other:a\"}",
            "pack");
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("akashicfishing:tucunare", "akashicfishing:fish/tucunare");
        expected.put("akashicfishing:bota_velha", "akashicfishing:junk/boot");
        expected.put("akashicfishing:x/y.z-1", "other:a");
        assertEquals(expected, index.icons());
        assertTrue(
            index.errors()
                .isEmpty());
    }

    @Test
    void laterPacksOverrideEarlierOnes() {
        ItemModelIndex index = new ItemModelIndex();
        index.add("akashicfishing", "{\"tucunare\": \"old\"}", "low");
        index.add("akashicfishing", "{\"tucunare\": \"new\"}", "high");
        assertEquals(
            "akashicfishing:new",
            index.icons()
                .get("akashicfishing:tucunare"));
    }

    @Test
    void badEntriesAreReportedAndSkippedWithoutLosingTheRest() {
        ItemModelIndex index = new ItemModelIndex();
        index.add(
            "pack",
            "{\"Upper\": \"a\", \"ok\": \"a\", \"num\": 3, \"bad\": \"BAD:x\", \"slash\": \"a/\", \"sp\": \"a b\"}",
            "file");
        assertEquals(
            Arrays.asList("pack:ok"),
            Arrays.asList(
                index.icons()
                    .keySet()
                    .toArray()));
        assertEquals(
            5,
            index.errors()
                .size(),
            index.errors()
                .toString());
        assertTrue(
            index.errors()
                .get(0)
                .startsWith("file: invalid model key 'Upper'"));

        ItemModelIndex broken = new ItemModelIndex();
        broken.add("pack", "{\"a\": ", "file");
        broken.add("pack", "[\"a\"]", "file2");
        assertTrue(
            broken.icons()
                .isEmpty());
        assertEquals(
            2,
            broken.errors()
                .size());
    }

    @Test
    void aModelCanHaveASlotCell() {
        ItemModelIndex index = new ItemModelIndex();
        index.add(
            "akashicfishing",
            "{\"tela/botao-0\": {\"icone\": \"gui/botao-0\", \"celula\": \"gui/botao-0_celula\"},"
                + " \"tucunare\": \"fish/tucunare\", \"sem-icone\": {\"celula\": \"x\"},"
                + " \"celula-ruim\": {\"icone\": \"ok\", \"celula\": 3}}",
            "test");
        assertEquals(
            "akashicfishing:gui/botao-0",
            index.icons()
                .get("akashicfishing:tela/botao-0"));
        assertEquals(
            "akashicfishing:gui/botao-0_celula",
            index.cells()
                .get("akashicfishing:tela/botao-0"));
        assertEquals(
            "akashicfishing:fish/tucunare",
            index.icons()
                .get("akashicfishing:tucunare"));
        assertEquals(
            1,
            index.cells()
                .size(),
            "only the button has a cell");
        assertEquals(
            2,
            index.errors()
                .size(),
            index.errors()
                .toString());
        // a later pack that redeclares the model without a cell drops it
        index.add("akashicfishing", "{\"tela/botao-0\": \"gui/outro\"}", "later");
        assertEquals(
            0,
            index.cells()
                .size());
    }

    @Test
    void aSiblingEntryGivesTheCellToo() {
        // what an older Kunftig reads as one more model (no item uses it): the pack keeps working there
        ItemModelIndex index = new ItemModelIndex();
        index.add(
            "akashicfishing",
            "{\"tela/botao-0\": \"gui/botao-0\", \"tela/botao-0.celula\": \"gui/botao-0_celula\","
                + " \"orfa.celula\": \"gui/x\"}",
            "test");
        assertEquals(
            "akashicfishing:gui/botao-0_celula",
            index.cells()
                .get("akashicfishing:tela/botao-0"));
        assertEquals(
            1,
            index.cells()
                .size(),
            "a cell without its model is no cell");
        assertEquals(
            0,
            index.errors()
                .size());
    }

    @Test
    void iconNames() {
        assertEquals("ns:path/to/icon", ItemModelIndex.iconName("ns", "path/to/icon"));
        assertEquals("other:icon", ItemModelIndex.iconName("ns", "other:icon"));
        assertNull(ItemModelIndex.iconName("ns", "/abs"));
        assertNull(ItemModelIndex.iconName("ns", ""));
        assertNull(ItemModelIndex.iconName("ns", ":x"));
        assertNull(ItemModelIndex.iconName("ns", "a:b:c"));
    }
}
