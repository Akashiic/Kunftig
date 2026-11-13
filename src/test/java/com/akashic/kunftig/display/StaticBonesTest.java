package com.akashic.kunftig.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The split of a display model into the bones compiled once and the bones walked every frame. */
class StaticBonesTest {

    private static final class Bone {

        final String name;
        final List<Bone> children = new ArrayList<>();

        Bone(String name, Bone... children) {
            this.name = name;
            this.children.addAll(Arrays.asList(children));
        }
    }

    private final Map<String, Bone> byName = new HashMap<>();

    private Bone bone(String name, Bone... children) {
        Bone b = new Bone(name, children);
        byName.put(name, b);
        return b;
    }

    /** A ship as Expeditions builds it: the hull, a wheel under the steering bone, a door, a flag on a mast. */
    private List<Bone> ship() {
        return Collections.singletonList(
            bone(
                "navio",
                bone("casco"),
                bone("governo", bone("leme", bone("cana")), bone("roda")),
                bone("camara", bone("porta"), bone("vidros")),
                bone("mastro", bone("verga", bone("bandeira"))),
                bone("canhoes_n3", bone("carreta"))));
    }

    private StaticBones<Bone> plan(Set<String> moves, Set<String> hidden) {
        return StaticBones.plan(ship(), b -> b.children, b -> b.name, moves::contains, n -> !hidden.contains(n));
    }

    private static Set<String> set(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    @Test
    void nothingMoves() {
        StaticBones<Bone> p = plan(set(), set());
        for (Bone b : byName.values()) {
            assertTrue(p.still(b), b.name);
            assertFalse(p.walk(b), b.name + " would be walked every frame for nothing");
        }
        assertEquals(byName.size(), p.stillBones());
        assertEquals(0, p.movingBones());
    }

    @Test
    void aMovingBoneTakesWhatHangsOnItAndIsReachedThroughStillBones() {
        StaticBones<Bone> p = plan(set("leme", "bandeira"), set());
        // the rudder and the tiller under it move; the wheel beside them does not
        assertFalse(p.still(byName.get("leme")));
        assertFalse(p.still(byName.get("cana")));
        assertTrue(p.still(byName.get("roda")));
        assertTrue(p.still(byName.get("governo")));
        // the walk goes navio -> governo -> leme -> cana, and navio -> mastro -> verga -> bandeira, and nowhere else
        for (String n : new String[] { "navio", "governo", "leme", "cana", "mastro", "verga", "bandeira" }) {
            assertTrue(p.walk(byName.get(n)), n);
        }
        for (String n : new String[] { "casco", "roda", "camara", "porta", "vidros", "canhoes_n3", "carreta" }) {
            assertFalse(p.walk(byName.get(n)), n);
        }
        assertEquals(3, p.movingBones());
        assertEquals(byName.size() - 3, p.stillBones());
    }

    @Test
    void aHiddenBoneAndEverythingUnderItIsInNeither() {
        StaticBones<Bone> p = plan(set("carreta", "porta"), set("canhoes_n3"));
        assertFalse(p.still(byName.get("canhoes_n3")));
        assertFalse(p.still(byName.get("carreta")));
        assertFalse(p.walk(byName.get("canhoes_n3")));
        assertFalse(p.walk(byName.get("carreta")));
        // the door still moves and is still reached
        assertTrue(p.walk(byName.get("camara")));
        assertFalse(p.still(byName.get("porta")));
        assertEquals(1, p.movingBones());
        assertEquals(byName.size() - 3, p.stillBones());
    }

    @Test
    void aMovingRootLeavesNothingToCompile() {
        StaticBones<Bone> p = plan(set("navio"), set());
        assertEquals(0, p.stillBones());
        assertEquals(byName.size(), p.movingBones());
        for (Bone b : byName.values()) {
            assertTrue(p.walk(b), b.name);
        }
    }
}
