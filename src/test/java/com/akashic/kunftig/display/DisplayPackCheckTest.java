package com.akashic.kunftig.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.akashic.kunftig.common.text.Json;
import com.akashic.kunftig.rodmodel.BedrockAnimations;

/**
 * Checks the displays of a real resource pack against what Kunftig reads: every {@code kunftig/displays.json} parses,
 * and every file, animation and bone it names exists. Runs only when asked, with the rods' check:
 * {@code ./gradlew :test -ProdPack=<resource pack folder>}. A pack without displays passes.
 */
@EnabledIfSystemProperty(named = "kunftig.rodPack", matches = ".+")
class DisplayPackCheckTest {

    @Test
    void everyDisplayOfThePackIsComplete() throws Exception {
        File assets = new File(System.getProperty("kunftig.rodPack"), "assets");
        List<String> problems = new ArrayList<>();
        File[] namespaces = assets.listFiles(File::isDirectory);
        assertFalse(namespaces == null, "no assets/ in " + assets.getParent());
        for (File ns : namespaces) {
            File manifest = new File(ns, DisplayModelIndex.PATH);
            if (!manifest.isFile()) {
                continue;
            }
            DisplayModelIndex index = new DisplayModelIndex();
            index.add(ns.getName(), read(manifest), manifest.getPath());
            problems.addAll(index.errors());
            for (DisplayModelIndex.Model m : index.models()
                .values()) {
                check(assets, m, problems);
            }
        }
        assertEquals(new ArrayList<String>(), problems);
    }

    private static void check(File assets, DisplayModelIndex.Model m, List<String> problems) throws IOException {
        String p = m.id + ": ";
        File geo = file(assets, m.geo);
        Set<String> bones = geo.isFile() ? bones(read(geo)) : new HashSet<>();
        if (bones.isEmpty()) {
            problems.add(p + "no bones in " + m.geo);
        }
        List<String> textures = new ArrayList<>();
        textures.add(m.texture);
        textures.addAll(m.variants.values());
        for (String t : textures) {
            if (!file(assets, t).isFile()) {
                problems.add(p + "missing " + t);
            }
        }
        BedrockAnimations anims = null;
        File a = file(assets, m.animations);
        if (!a.isFile()) {
            problems.add(p + "missing " + m.animations);
        } else {
            try {
                anims = BedrockAnimations.read(read(a));
            } catch (IllegalArgumentException e) {
                problems.add(p + m.animations + ": " + e.getMessage());
            }
        }
        for (Map.Entry<String, DisplayModelIndex.State> s : m.states.entrySet()) {
            if (anims != null && anims.clip(s.getValue().animation) == null) {
                problems.add(p + "state " + s.getKey() + ": no animation " + s.getValue().animation);
            }
        }
        List<String> named = new ArrayList<>(m.itemBones);
        named.addAll(m.lightBones);
        named.addAll(m.translucentBones);
        for (String b : named) {
            if (!bones.isEmpty() && !bones.contains(b)) {
                problems.add(p + "no bone " + b + " in " + m.geo);
            }
        }
    }

    /** Bone names of a Bedrock geometry 1.12.0 file. */
    private static Set<String> bones(String json) {
        Set<String> names = new HashSet<>();
        try {
            Object root = Json.parse(json, 64);
            for (Object g : (List<?>) ((Map<?, ?>) root).get("minecraft:geometry")) {
                for (Object b : (List<?>) ((Map<?, ?>) g).get("bones")) {
                    names.add(String.valueOf(((Map<?, ?>) b).get("name")));
                }
            }
        } catch (Json.JsonException | RuntimeException e) {
            names.clear();
        }
        return names;
    }

    private static File file(File assets, String location) {
        int colon = location.indexOf(':');
        return new File(new File(assets, location.substring(0, colon)), location.substring(colon + 1));
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
