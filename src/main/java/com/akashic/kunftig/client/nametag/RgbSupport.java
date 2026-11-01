package com.akashic.kunftig.client.nametag;

import java.lang.reflect.Field;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.KunftigConfig;
import com.akashic.kunftig.common.text.ColorMode;

/**
 * Decides whether 24-bit colors can be drawn. Angelica's batching font renderer draws Spigot's {@code §x} format
 * when {@code enableFontRenderer} and {@code enableRGBColors} are on; the vanilla renderer would instead read the
 * hex digits as 16-color codes. Angelica is read reflectively: it is optional and its config class may change.
 */
public final class RgbSupport {

    private static final String ANGELICA_CONFIG = "com.gtnewhorizons.angelica.config.AngelicaConfig";

    private static boolean probed;
    private static Field fontRendererField;
    private static Field rgbColorsField;
    private static volatile ColorMode current = ColorMode.LEGACY;

    private RgbSupport() {}

    /** The color mode for rendering now. Cheap: a volatile read. */
    public static ColorMode current() {
        return current;
    }

    /** Re-evaluates the mode; call from the client thread (once per tick). */
    public static void refresh() {
        switch (KunftigConfig.nametagColorMode) {
            case RGB:
                current = ColorMode.RGB;
                return;
            case LEGACY:
                current = ColorMode.LEGACY;
                return;
            case AUTO:
            default:
                current = angelicaRgbEnabled() ? ColorMode.RGB : ColorMode.LEGACY;
        }
    }

    private static boolean angelicaRgbEnabled() {
        if (!probed) {
            probed = true;
            try {
                Class<?> config = Class.forName(ANGELICA_CONFIG, false, RgbSupport.class.getClassLoader());
                fontRendererField = config.getField("enableFontRenderer");
                rgbColorsField = config.getField("enableRGBColors");
                Kunftig.LOG.info("Angelica detected: RGB nametags follow its enableFontRenderer/enableRGBColors");
            } catch (ClassNotFoundException e) {
                Kunftig.LOG.info("Angelica not present: nametag colors are reduced to the 16 legacy colors");
            } catch (NoSuchFieldException | LinkageError e) {
                Kunftig.LOG.warn("This Angelica version has no RGB text switch; nametag colors use 16 colors", e);
            }
        }
        if (fontRendererField == null || rgbColorsField == null) {
            return false;
        }
        try {
            return fontRendererField.getBoolean(null) && rgbColorsField.getBoolean(null);
        } catch (IllegalAccessException | RuntimeException e) {
            fontRendererField = null;
            Kunftig.LOG.warn("Could not read Angelica's RGB switch; nametag colors use 16 colors", e);
            return false;
        }
    }
}
