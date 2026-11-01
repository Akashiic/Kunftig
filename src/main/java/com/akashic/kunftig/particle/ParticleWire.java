package com.akashic.kunftig.particle;

import java.util.Locale;

import com.akashic.kunftig.api.particle.KunftigParticle;
import com.akashic.kunftig.api.particle.KunftigParticle.DataKind;

/**
 * How Kunftig particles travel: inside the vanilla particle packet ({@code S2APacketParticles}), whose name field
 * (up to 64 characters) carries {@code kunftig:<particle>} or {@code kunftig:<particle>#AARRGGBB}. Position, offsets,
 * speed and count keep their vanilla meaning.
 * <p>
 * Only clients that registered {@link #CHANNEL} receive these names; 1.7.10 clients without Kunftig would ignore them
 * (an unknown name spawns nothing), but they are sent the vanilla fallback instead. No Minecraft classes here.
 */
public final class ParticleWire {

    /** Registered by Kunftig clients that draw these packets (Bukkit 1.7 allows at most 16 characters). */
    public static final String CHANNEL = "kunftig:fx";
    public static final String PREFIX = "kunftig:";
    /** Length limit of the name in {@code S2APacketParticles}. */
    public static final int MAX_NAME_LENGTH = 64;

    /** Smallest red component sent to {@code reddust}: 1.7.10 replaces exactly 0 with 1 (full red). */
    static final float MIN_DUST_RED = 1.0E-4F;

    private ParticleWire() {}

    /** A decoded Kunftig particle name. */
    public static final class Decoded {

        public final KunftigParticle particle;
        /** ARGB color sent with the particle, or 0. */
        public final int color;

        Decoded(KunftigParticle particle, int color) {
            this.particle = particle;
            this.color = color;
        }
    }

    public static boolean isKunftig(String name) {
        return name != null && name.startsWith(PREFIX);
    }

    /** {@code kunftig:end_rod}, or {@code kunftig:falling_dust#ffaa5500} with a color (0 for none). */
    public static String encode(KunftigParticle particle, int argb) {
        String name = PREFIX + particle.name()
            .toLowerCase(Locale.ROOT);
        if (argb != 0) {
            name += "#" + String.format(Locale.ROOT, "%08x", argb);
        }
        return name;
    }

    /** The particle of a Kunftig name, or null if malformed or unknown to this version (then draw nothing). */
    public static Decoded decode(String name) {
        if (!isKunftig(name) || name.length() > MAX_NAME_LENGTH) {
            return null;
        }
        String body = name.substring(PREFIX.length());
        int color = 0;
        int hash = body.indexOf('#');
        if (hash >= 0) {
            String hex = body.substring(hash + 1);
            if (hex.length() != 8) {
                return null;
            }
            try {
                color = (int) Long.parseLong(hex, 16);
            } catch (NumberFormatException e) {
                return null;
            }
            body = body.substring(0, hash);
        }
        KunftigParticle particle;
        try {
            particle = KunftigParticle.valueOf(body.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!body.equals(
            particle.name()
                .toLowerCase(Locale.ROOT))) {
            return null;
        }
        return new Decoded(particle, color);
    }

    /**
     * The 1.7.10 name of a vanilla particle, with item/block ids for {@code iconcrack_}, {@code blockcrack_} and
     * {@code blockdust_} (the client needs both numbers for blocks).
     */
    public static String vanillaName(KunftigParticle particle, int id, int meta) {
        if (particle.isModern()) {
            throw new IllegalArgumentException(particle + " has no 1.7.10 name");
        }
        DataKind kind = particle.dataKind();
        if (kind == DataKind.ITEM || kind == DataKind.BLOCK) {
            return particle.vanillaName() + id + "_" + meta;
        }
        return particle.vanillaName();
    }

    /** The color the fallback of {@code particle} is tinted with: the sent color, else the particle's default. */
    public static int fallbackColor(KunftigParticle particle, int sentColor) {
        return sentColor != 0 ? sentColor : particle.defaultColor();
    }

    /**
     * The velocity that makes a 1.7.10 colored particle ({@code reddust}, {@code mobSpell}, {@code mobSpellAmbient})
     * take {@code argb}: these particles read their color from the velocity arguments.
     */
    public static float[] colorAsVelocity(KunftigParticle vanilla, int argb) {
        float red = (argb >> 16 & 0xFF) / 255.0F;
        float green = (argb >> 8 & 0xFF) / 255.0F;
        float blue = (argb & 0xFF) / 255.0F;
        if (vanilla == KunftigParticle.DUST && red == 0) {
            red = MIN_DUST_RED;
        }
        return new float[] { red, green, blue };
    }
}
