package factorization.servo;

import factorization.api.Coord;
import factorization.api.FzOrientation;
import factorization.shared.Core;
import factorization.shared.Core.TabType;
import factorization.shared.ItemCraftingComponent;
import factorization.util.SpaceUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;

public class ItemServoMotor extends ItemCraftingComponent {

    public ItemServoMotor(String name) {
        super("servo/" + name);
        Core.tab(this, TabType.SERVOS);
        setMaxStackSize(16);
    }

    protected AbstractServoMachine makeMachine(World w) {
        return new ServoMotor(w);
    }
    
    @Override
    public boolean onItemUseFirst(ItemStack is, EntityPlayer player, World w, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        if (!player.capabilities.allowEdit) return false;
        Coord c = new Coord(w, x, y, z);
        if (c.getTE(TileEntityServoRail.class) == null) {
            return false;
        }
        if (w.isRemote) {
            return false;
        }
        AbstractServoMachine motor = makeMachine(w);
        motor.posX = c.x;
        motor.posY = c.y;
        motor.posZ = c.z;
        //c.setAsEntityLocation(motor);
        //w.spawnEntityInWorld(motor);
        ForgeDirection top = ForgeDirection.getOrientation(side);
        
        ArrayList<FzOrientation> valid = new ArrayList<>();
        motor.motionHandler.beforeSpawn();
        
        ForgeDirection playerAngle = ForgeDirection.getOrientation(SpaceUtil.determineOrientation(player));
        
        for (ForgeDirection fd : ForgeDirection.VALID_DIRECTIONS) {
            if (top == fd || top.getOpposite() == fd) {
                continue;
            }
            if (motor.motionHandler.validDirection(fd, false)) {
                FzOrientation t = FzOrientation.fromDirection(fd).pointTopTo(top);
                if (t != FzOrientation.UNKNOWN) {
                    if (fd == playerAngle) {
                        valid.clear();
                        valid.add(t);
                        break;
                    }
                    valid.add(t);
                }
            }
        }
        final Vec3 vP = Vec3.createVectorHelper(hitX, hitY, hitZ).normalize();
        valid.sort((a, b) -> {
            double dpA = vP.dotProduct(Vec3.createVectorHelper(a.facing.offsetX, a.facing.offsetY, a.facing.offsetZ));
            double dpB = vP.dotProduct(Vec3.createVectorHelper(b.facing.offsetX, b.facing.offsetY, b.facing.offsetZ));
            double theta_a = Math.acos(dpA);
            double theta_b = Math.acos(dpB);
            return Double.compare(theta_a, theta_b);
        });
        if (!valid.isEmpty()) {
            motor.motionHandler.orientation = valid.get(0);
        }
        motor.motionHandler.prevOrientation = motor.motionHandler.orientation;
        if (!player.capabilities.isCreativeMode) {
            is.stackSize--;
        }
        motor.spawnServoMotor();
        return true;
    }
}
