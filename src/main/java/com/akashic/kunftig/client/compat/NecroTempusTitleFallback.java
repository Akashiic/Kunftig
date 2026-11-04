package com.akashic.kunftig.client.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import com.akashic.kunftig.Kunftig;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * NecroTempus draws its titles only on {@code RenderGameOverlayEvent.Post(EXPERIENCE)}. Forge skips that post whenever
 * something cancels {@code Pre(EXPERIENCE)} ({@code GuiIngameForge#renderExperience} returns right after the pre), and
 * never reaches it while the player rides a horse (the jump bar replaces the XP bar), so titles (a plugin's whole HUD,
 * for AkashicFishing's minigame) vanished without an error on clients where another mod hides or replaces the XP bar.
 * <p>
 * This watches each frame: when the frame ends ({@code Post(ALL)}) without an EXPERIENCE post, it hands NecroTempus's
 * own title listener the EXPERIENCE post it missed. Nothing else hears that event, and frames that had it are left
 * alone. Resolved by MethodHandles; inactive without NecroTempus or with
 * {@code -Dkunftig.necrotempusTitles.disable=true}.
 */
public final class NecroTempusTitleFallback {

    private static final String LISTENER = "io.github.cruciblemc.necrotempus.modules.features.title.client.render.TitleDisplayListener";

    private final Object listener;
    private final MethodHandle onPost;
    private boolean experiencePosted;
    private boolean failed;

    private NecroTempusTitleFallback(Object listener, MethodHandle onPost) {
        this.listener = listener;
        this.onPost = onPost;
    }

    /** Registers the fallback when NecroTempus is installed. */
    public static void install() {
        if (Boolean.getBoolean("kunftig.necrotempusTitles.disable")) {
            return;
        }
        try {
            Class<?> type = Class.forName(LISTENER, false, NecroTempusTitleFallback.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Object instance = lookup.findStatic(type, "getInstance", MethodType.methodType(type))
                .invoke();
            MethodHandle post = lookup.findVirtual(
                type,
                "onRenderGameOverlayEvent",
                MethodType.methodType(void.class, RenderGameOverlayEvent.Post.class));
            MinecraftForge.EVENT_BUS.register(new NecroTempusTitleFallback(instance, post));
            Kunftig.LOG.info("Kunftig: NecroTempus titles also drawn when the XP bar's overlay event is skipped");
        } catch (ClassNotFoundException e) {
            // No NecroTempus: nothing to do.
        } catch (Throwable e) {
            Kunftig.LOG.warn("Kunftig: NecroTempus title fallback not installed", e);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onPost(RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.EXPERIENCE) {
            experiencePosted = true;
        } else if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
            if (!experiencePosted && !failed) {
                try {
                    onPost.invoke(
                        listener,
                        new RenderGameOverlayEvent.Post(event, RenderGameOverlayEvent.ElementType.EXPERIENCE));
                } catch (Throwable e) {
                    failed = true;
                    Kunftig.LOG.warn("Kunftig: NecroTempus title fallback failed; disabled for this session", e);
                }
            }
            experiencePosted = false;
        }
    }
}
