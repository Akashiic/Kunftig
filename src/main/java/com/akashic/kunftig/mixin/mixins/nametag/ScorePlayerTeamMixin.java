package com.akashic.kunftig.mixin.mixins.nametag;

import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashic.kunftig.client.nametag.NametagClient;
import com.akashic.kunftig.client.nametag.RgbSupport;
import com.akashic.kunftig.client.nametag.TeamStyleState;

/**
 * Serves Kunftig team styles through the team's own getters.
 * <p>
 * Every 1.7.10 consumer of team text goes through these: nametags ({@code EntityPlayer.func_145748_c_} ->
 * {@code formatPlayerName} -> {@code formatString}), the vanilla tab list and the scoreboard sidebar
 * ({@code formatPlayerName}), and NecroTempus' tab list ({@code Team.formatString}). Overriding the data instead of
 * each renderer keeps Kunftig compatible with all of them. Only {@code @Inject}s are used, so other mods can still
 * inject into the same methods.
 */
@Mixin(ScorePlayerTeam.class)
public abstract class ScorePlayerTeamMixin {

    @Shadow
    @Final
    private Scoreboard theScoreboard;

    @Shadow
    private String namePrefixSPT;

    @Shadow
    private String colorSuffix;

    @Shadow
    public abstract String getRegisteredName();

    private TeamStyleState kunftig$style() {
        return NametagClient.styleFor(theScoreboard, getRegisteredName());
    }

    @Inject(method = "getColorPrefix", at = @At("HEAD"), cancellable = true)
    private void kunftig$prefix(CallbackInfoReturnable<String> cir) {
        TeamStyleState style = kunftig$style();
        if (style != null && style.affectsText()) {
            cir.setReturnValue(style.prefix(namePrefixSPT, RgbSupport.current()));
        }
    }

    @Inject(method = "getColorSuffix", at = @At("HEAD"), cancellable = true)
    private void kunftig$suffix(CallbackInfoReturnable<String> cir) {
        TeamStyleState style = kunftig$style();
        if (style != null && style.affectsText()) {
            cir.setReturnValue(style.suffix(colorSuffix, RgbSupport.current()));
        }
    }

    /** Only gradient names need this; otherwise vanilla's prefix + name + suffix via the getters is exact. */
    @Inject(method = "formatString", at = @At("HEAD"), cancellable = true)
    private void kunftig$format(String name, CallbackInfoReturnable<String> cir) {
        TeamStyleState style = kunftig$style();
        if (style != null && style.hasGradientName()) {
            cir.setReturnValue(style.format(namePrefixSPT, name, colorSuffix, RgbSupport.current()));
        }
    }
}
