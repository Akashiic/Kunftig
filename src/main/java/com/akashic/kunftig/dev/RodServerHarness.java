package com.akashic.kunftig.dev;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.ScreenShotHelper;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.rodmodel.RodModels;
import com.akashic.kunftig.rodmodel.RodModelIndex;
import com.akashic.kunftig.rodmodel.RodTimeline;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development-only check of the 3D rods against a real server with a fishing plugin (never in the release jar): when
 * the run directory's {@code kunftig-rod-harness} file says {@code server <host> <port>}, the client connects, asks
 * the server for a rod (commands, as op), casts into the water and plays two fights with the states the server sends:
 * one won (Shift on the bite, Shift again), one left to escape. Every state and tension the client receives goes to
 * {@code kunftig-rod-harness.txt}, with screenshots of the bite, the fight and the catch from the front.
 */
public final class RodServerHarness {

    private static final int JOIN_TICKS = 100;
    private static final int BITE_TIMEOUT = 20 * 120;

    private enum Phase {
        MENU,
        JOINING,
        SETUP,
        CAST_WIN,
        WAIT_BITE_WIN,
        FIGHT_WIN,
        AFTER_WIN,
        CAST_ESCAPE,
        WAIT_BITE_ESCAPE,
        ESCAPING,
        OBSERVE,
        DONE
    }

    /** How long the observer stays beside the angler, logging every title it is shown. */
    private static final int OBSERVE_TICKS = 20 * 240;

    private final String host;
    private final int port;
    /** The observer: stands beside the angler and logs every title its own screen gets (a title leaking from him). */
    private final boolean observer;
    private String lastTitle;
    private Phase phase = Phase.MENU;
    private int ticks;
    private int menuTicks;
    private int phaseTicks;
    private int setupStep;
    private String lastState;
    private float lastTension = -1;
    private PrintWriter out;
    private String shot;
    private boolean quitting;

    private final int x;

    private RodServerHarness(String host, int port, boolean observer, int x) {
        this.host = host;
        this.port = port;
        this.observer = observer;
        this.x = x;
    }

    /** Called by {@link RodRenderHarness#install()} when the trigger file asks for the server mode. */
    static void install(String host, int port) {
        install(host, port, false);
    }

    /** {@code observer}: the trigger file says {@code observer <host> <port>}. */
    static void install(String host, int port, boolean observer) {
        install(host, port, observer, 0);
    }

