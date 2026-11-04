package com.akashic.kunftig.client.rodmodel;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.rodmodel.RodModelDef;
import com.akashic.kunftig.rodmodel.RodModelIndex;
import com.akashic.kunftig.rodmodel.RodWire;

/**
 * {@code /kunftigclient rods ...}: status of the 3D rods and in-game tuning (issue #13), client side only.
 *
 * <pre>
 * /kunftigclient rods                                     status
 * /kunftigclient rods state &lt;state&gt; [tension]              plays a state on your rod (no server needed)
 * /kunftigclient rods display &lt;context&gt;                     the display in use for the rod in your hand
 * /kunftigclient rods display &lt;context&gt; rx ry rz tx ty tz scale   tries a display on every rod (until reset)
 * /kunftigclient rods display &lt;context&gt; reset
 * </pre>
 *
 * Contexts: {@code primeira_pessoa}, {@code terceira_pessoa}, {@code chao}, {@code moldura}. A display that looks
 * right is printed as JSON (and logged) to be copied into the pack's generator.
 */
public final class RodModelCommand {

    public static final String USAGE = "/kunftigclient rods [state <state> [tension] | display <context> [rx ry rz tx ty tz scale | reset]]";

    private RodModelCommand() {}

    public static void process(ICommandSender sender, String[] args) {
        RodModels rods = RodModels.INSTANCE;
        if (args.length == 0) {
            say(sender, EnumChatFormatting.GRAY + "3D rods: " + rods.status());
            for (String e : rods.index()
                .errors()) {
                say(sender, EnumChatFormatting.RED + e);
            }
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("state".equals(sub) && args.length >= 2) {
            String state = args[1].toLowerCase(Locale.ROOT);
            if (!RodWire.STATE_NAME.matcher(state)
                .matches()) {
                say(sender, EnumChatFormatting.RED + "Invalid state: " + state);
                return;
            }
            float tension = args.length >= 3 ? number(args[2]) : 0;
            if (Float.isNaN(tension) || !rods.forceLocalState(state, tension)) {
                say(sender, EnumChatFormatting.RED + "Not possible: 3D rods are off, or the tension is not a number");
                return;
            }
            say(sender, EnumChatFormatting.GRAY + "Your rod plays " + state + " (tension " + tension + ")");
            return;
        }
        if ("display".equals(sub) && args.length >= 2) {
            display(sender, args);
            return;
        }
        say(sender, EnumChatFormatting.RED + USAGE);
    }

    private static void display(ICommandSender sender, String[] args) {
        RodModels rods = RodModels.INSTANCE;
        String context = args[1];
        if (!RodModelDef.FIRST_PERSON.equals(context) && !RodModelDef.THIRD_PERSON.equals(context)
            && !RodModelDef.GROUND.equals(context)
            && !RodModelDef.FRAME.equals(context)) {
            say(sender, EnumChatFormatting.RED + "Context: primeira_pessoa, terceira_pessoa, chao or moldura");
            return;
        }
        if (args.length == 3 && "reset".equalsIgnoreCase(args[2])) {
            rods.overrideDisplay(context, null);
            say(sender, EnumChatFormatting.GRAY + context + ": back to each rod's manifest");
            return;
        }
        if (args.length == 2) {
            Minecraft mc = Minecraft.getMinecraft();
            RodModelIndex.Resolved r = mc.thePlayer == null ? null : rods.resolve(mc.thePlayer.getHeldItem());
            if (r == null) {
                say(sender, EnumChatFormatting.RED + "Hold a rod with a 3D model");
                return;
            }
            RodModelDef.Display d = rods.display(r.model, context);
            say(sender, EnumChatFormatting.GRAY + r.model.id + " " + context + ": " + (d == null ? "none" : d));
            return;
        }
        if (args.length != 9) {
            say(sender, EnumChatFormatting.RED + USAGE);
            return;
        }
        float[] v = new float[7];
        for (int i = 0; i < 7; i++) {
            v[i] = number(args[i + 2]);
            if (Float.isNaN(v[i])) {
                say(sender, EnumChatFormatting.RED + "Not a number: " + args[i + 2]);
                return;
            }
        }
        if (!(v[6] > 0)) {
            say(sender, EnumChatFormatting.RED + "The scale must be above 0");
            return;
        }
        RodModelDef.Display d = new RodModelDef.Display(
            new float[] { v[0], v[1], v[2] },
            new float[] { v[3], v[4], v[5] },
            v[6]);
        rods.overrideDisplay(context, d);
        say(sender, EnumChatFormatting.GRAY + "\"" + context + "\": " + d);
        Kunftig.LOG.info("Kunftig 3D rods: display \"{}\": {}", context, d);
    }

    private static float number(String s) {
        try {
            float f = Float.parseFloat(s);
            return Float.isFinite(f) ? f : Float.NaN;
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private static void say(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
}
