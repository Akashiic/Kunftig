package com.akashic.kunftig.api.bukkit.hud;

import java.util.regex.Pattern;

/**
 * One element of a player's screen HUD ({@link KunftigHud}): text (with {@code §} colors and line breaks), a bar, a
 * picture from the resource pack, or any of them together, placed from an anchor of the screen. Immutable; built with
 * {@link #builder}.
 *
 * <pre>
 * HudElement rank = HudElement.builder("overkill:rank")
 *     .anchor(HudElement.Anchor.RIGHT).offset(-12, -30).scale(2)
 *     .texture("overkill:textures/hud/rank_s.png", 32, 32)
 *     .text("§6§lSTYLISH")
 *     .bar(0.65f, 64, 0xFFFFAA00, 0x80000000)
 *     .duration(100, 20)                       // 5 s on screen, the last second fading
 *     .build();
 * </pre>
 *
 * Layout, top to bottom from the anchor: the picture, the text lines, the bar. Positions and sizes are GUI pixels
 * (the scaled resolution, like vanilla's HUD), times {@link Builder#scale}.
 */
public final class HudElement {

    /** Where on the screen the offset counts from (the element is aligned to the same side). */
    public enum Anchor {
        TOP_LEFT,
        TOP,
        TOP_RIGHT,
        LEFT,
        CENTER,
        RIGHT,
        BOTTOM_LEFT,
        BOTTOM,
        BOTTOM_RIGHT
    }

    /** {@code namespace:path}, lower case: the namespace is the plugin's, the path names the element. */
    public static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,64}");
    /** A PNG of a resource pack, {@code namespace:path.png}. */
    public static final Pattern TEXTURE = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}\\.png");

    private final String id;
    private final Anchor anchor;
    private final int x;
    private final int y;
    private final float scale;
    private final String text;
    private final float progress;
    private final int barWidth;
    private final int barColor;
    private final int barBackground;
    private final String texture;
    private final int textureWidth;
    private final int textureHeight;
    private final int durationTicks;
    private final int fadeTicks;

    private HudElement(Builder b) {
        id = b.id;
        anchor = b.anchor;
        x = b.x;
        y = b.y;
        scale = b.scale;
        text = b.text;
        progress = b.progress;
        barWidth = b.barWidth;
        barColor = b.barColor;
        barBackground = b.barBackground;
        texture = b.texture;
        textureWidth = b.textureWidth;
        textureHeight = b.textureHeight;
        durationTicks = b.durationTicks;
        fadeTicks = b.fadeTicks;
    }

    /** @throws IllegalArgumentException for an id that does not match {@link #ID} */
    public static Builder builder(String id) {
        if (id == null || !ID.matcher(id)
            .matches()) {
            throw new IllegalArgumentException("hud element id must match " + ID + ": " + id);
        }
        return new Builder(id);
    }

    public String getId() {
        return id;
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public float getScale() {
        return scale;
    }

    public String getText() {
        return text;
    }

    /** 0..1, or -1 without a bar. */
    public float getProgress() {
        return progress;
    }

    public int getBarWidth() {
        return barWidth;
    }

    public int getBarColor() {
        return barColor;
    }

    public int getBarBackground() {
        return barBackground;
    }

    /** {@code ""} without a picture. */
    public String getTexture() {
        return texture;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    /** 0: until removed. */
    public int getDurationTicks() {
        return durationTicks;
    }

    public int getFadeTicks() {
        return fadeTicks;
    }

    /** Builds a {@link HudElement}. */
    public static final class Builder {

        private final String id;
        private Anchor anchor = Anchor.TOP_LEFT;
        private int x;
        private int y;
        private float scale = 1;
        private String text = "";
        private float progress = -1;
        private int barWidth = 80;
        private int barColor = 0xFFFFFFFF;
        private int barBackground = 0x80000000;
        private String texture = "";
        private int textureWidth;
        private int textureHeight;
        private int durationTicks;
        private int fadeTicks;

        private Builder(String id) {
            this.id = id;
        }

        public Builder anchor(Anchor value) {
            anchor = value == null ? Anchor.TOP_LEFT : value;
            return this;
        }

        /** GUI pixels from the anchor (positive: right and down). */
        public Builder offset(int dx, int dy) {
            x = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, dx));
            y = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, dy));
            return this;
        }

        /** 0.25 to 8 (text and picture alike). */
        public Builder scale(float value) {
            scale = Float.isFinite(value) ? Math.max(0.25F, Math.min(8, value)) : 1;
            return this;
        }

        /** Text with {@code §} colors; {@code \n} breaks lines. Up to 512 characters. */
        public Builder text(String value) {
            String t = value == null ? "" : value;
            text = t.length() > 512 ? t.substring(0, 512) : t;
            return this;
        }

        /** A bar filled to {@code progress} (0..1), {@code width} pixels wide, ARGB colors. */
        public Builder bar(float value, int width, int color, int background) {
            progress = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
            barWidth = Math.max(1, Math.min(1000, width));
            barColor = color;
            barBackground = background;
            return this;
        }

        /** A picture of the resource pack, drawn {@code width} x {@code height} pixels (the whole PNG). */
        public Builder texture(String png, int width, int height) {
            if (png == null || !TEXTURE.matcher(png)
                .matches()) {
                throw new IllegalArgumentException("texture must match " + TEXTURE + ": " + png);
            }
            texture = png;
            textureWidth = Math.max(1, Math.min(1000, width));
            textureHeight = Math.max(1, Math.min(1000, height));
            return this;
        }

        /** On screen for {@code ticks} (0: until removed), fading out over the last {@code fade} ticks (up to 255). */
        public Builder duration(int ticks, int fade) {
            durationTicks = Math.max(0, ticks);
            fadeTicks = Math.max(0, Math.min(255, fade));
            return this;
        }

        public HudElement build() {
            return new HudElement(this);
        }
    }
}
