package com.akashic.kunftig.blade;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Wavefront OBJ model as SlashBlade reads its blades and effects (Metasequoia exports: {@code v}, {@code vt},
 * {@code vn}, {@code f} and {@code g}/{@code o} groups). Same semantics as SlashBlade: Resharped's loader
 * ({@code client/renderer/model/obj}, MIT): texture V is flipped on load ({@code 1 - v}), faces before any group go to
 * {@code "Default"}, several groups may share a name, a face keeps its per-vertex normals (smooth shading) or falls
 * back to its own normal, and on emission each UV is pulled {@link #UV_INSET} toward the face's UV centre after the UV
 * operator. No Minecraft classes: the client draws it with the {@link Face} data.
 */
public final class ObjModel {

    /** How far each UV is pulled toward its face's UV centre, as SlashBlade does (against bleeding). */
    public static final float UV_INSET = 0.0005F;

    /** One face: 3 vertices (SlashBlade's models are all triangles) or 4. */
    public static final class Face {

        /** x, y, z per vertex. */
        public final float[][] positions;
        /** u, v per vertex (v already flipped), or null without {@code vt}. */
        public final float[][] uvs;
        /** x, y, z per vertex, or null without {@code vn}. */
        public final float[][] normals;
        /** The face's own normal ((v1-v0) x (v2-v0), normalized). */
        public final float[] faceNormal;

        Face(float[][] positions, float[][] uvs, float[][] normals) {
            this.positions = positions;
            this.uvs = uvs;
            this.normals = normals;
            this.faceNormal = faceNormal(positions);
        }

        public int size() {
            return positions.length;
        }

        /** The normal drawn at vertex {@code i}: its own, else the face's. */
        public float[] normal(int i) {
            return normals != null ? normals[i] : faceNormal;
        }

        /**
         * The UV drawn at vertex {@code i} under the operator {@code (uScale, vScale, uOffset, vOffset)}: scaled and
         * offset, then pulled {@link #UV_INSET} toward the face's (operated) UV centre. {0, 0} without UVs.
         */
        public float[] uv(int i, float uScale, float vScale, float uOffset, float vOffset) {
            if (uvs == null) {
                return new float[] { 0, 0 };
            }
            float avgU = 0;
            float avgV = 0;
            for (float[] t : uvs) {
                avgU += t[0] * uScale + uOffset;
                avgV += t[1] * vScale + vOffset;
            }
            avgU /= uvs.length;
            avgV /= uvs.length;
            float u = uvs[i][0] * uScale + uOffset;
            float v = uvs[i][1] * vScale + vOffset;
            return new float[] { u + (u > avgU ? -UV_INSET : UV_INSET), v + (v > avgV ? -UV_INSET : UV_INSET) };
        }

        private static float[] faceNormal(float[][] p) {
            float ax = p[1][0] - p[0][0];
            float ay = p[1][1] - p[0][1];
            float az = p[1][2] - p[0][2];
            float bx = p[2][0] - p[0][0];
            float by = p[2][1] - p[0][1];
            float bz = p[2][2] - p[0][2];
            double nx = ay * bz - az * by;
            double ny = az * bx - ax * bz;
            double nz = ax * by - ay * bx;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len == 0) {
                return new float[] { 0, 1, 0 };
            }
            return new float[] { (float) (nx / len), (float) (ny / len), (float) (nz / len) };
        }
    }

    /** A named group of faces. */
    public static final class Group {

        public final String name;
        public final List<Face> faces;

        Group(String name, List<Face> faces) {
            this.name = name;
            this.faces = Collections.unmodifiableList(faces);
        }
    }

    private final List<Group> groups;

    private ObjModel(List<Group> groups) {
        this.groups = Collections.unmodifiableList(groups);
    }

    public List<Group> groups() {
        return groups;
    }

    /**
     * Every group whose name equals {@code name} ignoring case (SlashBlade draws them all), in file order; empty when
     * there is none.
     */
    public List<Group> groups(String name) {
        List<Group> out = new ArrayList<>();
        for (Group g : groups) {
            if (g.name.equalsIgnoreCase(name)) {
                out.add(g);
            }
        }
        return out;
    }

    public boolean has(String name) {
        for (Group g : groups) {
            if (g.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Group names in file order (repeated names once). */
    public List<String> names() {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Group g : groups) {
            seen.put(g.name, Boolean.TRUE);
        }
        return new ArrayList<>(seen.keySet());
    }

    /** {min x, min y, min z, max x, max y, max z} of a group's vertices (all groups for null). */
    public float[] bounds(String name) {
        float[] b = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE,
            -Float.MAX_VALUE };
        for (Group g : name == null ? groups : groups(name)) {
            for (Face f : g.faces) {
                for (float[] p : f.positions) {
                    for (int k = 0; k < 3; k++) {
                        b[k] = Math.min(b[k], p[k]);
                        b[k + 3] = Math.max(b[k + 3], p[k]);
                    }
                }
            }
        }
        return b;
    }

    /** A model that failed to read. */
    public static final class FormatException extends Exception {

        private static final long serialVersionUID = 1L;

        FormatException(String message) {
            super(message);
        }
    }

    /** Reads an OBJ file's text. */
    public static ObjModel parse(String text, String source) throws FormatException {
        List<float[]> v = new ArrayList<>();
        List<float[]> vt = new ArrayList<>();
        List<float[]> vn = new ArrayList<>();
        List<Group> groups = new ArrayList<>();
        String groupName = null;
        List<Face> faces = null;
        int lineNo = 0;
        try (BufferedReader in = new BufferedReader(new StringReader(text))) {
            for (String raw; (raw = in.readLine()) != null;) {
                lineNo++;
                String line = raw.replaceAll("\\s+", " ")
                    .trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] t = line.split(" ");
                switch (t[0]) {
                    case "v":
                        v.add(floats(t, 3, source, lineNo));
                        break;
                    case "vn":
                        vn.add(floats(t, 3, source, lineNo));
                        break;
                    case "vt": {
                        float[] uv = floats(t, 2, source, lineNo);
                        vt.add(new float[] { uv[0], 1 - uv[1] });
                        break;
                    }
                    case "f": {
                        if (faces == null) {
                            groupName = "Default";
                            faces = new ArrayList<>();
                        }
                        faces.add(face(t, v, vt, vn, source, lineNo));
                        break;
                    }
                    case "g":
                    case "o":
                        if (t.length < 2) {
                            throw new FormatException(source + ":" + lineNo + ": group without a name");
                        }
                        if (faces != null) {
                            groups.add(new Group(groupName, faces));
                        }
                        groupName = t[1];
                        faces = new ArrayList<>();
                        break;
                    default:
                        break; // mtllib, usemtl, s: ignored, as SlashBlade does
                }
            }
        } catch (IOException e) {
            throw new FormatException(source + ": " + e);
        }
        if (faces != null) {
            groups.add(new Group(groupName, faces));
        }
        return new ObjModel(groups);
    }

    private static float[] floats(String[] t, int n, String source, int lineNo) throws FormatException {
        if (t.length < n + 1) {
            throw new FormatException(source + ":" + lineNo + ": " + t[0] + " needs " + n + " numbers");
        }
        float[] out = new float[n];
        try {
            for (int i = 0; i < n; i++) {
                out[i] = Float.parseFloat(t[i + 1]);
                if (!Float.isFinite(out[i])) {
                    throw new NumberFormatException(t[i + 1]);
                }
            }
        } catch (NumberFormatException e) {
            throw new FormatException(source + ":" + lineNo + ": bad number " + e.getMessage());
        }
        return out;
    }

    private static Face face(String[] t, List<float[]> v, List<float[]> vt, List<float[]> vn, String source, int lineNo)
        throws FormatException {
        int n = t.length - 1;
        if (n != 3 && n != 4) {
            throw new FormatException(source + ":" + lineNo + ": a face needs 3 or 4 vertices, has " + n);
        }
        float[][] pos = new float[n][];
        float[][] uv = null;
        float[][] nor = null;
        for (int i = 0; i < n; i++) {
            String[] idx = t[i + 1].split("/", -1);
            pos[i] = at(v, idx[0], source, lineNo, "v");
            if (idx.length > 1 && !idx[1].isEmpty()) {
                if (uv == null) {
                    uv = new float[n][];
                }
                uv[i] = at(vt, idx[1], source, lineNo, "vt");
            }
            if (idx.length > 2 && !idx[2].isEmpty()) {
                if (nor == null) {
                    nor = new float[n][];
                }
                nor[i] = at(vn, idx[2], source, lineNo, "vn");
            }
        }
        if (uv != null && hasNull(uv) || nor != null && hasNull(nor)) {
            throw new FormatException(source + ":" + lineNo + ": a face mixes vertices with and without vt or vn");
        }
        return new Face(pos, uv, nor);
    }

    private static boolean hasNull(float[][] a) {
        for (float[] x : a) {
            if (x == null) {
                return true;
            }
        }
        return false;
    }

    private static float[] at(List<float[]> list, String index, String source, int lineNo, String what)
        throws FormatException {
        try {
            int i = Integer.parseInt(index);
            int k = i > 0 ? i - 1 : list.size() + i; // negative indices count from the end (OBJ)
            if (k < 0 || k >= list.size()) {
                throw new NumberFormatException();
            }
            return list.get(k);
        } catch (NumberFormatException e) {
            throw new FormatException(
                String
                    .format(Locale.ROOT, "%s:%d: no %s number %s (%d read)", source, lineNo, what, index, list.size()));
        }
    }
}
