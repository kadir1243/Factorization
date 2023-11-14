package factorization.mixin;

import cofh.asmhooks.HooksCore;
import factorization.coremodhooks.HookTargetsServer;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(targets = "cofh.tweak.asmhooks.HooksCore")
@Pseudo
public class CofhTweaksMixin {
    // TODO
    @Unique
    // Skyboy special request
    // cofh/tweak/asmhooks/HooksCore#CoFHTweaks_FZ_Hook(chunk, entity, bb, collidingBoundingBoxes)
    private static void CoFHTweaks_FZ_Hook(Chunk chunk, Entity entity, AxisAlignedBB box, List<AxisAlignedBB> boxes) {
        HookTargetsServer.addConstantCollidersCOFH(chunk, entity, box, boxes);
    }
}
