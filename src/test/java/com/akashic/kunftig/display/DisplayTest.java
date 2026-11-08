package com.akashic.kunftig.display;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void api9PartsTravelAndApi8MessagesStillRead() throws IOException {
        Map<String, Boolean> bones = new LinkedHashMap<>();
        bones.put("orbe_e", false);
        bones.put("orbe_e_apagado", true);
        DisplayWire.Message m = DisplayWire.read(
            DisplayWire.show(
                3,
                0,
                62,
                0,
                200f,
                "akashicfishing:leviata_do_abismo",
                "abismo",
                "ocioso",
                0,
                null,
                new float[] { 1, 1, 2.5f },
                bones));
        assertArrayEquals(new float[] { 1, 1, 2.5f }, m.scale, 1e-6f);
        assertEquals(bones, m.bones);
        DisplayWire.Message sc = DisplayWire.read(DisplayWire.scale(3, new float[] { 0.001f, 100f, Float.NaN }));
        assertEquals(DisplayWire.SCALE, sc.op);
        assertArrayEquals(new float[] { DisplayWire.MIN_SCALE, DisplayWire.MAX_SCALE, 1 }, sc.scale, 1e-6f);
        DisplayWire.Message bn = DisplayWire.read(DisplayWire.bones(3, bones));
        assertEquals(DisplayWire.BONES, bn.op);
        assertEquals(bones, bn.bones);
        assertEquals(3, DisplayWire.read(DisplayWire.move(3, 1, 2, 3, 4, 3)).ticks);
        assertEquals(DisplayWire.MAX_MOVE_TICKS, DisplayWire.read(DisplayWire.move(3, 1, 2, 3, 4, 9999)).ticks);

        // an API 8 server's SHOW and MOVE (without the API 9 tails) read with the defaults
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(DisplayWire.SHOW);
            out.writeInt(5);
            out.writeDouble(1);
            out.writeDouble(2);
            out.writeDouble(3);
            out.writeFloat(0);
            out.writeUTF("ns:m");
            out.writeUTF("");
            out.writeUTF("abrir");
            out.writeInt(0);
            out.writeByte(0);
        }
        DisplayWire.Message old = DisplayWire.read(bytes.toByteArray());
        assertEquals("abrir", old.state);
        assertArrayEquals(new float[] { 1, 1, 1 }, old.scale, 0f);
        assertTrue(old.bones.isEmpty());
        byte[] move = DisplayWire.move(5, 1, 2, 3, 4, 7);
        // the tail is the API 9 ticks (2 bytes) and the API 11 pitch and roll (8)
        assertEquals(0, DisplayWire.read(Arrays.copyOf(move, move.length - 10)).ticks, "MOVE sem a cauda: pula");
        assertEquals(7, DisplayWire.read(Arrays.copyOf(move, move.length - 8)).ticks, "MOVE da API 9: desliza");
    }

    @Test
    void theTrackerSendsScaleBonesAndGlides() {
        DisplayTracker<String> t = new DisplayTracker<>();
        DisplayTracker.State<String> d = t.create("w", 0, 62, 0, 0, "ns:boss", "", "ocioso", null, 64);
        List<DisplayTracker.Viewer<String>> near = Collections
            .singletonList(new DisplayTracker.Viewer<>("ana", "w", 5, 64, 0, true));
        d.setScale(2, 2, 2);
        d.setBone("orbe_e", false);
        List<DisplayTracker.Out<String>> out = t.update(near);
        assertEquals(1, out.size(), "quem chega recebe tudo no SHOW");
        DisplayWire.Message show = DisplayWire.read(out.get(0).data);
        assertArrayEquals(new float[] { 2, 2, 2 }, show.scale, 0f);
        assertEquals(Collections.singletonMap("orbe_e", false), show.bones);

        d.setBone("orbe_e", false);
        d.setScale(2, 2, 2);
        assertTrue(
            t.update(near)
                .isEmpty(),
            "o mesmo valor não manda nada");
        d.setScale(1, 1, 3);
        d.setBone("orbe_e_apagado", true);
        d.moveTo("w", 1, 62, 0, 10, 2);
        out = t.update(near);
        assertEquals(3, out.size());
        assertEquals(2, DisplayWire.read(out.get(0).data).ticks);
        assertEquals(DisplayWire.SCALE, DisplayWire.read(out.get(1).data).op);
        assertEquals(2, DisplayWire.read(out.get(2).data).bones.size());
        d.resetBones();
        out = t.update(near);
        assertTrue(DisplayWire.read(out.get(0).data).bones.isEmpty());
        d.moveTo("outro", 0, 0, 0, 0, 5);
        assertEquals(0, d.glide, "outro mundo: pula");
    }

    @Test
    void theManifestReadsTheApi9Fields() {
        DisplayModelIndex idx = new DisplayModelIndex();
        String base = "\"geo\":\"ns:geo/b.geo.json\",\"animacoes\":\"ns:animations/b.json\",\"textura\":\"ns:textures/b.png\","
            + "\"estados\":{\"ocioso\":{\"animacao\":\"animation.b.ocioso\",\"laco\":true}}";
        idx.add(
            "ns",
            "{\"_formato\":1,\"exibicoes\":{\"boss\":{" + base
                + ",\"escala\":4,\"ocultos\":[\"orbe_e_apagado\"],\"transicao\":6},"
                + "\"simples\":{"
                + base
                + "},\"grande\":{"
                + base
                + ",\"escala\":500}}}",
            "test");
        DisplayModelIndex.Model boss = idx.models()
            .get("ns:boss");
        assertEquals(4f, boss.scale, 0f);
        assertEquals(Collections.singletonList("orbe_e_apagado"), boss.hiddenBones);
        assertEquals(6, boss.transitionTicks);
        DisplayModelIndex.Model simple = idx.models()
            .get("ns:simples");
        assertEquals(1f, simple.scale, 0f);
        assertTrue(simple.hiddenBones.isEmpty());
        assertEquals(0, simple.transitionTicks);
        assertFalse(
            idx.models()
                .containsKey("ns:grande"),
            "escala fora de 0,05..64");
    }
}
