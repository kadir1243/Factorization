package factorization.mixin;

import factorization.coremodhooks.HookTargetsServer;
import factorization.coremodhooks.IExtraChunkData;
import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.List;

@Mixin(Chunk.class)
public class ChunkMixin implements IExtraChunkData {
    @Unique
    private Entity[] constant_colliders;
    @Override
    public Entity[] getConstantColliders() {
        return constant_colliders;
    }

    @Override
    public void setConstantColliders(Entity[] constants) {
        constant_colliders = constants;
    }

    @Inject(method = "getEntitiesWithinAABBForEntity", at = @At("TAIL"))
    private void getEntitiesWithinAABBForEntity(Entity p_76588_1_, AxisAlignedBB p_76588_2_, List<Entity> p_76588_3_, IEntitySelector p_76588_4_) {
        HookTargetsServer.addConstantColliders(this, p_76588_1_, p_76588_2_, p_76588_3_, p_76588_4_);
    }
}
