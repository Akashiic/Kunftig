package com.akashic.kunftig.blade;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** Writes small VMD files for tests. */
final class VmdWriter {

    private final String model;
    private final List<Object[]> keys = new ArrayList<>();

    VmdWriter(String model) {
        this.model = model;
    }

    VmdWriter key(String bone, int frame, double px, double py, double pz, double qx, double qy, double qz, double qw) {
        keys.add(
            new Object[] { bone, frame,
                new float[] { (float) px, (float) py, (float) pz, (float) qx, (float) qy, (float) qz, (float) qw } });
        return this;
    }

    byte[] bytes() {
        ByteBuffer b = ByteBuffer.allocate(54 + keys.size() * 111 + 4)
            .order(ByteOrder.LITTLE_ENDIAN);
        put(b, "Vocaloid Motion Data 0002", 30);
        put(b, model, 20);
        b.putInt(keys.size());
        for (Object[] k : keys) {
            put(b, (String) k[0], 15);
            b.putInt((Integer) k[1]);
            for (float f : (float[]) k[2]) {
                b.putFloat(f);
            }
            b.put(new byte[64]);
        }
        b.putInt(0);
        return b.array();
    }

    private static void put(ByteBuffer b, String s, int len) {
        byte[] raw = s.getBytes(Charset.forName("Shift_JIS"));
        byte[] out = new byte[len];
        System.arraycopy(raw, 0, out, 0, Math.min(len, raw.length));
        b.put(out);
    }
}
