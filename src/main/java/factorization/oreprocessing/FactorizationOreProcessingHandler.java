package factorization.oreprocessing;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import factorization.oreprocessing.ItemOreProcessing.OreType;
import factorization.shared.Core;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidContainerRegistry.FluidContainerData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreDictionary.OreRegisterEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FactorizationOreProcessingHandler {
    private static final String waterBucket = "fz.waterBucketLike";
    private Map<String, ItemStack> bestIngots = new HashMap<>();
    public static final float GRIND_MULTIPLY = 2F;
    public static final float REDUCE_MULTIPLY = 2.5F;
    public static final float CRYSTALLIZE_MULTIPLY = 3F;
    
    public static final float GRIND = GRIND_MULTIPLY;
    public static final float WASH = 1F;
    public static final float REDUCE = REDUCE_MULTIPLY/GRIND_MULTIPLY;
    public static final float CRYSTALLIZE = CRYSTALLIZE_MULTIPLY/REDUCE_MULTIPLY;
    
    void smelt(ItemStack is, ItemStack ore, ItemStack ingot) {
        float xp = 0;
        FurnaceRecipes smeltingRegistry = FurnaceRecipes.smelting();
        try {
            xp = smeltingRegistry.func_151398_b(ore);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        smeltingRegistry.func_151394_a(is.copy(), ingot.copy(), xp);
    }
    
    void addProcessingFront(OreType oreType, ItemStack ore, ItemStack ingot) {
        oreType.enable();
        ItemStack dirty = oreType.getStack("gravel");
        ItemStack clean = oreType.getStack("clean");
        TileEntityGrinder.addRecipe(ore, dirty, GRIND);
        TileEntitySlagFurnace.SlagRecipes.register(dirty, 1.1F, ingot, 0.2F, Blocks.dirt); //Or it could output reduced chunks instead.
        ItemStack clean8 = clean.copy();
        clean8.stackSize = 8;

        //dirty gravel -> clean gravel
        Core.registry.shapelessOreRecipe(clean, waterBucket, dirty);
        Core.registry.shapelessOreRecipe(clean8, waterBucket, dirty, dirty, dirty, dirty, dirty, dirty, dirty, dirty);

        smelt(clean, ore, ingot);
        smelt(dirty, ore, ingot);
    }
    
    void addProcessingEnd(OreType oreType, ItemStack ore, ItemStack ingot) {
        //Everything can be slagged
        oreType.enable();
        if (oreType.processingResult != null) {
            return;
        }
        oreType.processingResult = ingot;
        
        ItemStack reduced = oreType.getStack("reduced");
        ItemStack crystal = oreType.getStack("crystal");
        
        TileEntityCrystallizer.addRecipe(reduced, crystal, CRYSTALLIZE, Core.registry.aqua_regia);
        
        //All processing steps can be smelted
        smelt(reduced, ore, ingot);
        smelt(crystal, ore, ingot);
    }
    
    void addStandardReduction(OreType oreType, ItemStack ore, ItemStack ingot) {
        oreType.enable();
        ItemStack clean = oreType.getStack("clean");
        ItemStack reduced = oreType.getStack("reduced");
        TileEntitySlagFurnace.SlagRecipes.register(ore, 1.2F, ingot, 0.4F, oreType.surounding_medium);
        int r = (int)REDUCE;
        TileEntitySlagFurnace.SlagRecipes.register(clean, r, reduced, REDUCE - r, reduced);
    }
    
    void addGalenaReduction(OreType oreType, ItemStack ore) {
        ItemStack clean_galena = oreType.getStack("clean");
        ItemStack reduced_silver = OreType.SILVER.getStack("reduced");
        ItemStack reduced_lead = OreType.LEAD.getStack("reduced");
        
        TileEntitySlagFurnace.SlagRecipes.register(clean_galena, REDUCE, reduced_lead, REDUCE, reduced_silver);
    }
    
    //ArrayList<OreType> createdBodies = new ArrayList();

    void handleNewOre(String oreClass, ItemStack ore) {
        OreType oreType = OreType.fromOreClass(oreClass);
        if (oreType == null) {
            return;
        }
        if (ItemOreProcessing.OD_ores.contains(oreClass)) {
            oreType.enable();
            ItemStack ingot = bestIngots.get(oreClass);
            if (ingot == null) {
                ingot = oreType.processingResult;
            }
            if (ingot == null) {
                ingot = FurnaceRecipes.smelting().getSmeltingResult(ore);
            }
            if (ingot == null) return;
            addProcessingFront(oreType, ore, ingot);
            if (oreType == OreType.GALENA) {
                addGalenaReduction(oreType, ore);
                addProcessingEnd(OreType.SILVER, ore, new ItemStack(Core.registry.silver_ingot));
                addProcessingEnd(OreType.LEAD, ore, new ItemStack(Core.registry.lead_ingot));
                TileEntitySlagFurnace.SlagRecipes.register(ore, 1.2F, new ItemStack(Core.registry.lead_ingot), 1F, new ItemStack(Core.registry.silver_ingot));
            } else {
                addStandardReduction(oreType, ore, ingot);
                addProcessingEnd(oreType, ore, ingot);
            }
            oreType.processingResult = ingot;
        }
    }

    @SubscribeEvent
    public void registerOre(OreRegisterEvent evt) {
        handleNewOre(evt.Name, evt.Ore);
    }
    
    void loadWater() {
        FluidStack h2o = FluidContainerRegistry.getFluidForFilledItem(new ItemStack(Items.water_bucket));
        if (h2o == null) {
            return; //???
        }
        for (FluidContainerData container : FluidContainerRegistry.getRegisteredFluidContainerData()) {
            FluidStack liq = container.fluid;
            if (h2o.isFluidEqual(liq) && liq.amount == FluidContainerRegistry.BUCKET_VOLUME && container.filledContainer != null) {
                OreDictionary.registerOre(waterBucket, container.filledContainer);
            }
        }
    }
    
    public void addDictOres() {
        loadWater();
        
        for (OreType type : ItemOreProcessing.OreType.values()) {
            String oreClass = type.OD_ore;
            String ingotClass = type.OD_ingot;
            
            if (oreClass == null) {
                continue;
            }
            
            ItemStack bestIngot = null;
            Iterable<ItemStack> oreList = OreDictionary.getOres(oreClass);
            if (oreList == null || !oreList.iterator().hasNext()) {
                continue;
            }
            for (ItemStack ore : oreList) {
                ItemStack smeltsTo = FurnaceRecipes.smelting().getSmeltingResult(ore);
                if (smeltsTo == null) {
                    if (ingotClass == null) {
                        break;
                    }
                    Iterable<ItemStack> ingotList = OreDictionary.getOres(ingotClass);
                    if (ingotList == null) {
                        continue;
                    }
                    Iterator<ItemStack> it = ingotList.iterator();
                    if (!it.hasNext()) {
                        continue;
                    }
                    bestIngot = it.next();
                    break;
                }
                if (bestIngot == null || ore.getItemDamage() != 0) {
                    bestIngot = smeltsTo;
                }
            }
            if (bestIngot == null) {
                continue;
            }
            bestIngots.put(oreClass, bestIngot);
            for (ItemStack ore : oreList) {
                handleNewOre(oreClass, ore);
            }
        }
        Core.loadBus(this);
    }
    
}
