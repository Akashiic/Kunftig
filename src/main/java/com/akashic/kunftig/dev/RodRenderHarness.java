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
            if (mode.length <= 2 && mode[0].equals("sb")) {
                RodRenderHarness h = new RodRenderHarness();
                h.planSlashBlade(mode.length == 2 ? mode[1] : "sbref:yamato");
                FMLCommonHandler.instance()
                    .bus()
                    .register(h);
                Kunftig.LOG.warn("Kunftig DEV ROD HARNESS (sb) enabled: this client will take screenshots and quit");
                return;
            }
            if (mode.length == 2 && mode[0].equals("sbseq")) {
                RodRenderHarness h = new RodRenderHarness();
                h.planSlashBladeSequences(mode[1]);
                FMLCommonHandler.instance()
                    .bus()
                    .register(h);
                Kunftig.LOG.warn("Kunftig DEV ROD HARNESS (sbseq) enabled: this client will take screenshots and quit");
                return;
            }
            if (mode.length <= 3 && mode[0].equals("sbfx")) {
                RodRenderHarness h = new RodRenderHarness();
                h.fxNs = mode.length >= 2 ? mode[1] : "sbref";
                h.fxBlade = mode.length >= 3 ? mode[2] : "sbref:yamato";
                h.planSlashBladeEffects();
                FMLCommonHandler.instance()
                    .bus()
                    .register(h);
                Kunftig.LOG.warn("Kunftig DEV ROD HARNESS (sbfx) enabled: this client will take screenshots and quit");
                return;
            }
            if (mode.length <= 2 && mode[0].equals("models")) {
                RodRenderHarness h = new RodRenderHarness();
                h.planModels(mode.length == 2 ? mode[1] : "kfttest:katana");
                FMLCommonHandler.instance()
                    .bus()
                    .register(h);
                Kunftig.LOG
                    .warn("Kunftig DEV ROD HARNESS (models) enabled: this client will take screenshots and quit");
                return;
            }
            if (mode.length == 1 && mode[0].equals("blades")) {
                RodRenderHarness h = new RodRenderHarness();
                h.planBlades();
                FMLCommonHandler.instance()
                    .bus()
                    .register(h);
                Kunftig.LOG
                    .warn("Kunftig DEV ROD HARNESS (blades) enabled: this client will take screenshots and quit");
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
            if (mode.length == 3 && mode[0].equals("skins")) {
                SkinHarness.install(mode[1], Integer.parseInt(mode[2]));
                return;
            }
            if ((mode.length == 3 || mode.length == 4) && mode[0].equals("boss")) {
                BossHarness.install(mode[1], Integer.parseInt(mode[2]), mode.length == 4 ? mode[3] : "");
                return;
            }
            if (mode.length == 3 && mode[0].equals("caixa")) {
                LuckBoxHarness.install(mode[1], Integer.parseInt(mode[2]));
                return;
            }
            if (mode.length == 3 && mode[0].equals("overkill")) {
                OverkillServerHarness.install(mode[1], Integer.parseInt(mode[2]));
                return;
            }
            if (mode.length == 3 && mode[0].equals("server")) {
                RodServerHarness.install(mode[1], Integer.parseInt(mode[2]));
                return;
            }
            if (mode.length == 4 && mode[0].equals("server")) {
                RodServerHarness.install(mode[1], Integer.parseInt(mode[2]), false, Integer.parseInt(mode[3]));
                return;
            }
            if (mode.length == 5 && mode[0].equals("observer")) {
                RodServerHarness.installObserver(
                    mode[1],
                    Integer.parseInt(mode[2]),
                    Double.parseDouble(mode[3]),
                    Integer.parseInt(mode[4]));
                return;
            }
            if (mode.length == 3 && mode[0].equals("observer")) {
                RodServerHarness.install(mode[1], Integer.parseInt(mode[2]), true);
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

    /** A sword with an item model (the Overkill pack's blades are golden swords by default). */
    static ItemStack blade(String model) {
        ItemStack stack = new ItemStack(Items.golden_sword);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound values = new NBTTagCompound();
        values.setString("kunftig:model", model);
        tag.setTag("PublicBukkitValues", values);
        stack.setTagCompound(tag);
        return stack;
    }

    private static String state(Minecraft mc) {
        return RodModels.INSTANCE.active() ? String.valueOf(
            RodModels.INSTANCE.timeline(mc.thePlayer)
                .state())
            : "-";
    }

    private void check(String what, Object expected, Object actual) {
        report(
            (java.util.Objects.equals(expected, actual) ? "OK   " : "FAIL ") + what
                + ": expected "
                + expected
                + ", got "
                + actual);
    }

    /**
     * API 9 with the Overkill pack (resource pack "Overkill" selected): the katana in first and third person in its
     * states, the attack prediction driven by real swings, displays turned and flying, the screen HUD and the lock-on.
     * Checks go to the report as OK / FAIL lines.
     */
    private void planBlades() {
        String k = "overkill:katana/basica";
        steps.add(new Step("blade-fp-idle", SETTLE_TICKS, (mc, p) -> {
            place(p, 180, 10);
            view(mc, 0);
            hold(p, blade(k));
        }));
        steps.add(new Step("blade-fp-empty", 5, (mc, p) -> hold(p, null)));
        steps.add(new Step("blade-fp-sacar", 4, (mc, p) -> hold(p, blade(k))));
        // the attack prediction: real swings of the local player (EntityClientPlayerMP#swingItem)
        steps.add(new Step("blade-fp-predict-golpe_1", 5, (mc, p) -> {
            RodModels.INSTANCE.forceLocalState("parada", 0);
            mc.thePlayer.swingItem();
            check("swing from idle predicts golpe_1", "golpe_1", state(mc));
        }));
        steps.add(new Step("blade-fp-predict-golpe_2", 2, (mc, p) -> {
            mc.thePlayer.swingItem();
            check("swing in golpe_1's window predicts golpe_2", "golpe_2", state(mc));
            long start = RodModels.INSTANCE.timeline(mc.thePlayer)
                .start();
            mc.thePlayer.swingItem();
            check("a swing before golpe_2's window does nothing", "golpe_2", state(mc));
            RodModels.INSTANCE
                .onPayload(com.akashic.kunftig.rodmodel.RodWire.state(mc.thePlayer.getEntityId(), "golpe_2", 1, 0));
            check(
                "the server confirming golpe_2 does not restart it",
                start,
                RodModels.INSTANCE.timeline(mc.thePlayer)
                    .start());
            RodModels.INSTANCE
                .onPayload(com.akashic.kunftig.rodmodel.RodWire.state(mc.thePlayer.getEntityId(), "golpe_1", 0, 0));
            check("the server correcting to golpe_1 wins", "golpe_1", state(mc));
        }));
        String[][] moves = { { "golpe_1", "4" }, { "golpe_2", "4" }, { "golpe_3", "5" }, { "golpe_4", "6" },
            { "estocada", "6" }, { "lancamento", "5" }, { "aereo_3", "5" }, { "queda", "4" }, { "iaido", "3" },
            { "corte_dimensional", "9" }, { "onda", "9" }, { "corte_circular", "9" } };
        for (String[] m : moves) {
            steps.add(
                new Step(
                    "blade-fp-" + m[0],
                    Integer.parseInt(m[1]),
                    (mc, p) -> RodModels.INSTANCE.forceLocalState(m[0], 0)));
        }
        steps.add(new Step("blade-tp-front-idle", 25, (mc, p) -> {
            RodModels.INSTANCE.forceLocalState("parada", 0);
            view(mc, 2);
        }));
        for (String[] m : new String[][] { { "golpe_1", "4" }, { "golpe_3", "5" }, { "golpe_4", "6" }, { "iaido", "3" },
            { "estocada", "6" } }) {
            steps.add(
                new Step(
                    "blade-tp-front-" + m[0],
                    Integer.parseInt(m[1]),
                    (mc, p) -> RodModels.INSTANCE.forceLocalState(m[0], 0)));
        }
        steps.add(new Step("blade-tp-back-golpe_2", 4, (mc, p) -> {
            view(mc, 1);
            RodModels.INSTANCE.forceLocalState("golpe_2", 0);
        }));
        // displays: a dimension cut, a wave flying with a roll, a summoned sword tipped down
        steps.add(new Step("blade-displays", 12, (mc, p) -> {
            view(mc, 0);
            RodModels.INSTANCE.forceLocalState("parada", 0);
            place(p, 180, 5);
            com.akashic.kunftig.client.display.DisplayClient d = com.akashic.kunftig.client.display.DisplayClient.INSTANCE;
            d.onPayload(
                com.akashic.kunftig.display.DisplayWire.show(
                    9001,
                    -1.5,
                    5.2,
                    -4.5,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "overkill:corte_dimensional",
                    "",
                    "cortar",
                    0,
                    null));
            d.onPayload(
                com.akashic.kunftig.display.DisplayWire
                    .show(9002, 1.5, 4.8, -2.5, 180, 0, 30, 0, 0, -0.08, "overkill:onda", "", "voar", 0, null));
            d.onPayload(
                com.akashic.kunftig.display.DisplayWire
                    .show(9003, 0.5, 5.6, -3, 180, 25, 0, 0, 0, 0, "overkill:espada_invocada", "", "voar", 0, null));
            d.onPayload(
                com.akashic.kunftig.display.DisplayWire
                    .show(9004, 0.5, 4, -6, 0, 0, 0, 0, 0, 0, "overkill:corte_circular", "", "girar", 0, null));
        }));
        steps.add(new Step("blade-displays-later", 6, (mc, p) -> {
            check("4 displays shown", 4, com.akashic.kunftig.client.display.DisplayClient.INSTANCE.count());
            com.akashic.kunftig.client.display.DisplayClient.INSTANCE
                .onPayload(com.akashic.kunftig.display.DisplayWire.move(9003, 2.5, 5, -3, 270, 6, -30, 0));
        }));
        // the screen HUD
        steps.add(new Step("blade-hud", 4, (mc, p) -> {
            com.akashic.kunftig.client.screen.HudClient h = com.akashic.kunftig.client.screen.HudClient.INSTANCE;
            h.onPayload(
                com.akashic.kunftig.screen.HudWire.set(
                    "overkill:rank",
                    new com.akashic.kunftig.screen.HudWire.Element(
                        com.akashic.kunftig.screen.HudWire.RIGHT,
                        -16,
                        -34,
                        3,
                        "\u00a76\u00a7lS",
                        -1,
                        0,
                        0,
                        0,
                        "",
                        0,
                        0,
                        0,
                        0)));
            h.onPayload(
                com.akashic.kunftig.screen.HudWire.set(
                    "overkill:rank_titulo",
                    new com.akashic.kunftig.screen.HudWire.Element(
                        com.akashic.kunftig.screen.HudWire.RIGHT,
                        -16,
                        0,
                        1,
                        "\u00a7e\u00a7lSelvagem\n\u00a7712 golpes",
                        0.6F,
                        64,
                        0xFFFFFF55,
                        0x80000000,
                        "",
                        0,
                        0,
                        0,
                        0)));
            h.onPayload(
                com.akashic.kunftig.screen.HudWire.set(
                    "overkill:carga",
                    new com.akashic.kunftig.screen.HudWire.Element(
                        com.akashic.kunftig.screen.HudWire.CENTER,
                        0,
                        22,
                        1,
                        "\u00a7d\u00a7lCorte Dimensional",
                        1,
                        60,
                        0xFFC77DFF,
                        0x80000000,
                        "",
                        0,
                        0,
                        0,
                        0)));
            check("3 HUD elements", 3, h.count());
        }));
        // the lock-on: a player off to the right; the camera turns to him and marks him
        steps.add(new Step("blade-lockon-setup", 10, (mc, p) -> {
            com.akashic.kunftig.client.display.DisplayClient.INSTANCE.clear();
            place(p, 180, 0);
            side = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "Alvo"));
            side.setPositionAndRotation(5.5, 4 + side.yOffset, -4.5, 90, 0);
            side.inventory.setInventorySlotContents(0, blade(k));
            side.inventory.currentItem = 0;
            mc.theWorld.addEntityToWorld(-4243, side);
        }));
        steps.add(
            new Step(
                "blade-lockon",
                25,
                (mc, p) -> com.akashic.kunftig.client.screen.CameraClient.INSTANCE
                    .onPayload(com.akashic.kunftig.screen.CameraWire.lock(side.getEntityId(), 0.3F, 24, true))));
        steps.add(new Step("blade-lockon-side-golpe_3", 5, (mc, p) -> {
            float[] want = com.akashic.kunftig.screen.CameraWire.look(
                mc.thePlayer.posX,
                mc.thePlayer.posY,
                mc.thePlayer.posZ,
                side.posX,
                side.posY + side.height * 0.6 - side.yOffset,
                side.posZ);
            float off = Math.abs(com.akashic.kunftig.screen.CameraWire.wrap(want[0] - mc.thePlayer.rotationYaw));
            check("the camera turned to the target (yaw within 3 degrees)", true, off < 3);
            check("still locked", true, com.akashic.kunftig.client.screen.CameraClient.INSTANCE.locked());
            if (RodModels.INSTANCE.active()) {
                RodModels.INSTANCE.timeline(side)
                    .server("golpe_3", RodModels.INSTANCE.clock());
            }
        }));
        steps.add(new Step("blade-unlocked", 3, (mc, p) -> {
            com.akashic.kunftig.client.screen.CameraClient.INSTANCE
                .onPayload(com.akashic.kunftig.screen.CameraWire.unlock());
            check("unlocked", false, com.akashic.kunftig.client.screen.CameraClient.INSTANCE.locked());
        }));
    }

    /**
     * Players drawn with 3D Futura Engine's models (futura3d on the client's classpath, development only): a 3D rod of
     * the AkashicFishing pack and the vanilla rod at the model's hand with the line out, from the front, from behind
     * and from the side, and a SlashBlade-style blade ({@code bladeModel}, from any blade pack) sheathed and drawn, on
     * futura3d's {@code default} model and on {@code wine_fox/13_matured} (which has the blade locators).
     */
    private void planModels(String bladeModel) {
        steps.add(new Step("models-rod-front", SETTLE_TICKS, (mc, p) -> {
            place(p, 180, 10);
            view(mc, 2);
            hold(p, rod("akashicfishing:vara/vara-solar"));
        }));
        steps.add(new Step("models-rod-line-out", 40, (mc, p) -> {
            report(
                "futura3d present=" + com.akashic.kunftig.client.compat.FuturaModels.present()
                    + " modelled(third person)="
                    + com.akashic.kunftig.client.compat.FuturaModels.isModelled(mc.thePlayer)
                    + " hand="
                    + futuraLocator(mc.thePlayer, "RightHandLocator"));
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
        }));
        steps.add(new Step("models-rod-line-out-back", 15, (mc, p) -> view(mc, 1)));
        steps.add(new Step("models-rod-fight", 25, (mc, p) -> {
            view(mc, 2);
            RodModels.INSTANCE.forceLocalState("briga", 0.9F);
        }));
        steps.add(new Step("models-vanilla-rod-cast", 40, (mc, p) -> {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            RodModels.INSTANCE.forceLocalState("parada", 0);
            hold(p, rod(null));
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
        }));
        steps.add(new Step("models-vanilla-rod-side", SETTLE_TICKS, (mc, p) -> {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            hold(p, null);
            view(mc, 0);
            place(p, 180, 5);
            side = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "Lateral"));
            side.setPositionAndRotation(0.5, 4 + side.yOffset, -2.5, 270, 0);
            turn(side, 270);
            side.inventory.setInventorySlotContents(0, rod(null));
            side.inventory.currentItem = 0;
            mc.theWorld.addEntityToWorld(-4242, side);
            EntityFishHook hook = new EntityFishHook(mc.theWorld, side.posX + 3, side.posY - 1, side.posZ - 1, side);
            mc.theWorld.addEntityToWorld(-4243, hook);
        }));
        steps.add(new Step("models-rod-side", SETTLE_TICKS, (mc, p) -> {
            report(
                "modelled(first person, local)="
                    + com.akashic.kunftig.client.compat.FuturaModels.isModelled(mc.thePlayer)
                    + " modelled(side)="
                    + com.akashic.kunftig.client.compat.FuturaModels.isModelled(side));
            side.inventory.setInventorySlotContents(0, rod("akashicfishing:vara/vara-solar"));
        }));
        steps.add(new Step("models-rod-side-fight", 25, (mc, p) -> {
            if (RodModels.INSTANCE.active()) {
                RodModels.INSTANCE.timeline(side)
                    .server("briga", RodModels.INSTANCE.clock());
                RodModels.INSTANCE.timeline(side)
                    .setTension(0.8F);
            }
        }));
        steps.add(new Step("models-blade-side-idle", SETTLE_TICKS, (mc, p) -> {
            mc.theWorld.removeEntityFromWorld(-4243);
            side.fishEntity = null;
            side.inventory.setInventorySlotContents(0, blade(bladeModel));
        }));
        steps.add(
            new Step(
                "models-blade-side-combo",
                10,
                (mc, p) -> com.akashic.kunftig.client.blade.BladeClient.INSTANCE.play(side, longChain(), null)));
        steps.add(new Step("models-blade-front-idle", SETTLE_TICKS, (mc, p) -> {
            mc.theWorld.removeEntityFromWorld(side.getEntityId());
            place(p, 180, 10);
            view(mc, 2);
            hold(p, blade(bladeModel));
        }));
        steps.add(
            new Step(
                "models-blade-front-combo",
                10,
                (mc, p) -> com.akashic.kunftig.client.blade.BladeClient.INSTANCE
                    .play(mc.thePlayer, longChain(), null)));
        steps.add(new Step("models-blade-back-combo", 10, (mc, p) -> view(mc, 1)));
        steps.add(new Step("models-wine-blade-idle", SETTLE_TICKS, (mc, p) -> {
            report("futura3d model wine_fox/13_matured: " + useFuturaModel("wine_fox/13_matured"));
            com.akashic.kunftig.client.blade.BladeClient.INSTANCE
                .play(mc.thePlayer, java.util.Collections.emptyList(), null);
            view(mc, 2);
        }));
        steps.add(new Step("models-wine-blade-combo", 10, (mc, p) -> {
            report(
                "blade locators: blade="
                    + com.akashic.kunftig.client.compat.FuturaModels.hasLocator(mc.thePlayer, "BladeLocator")
                    + " sheath="
                    + com.akashic.kunftig.client.compat.FuturaModels.hasLocator(mc.thePlayer, "SheathLocator")
                    + " waist="
                    + com.akashic.kunftig.client.compat.FuturaModels.hasLocator(mc.thePlayer, "LeftWaistLocator"));
            com.akashic.kunftig.client.blade.BladeClient.INSTANCE.play(mc.thePlayer, longChain(), null);
        }));
        steps.add(new Step("models-wine-blade-back-combo", 10, (mc, p) -> view(mc, 1)));
        steps.add(new Step("models-wine-blade-side-idle", SETTLE_TICKS, (mc, p) -> {
            hold(p, null);
            view(mc, 0);
            place(p, 180, 5);
            side = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "Lateral"));
            side.setPositionAndRotation(0.5, 4 + side.yOffset, -2.5, 270, 0);
            turn(side, 270);
            side.inventory.setInventorySlotContents(0, blade(bladeModel));
            side.inventory.currentItem = 0;
            mc.theWorld.addEntityToWorld(-4244, side);
        }));
        steps.add(
            new Step(
                "models-wine-blade-side-combo",
                10,
                (mc, p) -> com.akashic.kunftig.client.blade.BladeClient.INSTANCE.play(side, longChain(), null)));
        steps.add(new Step("models-wine-rod-line-out", 40, (mc, p) -> {
            mc.theWorld.removeEntityFromWorld(side.getEntityId());
            place(p, 180, 10);
            view(mc, 2);
            hold(p, rod("akashicfishing:vara/vara-solar"));
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
        }));
        steps.add(new Step("models-wine-vanilla-rod-cast", 40, (mc, p) -> {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            hold(p, rod(null));
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
        }));
        steps.add(new Step("models-wine-vanilla-rod-side", SETTLE_TICKS, (mc, p) -> {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            hold(p, null);
            view(mc, 0);
            place(p, 180, 5);
            side = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "Lateral"));
            side.setPositionAndRotation(0.5, 4 + side.yOffset, -2.5, 270, 0);
            turn(side, 270);
            side.inventory.setInventorySlotContents(0, rod(null));
            side.inventory.currentItem = 0;
            mc.theWorld.addEntityToWorld(-4245, side);
            EntityFishHook hook = new EntityFishHook(mc.theWorld, side.posX + 3, side.posY - 1, side.posZ - 1, side);
            mc.theWorld.addEntityToWorld(-4246, hook);
        }));
    }

    /** A combo that plays for 20 s: the blade stays out. */
    private static java.util.List<com.akashic.kunftig.blade.BladeTimeline.Segment> longChain() {
        return java.util.Collections
            .singletonList(new com.akashic.kunftig.blade.BladeTimeline.Segment(0, 60, true, 20000));
    }

    /** futura3d's locator, by reflection on its API (development check), as a short text. */
    private static String futuraLocator(EntityPlayer player, String name) {
        try {
            float[] m = (float[]) Class.forName("com.akashic.futura.api.FuturaModels")
                .getMethod("locator", EntityPlayer.class, String.class)
                .invoke(null, player, name);
            return m == null ? "null" : String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", m[12], m[13], m[14]);
        } catch (ReflectiveOperationException | LinkageError e) {
            return "unavailable";
        }
    }

    /** Switches futura3d to a built-in model (development check, by reflection on its client class). */
    private static String useFuturaModel(String builtin) {
        try {
            Class<?> c = Class.forName("com.akashic.futura.client.PlayerModels");
            Object models = c.getField("INSTANCE")
                .get(null);
            return String.valueOf(
                c.getMethod("use", String.class)
                    .invoke(models, builtin));
        } catch (ReflectiveOperationException | LinkageError e) {
            return "unavailable";
        }
    }

    /** A SlashBlade combo state chain: (start, end) frame pairs, each lasting its frames at speed 1. */
    private static java.util.List<com.akashic.kunftig.blade.BladeTimeline.Segment> chain(int... frames) {
        java.util.List<com.akashic.kunftig.blade.BladeTimeline.Segment> out = new ArrayList<>();
        for (int i = 0; i < frames.length; i += 2) {
            out.add(
                new com.akashic.kunftig.blade.BladeTimeline.Segment(
                    frames[i],
                    frames[i + 1],
                    false,
                    com.akashic.kunftig.blade.BladeTimeline.Segment.duration(frames[i], frames[i + 1], 1, 0)));
        }
        return out;
    }

    /**
     * SlashBlade-style blades with a pack declaring {@code sbref:yamato} (SlashBlade's own files, local only): the
     * blade at rest and through combo_a1, combo_a2 and aerial_rave_a1 (blade rig and body motion), from behind, from
     * the front and in first person, at the ticks the reference captures use.
     */
    /**
     * Long stretches of the blade motion with their body clips, from behind, every 3 ticks, the hotbar holding only the
     * blade: to compare where the blade is against the hand with SlashBlade's own captures (A3, A4, aerial B3).
     */
    private void planSlashBladeSequences(String k) {
        com.akashic.kunftig.client.blade.BladeClient c = com.akashic.kunftig.client.blade.BladeClient.INSTANCE;
        steps.add(new Step("sbseq-setup", SETTLE_TICKS, (mc, p) -> {
            for (int i = 0; i < p.inventory.mainInventory.length; i++) {
                p.inventory.mainInventory[i] = null;
                mc.thePlayer.inventory.mainInventory[i] = null;
            }
            p.inventory.currentItem = 0;
            mc.thePlayer.inventory.currentItem = 0;
            place(p, 180, 0);
            view(mc, 1);
            hold(p, blade(k));
        }));
        Object[][] seqs = { { "a3", 200, 306, Boolean.TRUE }, { "a4", 500, 608, Boolean.TRUE },
            { "airb3", 1400, 1443, Boolean.FALSE } };
        for (Object[] q : seqs) {
            String name = (String) q[0];
            int start = (Integer) q[1];
            int end = (Integer) q[2];
            boolean blend = (Boolean) q[3];
            int ticks = (int) Math.ceil((end - start) * 2 / 3.0);
            for (int t = 0; t <= ticks; t += 3) {
                final boolean first = t == 0;
                steps.add(new Step(String.format("sbseq-%s-t%02d", name, t), first ? 1 : 3, (mc, p) -> {
                    if (first) {
                        view(mc, 1);
                        c.play(
                            mc.thePlayer,
                            chain(start, end),
                            new com.akashic.kunftig.blade.BladeWire.Body(start, end, blend));
                    }
                }));
            }
            steps.add(new Step("sbseq-" + name + "-rest", 30, (mc, p) -> {}));
        }
    }

    private void planSlashBlade(String k) {
        com.akashic.kunftig.client.blade.BladeClient c = com.akashic.kunftig.client.blade.BladeClient.INSTANCE;
        steps.add(new Step("sb-tp-back-idle", SETTLE_TICKS, (mc, p) -> {
            place(p, 180, 0);
            view(mc, 1);
            hold(p, blade(k));
            report(
                "sb blades active=" + c.active()
                    + " blades="
                    + c.index()
                        .blades()
                        .keySet()
                    + " errors="
                    + c.index()
                        .errors());
        }));
        Object[][] combos = { { "a1", chain(1, 10, 10, 21, 21, 41), new int[] { 1, 41 }, Boolean.TRUE },
            { "a2", chain(100, 115, 115, 132, 132, 151), new int[] { 100, 151 }, Boolean.TRUE },
            { "air1", chain(1100, 1122), new int[] { 1100, 1132 }, Boolean.FALSE },
            // frames 204+: the sheath bone leaves the blade bone (A1 and A2 are sheathed strikes)
            { "b1", chain(200, 242), new int[] { 200, 242 }, Boolean.TRUE } };
        int[] views = { 1, 2, 0 };
        String[] viewNames = { "back", "front", "fp" };
        for (int v = 0; v < views.length; v++) {
            final int view = views[v];
            final String vn = viewNames[v];
            for (Object[] combo : combos) {
                @SuppressWarnings("unchecked")
                java.util.List<com.akashic.kunftig.blade.BladeTimeline.Segment> segs = (java.util.List<com.akashic.kunftig.blade.BladeTimeline.Segment>) combo[1];
                int[] body = (int[]) combo[2];
                boolean blend = (Boolean) combo[3];
                String name = (String) combo[0];
                int previous = 0;
                for (int tick : new int[] { 2, 4, 6, 8, 12, 18 }) {
                    final boolean first = previous == 0;
                    steps.add(
                        new Step(
                            "sb-" + vn + "-" + name + "-t" + String.format("%02d", tick),
                            tick - previous,
                            (mc, p) -> {
                                if (first) {
                                    view(mc, view);
                                    c.play(
                                        mc.thePlayer,
                                        segs,
                                        new com.akashic.kunftig.blade.BladeWire.Body(body[0], body[1], blend));
                                }
                            }));
                    previous = tick;
                }
                steps.add(new Step("sb-" + vn + "-" + name + "-rest", 30, (mc, p) -> {}));
            }
        }
        // slash arcs (SlashBlade's doSlash at the click) at three ranks: none (grey), B (white outside), SSS (all)
        float[][] ranks = { { 0 }, { 3 }, { 5.5F } };
        for (float[] rank : ranks) {
            int previous = 0;
            for (int tick : new int[] { 1, 3, 5, 7 }) {
                final boolean first = previous == 0;
                steps.add(new Step(String.format("sb-slash-r%.1f-t%02d", rank[0], tick), tick - previous, (mc, p) -> {
                    if (first) {
                        view(mc, 1);
                        place(p, 180, 0);
                        c.play(
                            mc.thePlayer,
                            chain(1, 10, 10, 21, 21, 41),
                            new com.akashic.kunftig.blade.BladeWire.Body(1, 41, true));
                        slash(p, -10, rank[0]);
                    }
                }));
                previous = tick;
            }
            steps.add(new Step(String.format("sb-slash-r%.1f-rest", rank[0]), 25, (mc, p) -> {}));
        }
        steps.add(new Step("sb-slash-a2-sss-t03", 3, (mc, p) -> {
            c.play(
                mc.thePlayer,
                chain(100, 115, 115, 132, 132, 151),
                new com.akashic.kunftig.blade.BladeWire.Body(100, 151, true));
            slash(p, 170, 5.5F);
        }));
        steps.add(new Step("sb-slash-a2-sss-t06", 3, (mc, p) -> {}));
        steps.add(new Step("sb-slash-front-sss-t03", 25, (mc, p) -> {}));
        steps.add(new Step("sb-slash-front-sss-t03b", 3, (mc, p) -> {
            view(mc, 2);
            c.play(
                mc.thePlayer,
                chain(1, 10, 10, 21, 21, 41),
                new com.akashic.kunftig.blade.BladeWire.Body(1, 41, true));
            slash(p, -10, 5.5F);
        }));
        steps.add(new Step("sb-tp-back-standby-slot0", SETTLE_TICKS, (mc, p) -> {
            view(mc, 1);
            p.inventory.setInventorySlotContents(0, blade(k));
            p.inventory.currentItem = 1;
            mc.thePlayer.inventory.setInventorySlotContents(0, blade(k));
            mc.thePlayer.inventory.currentItem = 1;
            hold(p, null);
        }));
        steps.add(new Step("sb-front-standby-slot0", 10, (mc, p) -> view(mc, 2)));
    }

    private net.minecraft.entity.monster.EntityZombie target;
    private String fxNs = "sbref";
    private String fxBlade = "sbref:yamato";

    /** The Judgement Cut, drives, summoned swords, the lock-on and the rank HUD, with SlashBlade's own models. */
    private void planSlashBladeEffects() {
        String k = fxBlade;
        com.akashic.kunftig.client.blade.BladeEffects fx = com.akashic.kunftig.client.blade.BladeEffects.INSTANCE;
        com.akashic.kunftig.client.blade.BladeHud hud = com.akashic.kunftig.client.blade.BladeHud.INSTANCE;
        int color = 0x3333FF;
        steps.add(new Step("sbfx-setup", SETTLE_TICKS, (mc, p) -> {
            place(p, 180, 0);
            view(mc, 0);
            hold(p, blade(k));
            target = new net.minecraft.entity.monster.EntityZombie(p.worldObj);
            target.setLocationAndAngles(0.5, 4, -5.5, 0, 0);
            target.addPotionEffect(
                new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.moveSlowdown.id, 100000, 20));
            target.setHealth(10);
            target.setCurrentItemOrArmor(4, new ItemStack(Items.leather_helmet)); // no burning in the sun
            p.worldObj.spawnEntityInWorld(target);
            report("sbfx effects=" + fx.count());
        }));
        // slash arcs at ranks none, B and SSS, seen from behind
        for (float rank : new float[] { 0, 3, 5.5F }) {
            int prev = 0;
            for (int tick : new int[] { 1, 3, 5, 7 }) {
                final boolean first = prev == 0;
                steps.add(new Step(String.format("sbfx-slash-r%.1f-t%02d", rank, tick), tick - prev, (mc, p) -> {
                    if (first) {
                        view(mc, 1);
                        place(p, 180, 0);
                        slash(p, -10, rank);
                    }
                }));
                prev = tick;
            }
            steps.add(new Step(String.format("sbfx-slash-r%.1f-rest", rank), 12, (mc, p) -> {}));
        }
        // Judgement Cut on the zombie's eyes, critical: three sub-slashes 120 degrees apart (ticks 1, 2, 3)
        int[] jcTicks = { 1, 2, 3, 5, 7, 9 };
        int previous = 0;
        for (int tick : jcTicks) {
            final boolean first = previous == 0;
            steps.add(new Step(String.format("sbfx-jc-t%02d", tick), tick - previous, (mc, p) -> {
                if (first) {
                    view(mc, 0);
                    double ey = target.posY + target.getEyeHeight();
                    fx.add(
                        new com.akashic.kunftig.blade.EffectWire.JudgementCut(
                            fxNs,
                            target.posX,
                            ey,
                            target.posZ,
                            0,
                            0,
                            color,
                            10));
                    int[] n = { 0 };
                    int seed = 37;
                    everyTick = () -> {
                        if (++n[0] <= 3) {
                            fx.add(
                                new com.akashic.kunftig.blade.EffectWire.Slash(
                                    fxNs,
                                    target.posX,
                                    ey,
                                    target.posZ,
                                    120F * n[0] + seed,
                                    0,
                                    30,
                                    0,
                                    color,
                                    5.5F,
                                    0.5F,
                                    10));
                        }
                    };
                }
            }));
            previous = tick;
        }
        steps.add(new Step("sbfx-jc-red-t04", 20, (mc, p) -> everyTick = null));
        steps.add(new Step("sbfx-jc-red-t04b", 4, (mc, p) -> {
            fx.add(
                new com.akashic.kunftig.blade.EffectWire.JudgementCut(
                    fxNs,
                    target.posX,
                    target.posY + target.getEyeHeight(),
                    target.posZ,
                    0,
                    0,
                    0xFF3333,
                    10));
        }));
        // drives: horizontal (speed 0.5 to see it), vertical, and wave edge (5 vertical, 0.2..1.0)
        steps.add(new Step("sbfx-drive-h-t03", 20, (mc, p) -> view(mc, 1)));
        steps.add(new Step("sbfx-drive-h-t03b", 3, (mc, p) -> drive(fx, p, 1, 0, 0.5, color, 10)));
        steps.add(new Step("sbfx-drive-v-t03", 3, (mc, p) -> drive(fx, p, 2, -90, 0.5, color, 10)));
        steps.add(new Step("sbfx-wave-t04", 15, (mc, p) -> {
            for (int i = 0; i < 5; i++) {
                drive(fx, p, 10 + i, 90, 0.2 + i * 0.2, color, 20);
            }
        }));
        steps.add(new Step("sbfx-wave-t10", 6, (mc, p) -> {}));
        // summoned swords: one basic sword flying into the zombie and sticking, then a spiral ring and heavy rain
        steps.add(new Step("sbfx-sword-t01", 25, (mc, p) -> {
            view(mc, 0);
            double sx = p.posX - 1;
            double sy = p.posY + 1.62;
            double sz = p.posZ;
            double dx = target.posX - sx;
            double dy = target.posY + target.getEyeHeight() * 0.5 - sy;
            double dz = target.posZ - sz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double vx = dx / len * 3;
            double vy = dy / len * 3;
            double vz = dz / len * 3;
            fx.add(
                new com.akashic.kunftig.blade.EffectWire.Projectile(
                    fxNs,
                    "espada",
                    20,
                    com.akashic.kunftig.blade.EffectWire.KIND_SWORD,
                    sx,
                    sy,
                    sz,
                    vx,
                    vy,
                    vz,
                    (float) Math.toDegrees(Math.atan2(vx, vz)),
                    (float) Math.toDegrees(Math.atan2(vy, Math.sqrt(vx * vx + vz * vz))),
                    75,
                    color,
                    0));
            int[] n = { 0 };
            everyTick = () -> {
                if (++n[0] == 2) {
                    com.akashic.kunftig.blade.EffectProjectile sw = fx.projectile(20);
                    if (sw != null) {
                        sw.stick(new com.akashic.kunftig.blade.EffectWire.Stick(20, target.getEntityId(), 40));
                    }
                }
            };
        }));
        steps.add(new Step("sbfx-sword-stuck", 10, (mc, p) -> {}));
        steps.add(new Step("sbfx-sword-stuck-back", 10, (mc, p) -> view(mc, 1)));
        steps.add(new Step("sbfx-spiral", 12, (mc, p) -> {
            view(mc, 1);
            fx.remove(20, true);
            for (int i = 0; i < 8; i++) {
                fx.add(
                    new com.akashic.kunftig.blade.EffectWire.Projectile(
                        fxNs,
                        "espada",
                        30 + i,
                        com.akashic.kunftig.blade.EffectWire.KIND_SWORD,
                        p.posX,
                        p.posY + 0.81,
                        p.posZ,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        color,
                        0));
            }
            everyTick = () -> {
                long time = mc.theWorld.getTotalWorldTime();
                for (int i = 0; i < 8; i++) {
                    float deg = ((time % 30) * 12 + 360F / 8 * i) % 360;
                    double r = Math.toRadians(deg);
                    com.akashic.kunftig.blade.EffectProjectile sw = fx.projectile(30 + i);
                    if (sw != null) {
                        sw.move(
                            new com.akashic.kunftig.blade.EffectWire.Move(
                                30 + i,
                                mc.thePlayer.posX - Math.sin(r) * 2,
                                mc.thePlayer.boundingBox.minY + 0.81,
                                mc.thePlayer.posZ + Math.cos(r) * 2,
                                0,
                                0,
                                0,
                                -deg,
                                0));
                    }
                }
            };
        }));
        steps.add(new Step("sbfx-heavy-rain", 6, (mc, p) -> {
            everyTick = null;
            view(mc, 0);
            place(p, 180, -35);
            for (int i = 0; i < 8; i++) {
                fx.remove(30 + i, false);
            }
            java.util.Random rnd = new java.util.Random(5);
            for (int i = 0; i < 19; i++) {
                double ox = i == 0 ? 0 : rnd.nextDouble() * 5 - 2.5;
                double oz = i == 0 ? 0 : rnd.nextDouble() * 5 - 2.5;
                fx.add(
                    new com.akashic.kunftig.blade.EffectWire.Projectile(
                        fxNs,
                        "espada",
                        40 + i,
                        com.akashic.kunftig.blade.EffectWire.KIND_SWORD,
                        target.posX + ox,
                        target.posY + 7,
                        target.posZ + oz,
                        0,
                        0,
                        0,
                        0,
                        -90,
                        0,
                        color,
                        0));
            }
        }));
        steps.add(new Step("sbfx-heavy-rain-fall", 3, (mc, p) -> {
            for (int i = 0; i < 19; i++) {
                com.akashic.kunftig.blade.EffectProjectile sw = fx.projectile(40 + i);
                if (sw != null) {
                    sw.move(
                        new com.akashic.kunftig.blade.EffectWire.Move(
                            40 + i,
                            sw.pos[0],
                            sw.pos[1],
                            sw.pos[2],
                            0,
                            -4,
                            0,
                            0,
                            -90));
                }
            }
        }));
        // lock-on: Sneak held with a target; the view turns toward it from 50 degrees away
        steps.add(new Step("sbfx-lock-t01", 30, (mc, p) -> {
            for (int i = 0; i < 19; i++) {
                fx.remove(40 + i, false);
            }
            place(p, 180 + 50, 0);
            view(mc, 0);
            mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = 230;
            hud.lock(new com.akashic.kunftig.blade.EffectWire.Lock(fxNs, target.getEntityId(), color));
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
        }));
        steps.add(new Step("sbfx-lock-t10", 9, (mc, p) -> report("lock yaw=" + mc.thePlayer.rotationYaw)));
        steps.add(new Step("sbfx-lock-t40", 30, (mc, p) -> report("lock yaw=" + mc.thePlayer.rotationYaw)));
        steps.add(new Step("sbfx-lock-back", 5, (mc, p) -> {
            report("lock yaw=" + mc.thePlayer.rotationYaw);
            view(mc, 1);
        }));
        steps.add(new Step("sbfx-lock-hurt", 5, (mc, p) -> {
            target.setHealth(3);
            view(mc, 0);
        }));
        // the rank HUD: a rise to D (text variant), then SSS, then SSS after the text second
        steps.add(new Step("sbfx-rank-d-rise", 3, (mc, p) -> {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            hud.unlock();
            hud.rank(new com.akashic.kunftig.blade.EffectWire.Rank(fxNs, 450));
            hud.hits(new com.akashic.kunftig.blade.EffectWire.Hits(fxNs, 12));
        }));
        steps.add(new Step("sbfx-rank-d", 25, (mc, p) -> {}));
        steps.add(new Step("sbfx-rank-sss-rise", 3, (mc, p) -> {
            hud.rank(new com.akashic.kunftig.blade.EffectWire.Rank(fxNs, 1700));
            hud.hits(new com.akashic.kunftig.blade.EffectWire.Hits(fxNs, 57));
        }));
        steps.add(new Step("sbfx-rank-sss", 25, (mc, p) -> {}));
        steps.add(new Step("sbfx-rank-gone", 100, (mc, p) -> {}));
    }

    /** SlashBlade's Drive.doSlash for the harness: at the feet + eyes*0.75 + look*0.3, along the look. */
    private void drive(com.akashic.kunftig.client.blade.BladeEffects fx, EntityPlayerMP p, int id, float roll,
        double speed, int color, float lifetime) {
        double yaw = Math.toRadians(p.rotationYaw);
        double lx = -Math.sin(yaw);
        double lz = Math.cos(yaw);
        double vx = lx * speed;
        double vz = lz * speed;
        fx.add(
            new com.akashic.kunftig.blade.EffectWire.Projectile(
                fxNs,
                "onda",
                id,
                com.akashic.kunftig.blade.EffectWire.KIND_DRIVE,
                p.posX + lx * 0.3,
                p.posY + 1.62 * 0.75,
                p.posZ + lz * 0.3,
                vx,
                0,
                vz,
                (float) Math.toDegrees(Math.atan2(vx, vz)),
                0,
                roll,
                color,
                lifetime));
    }

    /** SlashBlade's doSlash for the harness: at the feet + eyes*0.75 + look*0.3, the player's yaw, pitch 0, blue. */
    private void slash(EntityPlayerMP p, float roll, float rank) {
        double yaw = Math.toRadians(p.rotationYaw);
        double pitch = Math.toRadians(p.rotationPitch);
        double lx = -Math.sin(yaw) * Math.cos(pitch);
        double ly = -Math.sin(pitch);
        double lz = Math.cos(yaw) * Math.cos(pitch);
        com.akashic.kunftig.client.blade.BladeEffects.INSTANCE.add(
            new com.akashic.kunftig.blade.EffectWire.Slash(
                fxNs,
                p.posX + lx * 0.3,
                p.posY + 1.62 * 0.75 + ly * 0.3,
                p.posZ + lz * 0.3,
                p.rotationYaw,
                0,
                roll,
                0,
                0x3333FF,
                rank,
                1,
                10));
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
