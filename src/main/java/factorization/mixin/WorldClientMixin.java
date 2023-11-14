package factorization.mixin;

import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.coremodhooks.HookTargetsClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.event.world.WorldEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldClient.class)
@SideOnly(Side.CLIENT)
public class WorldClientMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/eventhandler/EventBus;post(Lcpw/mods/fml/common/eventhandler/Event;)Z", remap = false))
    private boolean init(EventBus bus, WorldEvent.Load event) {
        return HookTargetsClient.abortClientLoadEvent(bus, event);
    }
}
