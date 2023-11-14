package factorization.mixin;

import factorization.coremodhooks.HookTargetsServer;
import net.minecraft.entity.Entity;
import net.minecraft.world.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Explosion.class)
public class ExplosionMixin {
    @Redirect(method = "doExplosionA", at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/EnchantmentProtection;func_92092_a(Lnet/minecraft/entity/Entity;D)D"))
    private double func_92092_a(Entity ent, double dmg) {
        return HookTargetsServer.clipExplosionResistance(ent, dmg);
    }
}
