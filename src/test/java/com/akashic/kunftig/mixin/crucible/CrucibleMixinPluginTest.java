package com.akashic.kunftig.mixin.crucible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Issue #3, regression of 8a18b84: a mixin on org.bukkit.World in the required config crashed TEOF at startup
 * (MixinTargetAlreadyLoadedException), because PlayerContainerFix's coremod loads World with Class.forName before
 * Mixin's DEFAULT phase. The fix must never be able to stop a server.
 */
class CrucibleMixinPluginTest {

    private static final Logger LOG = LogManager.getLogger("KunftigTest");

    private static CrucibleMixinPlugin.Environment env(boolean disabled, boolean defect, boolean loaded) {
        return new CrucibleMixinPlugin.Environment() {

            @Override
            public boolean disabled() {
                return disabled;
            }

            @Override
            public boolean hasDefect() {
                return defect;
            }

            @Override
            public boolean worldAlreadyLoaded() {
                return loaded;
            }
        };
    }

    @Test
    void offersTheMixinOnlyWhenItCanApply() {
        assertEquals(
            Collections.singletonList(CrucibleMixinPlugin.MIXIN),
            CrucibleMixinPlugin.mixins(env(false, true, false), LOG));
        assertTrue(
            CrucibleMixinPlugin.mixins(env(false, true, true), LOG)
                .isEmpty(),
            "TEOF: World loaded early");
        assertTrue(
            CrucibleMixinPlugin.mixins(env(true, true, false), LOG)
                .isEmpty(),
            "kill switch");
        assertTrue(
            CrucibleMixinPlugin.mixins(env(false, false, false), LOG)
                .isEmpty(),
            "no defect / no Bukkit");
    }

    private static JsonObject config(String name) throws IOException {
        try (InputStream in = CrucibleMixinPluginTest.class.getClassLoader()
            .getResourceAsStream(name)) {
            return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }

    private static boolean mentions(JsonObject config, String mixin) {
        for (String list : new String[] { "mixins", "client", "server" }) {
            JsonArray entries = config.has(list) ? config.getAsJsonArray(list) : new JsonArray();
            for (JsonElement entry : entries) {
                if (entry.getAsString()
                    .contains(mixin)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void theCrucibleConfigIsOptionalAndListsNothing() throws IOException {
        JsonObject crucible = config("mixins.kunftig.crucible.json");
        assertFalse(
            crucible.get("required")
                .getAsBoolean(),
            "an early-loaded target must only be logged");
        assertFalse(mentions(crucible, CrucibleMixinPlugin.MIXIN), "offered by getMixins only");
        // Coremods may load World while they are injected (PREINIT); a PREINIT config can still catch that load.
        assertEquals(
            "@env(PREINIT)",
            crucible.get("target")
                .getAsString());
        assertEquals(
            CrucibleMixinPlugin.class.getName(),
            crucible.get("plugin")
                .getAsString());
        assertEquals(
            CrucibleMixinPlugin.class.getPackage()
                .getName(),
            crucible.get("package")
                .getAsString());
    }

    @Test
    void theRequiredConfigNeverTargetsBukkit() throws IOException {
        JsonObject main = config("mixins.kunftig.json");
        assertTrue(
            main.get("required")
                .getAsBoolean());
        assertFalse(mentions(main, "crucible"), "Bukkit classes may be loaded by other coremods before mixins");
        List<String> none = Collections.emptyList();
        assertEquals(
            none.size(),
            main.getAsJsonArray("server")
                .size());
    }
}
