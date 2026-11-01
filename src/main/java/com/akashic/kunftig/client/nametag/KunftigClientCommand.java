package com.akashic.kunftig.client.nametag;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.akashic.kunftig.Tags;
import com.akashic.kunftig.common.protocol.KnpProtocol;

/** {@code /kunftigclient}: client-side diagnostics, so support can tell at once whether the protocol is active. */
public final class KunftigClientCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "kunftigclient";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kunftigclient";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        int version = NametagClient.negotiatedVersion();
        String session = version == 0 ? EnumChatFormatting.RED + "inactive (this server does not run Kunftig)"
            : EnumChatFormatting.GREEN + "protocol " + version + ", server " + NametagClient.serverVersion();
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GOLD + "Kunftig "
                    + Tags.VERSION
                    + EnumChatFormatting.GRAY
                    + " | channel "
                    + KnpProtocol.CHANNEL));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "Nametags: " + session));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GRAY + "Team styles: "
                    + NametagClient.styleCount()
                    + " | color mode: "
                    + RgbSupport.current()));
    }
}
