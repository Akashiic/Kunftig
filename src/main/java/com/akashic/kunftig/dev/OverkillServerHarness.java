package com.akashic.kunftig.dev;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;

import com.akashic.kunftig.Kunftig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development-only play test of the Overkill plugin on a real server (never in the release jar): when the run
 * directory's {@code kunftig-rod-harness} file says {@code overkill <host> <port>}, the client joins, gets a katana and
 * the forge's materials (commands, as op), locks on a zombie with Sneak, plays a combo with right clicks, charges and
 * releases a slash art, kills a few zombies, and then uses the forge (shift-clicks the katana, a stone and a talisman
 * in, presses the button, takes the katana out, repairs it). Chat lines, the katana's name and lore, the zombies'
 * health and every step go to {@code kunftig-rod-harness.txt}, with screenshots ({@code overkill-*.png}).
 */
public final class OverkillServerHarness {

    private final String host;
    private final int port;
    private PrintWriter out;
    private int ticks;
    private int step;
    private int wait;
    private int menuTicks;
    private boolean joined;
    private String shot;
    private boolean quitting;
    private boolean done;
    private EntityLivingBase target;

    private OverkillServerHarness(String host, int port) {
        this.host = host;
        this.port = port;
    }

    static void install(String host, int port) {
        OverkillServerHarness h = new OverkillServerHarness(host, port);
        FMLCommonHandler.instance()
            .bus()
            .register(h);
        MinecraftForge.EVENT_BUS.register(h);
        Kunftig.LOG.warn("Kunftig DEV OVERKILL HARNESS enabled: {}:{}", host, port);
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        report("chat: " + e.message.getUnformattedText());
    }

    // ---- the script ----------------------------------------------------------------------------------------------

    /** One step per call; returns the ticks to wait before the next. Negative ends. */
    private int run(Minecraft mc, int s) {
        String me = mc.thePlayer.getCommandSenderName();
        int x = 0;
        int z = 0;
        switch (s) {
            case 0:
                return cmd(mc, "/gamerule doDaylightCycle false");
            case 1:
                return cmd(mc, "/time set 6000");
            case 2:
                return cmd(mc, "/gamerule doMobSpawning false");
            case 3:
                return cmd(mc, "/effect " + me + " 11 3600 4");
            case 4:
                return cmd(mc, "/effect " + me + " 10 3600 4");
            case 5:
                return cmd(mc, "/xp 200L " + me);
            case 6:
                return cmd(mc, "/tp " + me + " " + (x + 0.5) + " 4 " + (z + 0.5));
            case 7:
                mc.thePlayer.rotationYaw = 0;
                mc.thePlayer.rotationPitch = 10;
                return cmd(mc, "/clear " + me);
            case 8:
                return cmd(mc, "/overkill dar katana/kusanagi " + me);
            case 9:
                return cmd(mc, "/overkill pedra mitica 3 " + me);
            case 10:
                return cmd(mc, "/overkill ofuda 100 1 " + me);
            case 11:
                return cmd(mc, "/overkill pedra tamahagane 1 " + me);
            case 12:
                mc.thePlayer.inventory.currentItem = 0;
                report("hand: " + describe(mc.thePlayer.getHeldItem()));
                return 40;
            case 13:
                report("hand after the star: " + describe(mc.thePlayer.getHeldItem()));
                shoot("01-empunhada");
                return 20;
            case 14:
                return cmd(mc, zombie(0, 3));
            case 15:
                look(mc);
                return 20;
            case 16:
                // lock-on: hold Sneak
                key(mc.gameSettings.keyBindSneak, true);
                return 20;
            case 17:
                shoot("02-lock-on");
                report("target: " + health());
                return 5;
            case 18:
            case 20:
            case 22:
            case 24:
                click(mc.gameSettings.keyBindUseItem);
                report("right click (combo) " + health());
                return 3;
            case 19:
            case 21:
            case 23:
            case 25:
                shoot("03-combo-" + (s - 17) / 2);
                return 5;
            case 26:
                report("after the combo: " + health());
                return 30;
            case 27:
                // the slash art: hold the use button 12 ticks (just), then release
                key(mc.gameSettings.keyBindSneak, false);
                key(mc.gameSettings.keyBindUseItem, true);
                return 12;
            case 28:
                shoot("04-carregada");
                key(mc.gameSettings.keyBindUseItem, false);
                report("released the charge " + health());
                return 4;
            case 29:
                shoot("05-slash-art");
                return 6;
            case 30:
                shoot("06-slash-art-2");
                return 30;
            case 31:
                report("after the slash art: " + health());
                return 5;
            case 32:
            case 33:
            case 34:
                // three more zombies, killed with combos
                return cmd(mc, zombie(s - 33, 3));
            case 35:
                look(mc);
                mc.gameSettings.thirdPersonView = 1;
                return 10;
            default:
                break;
        }
        if (s >= 36 && s < 96) {
            // a click every 4 ticks for 12 seconds; looking at the nearest zombie
            look(mc);
            click(mc.gameSettings.keyBindUseItem);
            if (s == 40) {
                shoot("07-terceira-pessoa");
            }
            if (s % 10 == 0) {
                report("fighting: " + health());
            }
            return 4;
        }
        switch (s) {
            case 96:
                mc.gameSettings.thirdPersonView = 0;
                return 40;
            case 97:
                report("hand after the fight: " + describe(mc.thePlayer.getHeldItem()));
                report("inventory: " + inventory(mc));
                return cmd(mc, "/forja");
            case 98:
                return 30;
            case 99: {
                Container c = mc.thePlayer.openContainer;
                report(
                    "open container: " + c.getClass()
                        .getSimpleName() + " slots " + c.inventorySlots.size());
                if (c.inventorySlots.size() < 63) {
                    report("the forge did not open");
                    return -1;
                }
                shoot("08-forja-vazia");
                return 10;
            }
            case 100:
                // the katana from the hotbar (container slot 54) to the katana slot: shift-click
                shift(mc, 54);
                return 10;
            case 101:
                shift(mc, findStack(mc, "Pedra de Afiar"));
                return 10;
            case 102:
                shift(mc, findStack(mc, "Ofuda"));
                return 15;
            case 103:
                report("forge: " + forge(mc));
                shoot("09-forja-pronta");
                return 5;
            case 104:
                windowClick(mc, 22, 0, 0);
                report("pressed APRIMORAR");
                return 12;
            case 105:
                shoot("10-forja-sorteio");
                return 20;
            case 106:
                shoot("11-forja-resultado");
                report("forge: " + forge(mc));
                return 40;
            case 107:
                report("output: " + describe(slotStack(mc, 16)));
                shift(mc, 16);
                return 10;
            case 108:
                // again: back in, repair, out
                shift(mc, findKatana(mc));
                return 10;
            case 109:
                windowClick(mc, 18, 0, 0);
                report("pressed Reparar");
                return 15;
            case 110:
                report("forge after the repair: " + forge(mc));
                shoot("12-forja-reparo");
                shift(mc, 10);
                return 10;
            case 111:
                mc.thePlayer.closeScreen();
                return 20;
            case 112:
                report("inventory at the end: " + inventory(mc));
                for (int i = 0; i < 9; i++) {
                    ItemStack st = mc.thePlayer.inventory.getStackInSlot(i);
                    if (st != null && st.getItem() == net.minecraft.init.Items.golden_sword) {
                        mc.thePlayer.inventory.currentItem = i;
                    }
                }
                return 20;
            case 113:
                report("katana at the end: " + describe(mc.thePlayer.getHeldItem()));
                shoot("13-fim");
                return 20;
            default:
                return -1;
        }
    }

