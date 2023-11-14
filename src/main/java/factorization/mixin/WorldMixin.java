package factorization.mixin;

import factorization.coremodhooks.HookTargetsServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldMixin {
    @Inject(method = "checkBlockCollision", at = @At("RETURN"))
    public boolean checkBlockCollision(AxisAlignedBB box, CallbackInfoReturnable<Boolean> cir) {
        return HookTargetsServer.checkHammerCollision((World) (Object) this, box);
    }
}
