package factorization.weird;

import java.util.List;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.common.*;
import factorization.coremodhooks.UnhandledGuiKeyEvent;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import factorization.shared.Core;
import factorization.shared.Core.TabType;
import factorization.shared.FactorizationTextureLoader;
import factorization.shared.ItemFactorization;

public class ItemPocketTable extends ItemFactorization {
    
    public ItemPocketTable() {
        super("tool/pocket_crafting_table", TabType.TOOLS);
        setMaxStackSize(1);
        setFull3D();
        Core.loadBus(this);
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        super.registerIcons(reg);
        FactorizationTextureLoader.register(reg, ItemIcons.class, null, "factorization:");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        return activateTable(stack, world, player);
    }
    
    ItemStack activateTable(ItemStack stack, World world, EntityPlayer player) {
        ItemStack save = player.inventory.getItemStack();
        if (save != null) {
            player.inventory.setItemStack(null);
        }
        if (!world.isRemote){ 
            player.openGui(Core.instance, FactoryType.POCKETCRAFTGUI.gui, player.worldObj, 0, 0, 0);
        }
        if (save != null) {
            player.inventory.setItemStack(save); // NORELEASE: This doesn't work properly! Client doesn't know it's holding it... (may be working now; needs server test)
            if (!player.worldObj.isRemote && player instanceof EntityPlayerMP) {
                ((EntityPlayerMP) player).updateHeldItem();
            }
        }
        return stack;
    }
    
    public ItemStack findPocket(EntityPlayer player) {
        InventoryPlayer inv = player.inventory;
        int need_to_move = -1;
        int a_free_space = -1;
        for (int i = 0; i < inv.mainInventory.length; i++) {
            boolean in_crafting_area = i % 9 >= (9 - 3) && i > 9;
            ItemStack is = inv.mainInventory[i]; // A little bit gross; using it the proper causes us to check armor slots.
            if (is == null) {
                if (!in_crafting_area) {
                    if (a_free_space == -1 || a_free_space < 9) {
                        // Silly condition because: If it's not set, we should set it. If it's < 9, it's in the hotbar, which is a poor choice.
                        // If it is going to the hotbar, it'll end up in the last empty slot.
                        a_free_space = i;
                    }
                }
                continue;
            }
            if (is.getItem() == this) {
                if (in_crafting_area) {
                    need_to_move = i;
                } else {
                    return is;
                }
            }
        }
        ItemStack mouse_item = player.inventory.getItemStack();
        if (mouse_item != null && mouse_item.getItem() == this && player.openContainer instanceof ContainerPocket) {
            return mouse_item;
        }
        if (need_to_move != -1 && a_free_space != -1) {
            ItemStack pocket = inv.getStackInSlot(need_to_move);
            inv.setInventorySlotContents(need_to_move, null);
            inv.setInventorySlotContents(a_free_space, pocket);
            return pocket;
        }
        return null;
    }

    public boolean tryOpen(EntityPlayer player) {
        ItemStack is = findPocket(player);
        if (is == null) {
            return false;
        }
        activateTable(is, player.worldObj, player);
        return true;
    }
    
    @Override
    public void addExtraInformation(ItemStack is, EntityPlayer player, List<String> infoList, boolean verbose) {
        if (player.worldObj.isRemote) {
            String key = Core.proxy.getPocketCraftingTableKey();
            if (key != null && key != "") {
                final String prefix = "item.factorization:tool/pocket_crafting_table.";
                infoList.add(StatCollector.translateToLocalFormatted(prefix + "yesNEI", key));
            }
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void craftingTableKey(UnhandledGuiKeyEvent event) {
        if (!FzConfig.pocket_craft_anywhere) return;
        if (!(event.gui instanceof GuiContainer)) return;
        if (FactorizationKeyHandler.pocket_key.getKeyCode() == event.keysym) {
            Command.craftOpen.call(event.player);
        }
    }
}
