package factorization.compat;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import factorization.api.IRotationalEnergySource;
import factorization.shared.Core;
import factorization.truth.DocumentationModule;
import factorization.truth.api.IObjectWriter;
import factorization.truth.word.ItemWord;
import ic2.api.recipe.Recipes;
import ic2.core.AdvRecipe;
import ic2.core.AdvShapelessRecipe;
import mods.railcraft.api.crafting.IBlastFurnaceRecipe;
import mods.railcraft.api.crafting.ICokeOvenRecipe;
import mods.railcraft.api.crafting.IRockCrusherRecipe;
import mods.railcraft.api.crafting.RailcraftCraftingManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class DefaultCompatEvents {
    @SubscribeEvent
    public void compatInit(CompatLoadEvent.Init event) {
        if (Loader.isModLoaded("erebus")) {
            Iterable<Object> compost_recipes = find("erebus.recipes.ComposterRegistry", "registry");
            if (compost_recipes != null) {
                FMLInterModComms.sendMessage(DocumentationModule.modid, "AddRecipeCategory", "container.composter|factorization.compat.erebus.Compat_erebus|compost_recipes");
            }
            Iterable<Object> offering_altar_recipes = find("erebus.recipes.OfferingAltarRecipe", "list");
            if (offering_altar_recipes != null) {
                FMLInterModComms.sendMessage(DocumentationModule.modid, "AddRecipeCategory", "tile.erebus.offeringAltar.name|factorization.compat.erebus.Compat_erebus|offering_altar_recipes");
            }
            Iterable<Object> smoothie_recipes = find("erebus.recipes.SmoothieMakerRecipe", "recipes");
            if (smoothie_recipes != null) {
                FMLInterModComms.sendMessage(DocumentationModule.modid, "AddRecipeCategory", "tile.erebus.smoothieMaker.name|factorization.compat.erebus.Compat_erebus|smoothie_recipes");
            }
        }
        if (Loader.isModLoaded("IC2")) {
            IRotationalEnergySource.adapter.register(new RotationalEnergySourceAdapter());

            NBTTagCompound tag;
            {
                tag = standardIc2Recipe("blockcutter");
                tag.setTag("catalyst", list("getValue().metadata.'hardness'#Blade Tier"));
                imc(tag);
            }
            {
                tag = standardIc2Recipe("centrifuge");
                tag.setTag("catalyst", list("getValue().metadata.'minHeat'#Heat"));
                imc(tag);
            }

            Class<Recipes> cl = Recipes.class;
            for (Field field : cl.getFields()) {
                final int modifiers = field.getModifiers();
                if ((modifiers & Modifier.PUBLIC) == 0) continue;
                if ((modifiers & Modifier.STATIC) == 0) continue;
                field.setAccessible(true);
                String name = field.getName();
                if (handled.contains(name)) continue;
                FMLInterModComms.sendMessage(DocumentationModule.modid, "AddRecipeCategory", "fzdoc.ic2.recipe." + name + "|ic2.api.recipe.Recipes|" + name);
            }
            IObjectWriter.adapter.register(AdvRecipe.class, new WriteShapedRecipe());
            IObjectWriter.adapter.register(AdvShapelessRecipe.class, new WriteShapelessRecipe());
        }

        if (Loader.isModLoaded("Railcraft")) {
            List<IRecipe> rollingmachine_recipes = RailcraftCraftingManager.rollingMachine.getRecipeList();
            List<? extends IRockCrusherRecipe> crusher_recipes = RailcraftCraftingManager.rockCrusher.getRecipes();
            List<? extends ICokeOvenRecipe> coke_oven = RailcraftCraftingManager.cokeOven.getRecipes();
            List<? extends IBlastFurnaceRecipe> blast_furnace = RailcraftCraftingManager.blastFurnace.getRecipes();
            FMLInterModComms.sendMessage(DocumentationModule.modid, "AddRecipeCategory", "tile.railcraft.machine.alpha.rolling.machine.name|factorization.compat.railcraft.Compat_Railcraft|rollingmachine_recipes");
            //FMLInterModComms.sendMessage(DocumentationModule.modid, "AddRecipeCategory", "tile.railcraft.machine.alpha.rock.crusher.name|factorization.compat.railcraft.Compat_Railcraft|crusher_recipes");
            FMLInterModComms.sendMessage(DocumentationModule.modid, "AddRecipeCategory", "railcraft.gui.coke.oven|factorization.compat.railcraft.Compat_Railcraft|coke_oven");
            FMLInterModComms.sendMessage(DocumentationModule.modid, "AddRecipeCategory", "railcraft.gui.blast.furnace|factorization.compat.railcraft.Compat_Railcraft|blast_furnace");
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("category", "tile.railcraft.machine.alpha.rock.crusher.name|factorization.compat.railcraft.Compat_Railcraft|crusher_recipes");
            tag.setTag("input", list("getInput()"));
            tag.setTag("output", list("getPossibleOuputs()"));
            imc(tag);
        }
    }

    private Iterable<Object> find(String className, String fieldName) {
        try {
            Class<? super Object> compost = ReflectionHelper.getClass(getClass().getClassLoader(), className);
            return ReflectionHelper.getPrivateValue(compost, null, fieldName);
        } catch (Throwable t) {
            Core.logWarn("Couldn't find erebus recipe: " + className + "." + fieldName, t);
            return null;
        }
    }

    public static NBTTagList list(String ...args) {
        NBTTagList ret = new NBTTagList();
        for (String a : args) ret.appendTag(new NBTTagString(a));
        return ret;
    }

    private void imc(NBTTagCompound tag) {
        FMLInterModComms.sendMessage(DocumentationModule.modid, "AddRecipeCategoryGuided", tag);
    }

    private static class WriteShapedRecipe implements IObjectWriter<AdvRecipe> {
        @Override
        public void writeObject(List<Object> out, AdvRecipe val, IObjectWriter<Object> generic) {
            int mask = val.masks[0];
            int m = 0;
            for (int i = 0; i < 9; i++) {
                if ((mask & 1 << 8 - i) == 0) {
                    out.add(new ItemWord((ItemStack) null));
                } else {
                    out.add(new ItemWord(AdvRecipe.expand(val.input[m++])));
                }
                if ((i + 1) % 3 == 0) {
                    out.add("\\nl");
                }
            }
        }
    }

    private static class WriteShapelessRecipe implements IObjectWriter<AdvShapelessRecipe> {
        @Override
        public void writeObject(List<Object> out, AdvShapelessRecipe val, IObjectWriter<Object> generic) {
            out.add("Shapeless: ");
            for (Object obj : val.input) {
                out.add(new ItemWord(AdvRecipe.expand(obj)));
            }
        }
    }

    private NBTTagCompound standardIc2Recipe(String name) {
        handled.add(name);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("category", "fzdoc.ic2.recipe." + name + "|ic2.api.recipe.Recipes|" + name);
        tag.setTag("input", list("getKey().getInputs()#Input"));
        tag.setTag("output", list("getValue().items"));
        return tag;
    }

    private final List<String> handled = new ArrayList<>();
}
