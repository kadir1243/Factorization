package factorization.shared;

import com.google.common.base.Splitter;
import factorization.util.DataUtil;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.Comparator;

public class Graylist<E> {
    public interface Loader<L> {
        L load(String name);
    }

    public Graylist(String source, Loader<E> loader, Comparator<E> comparator) {
        if (source == null || source.isEmpty()) {
            source = "-";
        }
        if (source.startsWith("+")) {
            defaultMode = false;
        } else if (source.startsWith("-")) {
            defaultMode = true;
        } else {
            throw new IllegalArgumentException("Graylist must start with either a + (meaning only the listed objects can be used) or a - (indicating that anything but the listed objects can be used)");
        }
        this.comparator = comparator;

        source = source.substring(1);
        Splitter commas = Splitter.on(",");
        for (String part : commas.split(source)) {
            members.add(loader.load(part));
        }
    }

    public static Graylist<Block> ofBlocks(String source) {
        return new Graylist<>(source, DataUtil::getBlockFromName, (o1, o2) -> o1 == o2 ? 0 : 1);
    }

    public static Graylist<Item> ofItems(String source) {
        return new Graylist<>(source, DataUtil::getItemFromName, (o1, o2) -> o1 == o2 ? 0 : 1);
    }

    public boolean passes(E element) {
        for (E member : members) {
            if (comparator.compare(member, element) == 0) {
                return !defaultMode;
            }
        }
        return defaultMode;
    }

    private final boolean defaultMode;
    private final Comparator<E> comparator;
    private final ArrayList<E> members = new ArrayList<>();
}
