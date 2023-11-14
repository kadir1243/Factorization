package factorization.servo.instructions;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import factorization.api.Coord;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.api.datahelpers.Share;
import factorization.common.BlockIcons;
import factorization.servo.Instruction;
import factorization.servo.ServoMotor;
import factorization.servo.ServoStack;
import factorization.util.FzUtil;

public class Compare extends Instruction {
    static enum CmpType {
        LT, LE, EQ, NE, GE, GT;
        IIcon getIcon() {
            switch (this) {
            default:
            case EQ: return BlockIcons.servo$cmp_eq;
            case NE: return BlockIcons.servo$cmp_ne;
            case GE: return BlockIcons.servo$cmp_ge;
            case GT: return BlockIcons.servo$cmp_gt;
            case LE: return BlockIcons.servo$cmp_le;
            case LT: return BlockIcons.servo$cmp_lt;
            }
        }
        
        private <T> boolean apply(Comparable<Comparable<T>> a, Comparable<T> b) {
            int cmp = (int) Math.signum(a.compareTo(b));
            return switch (this) {
                default -> cmp == 0;
                case NE -> cmp != 0;
                case GE -> cmp >= 0;
                case GT -> cmp > 0;
                case LE -> cmp <= 0;
                case LT -> cmp < 0;
            };
        }
    }
    
    CmpType cmp = CmpType.EQ;
    
    @Override
    public IDataSerializable putData(String prefix, DataHelper data) throws IOException {
        cmp = data.as(Share.VISIBLE, "cmp").putEnum(cmp);
        return this;
    }

    @Override
    protected ItemStack getRecipeItem() {
        return new ItemStack(Items.quartz);
    }

    @Override
    public void motorHit(ServoMotor motor) {
        ServoStack ss = motor.getArgStack();
        Object a = ss.pop();
        if (a == null) {
            motor.putError("CMP: Stack underflow");
            return;
        }
        Object b = ss.popType(a.getClass());
        if (b == null) {
            motor.putError("CMP: Stack underflow of type: " + a.getClass());
            return;
        }
        if (!(a instanceof Comparable<?> bC)) {
            motor.putError("CMP: Not Comparable: " + a.getClass());
            return;
        }
        ss.push(cmp.apply((Comparable)a, bC));
    }

    @Override
    public IIcon getIcon(ForgeDirection side) {
        return cmp.getIcon();
    }

    @Override
    public String getName() {
        return "fz.instruction.cmp";
    }

    @Override
    public String getInfo() {
        return null;
        //return "" + cmp.toString();
    }
    
    @Override
    public boolean onClick(EntityPlayer player, Coord block, ForgeDirection side) {
        if (!playerHasProgrammer(player)) {
            return false;
        }
        cmp = FzUtil.shiftEnum(cmp, CmpType.values(), 1);
        return true;
    }
}
