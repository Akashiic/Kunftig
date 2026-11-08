package com.akashic.kunftig.dev;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
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

    /** One step: does its thing, returns the ticks to wait before the next. */
    private interface Act {

        int run(Minecraft mc);
    }

    private final List<Act> script = new ArrayList<>();

    private int run(Minecraft mc, int s) {
        if (script.isEmpty()) {
            build();
        }
        return s < script.size() ? script.get(s)
            .run(mc) : -1;
    }

    private void add(Act a) {
        script.add(a);
    }

    private void c(String command) {
        add(mc -> cmd(mc, command.replace("@me", mc.thePlayer.getCommandSenderName())));
    }

    private void w(int ticks) {
        add(mc -> ticks);
    }

    private void shot(String name) {
        add(mc -> {
            shoot(name);
            return 1;
        });
    }

    private void note(String what) {
        add(mc -> {
            report("-- " + what + " " + health());
            return 0;
        });
    }

    /** A right click (a combo click), then the server's combo state (/overkill info answers in the chat). */
    private void click(int wait) {
        add(mc -> {
            look(mc);
            click(mc.gameSettings.keyBindUseItem);
            return 1;
        });
        add(mc -> {
            mc.thePlayer.sendChatMessage("/overkill info");
            return Math.max(0, wait - 1);
        });
    }

    private void keys(boolean down, String... which) {
        add(mc -> {
            for (String k : which) {
                KeyBinding b = k.equals("sneak") ? mc.gameSettings.keyBindSneak
                    : k.equals("back") ? mc.gameSettings.keyBindBack
                        : k.equals("forward") ? mc.gameSettings.keyBindForward : mc.gameSettings.keyBindJump;
                key(b, down);
            }
            return 0;
        });
    }

    private void jump() {
        keys(true, "jump");
        w(2);
        keys(false, "jump");
    }

    private void slot(int n) {
        add(mc -> {
            mc.thePlayer.inventory.currentItem = n;
            return 30;
        });
        add(mc -> {
            report("hand: " + describe(mc.thePlayer.getHeldItem()));
            return 0;
        });
    }

    /** A sturdy target that stays put until hit and does not burn: a zombie pigman with 200 health. */
    private void target() {
        c("/summon PigZombie ~0 ~ ~3 {Health:200,Attributes:[{Name:generic.maxHealth,Base:200}]}");
        w(20);
        add(mc -> {
            look(mc);
            return 5;
        });
    }

    private void view(int mode) {
        add(mc -> {
            mc.gameSettings.thirdPersonView = mode;
            return 0;
        });
    }

    private void build() {
        c("/gamerule doDaylightCycle false");
        c("/time set 6000");
        c("/gamerule doMobSpawning false");
        c("/effect @me 11 3600 4");
        c("/effect @me 10 3600 4");
        c("/xp 300L @me");
        c("/tp @me 0.5 4 0.5");
        add(mc -> {
            mc.thePlayer.rotationYaw = 0;
            mc.thePlayer.rotationPitch = 10;
            return 0;
        });
        c("/clear @me");
        c("/overkill dar katana/kusanagi @me");
        c("/overkill dar katana/serpente @me");
        c("/overkill dar katana/sakurajima @me");
        c("/overkill dar katana/raijin @me");
        c("/overkill pedra mitica 3 @me");
        c("/overkill ofuda 100 1 @me");
        slot(0);
        shot("01-empunhada");
        target();

        // lock-on: hold Sneak
        keys(true, "sneak");
        w(15);
        shot("02-lock-on");
        keys(false, "sneak");
        w(10);
        view(1);

        note("combo A");
        click(4);
        click(4);
        shot("03-combo-a3");
        click(7);
        click(6);
        shot("04-combo-a4");
        w(40);

        note("combo C (click at the end of A2)");
        click(4);
        click(13);
        click(5);
        shot("05-combo-c");
        w(40);

        note("combo B (click at the end of A3)");
        click(4);
        click(4);
        click(14);
        click(9);
        shot("06-combo-b1");
        for (int i = 0; i < 6; i++) {
            click(5);
            if (i == 2 || i == 5) {
                shot("07-combo-b" + (i + 2));
            }
        }
        w(40);

        note("upperslash (Sneak + back + click)");
        keys(true, "sneak", "back");
        w(2);
        click(1);
        keys(false, "sneak", "back");
        w(4);
        shot("08-upperslash");
        w(40);

        note("rapid slash (Sneak + forward + click)");
        keys(true, "sneak", "forward");
        w(2);
        click(1);
        keys(false, "sneak", "forward");
        w(4);
        shot("09-rapid-slash");
        w(40);

        note("aerial rave A (jump + clicks)");
        jump();
        w(2);
        click(4);
        click(4);
        shot("10-aerial-a2");
        click(5);
        shot("11-aerial-a3");
        w(40);

        note("aerial rave B (click at the end of A2 in the air)");
        jump();
        w(2);
        click(4);
        click(12);
        click(9);
        shot("12-aerial-b3");
        click(5);
        shot("13-aerial-b4");
        w(40);

        note("aerial cleave (in the air: Sneak + back + click)");
        jump();
        w(4);
        keys(true, "sneak", "back");
        click(1);
        keys(false, "sneak", "back");
        w(3);
        shot("14-aerial-cleave");
        w(12);
        shot("15-aerial-cleave-landing");
        w(40);
        shot("16-rank");

        String[] arts = { "julgamento", "onda", "corte-circular", "fio-da-tempestade" };
        for (int k = 0; k < 4; k++) {
            int n = k;
            note("slash art of slot " + n);
            slot(n);
            add(mc -> {
                look(mc);
                key(mc.gameSettings.keyBindUseItem, true);
                return 12;
            });
            add(mc -> {
                key(mc.gameSettings.keyBindUseItem, false);
                mc.thePlayer.sendChatMessage("/overkill info");
                return 3;
            });
            shot("17-art-" + n + "-" + arts[n]);
            w(8);
            shot("18-art-" + n + "-" + arts[n] + "-b");
            w(40);
        }
        note("after the arts");
        view(0);
        add(mc -> {
            report("inventory: " + inventory(mc));
            mc.thePlayer.inventory.currentItem = 0;
            return 20;
        });
        add(mc -> {
            report("katana at the end: " + describe(mc.thePlayer.getHeldItem()));
            return 0;
        });
        c("/forja");
        w(20);
        add(mc -> {
            if (mc.thePlayer.openContainer.inventorySlots.size() < 63) {
                report("the forge did not open");
                return -1;
            }
            shift(mc, 54);
            return 8;
        });
        add(mc -> {
            shift(mc, findStack(mc, "Pedra de Afiar"));
            return 8;
        });
        add(mc -> {
            windowClick(mc, 22, 0, 0);
            report("pressed APRIMORAR");
            return 12;
        });
        shot("19-forja-sorteio");
        w(50);
        add(mc -> {
            report("forge: " + forge(mc));
            shoot("20-forja-resultado");
            return 5;
        });
        add(mc -> {
            shift(mc, 16);
            mc.thePlayer.closeScreen();
            return 20;
        });
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
            if (o instanceof EntityMob && ((EntityMob) o).getHealth() > 0) {
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
                if (o instanceof EntityMob) {
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
