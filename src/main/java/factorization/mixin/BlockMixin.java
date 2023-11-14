package factorization.mixin;

import factorization.coremodhooks.HookTargetsServer;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
    @Inject(method = "onBlockDestroyedByExplosion", at = @At("TAIL"))
    private void onBlockDestroyedByExplosion(World world, int x, int y, int z, Explosion explosion, CallbackInfo ci) {
        HookTargetsServer.diamondExploded(this, world, x, y, z);
    }

    @SuppressWarnings({"DataFlowIssue", "RedundantCast", "ConstantValue"})
    @Inject(method = "canDropFromExplosion", at = @At("HEAD"), cancellable = true)
    private void canDropFromExplosion(Explosion explosion, CallbackInfoReturnable<Boolean> cir) {
        if (((Block) (Object) this) == Blocks.diamond_block) {
            cir.setReturnValue(false);
        }
    }
}
