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
import net.minecraft.util.ScreenShotHelper;

import com.akashic.kunftig.Kunftig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development-only check of AkashicFishing's skins (never in the release jar): with {@code skins <host> <port>} in the
 * run directory's {@code kunftig-rod-harness} file, the client connects, takes an old Sanrio rod (converted by the
 * plugin into a skin), opens the skin screen, applies, moves and takes off skins by clicking its slots, redeems a skin
 * item, and saves screenshots ({@code screenshots/skins-*.png}) with a report in {@code kunftig-rod-harness.txt}.
 */
public final class SkinHarness {

    /** One step: wait {@code delay} ticks, then do it. */
    private static final class Step {

        final int delay;
        final String kind;
        final String arg;

        Step(int delay, String kind, String arg) {
            this.delay = delay;
            this.kind = kind;
            this.arg = arg;
        }

        int delay() {
            return delay;
        }

        String kind() {
            return kind;
        }

        String arg() {
            return arg;
        }
    }

    private final String host;
    private final int port;
    private final List<Step> steps = new ArrayList<>();
    private int phase; // 0 menu, 1 joining, 2 script, 3 done
    private int phaseTicks;
    private int menuTicks;
    private int index;
    private int wait;
    private String shot;
    private boolean quitting;
    private PrintWriter out;

    private SkinHarness(String host, int port) {
        this.host = host;
        this.port = port;
        cmd(40, "/time set 6000");
        cmd(20, "/gamerule doDaylightCycle false");
        cmd(20, "/weather clear");
        cmd(20, "/clear Akashic");
        add(10, "hotbar", "0");
        cmd(20, "/af vara vara-hello-kitty 30 ouro +10");
        add(60, "shot", "skins-01-convertida");
        cmd(10, "/af skin liberar Akashic kuromi");
        cmd(30, "/skins");
        add(40, "shot", "skins-02-tela");
        add(5, "click", "11");
        add(40, "shot", "skins-03-kuromi-aplicada");
        add(5, "close", "");
        add(40, "shot", "skins-04-mao-kuromi");
        cmd(10, "/af vara vara-akashica 50 iridio asc");
        add(20, "hotbar", "1");
        cmd(30, "/skins");
        add(40, "shot", "skins-05-tela-akashica");
        add(5, "click", "11");
        add(40, "shot", "skins-06-kuromi-movida");
        add(5, "click", "8");
        add(40, "shot", "skins-07-desvinculada");
        add(5, "click", "10");
        add(40, "shot", "skins-08-kitty-akashica");
        add(5, "close", "");
        add(40, "shot", "skins-09-mao-kitty");
        add(5, "hotbar", "0");
        add(40, "shot", "skins-10-primeira-vara");
        cmd(10, "/af skin dar Akashic cinnamoroll");
        add(20, "hotbar", "2");
        add(20, "use", "");
        add(40, "shot", "skins-11-item");
        add(5, "hotbar", "1");
        cmd(20, "/equipamento");
        add(40, "shot", "skins-12-equipamento");
        add(5, "click", "8");
        add(40, "shot", "skins-13-do-equipamento");
        add(5, "close", "");
        cmd(20, "/skins");
        add(40, "shot", "skins-14-tela-tres");
        add(5, "close", "");
        cmd(20, "/af skin ver Akashic");
        add(40, "shot", "skins-15-ver");
        add(20, "done", "");
    }

    private void cmd(int delay, String command) {
        steps.add(new Step(delay, "chat", command));
    }

    private void add(int delay, String kind, String arg) {
        steps.add(new Step(delay, kind, arg));
    }

    static void install(String host, int port) {
        FMLCommonHandler.instance()
            .bus()
            .register(new SkinHarness(host, port));
        Kunftig.LOG.warn("Kunftig DEV SKIN HARNESS enabled: connecting to {}:{}, then quitting", host, port);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == 3) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (phase == 0) {
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
                phase = 1;
                phaseTicks = 0;
            }
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            if (++phaseTicks > 20 * 600) {
                report("could not join");
                finish();
            }
            return;
        }
        if (phase == 1) {
            if (++phaseTicks >= 100) {
                report("joined");
                phase = 2;
                wait = 0;
            }
            return;
        }
        mc.thePlayer.rotationPitch = 10;
        if (index >= steps.size()) {
            finish();
            return;
        }
        Step s = steps.get(index);
        if (++wait < s.delay()) {
            return;
        }
        wait = 0;
        index++;
        report(s.kind() + " " + s.arg());
        switch (s.kind()) {
            case "chat" -> mc.thePlayer.sendChatMessage(s.arg());
            case "hotbar" -> mc.thePlayer.inventory.currentItem = Integer.parseInt(s.arg());
            case "click" -> {
                if (mc.thePlayer.openContainer != null
                    && mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
                    mc.playerController.windowClick(
                        mc.thePlayer.openContainer.windowId,
                        Integer.parseInt(s.arg()),
                        0,
                        0,
                        mc.thePlayer);
                } else {
                    report("no screen open to click");
                }
            }
            case "close" -> mc.thePlayer.closeScreen();
            case "use" -> {
                if (mc.thePlayer.getHeldItem() != null) {
                    mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
                }
            }
            case "shot" -> {
                shot = s.arg();
                report(
                    "held: " + (mc.thePlayer.getHeldItem() == null ? "nothing"
                        : mc.thePlayer.getHeldItem()
                            .getDisplayName()));
            }
            case "done" -> finish();
            default -> report("unknown step " + s.kind());
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (shot != null && mc.theWorld != null) {
            ScreenShotHelper
                .saveScreenshot(mc.mcDataDir, shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
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
        }
        phase = 3;
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
            out.println(line);
            out.flush();
        } catch (IOException e) {
            Kunftig.LOG.warn("skin harness: could not write the report", e);
        }
        Kunftig.LOG.info("SKIN HARNESS: {}", line);
    }
}
