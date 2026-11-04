package com.akashic.kunftig.rodmodel;

/**
 * Where a 3D rod goes in each context: the matrix to multiply onto the GL matrix that 1.7.10 has set up when it asks
 * the item renderer to draw (issue #13). Each one first undoes the transforms 1.7.10 and Forge applied for a flat
 * item, then applies the manifest's {@code display} in the frame the previews used:
 * <ul>
 * <li>first person: the camera, with vanilla's equip, sway and swing offsets kept (its swing rotations are not: the
 * rod's own animations replace them);</li>
 * <li>third person: the right shoulder, Y up, with the hand 10 pixels below it (the previews' mannequin);</li>
 * <li>ground and item frame: the item entity, Y up.</li>
 * </ul>
 * The model drawn after it is GeckoLib's: blocks, X mirrored (GeckoLib's convention, also the previews'). No
 * Minecraft classes.
 */
public final class RodDisplays {

    /** Pixels per block. */
    private static final float PX = 1 / 16F;

    private RodDisplays() {}

    /**
     * Forge's transform for an {@code IItemRenderer} that does not ask for the block helper
     * ({@code ForgeHooksClient#renderEquippedItem}).
     */
    static RodMatrix forgeEquipped() {
        return RodMatrix.identity()
            .translate(0, -0.3F, 0)
            .scale(1.5F, 1.5F, 1.5F)
            .rotateY(50)
            .rotateZ(335)
            .translate(-0.9375F, -0.0625F, 0);
    }

    /**
     * What {@code RenderPlayer#renderEquippedItems} (and {@code RenderBiped}) apply to a full-3D item that rotates
     * around ({@code ItemFishingRod}) after the arm's {@code postRender}, Forge's part included.
     */
    static RodMatrix thirdPersonChain() {
        return thirdPersonChain(true, true);
    }

    /**
     * {@link #thirdPersonChain()} for any item: full-3D ({@code Item#isFull3D}, rotating around or not) or flat (a
     * fish: scaled 0.375 and turned 60, -90, 20 degrees).
     */
    static RodMatrix thirdPersonChain(boolean full3D, boolean rotatesAround) {
        RodMatrix m = RodMatrix.identity()
            .translate(-0.0625F, 0.4375F, 0.0625F);
        if (!full3D) {
            m = m.translate(0.25F, 0.1875F, -0.1875F)
                .scale(0.375F, 0.375F, 0.375F)
                .rotateZ(60)
                .rotateX(-90)
                .rotateZ(20);
            return m.multiply(forgeEquipped());
        }
        if (rotatesAround) {
            m = m.rotateZ(180)
                .translate(0, -0.125F, 0);
        }
        return m.translate(0, 0.1875F, 0)
            .scale(0.625F, -0.625F, 0.625F)
            .rotateX(-100)
            .rotateY(45)
            .multiply(forgeEquipped());
    }

    /**
     * The manifest's display: model (GeckoLib, blocks) to the context's frame. {@code offsetX/Y} move the frame's
     * origin (blocks), for the third person's hand.
     */
    public static RodMatrix display(RodModelDef.Display d, float[] grip, float offsetX, float offsetY) {
        float[] r = d.rotation;
        float[] t = d.translation;
        return RodMatrix.identity()
            .translate(-t[0] * PX + offsetX, t[1] * PX + offsetY, t[2] * PX)
            .rotateZ(r[2])
            .rotateY(-r[1])
            .rotateX(-r[0])
            .scale(d.scale, d.scale, d.scale)
            .translate(grip[0] * PX, -grip[1] * PX, -grip[2] * PX);
    }

    /**
     * First person ({@code EQUIPPED_FIRST_PERSON}).
     *
     * @param swing the player's swing progress ({@code getSwingProgress(partialTicks)}), whose rotations are undone
     */
    public static RodMatrix firstPerson(RodModelDef.Display d, float[] grip, float swing) {
        return firstPerson(d, grip, swing, true);
    }

    /**
     * {@link #firstPerson(RodModelDef.Display, float[], float)} for any item: vanilla turns an item that rotates around
     * ({@code Item#shouldRotateAroundWhenRendering}, the fishing rod) by 180 degrees, and no other.
     */
    public static RodMatrix firstPerson(RodModelDef.Display d, float[] grip, float swing, boolean rotatesAround) {
        float f6 = (float) Math.sin(swing * swing * Math.PI);
        float f7 = (float) Math.sin(Math.sqrt(swing) * Math.PI);
        // ItemRenderer#renderItemInFirstPerson after translate(0.56, -0.52 - equip, -0.72):
        // rotateY(45), the swing rotations, scale(0.4), rotateY(180) (rotates around), then Forge's part.
        RodMatrix applied = RodMatrix.identity()
            .rotateY(45)
            .rotateY(-f6 * 20)
            .rotateZ(-f7 * 20)
            .rotateX(-f7 * 80)
            .scale(0.4F, 0.4F, 0.4F);
        if (rotatesAround) {
            applied = applied.rotateY(180);
        }
        applied = applied.multiply(forgeEquipped());
        return applied.inverse()
            .translate(-0.56F, 0.52F, 0.72F)
            .multiply(display(d, grip, 0, 0));
    }

    /**
     * Third person ({@code EQUIPPED}): the shoulder frame of the previews is the arm's (after {@code postRender}) with
     * Y up; the arm's center is 1 pixel out from its pivot in 1.7.10 (on it in the previews) and the hand 10 pixels
     * down.
     */
    public static RodMatrix thirdPerson(RodModelDef.Display d, float[] grip) {
        return thirdPerson(d, grip, true, true);
    }

    /** {@link #thirdPerson(RodModelDef.Display, float[])} for any item ({@link #thirdPersonChain(boolean, boolean)}). */
    public static RodMatrix thirdPerson(RodModelDef.Display d, float[] grip, boolean full3D, boolean rotatesAround) {
        return thirdPersonChain(full3D, rotatesAround).inverse()
            .scale(-1, -1, 1)
            .multiply(display(d, grip, PX, -10 * PX));
    }

    /** Ground and item frame ({@code ENTITY}): Forge scaled a flat item by 0.5. */
    public static RodMatrix entity(RodModelDef.Display d, float[] grip) {
        return RodMatrix.identity()
            .scale(2, 2, 2)
            .multiply(display(d, grip, 0, 0));
    }

    /** A point of the model in pixels (Bedrock axes) in GeckoLib's space (blocks, X mirrored). */
    public static float[] geckoPoint(float[] px) {
        return new float[] { -px[0] * PX, px[1] * PX, px[2] * PX };
    }
}
