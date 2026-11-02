package com.akashic.kunftig.dev;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.KunftigConfig;
import com.akashic.kunftig.fishing.KunftigFishHook;
import com.akashic.kunftig.fishing.api.KunftigFishing;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development-only test harness (never in the release jar): with {@code -Dkunftig.devHarness=true} on the dev server, it
 * builds a pond in the air and measures real {@link EntityFishHook}s tick by tick, then stops the server. Results go to
 * {@code kunftig-harness.txt} in the run directory.
 */
public final class FishingHarness {

    // Pond: water blocks y 96..99 (surface drawn at 99 + 8/9), stone floor 95; bank: stone at y 99, feet at 100.
    private static final int X0 = 0, Z0 = 0, FLOOR = 95, TOP = 99, SIZE = 12;

    private final List<Scenario> scenarios = new ArrayList<>();
    private PrintWriter out;
    private WorldServer world;
    private FakePlayer angler;
    private int ticks;
    private Scenario current;

    /** Called reflectively by the mod when the property is set. */
    public static void install() {
        FMLCommonHandler.instance()
            .bus()
            .register(new FishingHarness());
        Kunftig.LOG.warn("Kunftig DEV HARNESS enabled: this server will run fishing tests and stop");
    }

    private abstract class Scenario {

        final String name;
        int age;

        Scenario(String name) {
            this.name = name;
        }

        abstract void start();

        /** @return false when done */
        abstract boolean tick();
    }

    private EntityFishHook cast(double x, double z, double vy) {
        EntityFishHook hook = new EntityFishHook(world, angler);
        hook.setPosition(x, TOP + 1.5, z);
        hook.motionX = 0;
        hook.motionY = vy;
        hook.motionZ = 0;
        angler.fishEntity = hook;
        world.spawnEntityInWorld(hook);
        return hook;
    }

    private final class Bobber extends Scenario {

        final double vy;
        final boolean modern;
        EntityFishHook hook;
        double deepest = Double.MAX_VALUE, highest = -Double.MAX_VALUE, sum;
        double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
        int counted;

        Bobber(double vy, boolean modern) {
            super((modern ? "modern" : "vanilla") + " bobber, entry vy=" + vy);
            this.vy = vy;
            this.modern = modern;
        }

        @Override
        void start() {
            KunftigConfig.fishingModernBobber = modern;
            hook = cast(X0 + 4.5, Z0 + 6.5, vy);
            KunftigFishing.parameters(hook)
                .setWaitTime(100000, 100000); // no bites: pure physics
        }

        @Override
        boolean tick() {
            double rel = hook.posY - (TOP + 8.0 / 9.0);
            if (age > 3) {
                deepest = Math.min(deepest, rel);
                highest = Math.max(highest, rel);
            }
            if (age >= 120) {
                sum += rel;
                counted++;
                low = Math.min(low, rel);
                high = Math.max(high, rel);
            }
            out.printf("%s tick %d y_rel %.4f vy %.4f%n", name, age, rel, hook.motionY);
            if (age >= 200 || hook.isDead) {
                out.printf(
                    "RESULT %s: alive=%s deepest=%.3f highest_after_entry=%.3f rest_mean=%.3f bob_range=%.3f%n",
                    name,
                    !hook.isDead,
                    deepest,
                    highest,
                    counted == 0 ? Double.NaN : sum / counted,
                    high - low);
                hook.setDead();
                angler.fishEntity = null;
                return false;
            }
            return true;
        }
    }

    private final class LockAndAim extends Scenario {

        final boolean aim;
        EntityFishHook hook;
        EntityItem item;
        double closest = Double.MAX_VALUE;

        LockAndAim(boolean aim) {
            super("lock+retrieve, aim=" + aim);
            this.aim = aim;
        }

        @Override
        void start() {
            KunftigConfig.fishingModernBobber = false;
            hook = cast(X0 + 5.5, Z0 + 1.5, -0.3); // 5 blocks from the angler on the bank
        }

