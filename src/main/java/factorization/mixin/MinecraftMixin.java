package factorization.mixin;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.coremodhooks.HookTargetsClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "func_147116_af", at = @At("HEAD"), cancellable = true)
    private void clickMouse(CallbackInfo ci) {
        if (HookTargetsClient.attackButtonPressed()) {
            ci.cancel();
        }
    }

    @Inject(method = "func_147121_ag", at = @At("HEAD"), cancellable = true)
    private void rightClickMouse(CallbackInfo ci) {
        if (HookTargetsClient.useButtonPressed()) {
            ci.cancel();
        }
    }

}
