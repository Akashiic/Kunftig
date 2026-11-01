package com.akashic.kunftig.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks, on the real 1.7.10 (Forge) bytecode, the facts EntityFishHookMixin relies on. If an update changed the
 * shape of onUpdate, this fails before anything ships.
 */
class EntityFishHookAnchorTest {

    private static final String WORLD = "net/minecraft/world/World";

    private static ClassNode load() throws IOException {
        try (InputStream in = EntityFishHookAnchorTest.class.getClassLoader()
            .getResourceAsStream("net/minecraft/entity/projectile/EntityFishHook.class")) {
            assertNotNull(in, "Minecraft classes on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        throw new AssertionError("missing " + name + desc);
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }

    private static boolean isCall(AbstractInsnNode insn, String owner, String name) {
        return insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.equals(owner)
            && ((MethodInsnNode) insn).name.equals(name);
    }

    @Test
    void redirectedReadGuardsExactlyTheBiteCycle() throws IOException {
        MethodNode onUpdate = method(load(), "onUpdate", "()V");

        List<AbstractInsnNode> waterChecks = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn = onUpdate.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (isCall(insn, WORLD, "isAABBInMaterial")) {
                waterChecks.add(insn);
            }
        }
        assertEquals(1, waterChecks.size(), "slice start must be unique");

        AbstractInsnNode read = waterChecks.get(0);
        while (!(read instanceof FieldInsnNode && read.getOpcode() == Opcodes.GETFIELD
            && ((FieldInsnNode) read).owner.equals(WORLD)
            && ((FieldInsnNode) read).name.equals("isRemote"))) {
            read = read.getNext();
            assertNotNull(read, "an isRemote read after the water loop");
        }

        // if (!worldObj.isRemote && waterFraction > 0.0D) { ...bite cycle... }
        AbstractInsnNode ifRemote = nextReal(read);
        assertEquals(Opcodes.IFNE, ifRemote.getOpcode());
        assertEquals(Opcodes.DLOAD, nextReal(ifRemote).getOpcode());
        assertEquals(Opcodes.DCONST_0, nextReal(nextReal(ifRemote)).getOpcode());
        assertEquals(Opcodes.DCMPL, nextReal(nextReal(nextReal(ifRemote))).getOpcode());
        AbstractInsnNode ifWater = nextReal(nextReal(nextReal(nextReal(ifRemote))));
        assertEquals(Opcodes.IFLE, ifWater.getOpcode());
        LabelNode end = ((JumpInsnNode) ifRemote).label;
        assertSame(end, ((JumpInsnNode) ifWater).label, "both conditions skip the same block");

        // The skipped block is the whole cycle: the three rolls (wait, lure, bite) and nothing after it.
        int rolls = 0;
        boolean reachedEnd = false;
        for (AbstractInsnNode insn = ifWater.getNext(); insn != null; insn = insn.getNext()) {
            if (insn == end) {
                reachedEnd = true;
                break;
            }
            if (isCall(insn, "net/minecraft/util/MathHelper", "getRandomIntegerInRange")) {
                rolls++;
            }
        }
        assertTrue(reachedEnd);
        assertEquals(3, rolls, "wait, lure and bite rolls inside the guarded block");
        for (AbstractInsnNode insn = end; insn != null; insn = insn.getNext()) {
            assertTrue(
                !isCall(insn, "net/minecraft/util/MathHelper", "getRandomIntegerInRange"),
                "no fishing roll after the guarded block");
        }
    }

    @Test
    void lootAndReelInTargetsExist() throws IOException {
        ClassNode node = load();
        method(node, "func_146034_e", "()I");
        MethodNode loot = method(node, "func_146033_f", "()Lnet/minecraft/item/ItemStack;");
        int category = 0, item = 0;
        for (AbstractInsnNode insn = loot.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (isCall(insn, "net/minecraftforge/common/FishingHooks", "getFishableCategory")) {
                category++;
            }
            if (isCall(insn, "net/minecraftforge/common/FishingHooks", "getRandomFishable")) {
                item++;
            }
        }
        assertEquals(1, category, "one category draw (statistic)");
        assertEquals(1, item, "one item draw, with the same chance");
    }
}
