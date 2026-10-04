package com.lotusblight.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screech's mesh, read once from the mod's resources: the triangles of every body part (made by tools/screech/sculpt_screech.py, a near
 * and a far level of detail) and the bones they hang on. The mesh is in units of 1/16 block, y up, front +z, the creature's left +x.
 */
public final class ScreechModel {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String BASE = "/assets/lotusblight/models/";
    private static ScreechModel instance;
    private static boolean tried;

    /** One body part: its bone and its triangles as 8 floats per vertex (position 3, normal 3, uv 2), three vertices a triangle. */
    public static final class Part {
        public final String name;
        public final int bone;
        public final float[] verts;
        /** Per triangle: whether it lies on a glowing texture (the hollows and the eye). */
        public final boolean[] glow;

        Part(String name, int bone, float[] verts) {
            this.name = name;
            this.bone = bone;
            this.verts = verts;
            int tris = verts.length / 24;
            this.glow = new boolean[tris];
            for (int t = 0; t < tris; t++) {
                float u = verts[t * 24 + 6] * 256f, v = verts[t * 24 + 7] * 256f;     // the first vertex's uv decides
                glow[t] = u < 64f && v >= 128f;                                       // the hollows' and the eye's tiles
            }
        }
    }

    public final String[] boneNames;
    public final int[] boneParent;
    public final float[][] bonePivot;                     // in model units
    public final Map<String, Integer> boneIndex = new HashMap<>();
    public final Part[] near, far;

    private ScreechModel(String[] names, int[] parents, float[][] pivots, Part[] near, Part[] far) {
        this.boneNames = names;
        this.boneParent = parents;
        this.bonePivot = pivots;
        this.near = near;
        this.far = far;
        for (int i = 0; i < names.length; i++) boneIndex.put(names[i], i);
    }

    /** The model, or null if its files can't be read (the creature is then simply not drawn). */
    public static synchronized ScreechModel get() {
        if (!tried) {
            tried = true;
            try {
                instance = load();
            } catch (Exception e) {
                LOG.warn("Screech: can't read the model: {}", e.toString());
            }
        }
        return instance;
    }

    private static ScreechModel load() throws IOException {
        JsonObject skeleton;
        try (InputStream in = ScreechModel.class.getResourceAsStream(BASE + "screech.skeleton.json")) {
            if (in == null) throw new IOException("screech.skeleton.json is missing");
            skeleton = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonArray bones = skeleton.getAsJsonArray("bones");
        int n = bones.size();
        String[] names = new String[n];
        int[] parents = new int[n];
        float[][] pivots = new float[n][3];
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            JsonObject b = bones.get(i).getAsJsonObject();
            names[i] = b.get("name").getAsString();
            index.put(names[i], i);
        }
        for (int i = 0; i < n; i++) {
            JsonObject b = bones.get(i).getAsJsonObject();
            JsonElement parent = b.get("parent");
            parents[i] = parent == null || parent.isJsonNull() ? -1 : index.get(parent.getAsString());
            JsonArray p = b.getAsJsonArray("pivot");
            for (int k = 0; k < 3; k++) pivots[i][k] = p.get(k).getAsFloat();
        }
        return new ScreechModel(names, parents, pivots, readParts("screech.bin", index), readParts("screech_far.bin", index));
    }

    private static Part[] readParts(String file, Map<String, Integer> boneIndex) throws IOException {
        byte[] data;
        try (InputStream in = ScreechModel.class.getResourceAsStream(BASE + file)) {
            if (in == null) throw new IOException(file + " is missing");
            data = in.readAllBytes();
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.get() != 'S' || buf.get() != 'C' || buf.get() != 'R' || buf.get() != 'M') throw new IOException(file + " is not a Screech mesh");
        buf.getInt();                                     // version
        int count = buf.getInt();
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int len = buf.get() & 0xFF;
            byte[] name = new byte[len];
            buf.get(name);
            String partName = new String(name, StandardCharsets.UTF_8);
            int tris = buf.getInt();
            float[] verts = new float[tris * 3 * 8];
            for (int k = 0; k < verts.length; k++) verts[k] = buf.getFloat();
            Integer bone = boneIndex.get(partName);
            if (bone == null) throw new IOException("a part of " + file + " has no bone: " + partName);
            parts.add(new Part(partName, bone, verts));
        }
        return parts.toArray(new Part[0]);
    }
}