    /** A zombie relative to the player (the chat takes 100 characters), with a helmet against the sun. */
    private String zombie(int dx, int dz) {
        return "/summon Zombie ~" + dx + " ~ ~" + dz + " {Equipment:[{},{},{},{},{id:310}]}";
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private int cmd(Minecraft mc, String c) {
        report("> " + c);
        mc.thePlayer.sendChatMessage(c);
        return 15;
    }

    private static void key(KeyBinding k, boolean down) {
        KeyBinding.setKeyBindState(k.getKeyCode(), down);
    }

    private static void click(KeyBinding k) {
        KeyBinding.onTick(k.getKeyCode());
    }

    /** Faces the nearest zombie (the lock-on camera does the rest while Sneak is held). */
    private void look(Minecraft mc) {
        double best = 1e9;
        target = null;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof EntityZombie && ((EntityZombie) o).getHealth() > 0) {
                double d = mc.thePlayer.getDistanceSqToEntity((Entity) o);
                if (d < best) {
                    best = d;
                    target = (EntityLivingBase) o;
                }
            }
        }
        if (target == null) {
            return;
        }
        double dx = target.posX - mc.thePlayer.posX;
        double dz = target.posZ - mc.thePlayer.posZ;
        double dy = target.posY + target.getEyeHeight() * 0.6 - (mc.thePlayer.posY);
        mc.thePlayer.rotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        mc.thePlayer.rotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    private String health() {
        if (target == null) {
            Minecraft mc = Minecraft.getMinecraft();
            int n = 0;
            for (Object o : mc.theWorld.loadedEntityList) {
                if (o instanceof EntityZombie) {
                    n++;
                }
            }
            return "(zombies around: " + n + ")";
        }
        return String.format(
            java.util.Locale.ROOT,
            "(zombie %d: %.1f/%.1f%s)",
            target.getEntityId(),
            target.getHealth(),
            target.getMaxHealth(),
            target.isDead ? " dead" : "");
    }

