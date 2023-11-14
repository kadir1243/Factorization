package factorization.migration;

import factorization.common.FactoryType;
import factorization.shared.TileEntityCommon;
import factorization.sockets.TileEntitySocketBase;
import net.minecraft.nbt.NBTTagCompound;

public class MigrationHelper {
    public static boolean migrate(byte oldVersion, FactoryType ft, TileEntityCommon tec, NBTTagCompound tag) {
        boolean any = false;
        if (tec instanceof TileEntitySocketBase tesb && oldVersion < 2) {
            tesb.migrate1to2();
            any = true;
        }
        return any;
    }
}
