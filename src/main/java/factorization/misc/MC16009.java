package factorization.misc;

import java.util.*;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import factorization.notify.Notice;

public class MC16009 extends CommandBase {
    
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
    
    @Override
    public String getCommandName() {
        return "mc16009";
    }

    String markDupes = "markDupes";
    String showUUIDs = "showUUIDs";
    
    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/mc16009 " + markDupes + "|" + showUUIDs;
    }
    
    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return Arrays.asList(markDupes, showUUIDs);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1) return;
        String sub = args[0];
        if (!(sender instanceof EntityPlayer player)) return;
        if (sub.equalsIgnoreCase(markDupes)) {
            countDupeEntities(player.worldObj, player);
        } else if (sub.equalsIgnoreCase(showUUIDs)) {
            showEntityUUIDs(player.worldObj, player);
        }
    }
    

    void showEntityUUIDs(World world, EntityPlayer player) {
        for (Entity ent : world.loadedEntityList) {
            new Notice(ent, ent.getUniqueID().toString()).send(player);
        }
    }
    
    void countDupeEntities(World world, EntityPlayer player) {
        int n = 0;
        int total = 0;
        Set<UUID> found = new HashSet<>();
        for (Entity ent : world.loadedEntityList) {
            if (!found.add(ent.getUniqueID())) {
                new Notice(ent, "dupe!").send(player);
                n++;
            }
            total++;
        }
        player.addChatMessage(new ChatComponentText(n + " dupes out of " + total));
    }
    

}
