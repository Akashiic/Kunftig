package com.akashic.kunftig.modern.math;

/**
 * The modern {@code net.minecraft.util.Mth} (1.17+ names and semantics), for porting modern code.
 * Trigonometry uses the same 65536-entry sine table as vanilla.
 */
public final class Mth {

    public static final float PI = (float) Math.PI;
    public static final float HALF_PI = (float) (Math.PI / 2D);
    public static final float TWO_PI = (float) (Math.PI * 2D);
    public static final float DEG_TO_RAD = (float) (Math.PI / 180D);
    public static final float RAD_TO_DEG = (float) (180D / Math.PI);
    public static final float EPSILON = 1.0E-5F;
    public static final float SQRT_OF_TWO = (float) Math.sqrt(2D);
    public static final double HALF = 0.5D;
    public static final double TWO = 2D;

    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin(i * Math.PI * 2D / 65536D);
    }

    private Mth() {}

    public static float sin(float value) {
        return SIN[(int) (value * 10430.378F) & 65535];
    }

    public static float cos(float value) {
        return SIN[(int) (value * 10430.378F + 16384F) & 65535];
    }

    public static float sin(double value) {
        return sin((float) value);
    }

    public static float cos(double value) {
        return cos((float) value);
    }

    public static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    public static int floor(float value) {
        int i = (int) value;

        return value < i ? i - 1 : i;
    }

    public static int floor(double value) {
        int i = (int) value;

        return value < i ? i - 1 : i;
    }

    public static long lfloor(double value) {
        long i = (long) value;

        return value < i ? i - 1L : i;
    }

    public static int ceil(float value) {
        int i = (int) value;

        return value > i ? i + 1 : i;
    }

    public static int ceil(double value) {
        int i = (int) value;

        return value > i ? i + 1 : i;
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static long clamp(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clampedLerp(double start, double end, double delta) {
        if (delta < 0D) return start;

        return delta > 1D ? end : lerp(delta, start, end);
    }

    public static float clampedLerp(float start, float end, float delta) {
        if (delta < 0F) return start;

        return delta > 1F ? end : lerp(delta, start, end);
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static int lerpInt(float delta, int start, int end) {
        return start + floor(delta * (end - start));
    }

    public static float inverseLerp(float delta, float start, float end) {
        return (delta - start) / (end - start);
    }

    public static double inverseLerp(double delta, double start, double end) {
        return (delta - start) / (end - start);
    }

    public static float wrapDegrees(float value) {
        float f = value % 360F;

        if (f >= 180F) f -= 360F;

        if (f < -180F) f += 360F;

        return f;
    }

    public static double wrapDegrees(double value) {
        double d = value % 360D;

        if (d >= 180D) d -= 360D;

        if (d < -180D) d += 360D;

        return d;
    }

    public static int wrapDegrees(int value) {
        int i = value % 360;

        if (i >= 180) i -= 360;

        if (i < -180) i += 360;

        return i;
    }

    public static float degreesDifference(float start, float end) {
        return wrapDegrees(end - start);
    }

    public static float rotLerp(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }

    public static double rotLerp(double delta, double start, double end) {
        return start + delta * wrapDegrees(end - start);
    }

    public static float approach(float value, float limit, float step) {
        step = Math.abs(step);

        return value < limit ? clamp(value + step, value, limit) : clamp(value - step, limit, value);
    }

    public static double atan2(double y, double x) {
        return Math.atan2(y, x);
    }

    public static float abs(float value) {
        return Math.abs(value);
    }

    public static int abs(int value) {
        return Math.abs(value);
    }

    public static int sign(double value) {
        if (value == 0D) return 0;

        return value > 0D ? 1 : -1;
    }

    public static boolean equal(float a, float b) {
        return Math.abs(b - a) < EPSILON;
    }

    public static boolean equal(double a, double b) {
        return Math.abs(b - a) < EPSILON;
    }

    public static int positiveModulo(int value, int mod) {
        return Math.floorMod(value, mod);
    }

    public static float positiveModulo(float value, float mod) {
        return (value % mod + mod) % mod;
    }

    public static double positiveModulo(double value, double mod) {
        return (value % mod + mod) % mod;
    }

    public static float frac(float value) {
        return value - floor(value);
    }

    public static double frac(double value) {
        return value - lfloor(value);
    }

    public static int nextInt(java.util.Random random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    public static float nextFloat(java.util.Random random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }

    public static double nextDouble(java.util.Random random, double min, double max) {
        return min >= max ? min : random.nextDouble() * (max - min) + min;
    }

    public static float catmullrom(float delta, float p0, float p1, float p2, float p3) {
        return 0.5F * (2F * p1 + (p2 - p0) * delta
            + (2F * p0 - 5F * p1 + 4F * p2 - p3) * delta * delta
            + (3F * p1 - p0 - 3F * p2 + p3) * delta * delta * delta);
    }

    public static float smoothstep(float value) {
        return value * value * value * (value * (value * 6F - 15F) + 10F);
    }

    public static double smoothstep(double value) {
        return value * value * value * (value * (value * 6D - 15D) + 10D);
    }

    public static float square(float value) {
        return value * value;
    }

    public static double square(double value) {
        return value * value;
    }

    public static double lengthSquared(double x, double y) {
        return x * x + y * y;
    }

    public static double length(double x, double y) {
        return Math.sqrt(lengthSquared(x, y));
    }

    public static double lengthSquared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    public static double length(double x, double y, double z) {
        return Math.sqrt(lengthSquared(x, y, z));
    }

    public static float invSqrt(float value) {
        return (float) (1D / Math.sqrt(value));
    }

    public static double invSqrt(double value) {
        return 1D / Math.sqrt(value);
    }

    public static long murmurHash3Mixer(long value) {
        value ^= value >>> 33;
        value *= -49064778989728563L;
        value ^= value >>> 33;
        value *= -4265267296055464877L;
        value ^= value >>> 33;

        return value;
    }

    public static int murmurHash3Mixer(int value) {
        value ^= value >>> 16;
        value *= -2048144789;
        value ^= value >>> 13;
        value *= -1028477387;
        value ^= value >>> 16;

        return value;
    }

    public static int hsvToRgb(float hue, float saturation, float value) {
        int i = (int) (hue * 6F) % 6;
        float f = hue * 6F - (float) i;
        float f1 = value * (1F - saturation);
        float f2 = value * (1F - f * saturation);
        float f3 = value * (1F - (1F - f) * saturation);

        float r;
        float g;
        float b;

        switch (i) {
            case 0 -> {
                r = value;
                g = f3;
                b = f1;
            }
            case 1 -> {
                r = f2;
                g = value;
                b = f1;
            }
            case 2 -> {
                r = f1;
                g = value;
                b = f3;
            }
            case 3 -> {
                r = f1;
                g = f2;
                b = value;
            }
            case 4 -> {
                r = f3;
                g = f1;
                b = value;
            }
            case 5 -> {
                r = value;
                g = f1;
                b = f2;
            }
            default -> throw new IllegalArgumentException("Bad color: " + hue + ", " + saturation + ", " + value);
        }

        return clamp((int) (r * 255F), 0, 255) << 16 | clamp((int) (g * 255F), 0, 255) << 8
            | clamp((int) (b * 255F), 0, 255);
    }

    public static int hsvToArgb(float hue, float saturation, float value, int alpha) {
        return clamp(alpha, 0, 255) << 24 | hsvToRgb(hue, saturation, value);
    }

}
