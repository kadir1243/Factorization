package factorization.truth.gen;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import factorization.truth.DocumentationModule;
import factorization.truth.api.IDocGenerator;
import factorization.truth.api.ITypesetter;
import factorization.truth.api.TruthError;
import factorization.util.LangUtil;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class ItemListViewer implements IDocGenerator {
    @Override
    public void process(ITypesetter sb, String arg) throws TruthError {
        if (arg.equalsIgnoreCase("all")) {
            listAll(sb, null);
            return;
        }
        CreativeTabs found = null;
        for (CreativeTabs ct : CreativeTabs.creativeTabArray) {
            if (ct.getTabLabel().equalsIgnoreCase(arg)) {
                found = ct;
                break;
            }
        }
        if (found != null) {
            listAll(sb, found);
        } else {
            listTabs(sb);
        }
    }
    
    void listTabs(ITypesetter sb) throws TruthError {
        String ret = "";
        ret += "\\title{Item Categories}\n\n";
        ret += "\n\n\\link{cgi/items/all}{All Items}";
        for (CreativeTabs ct : CreativeTabs.creativeTabArray) {
            if (ct == CreativeTabs.tabAllSearch || ct == CreativeTabs.tabInventory) {
                continue;
            }
            String text = ct.getTabLabel();
            ret += "\\nl\\link{cgi/items/" + text + "}{" + LangUtil.translateThis("itemGroup." + text) + "}";
        }
        sb.write(ret);
    }
    
    void listAll(ITypesetter out, CreativeTabs ct) throws TruthError {
        if (ct == null) {
            out.write("\\title{All Items}");
        } else {
            String title = ct.getTabLabel();
            title = LangUtil.translateThis("itemGroup." + title);
            out.write("\\title{" + title + "}");
        }
        out.write("\n\n");
        int size = DocumentationModule.getNameItemCache().size();
        Multimap<String, ItemStack> found = HashMultimap.create(size, 1);
        ArrayList<String> toSort = new ArrayList<>();
        for (Entry<String, List<ItemStack>> pair : DocumentationModule.getNameItemCache().entrySet()) {
            List<ItemStack> items = pair.getValue();
            for (ItemStack is : items) {
                if (ct != null && is.getItem().getCreativeTab() != ct) {
                    continue;
                }
                String name = is.getDisplayName();
                if (!found.containsKey(name)) {
                    toSort.add(name);
                }
                found.put(name, is);
            }
        }
        toSort.sort(String.CASE_INSENSITIVE_ORDER);
        
        for (String name : toSort) {
            for (ItemStack is : found.get(name)) {
                if (is == null) continue;
                out.write(is);
                out.write(" ");
                out.write(name);
                out.write("\n\n");
            }
        }
    }

}
