package factorization.sockets;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.common.FactoryType;
import factorization.shared.Core.TabType;
import factorization.shared.ItemFactorization;
import factorization.util.LangUtil;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public class ItemSocketPart extends ItemFactorization {

    public ItemSocketPart(String name, TabType tabType) {
        super(name, tabType);
        setHasSubtypes(true);
        setMaxDamage(0);
    }
    
    
    List<FactoryType> loadSockets() {
        List<FactoryType> ret = new ArrayList<>();
        for (FactoryType ft : FactoryType.values()) {
            if (ft == FactoryType.SOCKET_EMPTY) {
                continue;
            }
            Class<?> theClass = ft.getFactoryTypeClass();
            while (theClass != null) {
                theClass = theClass.getSuperclass();
                if (theClass == TileEntitySocketBase.class) {
                    TileEntitySocketBase ts = (TileEntitySocketBase) ft.getRepresentative();
                    ItemStack is = ts.getCreatingItem();
                    if (is == null) {
                        break;
                    }
                    if (is.getItem() == this) {
                        ret.add(ft);
                        break;
                    }
                }
            }
        }
        return ret;
    }
    
    FactoryType[] socketTypes = null;
    FactoryType[] getSockets() {
        if (socketTypes == null) {
            List<FactoryType> aft = loadSockets();
            socketTypes = new FactoryType[aft.size()];
            for (int i = 0; i < socketTypes.length; i++) {
                socketTypes[i] = aft.get(i);
            }
        }
        return socketTypes;
    }
    
    @SideOnly(Side.CLIENT)
    IIcon[] socketIIcons;

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        socketIIcons = new IIcon[FactoryType.MAX_ID];
        ItemStack me = new ItemStack(this);
        for (FactoryType ft : getSockets()) {
            me.setItemDamage(ft.md);
            socketIIcons[ft.md] = register.registerIcon(getUnlocalizedName(me).replace("item.", ""));
        }
    }
    
    @Override
    public String getUnlocalizedName(ItemStack is) {
        int md = is.getItemDamage();
        return super.getUnlocalizedName(is) + FactoryType.fromMd((byte) md);
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item itemId, CreativeTabs tab, List<ItemStack> list) {
        FactoryType[] ss = getSockets();
        for (FactoryType ft : ss) {
            list.add(ft.asSocketItem());
        }
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int md) {
        if (md > 0 && md < socketIIcons.length) {
            return socketIIcons[md];
        }
        return super.getIconFromDamage(md);
    }
    
    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer player,
            World world, int x, int y, int z, int side,
            float hitX, float hitY, float hitZ) {
        return true;
    }
    
    @Override
    protected void addExtraInformation(ItemStack is, EntityPlayer player, List<String> list, boolean verbose) {
        list.add(LangUtil.translate("item.factorization:socket_info"));
    }
}