    private static String describe(ItemStack st) {
        if (st == null) {
            return "nothing";
        }
        StringBuilder b = new StringBuilder(st.getDisplayName()).append(" x")
            .append(st.stackSize);
        NBTTagCompound tag = st.getTagCompound();
        if (tag != null && tag.hasKey("display")) {
            NBTTagList lore = tag.getCompoundTag("display")
                .getTagList("Lore", 8);
            for (int i = 0; i < lore.tagCount(); i++) {
                b.append(" | ")
                    .append(lore.getStringTagAt(i));
            }
        }
        return b.toString();
    }

    private static String inventory(Minecraft mc) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.thePlayer.inventory.getStackInSlot(i);
            if (st != null) {
                b.append('[')
                    .append(i)
                    .append("] ")
                    .append(st.getDisplayName())
                    .append(" x")
                    .append(st.stackSize)
                    .append("; ");
            }
        }
        return b.toString();
    }

    private static ItemStack slotStack(Minecraft mc, int slot) {
        Container c = mc.thePlayer.openContainer;
        return slot < c.inventorySlots.size() ? ((Slot) c.inventorySlots.get(slot)).getStack() : null;
    }

    private static String forge(Minecraft mc) {
        StringBuilder b = new StringBuilder();
        for (int slot : new int[] { 1, 4, 10, 13, 16, 18, 22, 26 }) {
            ItemStack st = slotStack(mc, slot);
            b.append("\n    [")
                .append(slot)
                .append("] ")
                .append(describe(st));
        }
        return b.toString();
    }

    private static int findStack(Minecraft mc, String name) {
        List<?> slots = mc.thePlayer.openContainer.inventorySlots;
        for (int i = 27; i < slots.size(); i++) {
            ItemStack st = ((Slot) slots.get(i)).getStack();
            if (st != null && st.getDisplayName()
                .contains(name)) {
                return i;
            }
        }
        return -1;
    }

    private static int findKatana(Minecraft mc) {
        List<?> slots = mc.thePlayer.openContainer.inventorySlots;
        for (int i = 27; i < slots.size(); i++) {
            ItemStack st = ((Slot) slots.get(i)).getStack();
            if (st != null && st.getItem() == net.minecraft.init.Items.golden_sword) {
                return i;
            }
        }
        return -1;
    }

    private void shift(Minecraft mc, int slot) {
        if (slot < 0) {
            report("shift-click: nothing to click");
            return;
        }
        windowClick(mc, slot, 0, 1);
        report("shift-click slot " + slot);
    }

    private static void windowClick(Minecraft mc, int slot, int button, int mode) {
        mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, slot, button, mode, mc.thePlayer);
    }

    private void shoot(String name) {
        shot = name;
    }

    // ---- ticks -----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;
        if (!joined) {
            if (mc.theWorld == null
                && (mc.currentScreen instanceof GuiMainMenu || mc.currentScreen != null && ++menuTicks > 60)) {
                mc.gameSettings.pauseOnLostFocus = false;
                mc.mouseHelper = new net.minecraft.util.MouseHelper() {

                    @Override
                    public void mouseXYChange() {
                        deltaX = 0;
                        deltaY = 0;
                    }
                };
                report("connecting to " + host + ":" + port);
                cpw.mods.fml.client.FMLClientHandler.instance()
                    .setupServerList();
                cpw.mods.fml.client.FMLClientHandler.instance()
                    .connectToServer(
                        mc.currentScreen,
                        new net.minecraft.client.multiplayer.ServerData("teste", host + ":" + port));
                joined = true;
                wait = 200;
            }
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            if (ticks > 20 * 600) {
                report("could not join / disconnected at step " + step);
                finish();
            }
            return;
        }
        // every screen but the forge's chest goes away (Xvfb brings the pause menu back)
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiContainer)
            && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
            mc.displayGuiScreen(null);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        int next;
        try {
            next = run(mc, step++);
        } catch (RuntimeException e) {
            report("step " + (step - 1) + " failed: " + e);
            next = -1;
        }
        if (next < 0) {
            finish();
        } else {
            wait = next;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (shot != null && mc.theWorld != null) {
            ScreenShotHelper.saveScreenshot(
                mc.mcDataDir,
                "overkill-" + shot + ".png",
                mc.displayWidth,
                mc.displayHeight,
                mc.getFramebuffer());
            report("screenshot " + shot);
            shot = null;
        }
        if (quitting) {
            quitting = false;
            mc.shutdown();
        }
    }

    private void finish() {
        report("done");
        if (out != null) {
            out.close();
            out = null;
        }
        done = true;
        quitting = true;
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
            return;
        }
        out.println("[" + ticks + "] " + line);
        out.flush();
    }
}
