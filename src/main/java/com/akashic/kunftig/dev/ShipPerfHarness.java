package com.akashic.kunftig.dev;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.display.DisplayClient;
import com.akashic.kunftig.display.DisplayWire;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development-only measurement of what a big display costs per frame (never in the release jar): with
 * {@code navio <model>} in the {@code kunftig-rod-harness} file (default {@code expeditions:fragata}; the pack that
 * declares it has to be among the client's resource packs), the client opens a flat world, shows the model as the
 * server would (the same wire messages, handed to {@link DisplayClient#onPayload}) and, for each case of
 * {@link #CASES}, reports the frames, the mean frame time and the mean time spent drawing the displays, and saves a
 * screenshot ({@code screenshots/navio-<case>.png}). Run it once with {@code -Dkunftig.displayMesh=false} and once
 * without, and compare the reports and the pictures.
 */
public final class ShipPerfHarness {

    private static final String WORLD = "kunftig-navio-perf";
    private static final int WARMUP_TICKS = 100;
    private static final int CASE_TICKS = 200;
    /** Where the first ship floats and how far apart the others are. */
    private static final double SHIP_Y = 6;
    private static final double SHIP_Z = 46;

    /**
     * {name, ships, player x, y, z, yaw, pitch, whether the rudder and the first door keep turning}. The ships lie
     * broadside to the player, one behind the other.
     */
    private static final Object[][] CASES = { { "aquecimento", 1, 0.5, 14.0, 0.5, 0f, 12f, false },
        { "um-de-traves", 1, 0.5, 14.0, 0.5, 0f, 12f, false }, { "tres", 3, 0.5, 30.0, -14.5, 0f, 22f, false },
        { "popa", 1, 34.5, 14.0, 30.5, 62f, 8f, false }, { "leme-e-porta", 1, 34.5, 14.0, 30.5, 62f, 8f, true },
        { "de-re", 1, 42.5, 14.2, 46.5, 90f, 4f, false }, { "nenhum", 0, 0.5, 14.0, 0.5, 0f, 12f, false } };

    private final String model;
    private boolean launched;
    private int menuTicks;
    private int index = -1;
    private int ticks;
    private boolean done;
    private PrintWriter out;
    private int shown;
    private long startNanos;
    private long lastFrame;
    private final List<Long> frames = new ArrayList<>();
    private long startAlloc;

    private ShipPerfHarness(String model) {
        this.model = model;
    }

    static void install(String model) {
        FMLCommonHandler.instance()
            .bus()
            .register(new ShipPerfHarness(model));
        Kunftig.LOG.warn("Kunftig DEV SHIP PERF HARNESS enabled: this client will measure {} and quit", model);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        if (!launched && mc.theWorld == null
            && (mc.currentScreen instanceof GuiMainMenu || mc.currentScreen != null && ++menuTicks > 60)) {
            launched = true;
            mc.gameSettings.pauseOnLostFocus = false;
            mc.gameSettings.limitFramerate = 260; // unlimited: the frame time is what is measured
            mc.gameSettings.enableVsync = false;
            mc.gameSettings.renderDistanceChunks = 6;
            mc.mouseHelper = new net.minecraft.util.MouseHelper() {

                @Override
                public void mouseXYChange() {
                    deltaX = 0;
                    deltaY = 0;
                }
            };
            mc.launchIntegratedServer(
                WORLD,
                WORLD,
                new WorldSettings(1L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
        mc.gameSettings.pauseOnLostFocus = false;
        mc.gameSettings.hideGUI = true;
        MinecraftServer server = MinecraftServer.getServer();
        EntityPlayerMP p = server == null ? null
            : server.getConfigurationManager()
                .func_152612_a(mc.thePlayer.getCommandSenderName());
        if (p == null) {
            return;
        }
        if (index < 0) {
            p.getServerForPlayer()
                .setWorldTime(6000);
            report(
                "java " + System.getProperty("java.version")
                    + ", meshes "
                    + (!"false".equalsIgnoreCase(System.getProperty("kunftig.displayMesh")) ? "on" : "off")
                    + ", model "
                    + model
                    + ", declared: "
                    + DisplayClient.INSTANCE.index()
                        .models()
                        .containsKey(model)
                    + ", drawn: "
                    + DisplayClient.INSTANCE.installed());
            begin(mc, p, 0);
            return;
        }
        Object[] c = CASES[index];
        // keep the player where the case wants it (creative flight, no drifting)
        p.capabilities.isFlying = true;
        mc.thePlayer.capabilities.isFlying = true;
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        if ((Boolean) c[7] && ticks % 30 == 0) {
            float a = ticks / 30 % 2 == 0 ? 32 : -32;
            DisplayClient.INSTANCE.onPayload(DisplayWire.pose(1, "rudder", 0, a, 0, 30));
            DisplayClient.INSTANCE.onPayload(DisplayWire.pose(1, "leme", 0, a, 0, 30));
            DisplayClient.INSTANCE.onPayload(DisplayWire.pose(1, "porta_camara_be", 0, a < 0 ? -92 : 0, 0, 30));
        }
        int length = index == 0 ? WARMUP_TICKS : CASE_TICKS;
        ticks++;
        if (ticks == length - 2) {
            finishCase(mc);
        }
        if (ticks == length - 1) {
            ScreenShotHelper.saveScreenshot(
                mc.mcDataDir,
                "navio-" + c[0] + ".png",
                mc.displayWidth,
                mc.displayHeight,
                mc.getFramebuffer());
        }
        if (ticks >= length) {
            if (index + 1 >= CASES.length) {
                done = true;
                report("done");
                out.close();
                mc.shutdown();
                return;
            }
            begin(mc, p, index + 1);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || index < 0 || done) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrame != 0) {
            frames.add(now - lastFrame);
        }
        lastFrame = now;
    }

    private void begin(Minecraft mc, EntityPlayerMP p, int next) {
        index = next;
        ticks = 0;
        Object[] c = CASES[next];
        int ships = (Integer) c[1];
        while (shown < ships) {
            shown++;
            DisplayClient.INSTANCE.onPayload(
                DisplayWire.show(
                    shown,
                    0.5,
                    SHIP_Y,
                    SHIP_Z + (shown - 1) * 26,
                    90,
                    model,
                    "",
                    "parado",
                    0,
                    Collections.<byte[]>emptyList()));
        }
        while (shown > ships) {
            DisplayClient.INSTANCE.onPayload(DisplayWire.hide(shown));
            shown--;
        }
        p.capabilities.allowFlying = true;
        p.capabilities.isFlying = true;
        p.sendPlayerAbilities();
        p.playerNetServerHandler
            .setPlayerLocation((Double) c[2], (Double) c[3], (Double) c[4], (Float) c[5], (Float) c[6]);
        System.gc();
        frames.clear();
        lastFrame = 0;
        startNanos = System.nanoTime();
        startAlloc = allocated();
        DisplayClient.INSTANCE.resetDrawCost();
    }

    private void finishCase(Minecraft mc) {
        Object[] c = CASES[index];
        long elapsed = System.nanoTime() - startNanos;
        long[] f = new long[frames.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = frames.get(i);
        }
        Arrays.sort(f);
        int n = f.length;
        long[] cost = DisplayClient.INSTANCE.drawCost();
        report(
            String.format(
                java.util.Locale.ROOT,
                "%-14s ships %d  frames %5d  frame mean %7.2f ms  median %7.2f ms  displays %7.3f ms/frame  alloc %7.0f KiB/frame",
                c[0],
                (Integer) c[1],
                n,
                n == 0 ? 0 : elapsed / 1e6 / n,
                n == 0 ? 0 : f[n / 2] / 1e6,
                cost[1] == 0 ? 0 : cost[0] / 1e6 / cost[1],
                n == 0 ? 0 : (allocated() - startAlloc) / 1024.0 / n));
    }

    private static long allocated() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean) {
            return ((com.sun.management.ThreadMXBean) bean).getThreadAllocatedBytes(
                Thread.currentThread()
                    .getId());
        }
        return 0;
    }

    private void report(String line) {
        try {
            if (out == null) {
                out = new PrintWriter(
                    new File(Minecraft.getMinecraft().mcDataDir, "kunftig-navio-perf.txt"),
                    StandardCharsets.UTF_8.name());
            }
            out.println(line);
            out.flush();
        } catch (IOException e) {
            Kunftig.LOG.warn("ship perf harness: {}", e.toString());
        }
        Kunftig.LOG.info("[navio] {}", line);
    }
}
