package factorization.servo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.common.BlockIcons;
import factorization.shared.Core;
import factorization.shared.Core.TabType;
import factorization.shared.ItemFactorization;
import factorization.util.ItemUtil;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;

public class ItemServoRailWidget extends ItemFactorization {
    public ItemServoRailWidget(String name) {
        super(name, TabType.SERVOS);
    }
    
    @Override
    public String getUnlocalizedName(ItemStack is) {
        ServoComponent sc = get(is);
        if (sc == null) {
            return super.getUnlocalizedName(is);
        }
        return super.getUnlocalizedName(is) + "." + sc.getName();
    }
    
    @Override
    public String getItemStackDisplayName(ItemStack is) {
        String s = super.getItemStackDisplayName(is);
        if (s == null || s.isEmpty()) {
            s = getUnlocalizedName(is);
        }
        return s;
    }

    ServoComponent get(ItemStack is) {
        if (!is.hasTagCompound()) {
            return null;
        }
        return ServoComponent.load(is.getTagCompound());
    }
    
    void update(ItemStack is, ServoComponent sc) {
        if (sc != null) {
            sc.save(ItemUtil.getTag(is));
        } else {
            is.setTagCompound(null);
        }
    }
    
    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer player, World world, int x, int y, int z, int side, float vx, float vy, float vz) {
        ServoComponent sc = get(is);
        if (sc == null) {
            return false;
        }
        Coord here = new Coord(world, x, y, z);
        TileEntityServoRail rail = here.getTE(TileEntityServoRail.class);
        if (rail == null) {
            sc.onItemUse(here, player);
        } else if (sc instanceof Decorator dec) {
            if (rail.decoration == null) {
                rail.setDecoration(dec);
                if (world.isRemote){
                    here.redraw();
                } else {
                    here.markBlockForUpdate();
                    rail.showDecorNotification(player);
                }
                if (!dec.isFreeToPlace() && !player.capabilities.isCreativeMode) {
                    is.stackSize--;
                }
            }
        }
        return super.onItemUse(is, player, world, x, y, z, side, vx, vy, vz);
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void addExtraInformation(ItemStack is, EntityPlayer player, List<String> list, boolean verbose) {
        ServoComponent sc = get(is);
        if (sc != null) {
            sc.addInformation(list);
        }
    }


    private List<ItemStack> subItemsCache = null;
    
    void loadSubItems() {
        if (subItemsCache != null) {
            return;
        }
        if (this == Core.registry.servo_widget_instruction) {
            subItemsCache = ServoComponent.sorted_instructions;
        } else if (this == Core.registry.servo_widget_decor) {
            subItemsCache = ServoComponent.sorted_decors;
        } else {
            subItemsCache = new ArrayList<>();
        }
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item id, CreativeTabs tab, List<ItemStack> list) {
        loadSubItems();
        list.addAll(subItemsCache);
    }
    
    @Override
    public IIcon getIcon(ItemStack stack, int renderPass, EntityPlayer player, ItemStack usingItem, int useRemaining) {
        return getIcon(stack, renderPass);
    }
    
    @Override
    public IIcon getIcon(ItemStack stack, int pass) {
        if (pass == 0) {
            return BlockIcons.servo$instruction_plate;
        }
        if (pass == 1) {
            ServoComponent sc = get(stack);
            IIcon ret = null;
            if (sc instanceof Decorator) {
                ret = ((Decorator) sc).getIcon(ForgeDirection.UNKNOWN);
            }
            if (ret == null) {
                ret = BlockIcons.uv_test;
            }
            return ret;
        }
        return null;
        
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public int getSpriteNumber() {
        return 0;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }
    
    @Override
    public int getRenderPasses(int metadata) {
        return 2;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister par1IIconRegister) { }
}
