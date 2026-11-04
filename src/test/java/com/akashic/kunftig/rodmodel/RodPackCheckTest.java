package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.akashic.kunftig.common.text.Json;

/**
 * Checks a real resource pack against what Kunftig reads (issue #13): every manifest parses, and every file, bone and
 * animation it names exists. Runs only when asked: {@code ./gradlew :test -ProdPack=<resource pack folder>} (the
 * folder with {@code assets/}).
 */
@EnabledIfSystemProperty(named = "kunftig.rodPack", matches = ".+")
class RodPackCheckTest {

    @Test
    void everyRodOfThePackIsComplete() throws Exception {
        File assets = new File(System.getProperty("kunftig.rodPack"), "assets");
        List<String> problems = new ArrayList<>();
        int rods = 0;
        File[] namespaces = assets.listFiles(File::isDirectory);
        for (File ns : namespaces == null ? new File[0] : namespaces) {
            File manifest = new File(ns, RodModelIndex.PATH);
            if (!manifest.isFile()) {
                continue;
            }
            RodModelIndex index = new RodModelIndex();
            index.add(ns.getName(), read(manifest), manifest.getPath());
            problems.addAll(index.errors());
            problems.addAll(index.ignored()); // a field the pack relies on and this Kunftig does not read
            for (RodModelDef rod : index.models()
                .values()) {
                rods++;
                check(assets, rod, problems);
            }
        }
        assertFalse(rods == 0, "no " + RodModelIndex.PATH + " under " + assets);
        assertEquals(new ArrayList<String>(), problems, rods + " rods");
    }

    private static void check(File assets, RodModelDef rod, List<String> problems) throws IOException {
        String p = rod.id + ": ";
        File geo = file(assets, rod.geo);
        Set<String> bones = geo.isFile() ? bones(read(geo)) : new HashSet<>();
        if (bones.isEmpty()) {
            problems.add(p + "no bones in " + rod.geo);
        }
        BedrockAnimations anims = animations(assets, rod.animations, p, problems);
        BedrockAnimations arm = rod.arm == null ? null : animations(assets, rod.arm, p, problems);
        for (String texture : textures(rod)) {
            if (!file(assets, texture).isFile()) {
                problems.add(p + "missing texture " + texture);
            }
        }
        Set<String> named = new HashSet<>(rod.lightBones);
        named.addAll(rod.translucentBones);
        named.addAll(rod.boneStages.keySet());
        named.addAll(rod.hiddenWhileCast);
        for (List<String> stone : rod.qualityBones.values()) {
            named.addAll(stone);
        }
        if (!rod.qualityBones.isEmpty() && !rod.qualityBones.containsKey(RodModelDef.DEFAULT_QUALITY)) {
            problems
                .add(p + "qualidade has no \"" + RodModelDef.DEFAULT_QUALITY + "\" (items without a quality suffix)");
        }
        if (rod.tipBone != null) {
            named.add(rod.tipBone);
        }
        if (rod.hangingLine != null) {
            named.add(rod.hangingLine.bone);
        }
        if (rod.sway != null) {
            if (rod.sway.pivot != null) {
                named.add(rod.sway.pivot);
            }
            named.addAll(rod.sway.bend);
        }
        for (String bone : named) {
            if (!bones.contains(bone)) {
                problems.add(p + "bone '" + bone + "' is not in " + rod.geo);
            }
        }
        for (Map.Entry<String, RodModelDef.Clips> s : rod.states.entrySet()) {
            RodModelDef.Clips c = s.getValue();
            for (String clip : Arrays.asList(c.rod, c.aura)) {
                if (clip != null && anims != null && anims.clip(clip) == null) {
                    problems.add(p + s.getKey() + ": no animation '" + clip + "' in " + rod.animations);
                }
            }
            for (String armClip : Arrays.asList(c.arm, c.armWithLine)) {
                if (armClip != null && arm != null && arm.clip(armClip) == null) {
                    problems.add(p + s.getKey() + ": no arm animation '" + armClip + "' in " + rod.arm);
                }
            }
        }
        if (anims != null) {
            for (Map.Entry<String, BedrockAnimations.Clip> clip : anims.clips()
                .entrySet()) {
                for (String bone : clip.getValue()
                    .rotatedBones()) {
                    if (!bones.contains(bone)) {
                        problems.add(p + clip.getKey() + " rotates '" + bone + "', not in " + rod.geo);
                    }
                }
            }
        }
        for (String context : rod.contexts) {
            if (!rod.displays.containsKey(context)) {
                problems.add(p + "context '" + context + "' has no display");
            }
        }
    }

    private static List<String> textures(RodModelDef rod) {
        List<String> all = new ArrayList<>();
        all.add(rod.texture);
        for (List<String> frames : rod.stageTextures.values()) {
            all.addAll(frames);
        }
        return all;
    }

    private static BedrockAnimations animations(File assets, String location, String p, List<String> problems)
        throws IOException {
        File f = file(assets, location);
        if (!f.isFile()) {
            problems.add(p + "missing " + location);
            return null;
        }
        try {
            return BedrockAnimations.read(read(f));
        } catch (IllegalArgumentException e) {
            problems.add(p + location + ": " + e.getMessage());
            return null;
        }
    }

    /** Bone names of a Bedrock geometry 1.12.0 file. */
    private static Set<String> bones(String json) {
        Set<String> names = new HashSet<>();
        try {
            Object root = Json.parse(json, 64);
            Object geometries = ((Map<?, ?>) root).get("minecraft:geometry");
            for (Object g : (List<?>) geometries) {
                Object bones = ((Map<?, ?>) g).get("bones");
                for (Object b : (List<?>) bones) {
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
