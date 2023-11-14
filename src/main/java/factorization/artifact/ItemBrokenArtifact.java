package factorization.artifact;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.shared.Core;
import factorization.shared.ItemFactorization;
import factorization.util.ItemUtil;
import factorization.util.LangUtil;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.AnvilUpdateEvent;

import java.util.Collections;
import java.util.List;

public class ItemBrokenArtifact extends ItemFactorization {
    public ItemBrokenArtifact() {
        super("brokenArtifact", Core.TabType.ARTIFACT);
        setMaxStackSize(1);
        Core.loadBus(this);
    }

    public static ItemStack build(ItemStack orig) {
        final NBTTagCompound out = new NBTTagCompound();
        orig.writeToNBT(out);
        ItemStack ret = new ItemStack(Core.registry.brokenTool);
        NBTTagCompound tag = ItemUtil.getTag(ret);
        tag.setTag("broken", out);
        ret.setItemDamage(Math.abs(out.hashCode()) % 1000);
        return ret;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) { }

    public static ItemStack get(ItemStack orig) {
        if (orig == null) return null;
        if (!(orig.getItem() instanceof ItemBrokenArtifact)) return null;
        NBTTagCompound tag = orig.getTagCompound();
        if (tag == null) return null;
        return ItemStack.loadItemStackFromNBT(tag.getCompoundTag("broken"));
    }

    @Override
    public String getItemStackDisplayName(ItemStack is) {
        ItemStack held = get(is);
        if (held == null) return super.getUnlocalizedNameInefficiently(is);
        return LangUtil.translateWithCorrectableFormat("item.factorization:brokenArtifact.shards", held.getDisplayName());
    }

    @Override
    public void getSubItems(Item stack, CreativeTabs tab, List<ItemStack> list) { }

    @Override
    protected void addExtraInformation(ItemStack is, EntityPlayer player, List<String> list, boolean verbose) {
        ItemStack held = get(is);
        if (held == null) return;
        ItemStack fresh = new ItemStack(held.getItem());
        ItemStack repair = getRepairItemStack(fresh);
        String got = LangUtil.translateWithCorrectableFormat("item.factorization:brokenArtifact.repairhint", repair.getDisplayName());
        Collections.addAll(list, got.split("\\\\n"));
        List<String> infos = held.getTooltip(player, false);
        if (!infos.isEmpty()) {
            list.add("");
            list.addAll(infos);
        }
    }

    @Deprecated /* Use getRepairItemStack */
    public Item getRepairItem(ItemStack held) {
        Item template = held.getItem();

        if (template instanceof ItemTool) {
            return ((ItemTool) template).func_150913_i(/*getToolMaterial*/).func_150995_f(/*getRepairItem*/);
        }
        return template;
    }

    public ItemStack getRepairItemStack(ItemStack held) {
        if (held.getItem() instanceof ItemTool) {
            return ((ItemTool) held.getItem()).func_150913_i(/*getToolMaterial*/).getRepairItemStack();
        }
        return held.copy();
    }

    @SubscribeEvent
    public void reforge(AnvilUpdateEvent event) {
        ItemStack right = event.right;
        ItemStack left = event.left;
        if (!ItemUtil.is(left, this)) return;
        ItemStack held = get(left);
        if (held == null) return;
        ItemStack template = getRepairItemStack(held);

        if (!ItemUtil.wildcardSimilar(right, template)) return;
        if (right.getItemDamage() != 0) return;
        // Check for enchants? Previous repairs? Nah.
        held.setItemDamage(0);
        int oldCost = held.getRepairCost();
        if (oldCost > 0) {
            held.setRepairCost(oldCost / 2);
        }
        event.output = held.copy();
        event.cost = 30;
        event.materialCost = 1;
    }
}
