package com.akashic.kunftig.dev;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.rodmodel.RodModels;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development-only visual check of the 3D rods (never in the release jar): when the run directory has a file named
 * {@code kunftig-rod-harness}, the client opens a flat creative world, holds rods with real item models, plays states,
 * casts, and saves a screenshot of each context to {@code screenshots/rods-*.png}, then quits. A report goes to
 * {@code kunftig-rod-harness.txt}.
 */
public final class RodRenderHarness {

    private static final String WORLD = "kunftig-rods";
    private static final int SETTLE_TICKS = 30;

    private final List<Step> steps = new ArrayList<>();
    private EntityOtherPlayerMP side;
    private PrintWriter out;
    private boolean launched;
    private int index = -1;
    private int ticks;
    private boolean shotPending;
    private boolean done;

    private interface Action {

        void run(Minecraft mc, EntityPlayerMP server);
    }

    private static final class Step {

        final String name;
        final Action action;
        final int wait;

        Step(String name, int wait, Action action) {
            this.name = name;
            this.wait = wait;
            this.action = action;
        }
    }

    /** Called reflectively by the client proxy when the trigger file exists. */
    public static void install() {
        RodRenderHarness h = new RodRenderHarness();
        h.plan();
        FMLCommonHandler.instance()
            .bus()
            .register(h);
        Kunftig.LOG.warn("Kunftig DEV ROD HARNESS enabled: this client will take screenshots and quit");
    }

    static ItemStack rod(String model) {
        ItemStack stack = new ItemStack(Items.fishing_rod);
        if (model != null) {
            NBTTagCompound tag = new NBTTagCompound();
            NBTTagCompound values = new NBTTagCompound();
            values.setString("kunftig:model", model);
            tag.setTag("PublicBukkitValues", values);
            stack.setTagCompound(tag);
        }
        return stack;
    }

    private static void hold(EntityPlayerMP p, ItemStack stack) {
        p.inventory.setInventorySlotContents(p.inventory.currentItem, stack);
        p.inventoryContainer.detectAndSendChanges();
    }

    private static void place(EntityPlayerMP p, float yaw, float pitch) {
        p.playerNetServerHandler.setPlayerLocation(0.5, 4, 0.5, yaw, pitch);
    }

    private static void view(Minecraft mc, int mode) {
        mc.gameSettings.thirdPersonView = mode;
        mc.gameSettings.hideGUI = false; // F1 would also hide the hand
    }

