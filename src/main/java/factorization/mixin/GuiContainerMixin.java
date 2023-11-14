package factorization.mixin;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.coremodhooks.UnhandledGuiKeyEvent;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@SideOnly(Side.CLIENT)
@Mixin(GuiContainer.class)
public class GuiContainerMixin extends GuiScreen {
    @Inject(method = "keyTyped", at = @At("TAIL"))
    private void keyTypedInject(char typedChar, int keyCode) {
        MinecraftForge.EVENT_BUS.post(new UnhandledGuiKeyEvent(typedChar, keyCode, mc.thePlayer, mc.currentScreen));
    }
}
