package factorization.truth.minecraft;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.shared.Core;
import factorization.truth.api.DocReg;
import factorization.truth.api.IManwich;
import factorization.util.EvilUtil;
import factorization.util.ItemUtil;
import factorization.util.LangUtil;
import factorization.util.PlayerUtil;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S37PacketStatistics;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemManSandwich extends ItemFood implements IManwich {
    StatBase manwhichStatus;

    public ItemManSandwich(int healAmount, float saturationModifier, String itemName) {
        super(healAmount, saturationModifier, false /* wolfs eat */);
        setPotionEffect(Potion.moveSlowdown.getId(), 12 /* 8 seconds long */, 9 /* modifier */, 1.0F /* probability */);
        Core.loadBus(this);
        setUnlocalizedName(itemName);
        setTextureName(itemName.replaceFirst("item.", ""));
        String n = itemName + ".status";
        manwhichStatus = new StatBase(n, new ChatComponentTranslation(n)).registerStat();
        setMaxStackSize(1);
        setHasSubtypes(true);
        DocReg.registerManwich(this);
        Core.tab(this, Core.TabType.TOOLS);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return super.getMaxItemUseDuration(stack); // I'd like '* 8', but it animates/SFXs poorly
    }

    @Override
    public float getDigSpeed(ItemStack itemstack, Block block, int metadata) {
        return super.getDigSpeed(itemstack, block, metadata); // / 2;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (hasManual(player) > 0) return stack;
        return super.onItemRightClick(stack, world, player);
    }

    @Override
    protected void onFoodEaten(ItemStack stack, World wrold, EntityPlayer player) {
        if (player instanceof FakePlayer) {
            super.onFoodEaten(stack, wrold, player);
            return;
        }
        boolean spicy = stack.getItemDamage() > 0;
        int nomage = spicy ? 7 : 1;
        StatisticsFile stats = PlayerUtil.getStatsFile(player);
        if (stats != null) {
            stats.func_150873_a(player, manwhichStatus, nomage);
            syncStat(player);
        }
        int saturationTime;
        if (spicy) {
            player.setFire(7);
            saturationTime = 20 * 60 * 5;
        } else {
            player.addPotionEffect(new PotionEffect(Potion.confusion.getId(), 50, 1, false));
            saturationTime = 20 * 25;
        }
        player.addPotionEffect(new PotionEffect(Potion.field_76443_y.getId(), saturationTime, 1, !Core.dev_environ));
        super.onFoodEaten(stack, wrold, player);
    }

    public int hasManual(EntityPlayer player) {
        if (PlayerUtil.isPlayerCreative(player)) return 1;
        StatisticsFile stats = PlayerUtil.getStatsFile(player);
        if (stats == null) return 0;
        return stats.writeStat(manwhichStatus);
    }

    @SubscribeEvent
    public void digestManwhich(PlayerEvent.Clone event) {
        if (!event.wasDeath) return;
        EntityPlayer player = event.entityPlayer;
        StatisticsFile stats = PlayerUtil.getStatsFile(player);
        if (stats == null) return;
        int sandwiches = stats.writeStat(manwhichStatus);
        if (sandwiches > 0) {
            stats.func_150873_a(player, manwhichStatus, sandwiches - 1);
            syncStat(player);
        }
    }

    @SubscribeEvent
    public void syncLoginManwhich(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        syncStat(event.player);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity ent, int currentSlot, boolean isHeld) {
        if (!isHeld) return;
        if (world.isRemote) return;
        if (ent instanceof EntityLivingBase player) {
            if (ent instanceof EntityPlayer p) {
                if (p.isUsingItem()) return; // Your mouth's got a firm grip on it
            }
            if (player.hurtTime > 0) {
                // My manwhich!
                EvilUtil.throwStack(player, stack, false);
                player.setCurrentItemOrArmor(0, null);
            }
        }
    }

    void syncStat(EntityPlayer _player) {
        if (!(_player instanceof EntityPlayerMP player)) return;
        StatisticsFile stats = PlayerUtil.getStatsFile(player);
        if (stats == null) return;
        int sandwiches = stats.writeStat(manwhichStatus);
        Map<StatBase, Integer> statInfo = new HashMap<>();
        statInfo.put(manwhichStatus, sandwiches);
        player.playerNetServerHandler.sendPacket(new S37PacketStatistics(statInfo));
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        boolean spicy = stack != null && stack.getItemDamage() > 0;
        String name = super.getUnlocalizedName(stack);
        if (spicy) return name + ".spicy";
        return name;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean verbose) {
        if (Core.dev_environ) {
            list.add("Sandwich level: " + hasManual(player));
        }
        if (PlayerUtil.isPlayerCreative(player)) {
            Core.brand(stack, player, list, verbose);
            return;
        }
        int nom = hasManual(player);
        String key;
        if (nom == 1) {
            key = "item.factorization:mansandwich.nom.once";
        } else if (nom > 1) {
            key = "item.factorization:mansandwich.nom.many";
        } else {
            key = "item.factorization:mansandwich.nom.delicious";
        }
        String t = LangUtil.translateThis(key);
        Collections.addAll(list, t.split("\\\\n"));
        Core.brand(stack, player, list, verbose);
    }


    @SideOnly(Side.CLIENT)
    IIcon spicy;

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        if (damage > 0) return spicy;
        return super.getIconFromDamage(damage);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        itemIcon = reg.registerIcon("factorization:mansandwich");
        spicy = reg.registerIcon("factorization:mansandwich_spicy");
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        super.getSubItems(item, tab, list);
        list.add(new ItemStack(this, 1, 1));
    }

    boolean given_recommendation;

    @Override
    public String getManwichDomain(EntityPlayer player) {
        return "factorization";
    }

    @Override
    public void recommendManwich(EntityPlayer player) {
        if (given_recommendation) return;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack is = player.inventory.getStackInSlot(i);
            if (!ItemUtil.is(is, this)) continue;
            player.addChatMessage(new ChatComponentTranslation("item.factorization:mansandwich.eatit"));
            return;
        }
        player.addChatMessage(new ChatComponentTranslation("item.factorization:mansandwich.hungry"));
        given_recommendation = true;
    }
}
