package com.akashic.kunftig.dev;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ScreenShotHelper;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.display.DisplayClient;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development-only check of AkashicFishing's lake boss (never in the release jar): with {@code boss <host> <port>}
 * in the run directory's {@code kunftig-rod-harness} file, the client connects to a flat test world with
 * AkashicFishing and the throwaway {@code TesteLago} plugin ({@code /lago}, {@code /voar}), digs a lake, places the
 * boss with the admin commands, runs the preview and the simulations (jumping the Ciranda's tentacles when they come),
 * then a real fight: tries to fly (as a mod would) with and without the flight lock, and fishes the weak points for the
 * strike bar. Screenshots go to {@code screenshots/boss-*.png} and the report to {@code kunftig-rod-harness.txt}.
 */
public final class BossHarness {

    private static final String LEVIATHAN = "akashicfishing:leviata_do_abismo";
    private static final String TENTACLE = "akashicfishing:tentaculo_do_abismo";
    /** Where the player stands: on the band, north of the lake's centre, facing it. */
    private double BAND_Z = -34.5;
    /** orbe_e / orbe_d / cauda_nucleo in the "ocioso" state's first frame (boss-trilhas.txt), blocks at scale 1. */
    private static final double[][] POINTS = { { 3.00, 17.46, 6.76 }, { -3.00, 17.46, 6.76 }, { -1.37, 8.57, -18.13 } };

    private final String host;
    private final int port;
    private int stage; // 0 menu, 1 joining, then the steps
    private int ticks;
    private int stageTicks;
    private int menuTicks;
    private int step;
    private boolean sawBoss;
    private String shot;
    private boolean quitting;
    private boolean done;
    private PrintWriter out;
    // the jump-rope
    private int jumps;
    private int jumpHold;
    private final java.util.Map<Integer, Double> lastAngle = new java.util.HashMap<>();
    // flight attempts
    private boolean flying;
    private double flyStartY;
    private double flyMaxY;
    private int flyTicks;
    // strikes
    private int casts;
    private int castTicks = -1;
    private int hooks;
    private int target;

    private final String only;

    private BossHarness(String host, int port, String only) {
        this.host = host;
        this.port = port;
        this.only = only;
        this.big = only.equals("grande");
        this.radius = big ? 40 : 30;
        this.BAND_Z = -(radius + 4.5);
    }

    /** "grande": a lake of radius 40 and the size on auto (the biggest boss the lake and the rods allow). */
    private final boolean big;
    private final int radius;

    static void install(String host, int port, String only) {
        FMLCommonHandler.instance()
            .bus()
            .register(new BossHarness(host, port, only));
        Kunftig.LOG.warn("Kunftig DEV BOSS HARNESS enabled: connecting to {}:{}, then quitting", host, port);
    }

    private interface Step {

        /** One tick of a step; true when it is over. */
        boolean tick(Minecraft mc, int t);
    }

    private final List<Step> steps = new ArrayList<>();

    private void plan() {
        String me = Minecraft.getMinecraft().thePlayer.getCommandSenderName();
        commands(
            "/time set 6000",
            "/gamerule doDaylightCycle false",
            "/gamerule keepInventory false",
            "/effect " + me + " 16 3600 0",
            "/lago " + radius + " " + (radius + 40),
            "/tp " + me + " 0.5 4 0.5",
            "/af boss local definir",
            "/af boss local raio " + radius,
            "/af boss local margem 10",
            "/tp " + me + " 0.5 5 " + BAND_Z,
            "/af boss local virar aqui",
            "/af boss tamanho " + (big ? "auto" : "1"),
            "/af boss checar",
            "/af boss status",
            "/give " + me + " 346 1");
        if (only.equals("anim")) {
            // only the animations: which state the controller plays and where a few bones are
            steps.add((mc, t) -> {
                look(mc, 0, -14);
                if (t == 0) {
                    sawBoss = false;
                    say("/af boss simular rugido 1");
                }
                if (t == 120) {
                    say("/af boss simular cauda 1");
                }
                if (t == 260) {
                    say("/af boss simular mordida 1");
                }
                if (t % 5 == 0) {
                    report("anim t=" + t + " " + animState());
                }
                shotAt(t, 50, "boss-anim-rugir");
                shotAt(t, 190, "boss-anim-cauda");
                shotAt(t, 340, "boss-anim-morder");
                return t >= 420;
            });
            return;
        }
        if (only.equals("fotos")) {
            // the three phases' textures, at the roar's peak, from the band and closer to the water
            for (int f = 1; f <= 3; f++) {
                final int phase = f;
                steps.add((mc, t) -> {
                    look(mc, 0, phase == 3 ? -24 : -14);
                    if (t == 0) {
                        say("/tp " + mc.thePlayer.getCommandSenderName() + " 0.5 5 " + (phase == 3 ? -31.5 : BAND_Z));
                    }
                    if (t == 10) {
                        say("/af boss simular rugido " + phase);
                    }
                    shotAt(t, 25, "boss-fase" + phase + "-ocioso");
                    shotAt(t, 62, "boss-fase" + phase + "-rugido");
                    return t > 200 && DisplayClient.INSTANCE.count() == 0;
                });
            }
            return;
        }
        if (big) {
            steps.add((mc, t) -> {
                look(mc, 0, -24);
                if (t == 0) {
                    say("/af boss previa 12");
                }
                if (t == 30) {
                    report("displays after /af boss previa: " + describeDisplays());
                }
                shotAt(t, 40, "boss-grande-faixa");
                if (t == 60) {
                    look(mc, 0, -38);
                }
                shotAt(t, 70, "boss-grande-faixa-alto");
                if (t == 90) {
                    say("/tp " + mc.thePlayer.getCommandSenderName() + " 0.5 5 " + (-(radius + 36.5)));
                }
                shotAt(t, 130, "boss-grande-longe");
                if (t == 150) {
                    say("/tp " + mc.thePlayer.getCommandSenderName() + " 0.5 5 " + BAND_Z);
                }
                return t > 280 && DisplayClient.INSTANCE.count() == 0;
            });
            fight();
            return;
        }
        if (only.equals("luta")) {
            fight();
            return;
        }
        if (only.equals("atencao")) {
            attention();
            return;
        }
        if (only.equals("mordida")) {
            bite();
            return;
        }
        if (only.equals("toca")) {
            lair(false);
            lair(true);
            return;
        }
        // the preview: from the band, then from farther away
        steps.add((mc, t) -> {
            look(mc, 0, -18);
            if (t == 0) {
                say("/af boss previa 14");
            }
            if (t == 30) {
                report("displays after /af boss previa: " + describeDisplays());
            }
            shotAt(t, 40, "boss-previa-faixa");
            if (t == 70) {
                say("/tp " + mc.thePlayer.getCommandSenderName() + " 0.5 5 -62.5");
            }
            shotAt(t, 100, "boss-previa-longe");
            if (t == 110) {
                say("/tp " + mc.thePlayer.getCommandSenderName() + " 0.5 5 " + BAND_Z);
            }
            shotAt(t, 140, "boss-previa-faixa2");
            return t > 320 && DisplayClient.INSTANCE.count() == 0;
        });
        // the Ciranda alone, phase 1, jumping the tentacles
        steps.add(simulation("ciranda 1", "boss-ciranda", 15));
        // every attack, phase 3
        steps.add(simulation("tudo 3", "boss-tudo", 30));
        // flight without the lock (the boss is gone): it should work
        steps.add(flight("sem-trava", false));
        fight();
    }

    /**
     * The attention (the boss turns to whoever it watches), the boss bars (one on screen per fight, none after) and the
     * flight after the fight (free again).
     */
    private void attention() {
        // survival (the boss ignores creative players: staff watching), with resistance V so its attacks do not kill
        commands("/gamemode 0 Developer", "/effect Developer 11 3600 4", "/effect Developer 23 3600 4");
        steps.add((mc, t) -> {
            look(mc, 0, -14);
            if (t == 0) {
                sawBoss = false;
                say("/af boss invocar");
                report("bars before: " + bossBars());
            }
            return t >= 160;
        });
        report("attention: four places around the lake");
        double ring = radius + 4.5;
        for (double a : new double[] { 270, 0, 90, 180, 270 }) {
            final double angle = a;
            final String name = "boss-atencao-" + (int) a;
            steps.add((mc, t) -> {
                double x = 0.5 + Math.cos(Math.toRadians(angle)) * ring;
                double z = 0.5 + Math.sin(Math.toRadians(angle)) * ring;
                if (t == 0) {
                    say(
                        String.format(
                            java.util.Locale.ROOT,
                            "/tp %s %.1f 5 %.1f",
                            mc.thePlayer.getCommandSenderName(),
                            x,
                            z));
                }
                // look at the lake's centre
                look(mc, (float) Math.toDegrees(Math.atan2(-(0.5 - x), 0.5 - z)), -12);
                if (t % 20 == 0) {
                    DisplayClient.Entry boss = null;
                    for (DisplayClient.Entry e : entries()) {
                        if (LEVIATHAN.equals(e.model)) {
                            boss = e;
                        }
                    }
                    if (boss != null) {
                        double want = angle - 90;
                        double got = boss.motion.yaw(1f);
                        double off = ((got - want) % 360 + 540) % 360 - 180;
                        report(
                            String.format(
                                java.util.Locale.ROOT,
                                "attention at %.0f° t=%d: boss yaw %.1f, facing me at %.1f (off by %.1f°), state %s",
                                angle,
                                t,
                                got,
                                want,
                                off,
                                boss.state));
                    }
                }
                shotAt(t, 150, name);
                return t >= 160;
            });
        }
        steps.add((mc, t) -> {
            if (t == 0) {
                report("bars during the fight: " + bossBars());
                say("/af boss encerrar");
            }
            if (t == 60) {
                report("bars after /af boss encerrar: " + bossBars());
                say("/af boss invocar");
            }
            if (t == 140) {
                report("bars in the second fight: " + bossBars());
                say("/af boss encerrar");
            }
            if (t == 200) {
                report("bars after the second /af boss encerrar: " + bossBars());
            }
            return t >= 220;
        });
        steps.add(flight("depois-da-luta", false));
    }

    /**
     * Effects cleared when coming into a fight, then the bite that catches, holds in the mouth and swallows (seen from
     * behind, third person, to see the player between the jaws).
     */
    private void bite() {
        commands("/gamemode 0 Developer", "/effect Developer 1 600 1", "/effect Developer 5 600 1");
        steps.add((mc, t) -> {
            look(mc, 0, -14);
            if (t == 0) {
                report(
                    "effects before the fight: " + mc.thePlayer.getActivePotionEffects()
                        .size());
                say("/af boss invocar");
            }
            if (t == 60) {
                report(
                    "effects after coming into the fight: " + mc.thePlayer.getActivePotionEffects()
                        .size() + ", health " + mc.thePlayer.getHealth());
                say("/af boss encerrar");
            }
            return t >= 100;
        });
        steps.add((mc, t) -> {
            look(mc, 0, -10);
            if (t == 0) {
                mc.gameSettings.thirdPersonView = 1;
                say("/af boss simular mordida 2 dano");
            }
            if (t % 5 == 0 && t > 40 && t < 200) {
                report(
                    String.format(
                        java.util.Locale.ROOT,
                        "bite t=%d: me at %.1f %.1f %.1f, health %.1f, dead %s",
                        t,
                        mc.thePlayer.posX,
                        mc.thePlayer.posY,
                        mc.thePlayer.posZ,
                        mc.thePlayer.getHealth(),
                        mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0));
            }
            for (int at : new int[] { 48, 62, 76, 95, 108, 118, 124 }) {
                shotAt(t, at, "boss-mordida-" + at);
            }
            if (t == 130 && mc.thePlayer.getHealth() <= 0) {
                mc.thePlayer.respawnPlayer();
            }
            if (t == 170) {
                mc.gameSettings.thirdPersonView = 0;
            }
            return t >= 220;
        });
    }

    /** The lights in the lair pose (tools/boss3d trilhas, "espreitar" first frame): blocks at size 1, yaw 0. */
    private static final double[][] LURK_LIGHTS = { { -0.77, 5.07, 8.56 }, { 0.77, 5.07, 8.56 },
        { -2.81, 8.05, -18.83 } };

    private DisplayClient.Entry leviathan() {
        for (DisplayClient.Entry e : entries()) {
            if (LEVIATHAN.equals(e.model)) {
                return e;
            }
        }
        return null;
    }

    /** A point of the model (local blocks at size 1) in the world, as the plugin's BossTracks.toWorld. */
    private static double[] world(DisplayClient.Entry b, double px, double py, double pz) {
        double s = b.scale[0];
        double r = Math.toRadians(-b.motion.yaw(1f));
        return new double[] { b.motion.x(1f) + px * s * Math.cos(r) + pz * s * Math.sin(r), b.motion.y(1f) + py * s,
            b.motion.z(1f) - px * s * Math.sin(r) + pz * s * Math.cos(r) };
    }

    private int lightIndex;
    private int lairIssued = -1;
    private int punches;
    private int dodgedAt = -1;
    private double waterY = Double.NaN;

    /**
     * The lair: either staying in front of the mouth (sucked in and, when it rises, swallowed), or punching the lights
     * out (dodging the suction behind it) until it opens its mouth and rises stunned.
     */
    private void lair(boolean fight) {
        String name = fight ? "luzes" : "parado";
        steps.add((mc, t) -> {
            if (t == 0) {
                mc.gameSettings.thirdPersonView = fight ? 0 : 1;
                say("/gamemode 0 Developer");
                lightIndex = 0;
                lairIssued = -1;
                punches = 0;
                dodgedAt = -1;
                waterY = Double.NaN;
            }
            if (t == 20) {
                say("/effect Developer 13 3600 0");
            }
            if (t == 30) {
                say("/tp " + mc.thePlayer.getCommandSenderName() + " 0.5 5 " + BAND_Z); // back from the respawn
            }
            DisplayClient.Entry b = leviathan();
            if (lairIssued < 0) {
                // the last simulation must be gone first (a new one is refused while the boss is in the lake)
                if (t >= 60 && b == null && t % 20 == 0) {
                    say("/af boss simular toca 2 dano");
                    lairIssued = t;
                }
                return t > 2400;
            }
            if (b == null) {
                return t > lairIssued + 400;
            }
            if (Double.isNaN(waterY) && "ocioso".equals(b.state)
                || Double.isNaN(waterY) && "afundar".equals(b.state) && t < 60) {
                waterY = b.motion.y(1f);
            }
            if (t % 10 == 0) {
                report(
                    String.format(
                        java.util.Locale.ROOT,
                        "toca-%s t=%d state %s boss y %.1f | me %.1f %.1f %.1f hp %.1f",
                        name,
                        t,
                        b.state,
                        b.motion.y(1f),
                        mc.thePlayer.posX,
                        mc.thePlayer.posY,
                        mc.thePlayer.posZ,
                        mc.thePlayer.getHealth()));
            }
            if (mc.thePlayer.getHealth() <= 0) {
                shotAt(t, t, "boss-toca-" + name + "-morto");
                mc.thePlayer.respawnPlayer();
                report("toca-" + name + ": died at t=" + t);
                return true;
            }
            if (!"espreitar".equals(b.state) && !"sugar".equals(b.state)) {
                if (fight && ("atordoado".equals(b.state) || "subir_atordoado".equals(b.state))) {
                    shotAt(t, t, "boss-toca-luzes-atordoado");
                    report("toca-luzes: the boss rose stunned at t=" + t);
                    return true;
                }
                if (!fight && "engolir".equals(b.state)) {
                    shot = shot == null && t % 15 == 0 ? "boss-toca-parado-engolir-" + t : shot;
                }
                return t > 2400;
            }
            if (!fight) {
                // in front of the mouth, a few blocks under the water, looking at it
                if (t % 40 == 0) {
                    double[] at = world(b, 0, 0, 16);
                    double[] mouth = world(b, 0, 6.38, 10.6);
                    say(
                        String.format(
                            java.util.Locale.ROOT,
                            "/tp %s %.1f %.1f %.1f",
                            mc.thePlayer.getCommandSenderName(),
                            at[0],
                            waterY - 2.5,
                            at[2]));
                    lookAt(mc, mouth);
                }
                if (t % 30 == 0) {
                    shot = "boss-toca-parado-" + t;
                }
                return t > 2400;
            }
            // punching: dodge behind it while it sucks
            if ("sugar".equals(b.state)) {
                if (dodgedAt < 0) {
                    dodgedAt = t;
                    double[] away = world(b, 0, 0, -34);
                    say(
                        String.format(
                            java.util.Locale.ROOT,
                            "/tp %s %.1f %.1f %.1f",
                            mc.thePlayer.getCommandSenderName(),
                            away[0],
                            waterY + 0.5,
                            away[2]));
                    report("toca-luzes: dodging the suction at t=" + t);
                    shot = "boss-toca-luzes-sugar";
                }
                return false;
            }
            dodgedAt = -1;
            if (lightIndex >= LURK_LIGHTS.length) {
                return t > 2400;
            }
            double[] l = LURK_LIGHTS[lightIndex];
            double[] light = world(b, l[0], l[1], l[2]);
            double[] spot = world(b, l[0], l[1], l[2] + (lightIndex < 2 ? -2.2 : 2.2));
            if (t % 6 == 0) {
                say(
                    String.format(
                        java.util.Locale.ROOT,
                        "/tp %s %.1f %.1f %.1f",
                        mc.thePlayer.getCommandSenderName(),
                        spot[0],
                        Math.min(spot[1] - 1.6, waterY - 1.0),
                        spot[2]));
            }
            lookAt(mc, light);
            if (t % 6 == 3) {
                mc.thePlayer.swingItem();
                punches++;
                if (punches == 2) {
                    shot = "boss-toca-luzes-soco-" + lightIndex;
                }
                if (punches >= 9) {
                    report("toca-luzes: done with light " + lightIndex + " at t=" + t);
                    lightIndex++;
                    punches = 0;
                }
            }
            return t > 2400;
        });
    }

    private static void lookAt(Minecraft mc, double[] at) {
        double dx = at[0] - mc.thePlayer.posX;
        double dy = at[1] - mc.thePlayer.posY;
        double dz = at[2] - mc.thePlayer.posZ;
        look(
            mc,
            (float) Math.toDegrees(Math.atan2(-dx, dz)),
            (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    /** How many NecroTempus boss bars this client has on screen (-1: no NecroTempus). */
    private static int bossBars() {
        try {
            Class<?> c = Class
                .forName("io.github.cruciblemc.necrotempus.modules.features.bossbar.client.ClientBossBarManager");
            return (Integer) c.getMethod("size")
                .invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private void fight() {
        // a real fight: the flight attempt while the boss emerges (no attacks for 6 s), then the strikes
        steps.add((mc, t) -> {
            if (t == 0) {
                sawBoss = false;
                say("/af boss invocar");
            }
            return t >= 3;
        });
        steps.add(flight("com-trava", true));
        steps.add(this::strikes);
        commands("/af boss status", "/af boss encerrar", "/af boss status");
    }

    private void commands(String... list) {
        for (String c : list) {
            steps.add((mc, t) -> {
                if (t == 0) {
                    say(c);
                }
                return t >= 20;
            });
        }
    }

    private Step simulation(String args, String name, int every) {
        return (mc, t) -> {
            look(mc, 0, -14);
            if (t == 0) {
                sawBoss = false;
                jumps = 0;
                say("/af boss simular " + args);
            }
            jumpRope(mc, t);
            if (t > 20 && t % every == 0 && t / every <= 60) {
                shot = String.format("%s-%04d", name, t);
            }
            if (DisplayClient.INSTANCE.count() > 0) {
                sawBoss = true;
            }
            if (t % 100 == 0) {
                report(name + " t=" + t + " displays: " + describeDisplays());
            }
            boolean over = t > 60 && sawBoss && DisplayClient.INSTANCE.count() == 0 || t > 20 * 240;
            if (over) {
                report(name + " over at t=" + t + ", jumps " + jumps);
            }
            return over;
        };
    }

    /** Jumps when a tentacle is about to pass under the player (they sweep the band around the lake). */
    private void jumpRope(Minecraft mc, int t) {
        if (jumpHold > 0 && --jumpHold == 0) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }
        // the lake's centre is the boss; the tentacles sweep around it: predict when one reaches our angle
        double cx = 0.5;
        double cz = 0.5;
        for (DisplayClient.Entry e : entries()) {
            if (LEVIATHAN.equals(e.model)) {
                cx = e.motion.x(1f);
                cz = e.motion.z(1f);
            }
        }
        double me = Math.toDegrees(Math.atan2(mc.thePlayer.posZ - cz, mc.thePlayer.posX - cx));
        double now = DisplayClient.INSTANCE.clock() + DisplayClient.INSTANCE.partialTicks();
        for (DisplayClient.Entry e : entries()) {
            if (!TENTACLE.equals(e.model)) {
                continue;
            }
            float pt = DisplayClient.INSTANCE.partialTicks();
            double[] p = { e.motion.x(pt), e.motion.y(pt), e.motion.z(pt), e.motion.yaw(pt) };
            double angle = Math.toDegrees(Math.atan2(p[2] - cz, p[0] - cx));
            Double before = lastAngle.put(e.id, angle);
            if (before == null) {
                continue;
            }
            double omega = ((angle - before) % 360 + 540) % 360 - 180; // degrees per tick, signed
            if (Math.abs(omega) < 0.2) {
                continue;
            }
            double ahead = ((me - angle) % 360 + 540) % 360 - 180;
            double eta = ahead / omega; // ticks until it is under us
            if (eta >= 2 && eta <= 4.5 && mc.thePlayer.onGround && jumpHold == 0) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                jumpHold = 3;
                jumps++;
                report(
                    String.format(
                        "jump at t=%d (tentacle %d %.1f° away, %.2f°/tick, eta %.1f)",
                        t,
                        e.id,
                        ahead,
                        omega,
                        eta));
            }
        }
    }

    /** Tries to fly up for 100 ticks: the server grants allowFlight every tick (/voar), as a flight mod would. */
    private Step flight(String name, boolean expectLock) {
        return (mc, t) -> {
            look(mc, 0, -10);
            if (t == 0) {
                say("/voar on");
                flyMaxY = flyStartY = mc.thePlayer.posY;
            }
            if (t >= 10 && t < 110) {
                mc.thePlayer.capabilities.allowFlying = true;
                mc.thePlayer.capabilities.isFlying = true;
                mc.thePlayer.sendPlayerAbilities();
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                flyMaxY = Math.max(flyMaxY, mc.thePlayer.posY);
                if (t % 10 == 0) {
                    report(
                        String.format(
                            "%s t=%d y=%.2f (+%.2f) motionY=%.3f flying=%s",
                            name,
                            t,
                            mc.thePlayer.posY,
                            mc.thePlayer.posY - flyStartY,
                            mc.thePlayer.motionY,
                            mc.thePlayer.capabilities.isFlying));
                }
            }
            shotAt(t, 25, "boss-voo-" + name + "-a");
            shotAt(t, 60, "boss-voo-" + name + "-b");
            if (t == 30) {
                report("displays: " + describeDisplays());
            }
            if (t == 110) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                mc.thePlayer.capabilities.isFlying = false;
                say("/voar off");
                report(
                    String.format(
                        "%s: highest %.2f blocks above the start (%s lock expected)",
                        name,
                        flyMaxY - flyStartY,
                        expectLock ? "with" : "no"));
            }
            if (t == 130) {
                say("/tp " + mc.thePlayer.getCommandSenderName() + " 0.5 5 " + BAND_Z);
            }
            return t >= 160;
        };
    }

    /** Casts at the weak points (looking at where they are when the boss rests) and stops the strike bar. */
    private boolean strikes(Minecraft mc, int t) {
        jumpRope(mc, t);
        if (mc.thePlayer.inventory.getCurrentItem() == null) {
            for (int i = 0; i < 9; i++) {
                if (mc.thePlayer.inventory.mainInventory[i] != null) {
                    mc.thePlayer.inventory.currentItem = i;
                }
            }
        }
        DisplayClient.Entry boss = null;
        for (DisplayClient.Entry e : entries()) {
            if (LEVIATHAN.equals(e.model)) {
                boss = e;
            }
        }
        if (boss == null) {
            // dead and respawned far away, or the fight is over
            if (t % 40 == 0 && mc.thePlayer.getHealth() > 0) {
                say("/tp " + mc.thePlayer.getCommandSenderName() + " 0.5 5 " + BAND_Z);
                castTicks = -1;
                hookedAt = -1;
            }
            if (++goneTicks > 400) {
                report("strikes: the boss is gone (t=" + t + ")");
                return true;
            }
            return false;
        }
        goneTicks = 0;
        if ("morrer".equals(boss.state) && !sawDeath) {
            sawDeath = true;
            deathTick = t;
        }
        if (sawDeath && (t == deathTick + 8 || t == deathTick + 40)) {
            shot = "boss-derrotado-" + (t - deathTick);
        }
        double[] p = POINTS[target % POINTS.length];
        double s = boss.scale[0];
        double r = Math.toRadians(-boss.motion.yaw(1f));
        double x = boss.motion.x(1f) + p[0] * s * Math.cos(r) + p[2] * s * Math.sin(r);
        double y = boss.motion.y(1f) + p[1] * s;
        double z = boss.motion.z(1f) - p[0] * s * Math.sin(r) + p[2] * s * Math.cos(r);
        double dx = x - mc.thePlayer.posX;
        double dy = y - mc.thePlayer.posY; // posY is the eye on the client player
        double dz = z - mc.thePlayer.posZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        look(mc, yaw, pitch);
        if (castTicks < 0 && t % 40 == 0 && mc.thePlayer.fishEntity == null) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            casts++;
            castTicks = 0;
            hookSeen = false;
            report(
                String.format(
                    "cast %d at point %d (%.1f %.1f %.1f), state %s, look %.0f/%.0f",
                    casts,
                    target % POINTS.length,
                    x,
                    y,
                    z,
                    boss.state,
                    yaw,
                    pitch));
        } else if (castTicks >= 0) {
            castTicks++;
            if (mc.thePlayer.fishEntity != null) {
                hookSeen = true;
            }
            boolean gone = hookSeen && mc.thePlayer.fishEntity == null;
            if (gone && castTicks < 200 && hookedAt < 0) {
                hookedAt = castTicks;
                hooks++;
                report("hook gone after " + castTicks + " ticks (hooked a point?)");
            }
            if (hookedAt >= 0) {
                int k = castTicks - hookedAt;
                if (k == 4) {
                    shot = "boss-golpe-" + hooks + "-a";
                }
                // the cursor crosses in 16 ticks for a weak point: stop it about the middle
                if (k == (hooks % 2 == 1 ? 5 : 9)) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
                }
                if (k == 11) {
                    shot = "boss-golpe-" + hooks + "-b";
                }
                if (k == 13) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
                }
                if (k == 30) {
                    shot = "boss-golpe-" + hooks + "-c";
                }
                if (k >= 45) {
                    castTicks = -1;
                    hookedAt = -1;
                    target++;
                }
            } else if (castTicks > 60) {
                // the hook flew somewhere else: reel it in and try again
                if (mc.thePlayer.fishEntity != null) {
                    mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
                }
                castTicks = -1;
                target++;
            }
        }
        boolean over = hooks >= 12 || casts >= 24 || t > 20 * 150;
        if (over) {
            report("strikes over: " + casts + " casts, " + hooks + " hooks");
        }
        return over;
    }

    private int hookedAt = -1;
    private boolean hookSeen;
    private int goneTicks;
    private boolean sawDeath;
    private int deathTick;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;
        if (stage == 0) {
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
                stage = 1;
                stageTicks = 0;
            }
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            if (++stageTicks > 20 * 600) {
                report("could not join");
                finish();
            }
            return;
        }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
            if (mc.thePlayer.getHealth() <= 0) {
                if (stageTicks % 40 == 0) {
                    report("died; respawning (rod in inventory: " + hasRod(mc) + ")");
                    mc.thePlayer.respawnPlayer();
                }
            } else {
                mc.displayGuiScreen(null);
            }
        }
        stageTicks++;
        if (stage == 1) {
            if (stageTicks >= 100) {
                report(
                    "joined; displays known: " + DisplayClient.INSTANCE.index()
                        .models()
                        .keySet() + ", drawn: " + DisplayClient.INSTANCE.installed());
                plan();
                stage = 2;
                stageTicks = 0;
                step = 0;
            }
            return;
        }
        if (step >= steps.size()) {
            finish();
            return;
        }
        if (steps.get(step)
            .tick(mc, stageTicks - 1)) {
            step++;
            stageTicks = 0;
        }
    }

    private static boolean hasRod(Minecraft mc) {
        for (net.minecraft.item.ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s != null && s.getItem() == net.minecraft.init.Items.fishing_rod) {
                return true;
            }
        }
        return false;
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

    private void shotAt(int t, int at, String name) {
        if (t == at) {
            shot = name;
        }
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
        mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.prevRotationPitch = pitch;
    }

    private void say(String command) {
        report("> " + command);
        Minecraft.getMinecraft().thePlayer.sendChatMessage(command);
    }

    @SuppressWarnings("unchecked")
    private static List<DisplayClient.Entry> entries() {
        try {
            Field f = DisplayClient.class.getDeclaredField("displays");
            f.setAccessible(true);
            return new ArrayList<>(((Map<Integer, DisplayClient.Entry>) f.get(DisplayClient.INSTANCE)).values());
        } catch (ReflectiveOperationException e) {
            return Collections.emptyList();
        }
    }

    /** The Leviathan's controller and a few bones, read from the client's renderer (reflection: dev only). */
    private static String animState() {
        try {
            Field rf = DisplayClient.class.getDeclaredField("renderer");
            rf.setAccessible(true);
            Object renderer = rf.get(DisplayClient.INSTANCE);
            Field af = renderer.getClass()
                .getDeclaredField("animatables");
            af.setAccessible(true);
            Object a = ((Map<?, ?>) af.get(renderer)).get(LEVIATHAN);
            if (a == null) {
                return "no animatable";
            }
            DisplayClient.Entry boss = null;
            for (DisplayClient.Entry e : entries()) {
                if (LEVIATHAN.equals(e.model)) {
                    boss = e;
                }
            }
            StringBuilder b = new StringBuilder();
            b.append("entry ")
                .append(boss == null ? "-" : boss.state + " start " + boss.start)
                .append(" clock ")
                .append(DisplayClient.INSTANCE.clock());
            Field anim = a.getClass()
                .getDeclaredField("animation");
            anim.setAccessible(true);
            b.append(" animatable ")
                .append(anim.get(a));
            software.bernie.geckolib3.core.manager.AnimationFactory factory = ((software.bernie.geckolib3.core.IAnimatable) a)
                .getFactory();
            int key = boss == null ? 0 : Math.floorMod(boss.id, 256);
            for (Object c : factory.getOrCreateAnimationData(key)
                .getAnimationControllers()
                .values()) {
                software.bernie.geckolib3.core.controller.AnimationController<?> ac = (software.bernie.geckolib3.core.controller.AnimationController<?>) c;
                b.append(" | ")
                    .append(ac.getAnimationState())
                    .append(" ")
                    .append(ac.getCurrentAnimation() == null ? "-" : ac.getCurrentAnimation().animationName)
                    .append(" offset ")
                    .append(ac.tickOffset)
                    .append(" trans ")
                    .append(ac.transitionLengthTicks);
            }
            Field mf = a.getClass()
                .getDeclaredField("model");
            mf.setAccessible(true);
            software.bernie.geckolib3.model.AnimatedGeoModel<?> model = (software.bernie.geckolib3.model.AnimatedGeoModel<?>) mf
                .get(a);
            for (String bone : new String[] { "corpo", "mandibula", "seg_3" }) {
                software.bernie.geckolib3.core.processor.IBone ib = model.getAnimationProcessor()
                    .getBone(bone);
                if (ib != null) {
                    b.append(
                        String.format(
                            " %s %.1f/%.1f/%.1f",
                            bone,
                            Math.toDegrees(ib.getRotationX()),
                            Math.toDegrees(ib.getRotationY()),
                            Math.toDegrees(ib.getRotationZ())));
                }
            }
            return b.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "anim state: " + e;
        }
    }

    private static String describeDisplays() {
        StringBuilder b = new StringBuilder();
        for (DisplayClient.Entry e : entries()) {
            b.append(
                String.format(
                    "[%s %s %s at %.1f %.1f %.1f yaw %.0f scale %.2f/%.2f/%.2f bones %s] ",
                    e.model.substring(e.model.indexOf(':') + 1),
                    e.variant,
                    e.state,
                    e.motion.x(1f),
                    e.motion.y(1f),
                    e.motion.z(1f),
                    e.motion.yaw(1f),
                    e.scale[0],
                    e.scale[1],
                    e.scale[2],
                    e.bones));
        }
        return b.length() == 0 ? "none" : b.toString();
    }

    private void finish() {
        report("done");
        if (out != null) {
            out.close();
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
        Kunftig.LOG.info("BOSS HARNESS: {}", line);
    }
}
