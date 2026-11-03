package com.akashic.kunftig.client.hologram;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.akashic.kunftig.hologram.HologramWire;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Client side of holograms: registers {@link HologramWire#CHANNEL} on join, keeps what the server sent, and draws each
 * line like a 1.7.10 nametag (facing the camera, a translucent background, faint through blocks and solid in front of
 * them), at the end of the world render. Every connection, respawn or dimension change starts empty: the server sends
 * them again. Client thread only.
 */
public final class HologramClient {

    public static final HologramClient INSTANCE = new HologramClient();

    /** Holograms farther than this from the camera are not drawn (the server's range is the real limit). */
    private static final double MAX_DISTANCE = 128;
    /** Blocks between two lines (a nametag line is 0.26 blocks tall at this scale). */
    static final double LINE_SPACING = 0.27;
    /** The nametag scale of 1.7.10 ({@code RendererLivingEntity#func_96449_a}). */
    private static final float SCALE = 0.016666668F * 1.6F;

    private static final class Entry {

        final double x;
        final double y;
        final double z;
        final List<String> lines;

        Entry(double x, double y, double z, List<String> lines) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lines = lines;
        }
    }

    private final Map<Integer, Entry> holograms = new ConcurrentHashMap<>();

    private HologramClient() {}

    /** At the end of {@code handleJoinGame}: a fresh connection knows no hologram and asks for them. */
    public void onJoinGame(NetHandlerPlayClient handler) {
        holograms.clear();
        handler.addToSendQueue(
            new C17PacketCustomPayload("REGISTER", HologramWire.CHANNEL.getBytes(StandardCharsets.UTF_8)));
    }

    /** Respawn or dimension change: the server shows them again where they apply. */
    public void clear() {
        holograms.clear();
    }

    /** A message on the hologram channel. */
    public void onPayload(byte[] data) {
        HologramWire.Message m = HologramWire.read(data);
        if (m == null) {
            return;
        }
        switch (m.op) {
            case HologramWire.SHOW:
                holograms.put(m.id, new Entry(m.x, m.y, m.z, m.lines));
                break;
            case HologramWire.LINES: {
                Entry e = holograms.get(m.id);
                if (e != null) {
                    holograms.put(m.id, new Entry(e.x, e.y, e.z, m.lines));
                }
                break;
            }
            case HologramWire.MOVE: {
                Entry e = holograms.get(m.id);
                if (e != null) {
                    holograms.put(m.id, new Entry(m.x, m.y, m.z, e.lines));
                }
                break;
            }
            case HologramWire.HIDE:
                holograms.remove(m.id);
                break;
            default:
                break;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (holograms.isEmpty() || mc.theWorld == null || mc.renderViewEntity == null || mc.gameSettings.hideGUI) {
            return;
        }
        EntityLivingBase view = mc.renderViewEntity;
        float t = event.partialTicks;
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * t;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * t;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * t;
        RenderManager rm = RenderManager.instance;
        FontRenderer font = mc.fontRenderer;
        for (Entry e : holograms.values()) {
            double dx = e.x - cx;
            double dy = e.y - cy;
            double dz = e.z - cz;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            for (int i = 0; i < e.lines.size(); i++) {
                String line = e.lines.get(i);
                if (!line.isEmpty()) {
                    drawLine(font, rm, line, dx, dy - i * LINE_SPACING, dz, mc.gameSettings.thirdPersonView == 2);
                }
            }
        }
    }

    /** {@code RendererLivingEntity#func_147906_a} (the nametag), without the entity. */
    private static void drawLine(FontRenderer font, RenderManager rm, String text, double x, double y, double z,
        boolean frontView) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(frontView ? -rm.playerViewX : rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-SCALE, -SCALE, SCALE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        int half = font.getStringWidth(text) / 2;
        Tessellator tess = Tessellator.instance;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tess.startDrawingQuads();
        tess.setColorRGBA_F(0.0F, 0.0F, 0.0F, 0.25F);
        tess.addVertex(-half - 1, -1, 0.0D);
        tess.addVertex(-half - 1, 8, 0.0D);
        tess.addVertex(half + 1, 8, 0.0D);
        tess.addVertex(half + 1, -1, 0.0D);
        tess.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        font.drawString(text, -half, 0, 0x20FFFFFF);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        font.drawString(text, -half, 0, -1);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }
}
