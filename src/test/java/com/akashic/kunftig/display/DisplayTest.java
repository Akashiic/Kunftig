package com.akashic.kunftig.display;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class DisplayTest {

    @Test
    void theWireCarriesEveryOperation() {
        List<byte[]> items = Arrays.asList(new byte[] { 1, 2, 3 }, new byte[0]);
        DisplayWire.Message m = DisplayWire.read(
            DisplayWire.show(7, 1.5, 64, -3.25, 90f, "akashicfishing:caixa_da_sorte", "epico", "abrir", 12, items));
        assertEquals(DisplayWire.SHOW, m.op);
        assertEquals(7, m.id);
        assertEquals(64, m.y, 1e-9);
        assertEquals(90f, m.yaw, 1e-6);
        assertEquals("akashicfishing:caixa_da_sorte", m.model);
        assertEquals("epico", m.variant);
        assertEquals("abrir", m.state);
        assertEquals(12, m.elapsed);
        assertEquals(2, m.items.size());
        assertArrayEquals(new byte[] { 1, 2, 3 }, m.items.get(0));
        assertEquals(0, m.items.get(1).length);
        DisplayWire.Message s = DisplayWire.read(DisplayWire.state(7, "fechar", 3));
        assertEquals("fechar", s.state);
        assertEquals(3, s.elapsed);
        assertEquals(DisplayWire.HIDE, DisplayWire.read(DisplayWire.hide(7)).op);
        assertEquals(DisplayWire.MOVE, DisplayWire.read(DisplayWire.move(7, 0, 0, 0, 10)).op);
        assertNull(DisplayWire.read(new byte[] { 9, 0, 0, 0, 1 }), "operação desconhecida");
        assertNull(DisplayWire.read(new byte[] { 0, 0 }), "curto demais");
    }

    @Test
    void lateViewersGetTheTimeAlreadyPlayed() {
        DisplayTracker<String> t = new DisplayTracker<>();
        DisplayTracker.State<String> d = t.create("w", 0, 64, 0, 0, "ns:m", "", "abrir", null, 48);
        List<DisplayTracker.Viewer<String>> near = Collections
            .singletonList(new DisplayTracker.Viewer<>("ana", "w", 5, 64, 0, true));
        List<DisplayTracker.Out<String>> out = t.update(near);
        assertEquals(1, out.size());
        assertEquals(0, DisplayWire.read(out.get(0).data).elapsed);
        for (int i = 0; i < 9; i++) {
            assertTrue(
                t.update(near)
                    .isEmpty(),
                "nada muda: nada a mandar");
        }
        List<DisplayTracker.Viewer<String>> both = Arrays.asList(
            new DisplayTracker.Viewer<>("ana", "w", 5, 64, 0, true),
            new DisplayTracker.Viewer<>("bia", "w", 0, 64, 10, true));
        out = t.update(both);
        assertEquals(1, out.size());
        assertEquals("bia", out.get(0).connection);
        assertEquals(10, DisplayWire.read(out.get(0).data).elapsed, "chega 10 ticks depois");
        d.setState("fechar", t.tick());
        out = t.update(both);
        assertEquals(2, out.size());
        assertEquals(DisplayWire.STATE, DisplayWire.read(out.get(0).data).op);
        assertEquals(0, DisplayWire.read(out.get(0).data).elapsed);
        d.remove();
        out = t.update(both);
        assertEquals(2, out.size());
        assertEquals(DisplayWire.HIDE, DisplayWire.read(out.get(0).data).op);
        assertEquals(0, t.size());
    }

    @Test
    void onlyListeningPlayersInRangeAndWorldSeeIt() {
        DisplayTracker<String> t = new DisplayTracker<>();
        t.create("w", 0, 64, 0, 0, "ns:m", "", "abrir", null, 10);
        List<DisplayTracker.Out<String>> out = t.update(
            Arrays.asList(
                new DisplayTracker.Viewer<>("longe", "w", 50, 64, 0, true),
                new DisplayTracker.Viewer<>("surdo", "w", 1, 64, 0, false),
                new DisplayTracker.Viewer<>("nether", "n", 1, 64, 0, true)));
        assertTrue(out.isEmpty());
    }

    @Test
    void theManifestIsReadAndProblemsAreListed() {
        DisplayModelIndex idx = new DisplayModelIndex();
        idx.add(
            "akashicfishing",
            "{\"_formato\":1,\"exibicoes\":{\"caixa_da_sorte\":{"
                + "\"geo\":\"akashicfishing:geo/caixa/c.geo.json\",\"animacoes\":\"akashicfishing:animations/c.json\","
                + "\"textura\":\"akashicfishing:textures/c.png\",\"variantes\":{\"epico\":\"akashicfishing:textures/e.png\"},"
                + "\"estados\":{\"abrir\":{\"animacao\":\"animation.c.abrir\",\"laco\":false}},"
                + "\"itens\":[\"item_0\",\"item_1\"],\"luz\":[\"brilho\"],\"translucido\":[\"feixe\"],\"distancia\":48},"
                + "\"quebrada\":{\"geo\":\"x\"}}}",
            "test");
        DisplayModelIndex.Model m = idx.models()
            .get("akashicfishing:caixa_da_sorte");
        assertEquals("akashicfishing:textures/e.png", m.texture("epico"));
        assertEquals("akashicfishing:textures/c.png", m.texture("raro"), "variante desconhecida: a padrão");
        assertFalse(m.states.get("abrir").loop);
        assertEquals(Arrays.asList("item_0", "item_1"), m.itemBones);
        assertEquals(48, m.maxDistance, 1e-9);
        assertFalse(
            idx.models()
                .containsKey("akashicfishing:quebrada"));
        assertEquals(
            1,
            idx.errors()
                .size());
    }
}
