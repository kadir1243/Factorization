package factorization.mixin;

import factorization.coremodhooks.IKinematicTracker;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public class EntityMixin implements IKinematicTracker {
    @Unique
    private long kinematics_last_change; // Should be initialized to somehting negative, but our mixins don't support that
    @Unique
    private double kinematics_motX, kinematics_motY, kinematics_motZ;

    @Override
    public double getKinematics_motX() {
        return kinematics_motX;
    }

    @Override
    public double getKinematics_motY() {
        return kinematics_motY;
    }

    @Override
    public double getKinematics_motZ() {
        return kinematics_motZ;
    }

    @Override
    public double getKinematics_yaw() {
        return kinematics_yaw;
    }

    @Unique
    private double kinematics_yaw;

    @Override
    public void reset(long now) {
        if (now == kinematics_last_change) return;
        kinematics_last_change = now;
        kinematics_motX = ((Entity) (Object)this).motionX;
        kinematics_motY = ((Entity) (Object)this).motionY;
        kinematics_motZ = ((Entity) (Object)this).motionZ;
        kinematics_yaw = ((Entity) (Object)this).rotationYaw;
    }


}