    private void plan() {
        steps.add(new Step("fp-solar-idle", SETTLE_TICKS, (mc, p) -> {
            place(p, 180, 10);
            view(mc, 0);
            hold(p, rod("akashicfishing:vara/vara-solar"));
        }));
        steps.add(
            new Step(
                "fp-akashica-ascension",
                SETTLE_TICKS,
                (mc, p) -> hold(p, rod("akashicfishing:vara/vara-akashica.iridio.m6"))));
        steps.add(new Step("fp-akashica-fight", 25, (mc, p) -> RodModels.INSTANCE.forceLocalState("briga", 1)));
        steps.add(new Step("fp-solar-cast", 50, (mc, p) -> {
            RodModels.INSTANCE.forceLocalState("parada", 0);
            hold(p, rod("akashicfishing:vara/vara-solar.m4"));
        }));
        steps.add(
            new Step(
                "fp-solar-line-out",
                40,
                (mc, p) -> mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem())));
        steps.add(new Step("tp-front-line-out", 15, (mc, p) -> view(mc, 2)));
        steps.add(new Step("tp-back-line-out", 15, (mc, p) -> view(mc, 1)));
        steps.add(new Step("tp-front-fight", 25, (mc, p) -> {
            view(mc, 2);
            RodModels.INSTANCE.forceLocalState("briga", 0.9F);
        }));
        steps.add(new Step("tp-front-idle-reeled", 40, (mc, p) -> {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            RodModels.INSTANCE.forceLocalState("parada", 0);
        }));
        steps.add(new Step("tp-front-vanilla-rod-cast", 40, (mc, p) -> {
            hold(p, rod(null));
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
        }));
        steps.add(new Step("side-idle", SETTLE_TICKS, (mc, p) -> {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            hold(p, null);
            view(mc, 0);
            place(p, 180, 5);
            side = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "Lateral"));
            side.setPositionAndRotation(0.5, 4 + side.yOffset, -2.5, 270, 0);
            side.rotationYawHead = side.prevRotationYawHead = 270;
            side.renderYawOffset = side.prevRenderYawOffset = 270;
            side.inventory.setInventorySlotContents(0, rod("akashicfishing:vara/vara-solar"));
            side.inventory.currentItem = 0;
            mc.theWorld.addEntityToWorld(-4242, side);
        }));
        steps.add(new Step("side-fight", 25, (mc, p) -> {
            if (RodModels.INSTANCE.active()) {
                RodModels.INSTANCE.timeline(side)
                    .server("briga", RodModels.INSTANCE.clock());
                RodModels.INSTANCE.timeline(side)
                    .setTension(0.8F);
            }
        }));
        steps.add(new Step("side-catch", 12, (mc, p) -> {
            if (RodModels.INSTANCE.active()) {
                RodModels.INSTANCE.timeline(side)
                    .server("captura", RodModels.INSTANCE.clock());
            }
        }));
        steps.add(new Step("ground-and-frame", SETTLE_TICKS, (mc, p) -> {
            mc.theWorld.removeEntityFromWorld(side.getEntityId());
            WorldServer w = p.getServerForPlayer();
            w.setBlock(0, 4, -3, Blocks.stone);
            w.setBlock(0, 5, -3, Blocks.stone);
            EntityItemFrame frame = new EntityItemFrame(w, 0, 5, -3, 0); // on the stone's south face
            frame.setDisplayedItem(rod("akashicfishing:vara/vara-de-coral.m2"));
            w.spawnEntityInWorld(frame);
            EntityItem item = new EntityItem(w, 1.2, 4.2, -1.2, rod("akashicfishing:vara/vara-estrela-cadente.m5"));
            item.delayBeforeCanPickup = 32767;
            item.lifespan = 1000000;
            w.spawnEntityInWorld(item);
            place(p, 180, 25);
        }));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        if (!launched && mc.currentScreen instanceof GuiMainMenu) {
            // Not from GuiOpenEvent: the main menu first opens while the game is still starting.
            launched = true;
            mc.gameSettings.pauseOnLostFocus = false;
            WorldSettings settings = new WorldSettings(
                1L,
                WorldSettings.GameType.CREATIVE,
                false,
                false,
                WorldType.FLAT);
            mc.launchIntegratedServer(WORLD, WORLD, settings);
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        EntityPlayerMP p = server == null ? null
            : server.getConfigurationManager()
                .func_152612_a(mc.thePlayer.getCommandSenderName());
        if (p == null || shotPending) {
            return;
        }
        if (index < 0) {
            p.getServerForPlayer()
                .setWorldTime(6000);
            report("rods: " + RodModels.INSTANCE.status());
            index = 0;
            ticks = 0;
            steps.get(0).action.run(mc, p);
            return;
        }
        if (++ticks >= steps.get(index).wait) {
            shotPending = true;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !shotPending || done) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Step step = steps.get(index);
        ScreenShotHelper.saveScreenshot(
            mc.mcDataDir,
            "rods-" + step.name + ".png",
            mc.displayWidth,
            mc.displayHeight,
            mc.getFramebuffer());
        report(
            step.name + ": state="
                + (mc.thePlayer == null || !RodModels.INSTANCE.active() ? "-"
                    : String.valueOf(
                        RodModels.INSTANCE.timeline(mc.thePlayer)
                            .state()))
                + " line="
                + (mc.thePlayer != null && mc.thePlayer.fishEntity != null));
        shotPending = false;
        ticks = 0;
        index++;
        if (index >= steps.size()) {
            done = true;
            report("done");
            out.close();
            mc.shutdown();
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        EntityPlayerMP p = server.getConfigurationManager()
            .func_152612_a(mc.thePlayer.getCommandSenderName());
        steps.get(index).action.run(mc, p);
    }

    private void report(String line) {
        try {
            if (out == null) {
                out = new PrintWriter(
                    Files.newBufferedWriter(
                        new File(Minecraft.getMinecraft().mcDataDir, "kunftig-rod-harness.txt").toPath(),
                        StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        out.println(line);
        out.flush();
        Kunftig.LOG.info("Rod harness: {}", line);
    }
}
