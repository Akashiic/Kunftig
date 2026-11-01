package com.akashic.kunftig.api.particle;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Particles a Kunftig server can show, named as in Minecraft 1.21 ({@code org.bukkit.Particle} of Paper 1.21.1).
 * <p>
 * <b>Vanilla particles</b> ({@link #isModern()} false) exist in every 1.7.10 client. <b>Modern particles</b> are drawn
 * by clients that have the Kunftig mod and Et Futurum Requiem; every other client sees {@link #fallback()}, a 1.7.10
 * particle chosen to look as close as possible (a Kunftig client without Et Futurum shows the fallback too).
 * <p>
 * Pure Java on purpose: the client mod uses the same table.
 */
public enum KunftigParticle {

    // ---------------------------------------------------------------- vanilla 1.7.10
    POOF("explode"),
    EXPLOSION("largeexplode"),
    EXPLOSION_EMITTER("hugeexplosion"),
    FIREWORK("fireworksSpark"),
    BUBBLE("bubble"),
    SPLASH("splash"),
    /** The fishing bobber wake ({@code WATER_WAKE} before 1.13). */
    FISHING("wake"),
    UNDERWATER("suspended"),
    /** Void fog particles; removed in 1.13. */
    DEPTH_SUSPEND("depthsuspend"),
    CRIT("crit"),
    ENCHANTED_HIT("magicCrit"),
    SMOKE("smoke"),
    LARGE_SMOKE("largesmoke"),
    EFFECT("spell"),
    INSTANT_EFFECT("instantSpell"),
    /** Potion swirls; takes a {@linkplain DataKind#COLOR color}. */
    ENTITY_EFFECT("mobSpell", DataKind.COLOR),
    /** Beacon swirls; takes a {@linkplain DataKind#COLOR color}. Removed in 1.20.5. */
    AMBIENT_ENTITY_EFFECT("mobSpellAmbient", DataKind.COLOR),
    WITCH("witchMagic"),
    DRIPPING_WATER("dripWater"),
    DRIPPING_LAVA("dripLava"),
    ANGRY_VILLAGER("angryVillager"),
    HAPPY_VILLAGER("happyVillager"),
    MYCELIUM("townaura"),
    /** With count 0, offset X is the note color (note / 24.0) and speed must be 1, as in modern Bukkit. */
    NOTE("note"),
    PORTAL("portal"),
    ENCHANT("enchantmenttable"),
    FLAME("flame"),
    LAVA("lava"),
    /** Removed in 1.13. */
    FOOTSTEP("footstep"),
    CLOUD("cloud"),
    /** Redstone dust ({@code REDSTONE} before 1.13); takes a {@linkplain DataKind#COLOR color}. */
    DUST("reddust", DataKind.COLOR),
    ITEM_SNOWBALL("snowballpoof"),
    /** Removed in 1.13. */
    SNOW_SHOVEL("snowshovel"),
    ITEM_SLIME("slime"),
    HEART("heart"),
    /** Item breaking; takes an {@linkplain DataKind#ITEM item}. */
    ITEM("iconcrack_", DataKind.ITEM),
    /** Block breaking; takes a {@linkplain DataKind#BLOCK block}. */
    BLOCK("blockcrack_", DataKind.BLOCK),
    /** Block dust (sprinting, landing); takes a {@linkplain DataKind#BLOCK block}. Merged into BLOCK in 1.13. */
    BLOCK_DUST("blockdust_", DataKind.BLOCK),

    // ---------------------------------------------------------------- modern, drawn by Et Futurum Requiem
    END_ROD(FIREWORK, DataKind.NONE, 0),
    SOUL_FIRE_FLAME(FLAME, DataKind.NONE, 0),
    /** Takes a {@linkplain DataKind#COLOR color} (modern Minecraft derives it from a block). Default gray. */
    FALLING_DUST(DUST, DataKind.COLOR, 0xFF7F7F7F),
    /** Default color is Et Futurum's honey; {@code color(...)} recolors the drop. */
    DRIPPING_HONEY(DRIPPING_LAVA, DataKind.COLOR, 0xFF8E6D14),
    /** Default color is 1.16's obsidian tear; {@code color(...)} recolors the drop. */
    DRIPPING_OBSIDIAN_TEAR(PORTAL, DataKind.COLOR, 0xFF8308E3),
    CHERRY_LEAVES(DUST, DataKind.NONE, 0xFFFFB7C5),
    CRIMSON_SPORE(DUST, DataKind.NONE, 0xFFC93C3C),
    WARPED_SPORE(DUST, DataKind.NONE, 0xFF2BB5A0),
    ASH(MYCELIUM, DataKind.NONE, 0),
    /** Rising bubbles of a soul sand bubble column. */
    BUBBLE_COLUMN_UP(BUBBLE, DataKind.NONE, 0),
    /** Sinking bubbles of a magma block bubble column. */
    CURRENT_DOWN(BUBBLE, DataKind.NONE, 0),
    FALLING_NECTAR(DUST, DataKind.NONE, 0xFFF2C94C),
    /** The dark hearts of 1.9+ attack damage. */
    DAMAGE_INDICATOR(CRIT, DataKind.NONE, 0),
    WAX_ON(HAPPY_VILLAGER, DataKind.NONE, 0),
    WAX_OFF(INSTANT_EFFECT, DataKind.NONE, 0),
    SCRAPE(INSTANT_EFFECT, DataKind.NONE, 0);

    /** What a particle needs besides position, offsets, speed and count. */
    public enum DataKind {
        /** Nothing. */
        NONE,
        /** An RGB color (optional; {@link #defaultColor()} when absent). */
        COLOR,
        /** An item id and damage value (required). */
        ITEM,
        /** A block id and metadata (required). */
        BLOCK
    }

    private static final Map<String, KunftigParticle> BY_NAME = new HashMap<>();

    static {
        for (KunftigParticle particle : values()) {
            BY_NAME.put(particle.name(), particle);
        }
        // Names of org.bukkit.Particle before 1.13 (Crucible, Spigot 1.9-1.12) and of Bukkit 1.13-1.20.4.
        String[][] aliases = { { "EXPLOSION_NORMAL", "POOF" }, { "EXPLOSION_LARGE", "EXPLOSION" },
            { "EXPLOSION_HUGE", "EXPLOSION_EMITTER" }, { "FIREWORKS_SPARK", "FIREWORK" }, { "WATER_BUBBLE", "BUBBLE" },
            { "WATER_SPLASH", "SPLASH" }, { "WATER_WAKE", "FISHING" }, { "SUSPENDED", "UNDERWATER" },
            { "SUSPENDED_DEPTH", "DEPTH_SUSPEND" }, { "CRIT_MAGIC", "ENCHANTED_HIT" }, { "SMOKE_NORMAL", "SMOKE" },
            { "SMOKE_LARGE", "LARGE_SMOKE" }, { "SPELL", "EFFECT" }, { "SPELL_INSTANT", "INSTANT_EFFECT" },
            { "SPELL_MOB", "ENTITY_EFFECT" }, { "SPELL_MOB_AMBIENT", "AMBIENT_ENTITY_EFFECT" },
            { "SPELL_WITCH", "WITCH" }, { "DRIP_WATER", "DRIPPING_WATER" }, { "DRIP_LAVA", "DRIPPING_LAVA" },
            { "VILLAGER_ANGRY", "ANGRY_VILLAGER" }, { "VILLAGER_HAPPY", "HAPPY_VILLAGER" },
            { "TOWN_AURA", "MYCELIUM" }, { "ENCHANTMENT_TABLE", "ENCHANT" }, { "REDSTONE", "DUST" },
            { "SNOWBALL", "ITEM_SNOWBALL" }, { "SLIME", "ITEM_SLIME" }, { "ITEM_CRACK", "ITEM" },
            { "BLOCK_CRACK", "BLOCK" } };
        for (String[] alias : aliases) {
            BY_NAME.put(alias[0], valueOf(alias[1]));
        }
    }

    private final String vanillaName;
    private final KunftigParticle fallback;
    private final DataKind dataKind;
    private final int defaultColor;

    /** A 1.7.10 particle. */
    KunftigParticle(String vanillaName) {
        this(vanillaName, DataKind.NONE);
    }

    KunftigParticle(String vanillaName, DataKind dataKind) {
        this.vanillaName = vanillaName;
        this.fallback = null;
        this.dataKind = dataKind;
        this.defaultColor = 0;
    }

    /** A modern particle shown as {@code fallback} (tinted with {@code defaultColor} if it takes a color). */
    KunftigParticle(KunftigParticle fallback, DataKind dataKind, int defaultColor) {
        this.vanillaName = null;
        this.fallback = fallback;
        this.dataKind = dataKind;
        this.defaultColor = defaultColor;
    }

    /** True if 1.7.10 clients cannot draw it (it needs Kunftig and Et Futurum Requiem on the client). */
    public boolean isModern() {
        return vanillaName == null;
    }

    /** The 1.7.10 particle shown to clients that cannot draw this one; {@code this} for vanilla particles. */
    public KunftigParticle fallback() {
        return fallback == null ? this : fallback;
    }

    public DataKind dataKind() {
        return dataKind;
    }

    /** ARGB color used when none is given, or 0 if none. */
    public int defaultColor() {
        return defaultColor;
    }

    /**
     * The name 1.7.10 uses for this particle on the wire, e.g. {@code wake} or {@code iconcrack_} (completed with the
     * ids); null for modern particles.
     */
    public String vanillaName() {
        return vanillaName;
    }

    /**
     * The particle with this name, ignoring case: Kunftig/Bukkit 1.21 names ({@code FISHING}, {@code DUST}), names of
     * {@code org.bukkit.Particle} on Crucible and Spigot 1.9 to 1.12 ({@code WATER_WAKE}, {@code REDSTONE}), or
     * null. Crucible's {@code BARRIER}, {@code WATER_DROP}, {@code ITEM_TAKE}, {@code MOB_APPEARANCE},
     * {@code DRAGON_BREATH}, {@code SWEEP_ATTACK}, {@code TOTEM} and {@code SPIT} have no 1.7.10 or Et Futurum
     * implementation and give null.
     */
    public static KunftigParticle fromName(String name) {
        return name == null ? null : BY_NAME.get(name.toUpperCase(Locale.ROOT));
    }
}
