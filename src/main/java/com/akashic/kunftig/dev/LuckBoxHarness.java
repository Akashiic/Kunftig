package com.akashic.kunftig.dev;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.display.DisplayClient;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development-only check of the displays (never in the release jar): with {@code caixa <host> <port>} in the run
 * directory's {@code kunftig-rod-harness} file, the client connects to a server with AkashicFishing, stands on a
 * stone over the water, asks for test luck boxes ({@code /af caixa}) and saves screenshots along each animation to
 * {@code screenshots/caixa-*.png}, with a report in {@code kunftig-rod-harness.txt}, then quits.
 */
public final class LuckBoxHarness {

    private static final String[][] BOXES = { { "epico", "1" }, { "lendario", "3" } };
    /** Screenshot ticks after the command (the box spawns about one tick later). */
    private static final int[] SHOTS = { 6, 14, 18, 24, 40, 62, 76, 82, 86, 92, 104, 124, 136, 142, 150 };

    private final String host;
    private final int port;
    private int phase; // 0 menu, 1 joining, 2 setup, 3 boxes, 4 done
    private int ticks;
    private int phaseTicks;
    private int menuTicks;
    private int step;
    private int box;
    private int boxTicks;
    private String shot;
    private boolean quitting;
    private PrintWriter out;

    private LuckBoxHarness(String host, int port) {
        this.host = host;
        this.port = port;
    }

    static void install(String host, int port) {
        FMLCommonHandler.instance()
            .bus()
            .register(new LuckBoxHarness(host, port));
        Kunftig.LOG.warn("Kunftig DEV LUCK BOX HARNESS enabled: connecting to {}:{}, then quitting", host, port);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == 4) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;
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
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
            mc.displayGuiScreen(null);
        }
        if (mc.thePlayer.getHealth() <= 0 && phaseTicks % 40 == 0) {
            mc.thePlayer.respawnPlayer();
        }
        phaseTicks++;
        if (phase == 1 && phaseTicks >= 100) {
            report(
                "joined; displays: " + DisplayClient.INSTANCE.index()
                    .models()
                    .keySet() + ", drawn: " + DisplayClient.INSTANCE.installed());
            phase = 2;
            phaseTicks = 0;
        } else if (phase == 2 && phaseTicks % 30 == 0) {
            String me = mc.thePlayer.getCommandSenderName();
            String[] commands = { "/time set 6000", "/gamerule doDaylightCycle false", "/effect " + me + " 13 3600 0",
                "/tp " + me + " 0.5 6 0.5", "/setblock 0 5 0 stone", "/setblock 0 5 -6 stone", "/setblock 0 3 3 water",
                "/setblock -1 3 3 water", "/setblock 1 3 3 water", "/setblock 0 3 4 water", "/setblock 0 3 2 water",
                "/tp " + me + " 0.5 6 0.5" };
            if (step < commands.length) {
                report("> " + commands[step]);
                mc.thePlayer.sendChatMessage(commands[step++]);
            } else {
                phase = 3;
                phaseTicks = 0;
                boxTicks = -40;
            }
        } else if (phase == 3) {
            mc.thePlayer.rotationYaw = 0;
            mc.thePlayer.rotationPitch = boxTicks < 2 ? -10 : -6;
            boxTicks++;
            // the box comes out of the pond 3 blocks ahead (z 3.5); then step back to see all of it
            if (boxTicks == 3) {
                mc.thePlayer.sendChatMessage("/tp " + mc.thePlayer.getCommandSenderName() + " 0.5 6 -5.5");
            } else if (boxTicks == -10) {
                mc.thePlayer.sendChatMessage("/tp " + mc.thePlayer.getCommandSenderName() + " 0.5 6 0.5");
            }
            if (boxTicks == 0) {
                String cmd = "/af caixa " + BOXES[box][0] + " " + BOXES[box][1];
                report("> " + cmd);
                mc.thePlayer.sendChatMessage(cmd);
            }
            for (int s : SHOTS) {
                if (boxTicks == s) {
                    shot = "caixa-" + BOXES[box][0] + "-" + String.format("%03d", s);
                }
            }
            if (boxTicks == 10) {
                report("displays on this client after the command: " + DisplayClient.INSTANCE.count());
            }
            if (boxTicks > 175) {
                report("box " + BOXES[box][0] + " done; displays left: " + DisplayClient.INSTANCE.count());
                box++;
                boxTicks = -20;
                if (box == BOXES.length) {
                    finish();
                }
            }
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
        phase = 4;
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
        Kunftig.LOG.info("LUCK BOX HARNESS: {}", line);
    }
}
