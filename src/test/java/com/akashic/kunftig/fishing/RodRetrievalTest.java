package com.akashic.kunftig.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

class RodRetrievalTest {

    /** Records the calls a retrieval makes. */
    private static final class Recorder implements RodRetrieval.Angler {

        final List<String> calls = new ArrayList<>();
        final boolean rod;
        final int damage;
        final boolean breaks;

        Recorder(boolean rod, int damage, boolean breaks) {
            this.rod = rod;
            this.damage = damage;
            this.breaks = breaks;
        }

        @Override
        public int reelIn() {
            calls.add("reelIn");
            return damage;
        }

        @Override
        public boolean holdsRod() {
            calls.add("holdsRod");
            return rod;
        }

        @Override
        public void damageRod(int amount) {
            calls.add("damageRod " + amount);
        }

        @Override
        public boolean rodBroken() {
            calls.add("rodBroken");
            return breaks;
        }

        @Override
        public void destroyRod() {
            calls.add("destroyRod");
        }

        @Override
        public void syncInventory() {
            calls.add("syncInventory");
        }

        @Override
        public void swing() {
            calls.add("swing");
        }
    }

    @Test
    void catchingAFishDamagesTheRodThenSwingsLikeTheRod() {
        Recorder angler = new Recorder(true, 1, false);
        assertEquals(1, RodRetrieval.retrieve(angler, true));
        assertEquals(
            Arrays.asList("holdsRod", "reelIn", "damageRod 1", "rodBroken", "syncInventory", "swing"),
            angler.calls);
    }

    @Test
    void aRodThatBreaksIsRemovedAsTryUseItemDoes() {
        Recorder angler = new Recorder(true, 3, true);
        assertEquals(3, RodRetrieval.retrieve(angler, true));
        assertEquals(
            Arrays.asList("holdsRod", "reelIn", "damageRod 3", "rodBroken", "destroyRod", "syncInventory", "swing"),
            angler.calls);
    }

    @Test
    void withoutDamageOnlyTheHookIsTouched() {
        Recorder angler = new Recorder(true, 2, false);
        assertEquals(2, RodRetrieval.retrieve(angler, false));
        assertEquals(Arrays.asList("holdsRod", "reelIn"), angler.calls);
    }

    @Test
    void nothingToDamageStillSwings() {
        Recorder nothing = new Recorder(true, 0, false);
        assertEquals(0, RodRetrieval.retrieve(nothing, true));
        assertEquals(Arrays.asList("holdsRod", "reelIn", "swing"), nothing.calls);

        Recorder noRod = new Recorder(false, 1, false);
        assertEquals(1, RodRetrieval.retrieve(noRod, true));
        assertEquals(Arrays.asList("holdsRod", "reelIn", "swing"), noRod.calls);
    }

    // ------------------------------------------------------------------ the vanilla code being mirrored

    private static ClassNode load(String internalName) throws IOException {
        try (InputStream in = RodRetrievalTest.class.getClassLoader()
            .getResourceAsStream(internalName + ".class")) {
            assertNotNull(in, internalName + " on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name)) {
                return method;
            }
        }
        throw new AssertionError("missing " + name);
    }

    private static List<String> calls(MethodNode method) {
        List<String> calls = new ArrayList<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                calls.add(((MethodInsnNode) insn).name);
            }
            if (insn.getOpcode() == Opcodes.NEW) {
                calls.add("new " + ((TypeInsnNode) insn).desc);
            }
        }
        return calls;
    }

    @Test
    void vanillaRodReelsInThenDamagesThenSwings() throws IOException {
        List<String> calls = calls(method(load("net/minecraft/item/ItemFishingRod"), "onItemRightClick"));
        int reelIn = calls.indexOf("func_146034_e");
        int damage = calls.indexOf("damageItem");
        int swing = calls.indexOf("swingItem");
        assertTrue(reelIn >= 0 && reelIn < damage && damage < swing, calls.toString());
    }

    @Test
    void tryUseItemRemovesBrokenItemsAndResendsTheInventory() throws IOException {
        List<String> calls = calls(method(load("net/minecraft/server/management/ItemInWorldManager"), "tryUseItem"));
        assertTrue(
            calls.contains("new net/minecraftforge/event/entity/player/PlayerDestroyItemEvent"),
            calls.toString());
        assertTrue(calls.contains("sendContainerToPlayer"), calls.toString());
    }
}
