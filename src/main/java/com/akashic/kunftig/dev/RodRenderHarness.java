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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFishHook;
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
    private int menuTicks;
    private int pausedTicks;
    private int index = -1;
    private int ticks;
    private boolean shotPending;
    private boolean done;
    /** The cells mode keeps its chest open (every other screen is closed: Xvfb brings the pause menu back). */
    private boolean keepChest;
    /** Runs on every client tick of the current step (camera turns, walking), or null. */
    private Runnable everyTick;

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
        // "server <host> <port>" in the trigger file: the server mode (RodServerHarness)
        try {
            String[] mode = new String(
                Files.readAllBytes(new File(Minecraft.getMinecraft().mcDataDir, "kunftig-rod-harness").toPath()),
                StandardCharsets.UTF_8).trim()
                    .split("\\s+");
            if (mode.length == 1 && mode[0].equals("perf")) {
                RodPerfHarness.install();
                return;
            }
            if (mode.length == 1 && mode[0].equals("cells")) {
                RodRenderHarness h = new RodRenderHarness();
                h.planCells();
                FMLCommonHandler.instance()
                    .bus()
                    .register(h);
                Kunftig.LOG.warn("Kunftig DEV ROD HARNESS (cells) enabled: this client will take screenshots and quit");
                return;
            }
            if (mode.length == 1 && mode[0].equals("fish")) {
                RodRenderHarness h = new RodRenderHarness();
                h.planFish();
                FMLCommonHandler.instance()
                    .bus()
                    .register(h);
                Kunftig.LOG.warn("Kunftig DEV ROD HARNESS (fish) enabled: this client will take screenshots and quit");
                return;
            }
            if (mode.length == 1 && mode[0].equals("edge")) {
                RodRenderHarness h = new RodRenderHarness();
                h.planEdge();
                FMLCommonHandler.instance()
                    .bus()
                    .register(h);
                Kunftig.LOG.warn("Kunftig DEV ROD HARNESS (edge) enabled: this client will take screenshots and quit");
                return;
            }
            if (mode.length == 3 && mode[0].equals("server")) {
                RodServerHarness.install(mode[1], Integer.parseInt(mode[2]));
                return;
            }
        } catch (IOException | NumberFormatException e) {
            Kunftig.LOG.warn("Kunftig rod harness: unreadable trigger file, running the render harness", e);
        }
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
        // The client too: a heavy modpack's integrated server can take seconds to sync the slot.
        Minecraft mc = Minecraft.getMinecraft();
        mc.thePlayer.inventory
            .setInventorySlotContents(mc.thePlayer.inventory.currentItem, stack == null ? null : stack.copy());
    }

    private static void place(EntityPlayerMP p, float yaw, float pitch) {
        p.playerNetServerHandler.setPlayerLocation(0.5, 4, 0.5, yaw, pitch);
    }

    private static void view(Minecraft mc, int mode) {
        mc.gameSettings.thirdPersonView = mode;
        mc.gameSettings.hideGUI = false; // F1 would also hide the hand
    }

    /** A display item of AkashicFishing's screens: paper with a model. */
    static ItemStack screenItem(String model) {
        ItemStack stack = new ItemStack(Items.paper);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound values = new NBTTagCompound();
        values.setString("kunftig:model", "akashicfishing:" + model);
        tag.setTag("PublicBukkitValues", values);
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * Item model slot cells: a chest with the Upgrader's big button (five slots) in each state and its pipe, in the
     * slots the plugin uses; the button must read as one piece, without the slot borders between its slots.
     */
    private void planCells() {
        keepChest = true;
        steps.add(new Step("cells-warmup", SETTLE_TICKS * 2, (mc, p) -> view(mc, 0)));
        String[] states = { "pronto", "bloqueado", "sorteando" };
        for (String state : states) {
            steps.add(new Step("cells-" + state, SETTLE_TICKS, (mc, p) -> {
                view(mc, 0);
                net.minecraft.inventory.InventoryBasic inv = new net.minecraft.inventory.InventoryBasic(
                    "Aprimorador",
                    false,
                    27);
                for (int i = 0; i < 5; i++) {
                    inv.setInventorySlotContents(18 + 2 + i, screenItem("tela/botao-" + state + "-" + i));
                }
                int[] levels = { 8, 8, 3, 0 };
                int[] cols = { 2, 3, 5, 6 };
                for (int i = 0; i < 4; i++) {
                    inv.setInventorySlotContents(
                        9 + cols[i],
                        screenItem("tela/tubo-ciano-" + levels[i] + (i % 2 == 1 ? "-fim" : "")));
                }
                mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiChest(mc.thePlayer.inventory, inv));
            }));
        }
    }

    /** A fish of AkashicFishing as the plugin makes it: the model and the size (cm) in the item's Bukkit data. */
    static ItemStack fish(String model, double cm, int meta) {
        ItemStack stack = new ItemStack(Items.fish, 1, meta);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound values = new NBTTagCompound();
        values.setString("kunftig:model", "akashicfishing:" + model);
        values.setDouble("akashicfishing:tamanho-cm", cm);
        tag.setTag("PublicBukkitValues", values);
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * 3D fish (held models with size bands): the local player holds one of each band in first person and in the front
     * view, then another player holds them side on, in each quality.
     */
    private void planFish() {
        Object[][] own = { { "robalo", 55.0, 0, "mao" }, { "pirarucu.ouro", 140.0, 0, "duas-maos" },
            { "pirarucu-ancestral.iridio", 420.0, 0, "acima" }, { "leviathan", 1800.0, 0, "leviathan" },
            { "peixe-palhaco.prata", 9.0, 2, "palhaco-prata" } };
        for (Object[] f : own) {
            steps.add(new Step("fish-fp-" + f[3], SETTLE_TICKS, (mc, p) -> {
                view(mc, 0);
                place(p, 180, 0);
                hold(p, fish((String) f[0], (Double) f[1], (Integer) f[2]));
            }));
        }
        for (Object[] f : own) {
            steps.add(new Step("fish-front-" + f[3], SETTLE_TICKS, (mc, p) -> {
                view(mc, 2);
                place(p, 180, 0);
                hold(p, fish((String) f[0], (Double) f[1], (Integer) f[2]));
            }));
        }
        steps.add(new Step("fish-side-setup", SETTLE_TICKS, (mc, p) -> {
            view(mc, 0);
            hold(p, null);
            place(p, 180, 5);
            side = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "Pescador"));
            side.setPositionAndRotation(0.5, 4 + side.yOffset, -3.5, 270, 0);
            turn(side, 225);
            side.inventory.setInventorySlotContents(0, fish("tucunare", 35, 0));
            side.inventory.currentItem = 0;
            mc.theWorld.addEntityToWorld(-4242, side);
        }));
        Object[][] others = { { "tucunare", 35.0, 0, "tucunare" }, { "dourado.ouro", 95.0, 1, "dourado-ouro" },
            { "peixe-lua.iridio", 300.0, 3, "peixe-lua-iridio" }, { "peixe-akasha.iridio", 150.0, 1, "akasha" },
            { "baiacu.prata", 30.0, 3, "baiacu-prata" }, { "leviathan.ouro", 2200.0, 0, "leviathan-ouro" } };
        for (Object[] f : others) {
            steps.add(
                new Step(
                    "fish-side-" + f[3],
                    SETTLE_TICKS,
                    (mc, p) -> {
                        side.inventory.setInventorySlotContents(0, fish((String) f[0], (Double) f[1], (Integer) f[2]));
                    }));
        }
        steps.add(new Step("fish-side-walk", 12, (mc, p) -> everyTick = () -> {
            side.limbSwing += 0.9F;
            side.limbSwingAmount = side.prevLimbSwingAmount = 1;
        }));
    }

    /**
     * Another player at the left edge of the view, holding a rod across it (pointing right, into the view): the camera
     * turns right step by step until the body leaves the view while the rod is still in it; then the same with the
     * line out (the hook in view).
     */
    private void planEdge() {
        int[] turns = { 0, 6, 10, 14, 18, 24 };
        steps.add(new Step("edge-setup", SETTLE_TICKS, (mc, p) -> {
            view(mc, 0);
            hold(p, null);
            place(p, 180, 0);
            side = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "Borda"));
            side.setPositionAndRotation(-2.5, 4 + side.yOffset, -2.5, 270, 0);
            turn(side, 270);
            side.inventory.setInventorySlotContents(0, rod("akashicfishing:vara/vara-akashica.iridio.m6"));
            side.inventory.currentItem = 0;
            mc.theWorld.addEntityToWorld(-4242, side);
        }));
        for (int t : turns) {
            steps.add(new Step("edge-turn-" + t, 12, (mc, p) -> look(mc, p, 180 + t)));
        }
        steps.add(new Step("edge-line-setup", 15, (mc, p) -> {
            look(mc, p, 180);
            EntityFishHook hook = new EntityFishHook(mc.theWorld, side.posX + 4, side.posY - 1, side.posZ - 1.5, side);
            mc.theWorld.addEntityToWorld(-4243, hook);
        }));
        for (int t : turns) {
            steps.add(new Step("edge-line-turn-" + t, 12, (mc, p) -> look(mc, p, 180 + t)));
        }
    }

    /** Turns the camera on both sides (the server's teleport can reach the client a few frames late). */
    private static void look(Minecraft mc, EntityPlayerMP p, float yaw) {
        place(p, yaw, 0);
        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 0;
    }

    private void plan() {
        steps.add(new Step("fp-solar-idle", SETTLE_TICKS, (mc, p) -> {
            place(p, 180, 10);
            view(mc, 0);
            hold(p, rod("akashicfishing:vara/vara-solar"));
        }));
        // The inertia (balanco): a turn to the right leaves the tip to the left; stopping, it swings past the middle.
        steps.add(new Step("fp-sway-turning-right", 4, (mc, p) -> everyTick = () -> mc.thePlayer.rotationYaw += 12));
        steps.add(new Step("fp-sway-overshoot", 7, (mc, p) -> everyTick = null));
        steps.add(new Step("fp-sway-looking-up", 4, (mc, p) -> everyTick = () -> mc.thePlayer.rotationPitch -= 6));
        steps.add(new Step("fp-sway-fast-turn", 3, (mc, p) -> everyTick = () -> mc.thePlayer.rotationYaw += 30));
        steps.add(new Step("fp-sway-fast-overshoot", 6, (mc, p) -> everyTick = null));
        steps.add(new Step("fp-sway-strafe-right", 6, (mc, p) -> {
            float yaw = (float) Math.toRadians(mc.thePlayer.rotationYaw);
            everyTick = () -> mc.thePlayer.setPosition(
                mc.thePlayer.posX - Math.cos(yaw) * 0.25,
                mc.thePlayer.posY,
                mc.thePlayer.posZ - Math.sin(yaw) * 0.25);
        }));
        steps.add(new Step("fp-sway-rest", 60, (mc, p) -> {
            everyTick = null;
            place(p, 180, 10);
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
        steps.add(new Step("side-catch-blend", 2, (mc, p) -> {
            if (RodModels.INSTANCE.active()) {
                RodModels.INSTANCE.timeline(side)
                    .server("captura", RodModels.INSTANCE.clock());
            }
        }));
        steps.add(new Step("side-catch", 10, (mc, p) -> {}));
        // Both hands on the rod while walking (no walking swing), then an item in Backhand's off hand: one hand.
        steps.add(new Step("side-walk-fight", 12, (mc, p) -> {
            if (RodModels.INSTANCE.active()) {
                RodModels.INSTANCE.timeline(side)
                    .server("briga", RodModels.INSTANCE.clock());
            }
            everyTick = () -> {
                side.limbSwing += 0.9F;
                side.limbSwingAmount = side.prevLimbSwingAmount = 1;
            };
        }));
        steps.add(new Step("side-fight-offhand", 12, (mc, p) -> {
            everyTick = null;
            report("backhand offhand: " + setOffhand(side, new ItemStack(Items.apple)));
        }));
        // The other side: the left arm on the rod, walking, and let go with an item in Backhand's off hand.
        steps.add(new Step("side-left-fight", 15, (mc, p) -> {
            everyTick = null;
            setOffhand(side, null);
            turn(side, 90);
            RodModels.INSTANCE.timeline(side)
                .server("briga", RodModels.INSTANCE.clock());
        }));
        steps.add(new Step("side-left-walk-fight", 12, (mc, p) -> everyTick = () -> {
            side.limbSwing += 0.9F;
            side.limbSwingAmount = side.prevLimbSwingAmount = 1;
        }));
        steps.add(new Step("side-left-offhand", 12, (mc, p) -> {
            everyTick = null;
            setOffhand(side, new ItemStack(Items.apple));
        }));
        steps.add(new Step("side-left-idle", 15, (mc, p) -> {
            setOffhand(side, null);
            RodModels.INSTANCE.timeline(side)
                .server("parada", RodModels.INSTANCE.clock());
        }));
        // The hanging line (linha_pendurada): straight down at rest, trailing while walking, swinging back on a stop.
        steps.add(new Step("side-left-hang-rest", 40, (mc, p) -> {}));
        steps.add(new Step("side-left-hang-walk", 8, (mc, p) -> everyTick = () -> {
            side.setPosition(side.posX - 0.2, side.posY, side.posZ);
            side.limbSwing += 0.9F;
            side.limbSwingAmount = side.prevLimbSwingAmount = 1;
        }));
        steps.add(new Step("side-left-hang-stop", 5, (mc, p) -> everyTick = null));
        steps.add(new Step("side-left-hang-catch", 12, (mc, p) -> {
            side.setPosition(side.posX + 1.6, side.posY, side.posZ);
            RodModels.INSTANCE.timeline(side)
                .server("captura", RodModels.INSTANCE.clock());
        }));
        // Both hands only once the line is out: waiting with the line in the water, then the line back, then a cast.
        steps.add(new Step("side-left-idle-line-out", 15, (mc, p) -> {
            EntityFishHook hook = new EntityFishHook(mc.theWorld, side.posX - 4, side.posY - 1, side.posZ, side);
            mc.theWorld.addEntityToWorld(-4243, hook);
        }));
        steps.add(new Step("side-left-line-back", 15, (mc, p) -> {
            mc.theWorld.removeEntityFromWorld(-4243);
            side.fishEntity = null;
        }));
        steps.add(
            new Step(
                "side-left-cast",
                6,
                (mc, p) -> RodModels.INSTANCE.timeline(side)
                    .server("lancar", RodModels.INSTANCE.clock())));
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
        steps.add(new Step("quality-stones", SETTLE_TICKS, (mc, p) -> {
            WorldServer w = p.getServerForPlayer();
            String[] qualities = { "", ".prata", ".ouro", ".iridio" };
            for (int i = 0; i < qualities.length; i++) {
                int x = i - 2;
                w.setBlock(x, 4, -2, Blocks.stone);
                w.setBlock(x, 5, -2, Blocks.stone);
                EntityItemFrame f = new EntityItemFrame(w, x, 5, -2, 0);
                f.setDisplayedItem(rod("akashicfishing:vara/vara-solar" + qualities[i]));
                w.spawnEntityInWorld(f);
            }
            p.playerNetServerHandler.setPlayerLocation(-0.5, 4, 1.2, 180, 4);
        }));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        // Any menu, not only vanilla's: modpacks replace it (TEOF's AkashicCinematicMenu). Not from GuiOpenEvent: the
        // main menu first opens while the game is still starting.
        if (!launched && mc.theWorld == null
            && (mc.currentScreen instanceof GuiMainMenu || mc.currentScreen != null && ++menuTicks > 60)) {
            launched = true;
            mc.gameSettings.pauseOnLostFocus = false;
            // Under Xvfb the grabbed pointer sends stray deltas every frame: only the harness turns the camera.
            mc.mouseHelper = new net.minecraft.util.MouseHelper() {

                @Override
                public void mouseXYChange() {
                    deltaX = 0;
                    deltaY = 0;
                }
            };
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
        if (p == null) {
            return;
        }
        if (shotPending) {
            // A slow client (software GL) can run several ticks before the frame is drawn: keep the motion going so
            // the shot shows it, not the spring already coming back.
            if (everyTick != null && !mc.isGamePaused()) {
                everyTick.run();
            }
            return;
        }
        // Modpacks open screens on join (TEOF: the pause menu, with the window unfocused under Xvfb); a paused
        // singleplayer game stops the ticks, and with them the animations, the inertia and the cast.
        closeScreens(mc);
        mc.ingameGUI.getChatGUI()
            .clearChatMessages();
        if (index < 0) {
            p.getServerForPlayer()
                .setWorldTime(6000);
            report("rods: " + RodModels.INSTANCE.status());
            index = 0;
            ticks = 0;
            steps.get(0).action.run(mc, p);
            return;
        }
        if (mc.isGamePaused()) {
            pausedTicks++;
            return; // the game (and Kunftig's tick) stands still: so does the harness
        }
        if (everyTick != null) {
            everyTick.run();
        }
        if (++ticks >= steps.get(index).wait) {
            shotPending = true;
        }
    }

    /** Closes whatever screen is open (Xvfb gives the window no focus: the pause menu comes back every frame). */
    private void closeScreens(Minecraft mc) {
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.currentScreen != null
            && !(keepChest && mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiChest)) {
            mc.displayGuiScreen(null);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && index >= 0 && !done) {
            closeScreens(Minecraft.getMinecraft());
            return;
        }
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
                + (mc.thePlayer != null && mc.thePlayer.fishEntity != null)
                + " paused="
                + pausedTicks
                + " sway="
                + sway(mc));
        pausedTicks = 0;
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

    private static String sway(Minecraft mc) {
        com.akashic.kunftig.rodmodel.RodModelIndex.Resolved r = mc.thePlayer == null ? null
            : RodModels.INSTANCE.resolve(mc.thePlayer.getHeldItem());
        float[][] s = r == null ? null : RodModels.INSTANCE.firstPersonSway(r.model);
        if (s == null) {
            return "-";
        }
        return String.format(
            java.util.Locale.ROOT,
            "root(up %.1f, left %.1f) bend(up %.1f, left %.1f) deg",
            Math.toDegrees(s[0][0]),
            Math.toDegrees(s[0][1]),
            Math.toDegrees(s[1][0]),
            Math.toDegrees(s[1][1]));
    }

    private static void turn(EntityOtherPlayerMP e, float yaw) {
        e.setPositionAndRotation(e.posX, e.posY, e.posZ, yaw, 0);
        e.rotationYawHead = e.prevRotationYawHead = yaw;
        e.renderYawOffset = e.prevRenderYawOffset = yaw;
        e.prevRotationYaw = yaw;
    }

    /** Backhand's off hand, by reflection (absent Backhand: false). */
    private static boolean setOffhand(EntityPlayer player, ItemStack stack) {
        try {
            Class.forName("xonin.backhand.api.core.BackhandUtils")
                .getMethod("setPlayerOffhandItem", EntityPlayer.class, ItemStack.class)
                .invoke(null, player, stack);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
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
