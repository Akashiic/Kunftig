package com.akashic.kunftig.client.screen;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.akashic.kunftig.screen.HudLayout;
import com.akashic.kunftig.screen.HudWire;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client side of the screen HUD ({@code KunftigHud}, API 11): keeps the elements the server set and draws them after
 * the vanilla HUD (picture, text lines, bar, from their anchor). Hidden with F1 like the rest of the HUD. Client
 * thread.
 */
public final class HudClient {

    public static final HudClient INSTANCE = new HudClient();
    private static final int LINE = 10;

    private static final class Shown {

        final HudWire.Element element;
        final long since;
        final ResourceLocation texture;

        Shown(HudWire.Element element, long since) {
            this.element = element;
            this.since = since;
            this.texture = element.texture.isEmpty() ? null : new ResourceLocation(element.texture);
        }
    }

    private final Map<String, Shown> elements = new LinkedHashMap<>();
    private long clock;

    private HudClient() {}

    public void clear() {
        elements.clear();
    }

    public int count() {
        return elements.size();
    }

    public void onPayload(byte[] data) {
        HudWire.Message m = HudWire.read(data);
        if (m == null) {
            return;
        }
        if (m.op == HudWire.CLEAR) {
            elements.clear();
        } else if (m.op == HudWire.REMOVE) {
            elements.remove(m.id);
        } else {
            elements.remove(m.id); // set again: last in the drawing order
            if (elements.size() >= HudWire.MAX_ELEMENTS) {
                Iterator<String> oldest = elements.keySet()
                    .iterator();
                oldest.next();
                oldest.remove();
            }
            elements.put(m.id, new Shown(m.element, clock));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getMinecraft()
            .isGamePaused()) {
            return;
        }
        clock++;
        elements.values()
            .removeIf(s -> s.element.durationTicks > 0 && clock - s.since >= s.element.durationTicks);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || elements.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        int sw = event.resolution.getScaledWidth();
        int sh = event.resolution.getScaledHeight();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_LIGHTING);
            for (Shown s : elements.values()) {
                draw(mc, font, s, sw, sh, event.partialTicks);
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    private void draw(Minecraft mc, FontRenderer font, Shown s, int sw, int sh, float partial) {
        HudWire.Element e = s.element;
        float alpha = HudLayout.alpha(clock - s.since, e.durationTicks, e.fadeTicks);
        if (alpha < 0.02F) {
            return;
        }
        String[] lines = e.text.isEmpty() ? new String[0] : e.text.split("\n", -1);
        int textW = 0;
        for (String l : lines) {
            textW = Math.max(textW, font.getStringWidth(l));
        }
        boolean bar = e.progress >= 0;
        int w = Math.max(Math.max(s.texture == null ? 0 : e.textureWidth, textW), bar ? e.barWidth : 0);
        int h = (s.texture == null ? 0 : e.textureHeight) + lines.length * LINE + (bar ? 5 : 0);
        int scaledW = Math.round(w * e.scale);
        int scaledH = Math.round(h * e.scale);
        int[] at = HudLayout.corner(e.anchor, sw, sh, scaledW, scaledH, e.x, e.y);
        int column = e.anchor % 3;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(at[0], at[1], 0);
            GL11.glScalef(e.scale, e.scale, 1);
            int y = 0;
            if (s.texture != null) {
                int tx = align(column, w, e.textureWidth);
                mc.getTextureManager()
                    .bindTexture(s.texture);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(1, 1, 1, alpha);
                Tessellator t = Tessellator.instance;
                t.startDrawingQuads();
                t.addVertexWithUV(tx, y + e.textureHeight, 0, 0, 1);
                t.addVertexWithUV(tx + e.textureWidth, y + e.textureHeight, 0, 1, 1);
                t.addVertexWithUV(tx + e.textureWidth, y, 0, 1, 0);
                t.addVertexWithUV(tx, y, 0, 0, 0);
                t.draw();
                y += e.textureHeight;
            }
            int a = Math.max(4, Math.round(alpha * 255)) << 24;
            for (String l : lines) {
                font.drawStringWithShadow(l, align(column, w, font.getStringWidth(l)), y + 1, 0xFFFFFF | a);
                y += LINE;
            }
            if (bar) {
                int bx = align(column, w, e.barWidth);
                Gui.drawRect(bx, y + 1, bx + e.barWidth, y + 4, fade(e.barBackground, alpha));
                int filled = Math.round(e.barWidth * e.progress);
                if (filled > 0) {
                    Gui.drawRect(bx, y + 1, bx + filled, y + 4, fade(e.barColor, alpha));
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    /** x of a part {@code part} wide inside an element {@code w} wide, aligned to the anchor's side. */
    private static int align(int column, int w, int part) {
        return column == 0 ? 0 : column == 1 ? (w - part) / 2 : w - part;
    }

    private static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