        @Override
        boolean tick() {
            if (age == 60) {
                KunftigFishHook access = KunftigFishing.access(hook);
                access.kunftig$setBiteWindow(400);
                KunftigFishing.parameters(hook)
                    .setReelLocked(true);
                KunftigFishing.parameters(hook)
                    .setCatchAimedAtAngler(aim);
                int locked = hook.func_146034_e();
                out.printf(
                    "RESULT %s: locked reel returned %d, hook alive=%s, angler.fishEntity kept=%s, still biting=%s%n",
                    name,
                    locked,
                    !hook.isDead,
                    angler.fishEntity == hook,
                    access.kunftig$biteWindow() > 0);
                int before = world.loadedEntityList.size();
                int damage = KunftigFishing.retrieve(hook, false);
                for (Object entity : world.loadedEntityList) {
                    if (entity instanceof EntityItem && ((EntityItem) entity).age == 0) {
                        item = (EntityItem) entity;
                    }
                }
                out.printf(
                    "RESULT %s: retrieve returned %d, hook dead=%s, fishEntity cleared=%s, item spawned=%s (entities %d->%d)%n",
                    name,
                    damage,
                    hook.isDead,
                    angler.fishEntity == null,
                    item != null,
                    before,
                    world.loadedEntityList.size());
            }
            if (item != null) {
                double dx = item.posX - angler.posX, dz = item.posZ - angler.posZ;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                closest = Math.min(closest, horizontal);
                out.printf(
                    "%s item tick %d horizontal %.3f y_above_feet %.3f%n",
                    name,
                    age - 60,
                    horizontal,
                    item.posY - angler.posY);
                if (age - 60 >= 40 || item.isDead) {
                    boolean onBank = item.posY >= angler.posY - 0.01;
                    boolean reachable = horizontal <= 1.3 && item.posY >= angler.posY - 0.5;
                    out.printf(
                        "RESULT %s: item after 40 ticks: horizontal=%.3f y_above_feet=%.3f on_bank=%s pickup_range=%s closest=%.3f%n",
                        name,
                        horizontal,
                        item.posY - angler.posY,
                        onBank,
                        reachable,
                        closest);
                    item.setDead();
                    return false;
                }
            }
            return age < 200;
        }
    }

    private void build() {
        for (int x = X0 - 1; x <= X0 + SIZE; x++) {
            for (int z = Z0 - 3; z <= Z0 + SIZE; z++) {
                for (int y = FLOOR; y <= TOP + 4; y++) {
                    world.setBlock(x, y, z, Blocks.air);
                }
                world.setBlock(x, FLOOR, z, Blocks.stone);
            }
        }
        for (int x = X0; x < X0 + SIZE; x++) {
            for (int z = Z0; z < Z0 + SIZE; z++) {
                for (int y = FLOOR + 1; y <= TOP; y++) {
                    world.setBlock(x, y, z, Blocks.water);
                }
            }
        }
        // Bank on the -z side: stone up to y 99, angler's feet at 100, 5 blocks from x 5.5.
        for (int x = X0 - 1; x <= X0 + SIZE; x++) {
            for (int z = Z0 - 3; z < Z0; z++) {
                for (int y = FLOOR; y <= TOP; y++) {
                    world.setBlock(x, y, z, Blocks.stone);
                }
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        try {
            if (world == null) {
                if (ticks < 40) {
                    return;
                }
                MinecraftServer server = MinecraftServer.getServer();
                world = server.worldServerForDimension(0);
                out = new PrintWriter(new FileWriter(new File("kunftig-harness.txt")), true);
                build();
                angler = FakePlayerFactory
                    .get(world, new GameProfile(UUID.fromString("41c82c87-7afb-4024-ba57-13d2c99cae77"), "[KunftigTest]"));
                angler.setPositionAndRotation(X0 + 5.5, TOP + 1, Z0 - 3.5, 0, 30);
                angler.inventory.mainInventory[angler.inventory.currentItem] = new ItemStack(Items.fishing_rod);
                for (double vy : new double[] { -0.3, -0.8, -1.2 }) {
                    scenarios.add(new Bobber(vy, false));
                    scenarios.add(new Bobber(vy, true));
                }
                scenarios.add(new LockAndAim(false));
                scenarios.add(new LockAndAim(true));
                return;
            }
            world.resetUpdateEntityTick(); // keep entities ticking without real players
            if (current == null) {
                if (scenarios.isEmpty()) {
                    out.println("DONE");
                    out.close();
                    MinecraftServer.getServer()
                        .initiateShutdown();
                    return;
                }
                current = scenarios.remove(0);
                current.start();
                return;
            }
            current.age++;
            if (!current.tick()) {
                current = null;
            }
        } catch (IOException | RuntimeException e) {
            Kunftig.LOG.error("Kunftig harness failed", e);
            if (out != null) {
                e.printStackTrace(out);
                out.close();
            }
            MinecraftServer.getServer()
                .initiateShutdown();
        }
    }
}
