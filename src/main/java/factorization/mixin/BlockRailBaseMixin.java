package factorization.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(BlockRailBase.class)
public class BlockRailBaseMixin extends Block {
    private BlockRailBaseMixin(Material materialIn) {
        super(materialIn);

        throw new AssertionError();
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List<AxisAlignedBB> list, Entity collider) {
        super.addCollisionBoxesToList(world, x, y, z, mask, list, collider);

        if (collider instanceof EntityPlayer) {
            final double h = 0.5;
            final double w = 0.25;
            final double s = 6.0 / 16.0;
            AxisAlignedBB box = null;
            BlockRailBase me = ((BlockRailBase)(Object)this);
            int i = me.getBasicRailMetadata(world, null, x, y, z);
            if (i == 0x2) { // Ascend East
                box = AxisAlignedBB.getBoundingBox(x + w, y, z + s, x + 1, y + h, z + 1 - s);
            } else if (i == 0x3) { // Ascend West
                box = AxisAlignedBB.getBoundingBox(x, y, z + s, x + w, y + h, z + 1 - s);
            } else if (i == 0x4) { // Ascend North
                box = AxisAlignedBB.getBoundingBox(x + s, y, z, x + 1 - s, y + h, z + w);
            } else if (i == 0x5) { // Ascend South
                box = AxisAlignedBB.getBoundingBox(x + s, y, z + w, x + 1 - s, y + h, z + 1);
            }
            if (box != null && mask.intersectsWith(box)) {
                list.add(box);
            }
        }
    }
}
