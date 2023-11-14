package factorization.oreprocessing;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import factorization.api.IActOnCraft;
import factorization.shared.Core;
import factorization.shared.Core.TabType;
import factorization.shared.ItemFactorization;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.*;

public class ItemOreProcessing extends ItemFactorization implements IActOnCraft {
    public static List<String> OD_ores = new ArrayList<>();
    public static List<String> OD_ingots = new ArrayList<>();
    private final OreType type;

    public enum OreType {
        IRON(0, 0xD8D8D8, "Iron", "oreIron", "ingotIron"),
        GOLD(1, 0xEEEB28, "Gold", "oreGold", "ingotGold"),
        LEAD(2, 0x2F2C3C, "Lead", "oreLead", "ingotLead"),
        TIN(3, 0xD7F7FF, "Tin", "oreTin", "ingotTin"),
        COPPER(4, 0xD68C39, "Copper", "oreCopper", "ingotCopper"),
        SILVER(5, 0x7B96B9, "Silver", null, "ingotSilver"),
        GALENA(6, 0x687B99, "Galena", "oreSilver", null),
        COBALT(8, 0x2376DD, "Cobalt", "oreCobalt", "ingotCobalt"),
        ARDITE(9, 0xF48A00, "Ardite", "oreArdite", "ingotArdite"),
        DARKIRON(10, 0x5000D4, "Dark Iron", "oreFzDarkIron", "ingotFzDarkIron");

        static {
            COBALT.surounding_medium = new ItemStack(Blocks.netherrack);
            ARDITE.surounding_medium = new ItemStack(Blocks.netherrack);
        }
        
        private final int ID;
        final int color;
        final String en_name;
        final String OD_ore;
        final String OD_ingot;
        public boolean enabled = false;
        ItemStack processingResult = null;
        ItemStack surounding_medium = new ItemStack(Blocks.stone);
        OreType(int ID, int color, String en_name, String OD_ore, String OD_ingot) {
            this.ID = ID;
            this.color = color;
            this.en_name = en_name;
            this.OD_ore = OD_ore;
            this.OD_ingot = OD_ingot;
            if (OD_ore != null) {
                OD_ores.add(OD_ore);
            }
            if (OD_ingot != null) {
                OD_ingots.add(OD_ingot);
            }
        }
        
        public void enable() {
            if (!this.enabled) {
                ItemStack dirty = this.getStack("gravel");
                ItemStack clean = this.getStack("clean");
                ItemStack reduced = this.getStack("reduced");
                ItemStack crystal = this.getStack("crystal");
                OreDictionary.registerOre("dirtyGravel" + this.en_name, dirty);
                OreDictionary.registerOre("cleanGravel" + this.en_name, clean);
                OreDictionary.registerOre("reduced" + this.en_name, reduced);
                OreDictionary.registerOre("crystalline" + this.en_name, crystal);
            }
            this.enabled = true;
        }
        
        public static OreType fromOreClass(String oreClass) {
            for (OreType ot : values()) {
                if (ot.OD_ingot != null && ot.OD_ingot.equals(oreClass)) {
                    return ot;
                }
                if (ot.OD_ore != null && ot.OD_ore.equals(oreClass)) {
                    return ot;
                }
            }
            return null;
        }

        private static final OreType[] VALUES = new OreType[] {IRON, GOLD, LEAD, TIN, COPPER, SILVER, GALENA, null, COBALT, ARDITE, DARKIRON};

        public static OreType fromID(int id) {
            if (id < 0 || id >= VALUES.length) {
                return null;
            }
            return VALUES[id];
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }

        private final Map<String, Item> items = new HashMap<>();

        public Item getItem(String stateName) {
            return items.computeIfAbsent(stateName, s -> new ItemOreProcessing(s, this));
        }

        public ItemStack getStack(String stateName) {
            return new ItemStack(getItem(stateName));
        }
    }

    private final String stateName;

    public ItemOreProcessing(String stateName, OreType type) {
        super("ore/" + stateName, TabType.MATERIALS);
        this.stateName = stateName;
        this.type = type;
        if (type != OreType.GALENA &&
                type != OreType.SILVER &&
                !(stateName.equals("crystal") ||
                        stateName.equals("reduced") ||
                        stateName.equals("gravel") ||
                        stateName.equals("clean"))) {
            setCreativeTab(Core.tabFactorization);
        }
    }

    @Override
    public int getColorFromItemStack(ItemStack is, int renderPass) {
        return this.type.color;
    }
    
    @Override
    public String getUnlocalizedName() {
        return "item.factorization:ore/" + stateName + "/" + this.type.toString();
    }

    @Override
    public void onCraft(ItemStack is, IInventory craftMatrix, int craftSlot, ItemStack result, EntityPlayer player) {
        if (result == null || player == null) {
            return;
        }
        if (player.worldObj == null) {
            if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
                return;
            }
        } else if (player.worldObj.isRemote) {
            return;
        }
        for (OreType value : OreType.values()) {
            if (result.getItem() != value.getItem("clean")) {
                return;
            }
            if (is.getItem() != value.getItem("gravel")) {
                return;
            }
        }
        if (Math.random() > 0.25) {
            return;
        }
        ItemStack toAdd = new ItemStack(Core.registry.sludge);
        if (!player.inventory.addItemStackToInventory(toAdd)) {
            player.dropPlayerItemWithRandomChoice(new ItemStack(Core.registry.sludge), false);
        }
    }
}
