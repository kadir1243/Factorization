package factorization.api.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class CraftingManagerGeneric<MachineType> implements Iterable<IVexatiousCrafting<MachineType>> {
    private static final Map<Class, CraftingManagerGeneric> systems = new HashMap<>();

    public static <M> CraftingManagerGeneric<M> get(Class<M> klass) {
        CraftingManagerGeneric<M> ret = systems.get(klass);
        if (ret != null) return ret;
        systems.put(klass, ret = new CraftingManagerGeneric<>(klass));
        return ret;
    }

    public final ArrayList<IVexatiousCrafting<MachineType>> list = new ArrayList<>();

    CraftingManagerGeneric(Class<MachineType> machineClass) {
        systems.put(machineClass, this);
    }

    public IVexatiousCrafting<MachineType> find(MachineType machine) {
        for (IVexatiousCrafting<MachineType> recipe : list) {
            if (recipe.matches(machine)) return recipe;
        }
        return null;
    }

    public void add(IVexatiousCrafting<MachineType> recipe) {
        list.add(recipe);
    }

    @Override
    public Iterator<IVexatiousCrafting<MachineType>> iterator() {
        return list.iterator();
    }
}
