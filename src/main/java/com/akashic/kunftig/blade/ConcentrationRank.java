package com.akashic.kunftig.blade;

/**
 * SlashBlade's concentration ("style") rank as its client keeps it ({@code IConcentrationRank}, Resharped, MIT code):
 * raw points, 300 per level up to 1799, decaying one point per tick since the last update but never below the floor
 * of the level they are in, so D..A stay at the bottom of their rank and S, SS and SSS fall back to S. The rank code is
 * {@link EffectWire#rankCode(float)}: 0 none, 1 D ... 5 S, 6 SS, 7 SSS. Times are game ticks. Not thread-safe.
 */
public final class ConcentrationRank {

    public static final int UNIT = 300;
    public static final int MAX_POINTS = 6 * UNIT - 1;
    /** Ticks the HUD shows after the last change. */
    public static final int VISIBLE_TICKS = 120;
    /** Ticks the HUD shows the rank's text after a rise. */
    public static final int TEXT_TICKS = 20;

    private long raw;
    private long lastUpdate;
    private long lastRise = Long.MIN_VALUE / 2;

    public long points(long now) {
        long reduction = Math.max(0, now - lastUpdate);
        return raw - Math.min(reduction, raw % UNIT);
    }

    public float level(long now) {
        return points(now) / (float) UNIT;
    }

    public int rank(long now) {
        return EffectWire.rankCode(level(now));
    }

    /** How far into its rank the level is, 0..1 (S and SS span 0.25 levels, SSS 0.5). */
    public float progress(long now) {
        float level = level(now);
        double bottom;
        double top;
        switch (rank(now)) {
            case 0:
                bottom = 0;
                top = 1;
                break;
            case 5:
                bottom = 5;
                top = 5.25;
                break;
            case 6:
                bottom = 5.25;
                top = 5.5;
                break;
            case 7:
                bottom = 5.5;
                top = 6; // SlashBlade: floor(bottom + 1)
                break;
            default:
                bottom = Math.floor(level);
                top = bottom + 1;
                break;
        }
        return (float) ((level - bottom) / (top - bottom));
    }

    /** The server's points arriving at {@code now}: SlashBlade's {@code RankSyncMessage}. */
    public void sync(long points, long now) {
        int old = rank(now);
        raw = Math.max(0, Math.min(MAX_POINTS, points));
        lastUpdate = now;
        if (old < rank(now)) {
            lastRise = now;
        }
    }

    /** SlashBlade's {@code addRankPoint}: the decayed points plus {@code delta}, clamped. */
    public void add(long delta, long now) {
        sync(points(now) + delta, now);
    }

    public long lastUpdate() {
        return lastUpdate;
    }

    public boolean visible(long now) {
        return rank(now) > 0 && now < lastUpdate + VISIBLE_TICKS;
    }

    public boolean showText(long now) {
        return now < lastRise + TEXT_TICKS;
    }

    public void reset() {
        raw = 0;
        lastUpdate = 0;
        lastRise = Long.MIN_VALUE / 2;
    }
}
