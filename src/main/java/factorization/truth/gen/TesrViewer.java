package factorization.truth.gen;

import factorization.truth.api.IDocGenerator;
import factorization.truth.api.ITypesetter;
import factorization.truth.api.TruthError;
import factorization.truth.word.ClipboardWord;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TesrViewer implements IDocGenerator {
    @Override
    public void process(ITypesetter out, String arg) throws TruthError {
        List<Class<? extends TileEntity>> cs = new ArrayList<>(TileEntityRendererDispatcher.instance.mapSpecialRenderers.keySet());
        cs.sort(Comparator.comparing(Class::getCanonicalName));
        out.write("\\title{TESRs}\n\n");
        for (Class<? extends TileEntity> c : cs) {
            out.write("\n\n" + c.getCanonicalName() + " ");
            out.write(new ClipboardWord("/scrap DeregisterTesr " + c.getCanonicalName()));
        }
    }
}
