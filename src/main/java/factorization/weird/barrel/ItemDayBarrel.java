package factorization.weird.barrel;

import factorization.common.FactoryType;
import factorization.shared.Core;
import factorization.shared.Core.TabType;
import factorization.shared.ItemBlockProxy;
import factorization.util.LangUtil;
import factorization.weird.barrel.TileEntityDayBarrel.Type;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ItemDayBarrel extends ItemBlockProxy {

    public ItemDayBarrel(String name) {
        super(Core.registry.factory_block_barrel, Core.registry.daybarrel_item_hidden, name, TabType.BLOCKS);
        setMaxDamage(0);
        setNoRepair();
    }
    
    @Override
    public boolean getShareTag() {
        return true;
    }

    @Override
    public String getItemStackDisplayName(ItemStack is) {
        Type upgrade = TileEntityDayBarrel.getUpgrade(is);
        String lookup = "factorization.factoryBlock.DAYBARREL.format";
        if (upgrade != Type.NORMAL) {
            lookup = "factorization.factoryBlock.DAYBARREL.format2";
        }
        String type = LangUtil.translate("factorization.factoryBlock.DAYBARREL." + upgrade);
        return LangUtil.translateWithCorrectableFormat(lookup, type, TileEntityDayBarrel.getLog(is).getDisplayName());
    }
    
    @Override
    @SideOnly(Side.CLIENT) // Invokes a client-only function getTooltip
    protected void addExtraInformation(ItemStack is, EntityPlayer player, List list, boolean verbose) {
        Type upgrade = TileEntityDayBarrel.getUpgrade(is);
        if (upgrade == Type.SILKY) {
            list.add(LangUtil.translateThis("factorization.factoryBlock.DAYBARREL.SILKY.silkhint"));
            TileEntityDayBarrel db = (TileEntityDayBarrel) FactoryType.DAYBARREL.getRepresentative();
            db.loadFromStack(is);
            int count = db.getItemCount();
            if (count > 0 && db.item != null) {
                if (db.item.getItem() == this) {
                    list.add("?");
                    return;
                }
                List sub = db.item.getTooltip/* Client-only */(player, false /* Propagating verbose would be natural, but let's keep the tool-tip short */);
                db.item.getItem().addInformation(db.item, player, sub, verbose);
                if (!sub.isEmpty()) {
                    Object first = sub.get(0);
                    sub.set(0, count + " " + first);
                    list.addAll(sub);
                }
            }
        }
    }
}