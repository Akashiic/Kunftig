package com.akashic.kunftig.modern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.modern.codec.Codec;
import com.akashic.kunftig.modern.item.DataComponentType;

class DataComponentTypeTest {

    private static final DataComponentType<Integer> CHARGES = new DataComponentType<>("testmod", "charges", Codec.INT);

    private static ItemStack stack() {
        // An item instance is enough: the component only touches the stack's tag.
        return new ItemStack(new Item(), 1, 0);
    }

    @Test
    void setGetRemove() {
        ItemStack stack = stack();
        assertFalse(CHARGES.has(stack));
        assertEquals(3, (int) CHARGES.getOrDefault(stack, 3));

        CHARGES.set(stack, 7);
        assertTrue(CHARGES.has(stack));
        assertEquals(7, (int) CHARGES.get(stack));
        assertEquals(
            7,
            stack.stackTagCompound.getCompoundTag("testmod")
                .getInteger("charges"));

        CHARGES.remove(stack);
        assertNull(stack.stackTagCompound, "an emptied stack tag is dropped");
    }

    @Test
    void cachedValueIsReusedUntilTheTagChanges() {
        DataComponentType<String> name = new DataComponentType<>("testmod", "name", Codec.STRING);
        ItemStack stack = stack();
        name.set(stack, new String("relic"));
        String first = name.get(stack);
        assertSame(first, name.get(stack), "unchanged tag: no re-parse");

        ItemStack copy = stack.copy();
        assertEquals("relic", name.get(copy));
    }

    @Test
    void nullStacksAreEmpty() {
        assertNull(CHARGES.get(null));
        assertEquals(1, (int) CHARGES.getOrDefault(null, 1));
        CHARGES.set(null, 5);
    }

    @Test
    void updateAppliesToCurrentValue() {
        ItemStack stack = stack();
        CHARGES.update(stack, 0, v -> v + 2);
        CHARGES.update(stack, 0, v -> v + 2);
        assertEquals(4, (int) CHARGES.get(stack));
    }
}
