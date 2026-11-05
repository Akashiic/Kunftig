package com.akashic.kunftig.dev;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.rodmodel.RodModels;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development-only measurement of what a 3D rod costs per frame (never in the release jar): with {@code perf} in the
 * {@code kunftig-rod-harness} file, the client opens a flat world and holds each rod of {@link #CASES} for a while, in
 * first and third person, and reports for each: frames, mean and worst frame time, the client thread's allocations
 * per frame and the collector's runs and time. Run it with a Java Flight Recorder recording for the hot methods.
 */
public final class RodPerfHarness {

    private static final String WORLD = "kunftig-rods-perf";
    private static final int WARMUP_TICKS = 200;
    private static final int CASE_TICKS = 400;

    /** {name, item model, view (0 first person, 2 third person front), state or null}. */
    private static final String[][] CASES = { { "warmup", "akashicfishing:vara/vara-solar", "0", null },
        { "vanilla-fp", null, "0", null }, { "solar-fp", "akashicfishing:vara/vara-solar", "0", null },
        { "akashica-m6-fp", "akashicfishing:vara/vara-akashica.iridio.m6", "0", null },
        { "akashica-m6-fp-briga", "akashicfishing:vara/vara-akashica.iridio.m6", "0", "briga" },
        { "akashica-m0-fp", "akashicfishing:vara/vara-akashica.iridio", "0", null },
        { "solar-tp", "akashicfishing:vara/vara-solar", "2", null },
        { "akashica-m6-tp", "akashicfishing:vara/vara-akashica.iridio.m6", "2", null },
        { "vanilla-tp", null, "2", null } };

    private boolean launched;
    private boolean copyMeasured;
    /** -Dkunftig.rodPerf.copyOnly=true: only the animation-copy timings, then quit. */
    private static final boolean COPY_ONLY = Boolean.getBoolean("kunftig.rodPerf.copyOnly");
    private int menuTicks;
    private int index = -1;
    private int ticks;
    private boolean done;
    private PrintWriter out;

    // per case
    private long startNanos;
    private long lastFrame;
    private final List<Long> frames = new ArrayList<>();
    private long startAlloc;
    private long startGcCount;
    private long startGcMillis;

    static void install() {
        FMLCommonHandler.instance()
            .bus()
            .register(new RodPerfHarness());
        Kunftig.LOG.warn("Kunftig DEV ROD PERF HARNESS enabled: this client will measure the 3D rods and quit");
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
        mc.ingameGUI.getChatGUI()
            .clearChatMessages();
        MinecraftServer server = MinecraftServer.getServer();
        EntityPlayerMP p = server == null ? null
            : server.getConfigurationManager()
                .func_152612_a(mc.thePlayer.getCommandSenderName());
        if (p == null) {
            return;
        }
        if (index < 0 && !copyMeasured) {
            copyMeasured = true;
            measureCopies(mc);
            if (COPY_ONLY) {
                done = true;
                report("done");
                out.close();
                mc.shutdown();
                return;
            }
        }
        if (index < 0) {
            p.getServerForPlayer()
                .setWorldTime(6000);
            p.playerNetServerHandler.setPlayerLocation(0.5, 4, 0.5, 180, 10);
            report("java " + System.getProperty("java.version") + ", rods: " + RodModels.INSTANCE.status());
            begin(mc, p, 0);
            return;
        }
        int length = index == 0 ? WARMUP_TICKS : CASE_TICKS;
        String state = CASES[index][3];
        if (state != null && ticks % 40 == 0 && RodModels.INSTANCE.active()) {
            RodModels.INSTANCE.forceLocalState(state, 0.5F + 0.5F * (float) Math.sin(ticks / 10.0));
        }
        if (++ticks >= length) {
            finishCase();
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
        String[] c = CASES[next];
        ItemStack stack = c[1] == null ? new ItemStack(net.minecraft.init.Items.fishing_rod)
            : RodRenderHarness.rod(c[1]);
        p.inventory.setInventorySlotContents(p.inventory.currentItem, stack);
        p.inventoryContainer.detectAndSendChanges();
        mc.thePlayer.inventory.setInventorySlotContents(mc.thePlayer.inventory.currentItem, stack.copy());
        mc.gameSettings.thirdPersonView = Integer.parseInt(c[2]);
        if (RodModels.INSTANCE.active()) {
            RodModels.INSTANCE.forceLocalState("parada", 0);
        }
        System.gc();
        frames.clear();
        lastFrame = 0;
        startNanos = System.nanoTime();
        startAlloc = allocated();
        long[] gc = gc();
        startGcCount = gc[0];
        startGcMillis = gc[1];
    }

    private void finishCase() {
        String[] c = CASES[index];
        long elapsed = System.nanoTime() - startNanos;
        long alloc = allocated() - startAlloc;
        long[] gc = gc();
        long[] f = new long[frames.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = frames.get(i);
        }
        Arrays.sort(f);
        int n = f.length;
        double mean = n == 0 ? 0 : elapsed / 1e6 / n;
        report(
            String.format(
                Locale.ROOT,
                "%-22s frames %5d  fps %6.1f  mean %6.2f ms  p50 %6.2f  p99 %7.2f  worst %7.2f ms  alloc %7.1f KB/frame"
                    + "  gc %3d runs %5d ms",
                c[0],
                n,
                n / (elapsed / 1e9),
                mean,
                n == 0 ? 0 : f[n / 2] / 1e6,
                n == 0 ? 0 : f[Math.min(n - 1, (int) (n * 0.99))] / 1e6,
                n == 0 ? 0 : f[n - 1] / 1e6,
                n == 0 ? 0 : alloc / 1024.0 / n,
                gc[0] - startGcCount,
                gc[1] - startGcMillis));
    }

    /**
     * GeckoLib 1.0.4 copies an animation ({@code Animation.copy}: Java serialization, Base64 and back) every time a
     * controller (re)starts it, which its unofficial port does at every turn of a looping animation. How long that copy
     * takes for the rods' animations, against a plain field copy.
     */
    private void measureCopies(Minecraft mc) {
        String[] rods = { "vara-akashica", "vara-solar", "vara-de-coral" };
        String[] clips = { "aura", "aura_briga", "parada", "briga" };
        for (String rod : rods) {
            net.minecraft.util.ResourceLocation file = new net.minecraft.util.ResourceLocation(
                "akashicfishing",
                "animations/vara/" + rod + ".animation.json");
            software.bernie.geckolib3.file.AnimationFile anims;
            try {
                anims = software.bernie.geckolib3.file.AnimationFileLoader.getInstance()
                    .loadAllAnimations(
                        software.bernie.geckolib3.molang.MolangRegistrar.getParser(),
                        file,
                        mc.getResourceManager());
            } catch (RuntimeException e) {
                report("copy " + rod + ": cannot load (" + e + ")");
                continue;
            }
            for (String clip : clips) {
                software.bernie.geckolib3.core.builder.Animation a = anims
                    .getAnimation("animation." + rod + "." + clip);
                if (a == null) {
                    continue;
                }
                int keys = 0;
                for (software.bernie.geckolib3.core.keyframe.BoneAnimation b : a.boneAnimations) {
                    keys += b.rotationKeyFrames.xKeyFrames.size() + b.positionKeyFrames.xKeyFrames.size()
                        + b.scaleKeyFrames.xKeyFrames.size();
                }
                for (int i = 0; i < 3; i++) {
                    software.bernie.geckolib3.core.builder.Animation.copy(a);
                }
                long before = allocated();
                long t0 = System.nanoTime();
                int n = 10;
                for (int i = 0; i < n; i++) {
                    software.bernie.geckolib3.core.builder.Animation.copy(a);
                }
                double ms = (System.nanoTime() - t0) / 1e6 / n;
                double mb = (allocated() - before) / 1048576.0 / n;
                long s0 = System.nanoTime();
                for (int i = 0; i < 1000; i++) {
                    shallow(a);
                }
                double shallowUs = (System.nanoTime() - s0) / 1e3 / 1000;
                report(
                    String.format(
                        Locale.ROOT,
                        "copy %-22s %-11s %5d keyframes (x)  Animation.copy %7.2f ms  %6.2f MB   shallow %6.3f us",
                        rod,
                        clip,
                        keys,
                        ms,
                        mb,
                        shallowUs));
            }
        }
    }

    private static software.bernie.geckolib3.core.builder.Animation shallow(
        software.bernie.geckolib3.core.builder.Animation a) {
        software.bernie.geckolib3.core.builder.Animation c = new software.bernie.geckolib3.core.builder.Animation();
        c.animationName = a.animationName;
        c.animationLength = a.animationLength;
        c.loop = a.loop;
        c.boneAnimations = a.boneAnimations;
        c.soundKeyFrames = a.soundKeyFrames;
        c.particleKeyFrames = a.particleKeyFrames;
        c.customInstructionKeyframes = a.customInstructionKeyframes;
        return c;
    }

    /** Bytes the client thread allocated so far (HotSpot), or 0. */
    private static long allocated() {
        java.lang.management.ThreadMXBean t = ManagementFactory.getThreadMXBean();
        if (t instanceof com.sun.management.ThreadMXBean) {
            return ((com.sun.management.ThreadMXBean) t).getThreadAllocatedBytes(
                Thread.currentThread()
                    .getId());
        }
        return 0;
    }

    private static long[] gc() {
        long count = 0;
        long millis = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, b.getCollectionCount());
            millis += Math.max(0, b.getCollectionTime());
        }
        return new long[] { count, millis };
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
        out.println(line);
        out.flush();
    }
}