    /** {@code x}: the angler stands at that x ({@code server <host> <port> <x>}), for two anglers side by side. */
    static void install(String host, int port, boolean observer, int x) {
        FMLCommonHandler.instance()
            .bus()
            .register(new RodServerHarness(host, port, observer, x));
        Kunftig.LOG.warn("Kunftig DEV ROD SERVER HARNESS enabled: connecting to {}:{}, then quitting", host, port);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == Phase.DONE) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;
        if (phase == Phase.MENU) {
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
                // FML's own way in (its handshake waits on a latch that only connectToServer sets up)
                cpw.mods.fml.client.FMLClientHandler.instance()
                    .setupServerList(); // what the multiplayer screen does first
                cpw.mods.fml.client.FMLClientHandler.instance()
                    .connectToServer(
                        mc.currentScreen,
                        new net.minecraft.client.multiplayer.ServerData("teste", host + ":" + port));
                go(Phase.JOINING);
            }
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            if (phase != Phase.JOINING && phase != Phase.MENU) {
                report("disconnected in " + phase);
                finish(mc);
            } else if (++phaseTicks > 20 * 600) {
                report("could not join");
                finish(mc);
            }
            return;
        }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
            mc.displayGuiScreen(null);
        }
        if (mc.thePlayer.getHealth() <= 0) {
            if (phase == Phase.JOINING || phase == Phase.SETUP) {
                if (phaseTicks % 40 == 0) {
                    report("joined dead (saved from an earlier run): respawning");
                    mc.thePlayer.respawnPlayer();
                }
                phaseTicks++;
                return;
            }
            report("the player died in " + phase);
            finish(mc);
            return;
        }
        watchState(mc);
        watchTitle();
        phaseTicks++;
        switch (phase) {
            case JOINING:
                if (phaseTicks >= JOIN_TICKS) {
                    report("joined; rods: " + RodModels.INSTANCE.status());
                    go(Phase.SETUP);
                }
                break;
            case SETUP:
                if (observer) {
                    observerSetup(mc);
                } else {
                    setup(mc);
                }
                break;
            case OBSERVE:
                if (phaseTicks % (20 * 30) == 0) {
                    report("observing (" + phaseTicks / 20 + " s); players here: " + mc.theWorld.playerEntities.size());
                }
                if (phaseTicks >= OBSERVE_TICKS) {
                    finish(mc);
                }
                break;
            case CAST_WIN:
            case CAST_ESCAPE:
                if (phaseTicks == 20) {
                    if (mc.thePlayer.getHeldItem() == null) {
                        report("nothing in the hand to cast");
                        finish(mc);
                        break;
                    }
                    report("cast (" + (phase == Phase.CAST_WIN ? "to win" : "to let it escape") + ")");
                    mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
                }
                if (phaseTicks == 60) {
                    report("line out: " + (mc.thePlayer.fishEntity != null));
                    go(phase == Phase.CAST_WIN ? Phase.WAIT_BITE_WIN : Phase.WAIT_BITE_ESCAPE);
                }
                break;
            case WAIT_BITE_WIN:
            case WAIT_BITE_ESCAPE:
                if ("mordida".equals(lastState)) {
                    view(mc, 2);
                    shoot("bite" + (phase == Phase.WAIT_BITE_WIN ? "-win" : "-escape"));
                    go(phase == Phase.WAIT_BITE_WIN ? Phase.FIGHT_WIN : Phase.ESCAPING);
                } else if (phaseTicks > BITE_TIMEOUT) {
                    report("no bite in " + BITE_TIMEOUT / 20 + " s");
                    finish(mc);
                }
                break;
            case FIGHT_WIN:
                // Shift on the bite starts the fight; Shift again hits (the test config makes the zone the whole bar).
                if (phaseTicks == 10 || phaseTicks == 50) {
                    sneak(mc, true);
                    report("Shift down");
                }
                if (phaseTicks == 13 || phaseTicks == 53) {
                    sneak(mc, false);
                }
                if (phaseTicks == 40) {
                    shoot("fight");
                }
                if ("captura".equals(lastState)) {
                    shoot("catch");
                    go(Phase.AFTER_WIN);
                } else if (phaseTicks > 20 * 30) {
                    report("no catch");
                    go(Phase.AFTER_WIN);
                }
                break;
            case AFTER_WIN:
                if (phaseTicks == 50) {
                    shoot("after-catch");
                }
                if (phaseTicks >= 80) {
                    view(mc, 0);
                    go(Phase.CAST_ESCAPE);
                }
                break;
            case ESCAPING:
                // No Shift: the fish gets away after the time to start; the rod should come back to rest.
                if (phaseTicks == 30) {
                    shoot("escaping");
                }
                if (phaseTicks == 20 * 8) {
                    shoot("escaped");
                    report("line out after the escape: " + (mc.thePlayer.fishEntity != null));
                    finish(mc);
                }
                break;
            default:
                break;
        }
    }

    private void setup(Minecraft mc) {
        if (phaseTicks % 30 != 0) {
            return;
        }
        String me = mc.thePlayer.getCommandSenderName();
        // Water breathing first (the flat world's surface is water), then a stone to stand on once the chunk is loaded.
        String[] commands = { "/time set 6000", "/gamerule doDaylightCycle false", "/effect " + me + " 13 3600 0",
            "/tp " + me + " " + (x + 0.5) + " 6 0.5 0 35", "/setblock " + x + " 5 0 stone",
            "/tp " + me + " " + (x + 0.5) + " 6 0.5 0 35", "/af vara vara-solar 1 ouro" };
        if (setupStep < commands.length) {
            report("> " + commands[setupStep]);
            mc.thePlayer.sendChatMessage(commands[setupStep++]);
            return;
        }
        mc.thePlayer.inventory.currentItem = 0;
        for (int slot = 0; slot < 9; slot++) {
            RodModelIndex.Resolved r = RodModels.INSTANCE.resolve(mc.thePlayer.inventory.getStackInSlot(slot));
            if (r != null) {
                mc.thePlayer.inventory.currentItem = slot;
                report("rod in slot " + slot + ": " + r.model.id + " quality " + r.quality + " stage " + r.stage);
                go(Phase.CAST_WIN);
                return;
            }
        }
        if (phaseTicks > 30 * (commands.length + 6)) {
            report("no 3D rod in the hotbar");
            finish(mc);
        }
    }

    /** The observer: water breathing, a stone two blocks east of the angler's, looking at him. */
    private void observerSetup(Minecraft mc) {
        if (phaseTicks % 30 != 0) {
            return;
        }
        String me = mc.thePlayer.getCommandSenderName();
        String[] commands = { "/effect " + me + " 13 3600 0", "/tp " + me + " 2.5 6 0.5 90 20", "/setblock 2 5 0 stone",
            "/tp " + me + " 2.5 6 0.5 90 20" };
        if (setupStep < commands.length) {
            report("> " + commands[setupStep]);
            mc.thePlayer.sendChatMessage(commands[setupStep++]);
            return;
        }
        report("observing beside the angler");
        go(Phase.OBSERVE);
    }

    /** Every title NecroTempus puts on this screen (read from its client manager), when it changes. */
    private void watchTitle() {
        String text;
        try {
            Class<?> manager = Class
                .forName("io.github.cruciblemc.necrotempus.modules.features.title.client.ClientTitleManager");
            Object title = manager.getMethod("getCurrentTitle")
                .invoke(null);
            text = title == null ? null
                : String.valueOf(
                    title.getClass()
                        .getMethod("toNbt")
                        .invoke(title));
        } catch (ReflectiveOperationException | LinkageError e) {
            text = "? " + e;
        }
        if (text == null ? lastTitle != null : !text.equals(lastTitle)) {
            report("title " + (text == null ? "(none)" : text.length() > 300 ? text.substring(0, 300) + "..." : text));
            lastTitle = text;
        }
    }

    /** Every state change of the local player's rod (from the server or the client) and the tension in the fight. */
    private void watchState(Minecraft mc) {
        RodTimeline t = RodModels.INSTANCE.active() ? RodModels.INSTANCE.timeline(mc.thePlayer) : null;
        String state = t == null ? null : t.state();
        if (state != null && !state.equals(lastState)) {
            report("state " + state + " (line " + (mc.thePlayer.fishEntity != null) + ")");
            lastState = state;
            lastTension = -1;
        }
        if (t != null && "briga".equals(state) && Math.abs(t.tension() - lastTension) >= 0.05F) {
            lastTension = t.tension();
            report(String.format(java.util.Locale.ROOT, "  tension %.2f", lastTension));
        }
    }

    private static void sneak(Minecraft mc, boolean down) {
        mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, down ? 1 : 2));
    }

    private static void view(Minecraft mc, int mode) {
        mc.gameSettings.thirdPersonView = mode;
    }

    private void shoot(String name) {
        shot = name;
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
                "rods-server-" + shot + ".png",
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

    private void go(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private void finish(Minecraft mc) {
        report("done");
        if (out != null) {
            out.close();
        }
        phase = Phase.DONE;
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
