package com.akashic.kunftig.common.text;

/** How colors are written into legacy {@code §}-formatted strings. */
public enum ColorMode {

    /**
     * 24-bit colors as Spigot's {@code §x§r§r§g§g§b§b} sequence (understood by Angelica's font renderer
     * with RGB enabled and by modern servers). Colors that are exactly a legacy color still use the short form.
     */
    RGB,

    /** Every color reduced to the nearest of the 16 legacy colors (vanilla 1.7.10 font renderer). */
    LEGACY
}
