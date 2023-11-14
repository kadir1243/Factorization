package factorization.sockets;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.api.FzOrientation;
import factorization.api.Quaternion;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.api.datahelpers.Share;
import factorization.common.BlockIcons;
import factorization.common.FactoryType;
import factorization.fzds.DeltaChunk;
import factorization.fzds.HammerEnabled;
import factorization.fzds.interfaces.IDeltaChunk;
import factorization.notify.Notice;
import factorization.servo.RenderServoMotor;
import factorization.servo.ServoMotor;
import factorization.shared.BlockRenderHelper;
import factorization.shared.Core;
import factorization.util.InvUtil;
import factorization.util.InvUtil.FzInv;
import factorization.util.ItemUtil;
import factorization.util.SpaceUtil;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

public class SocketShifter extends TileEntitySocketBase {
    public enum ShifterMode {
        MODE_STREAM, MODE_PULSE_EXACT, MODE_PULSE_SOME
    }
    //public boolean streamMode = true; // be like a hopper or a filter
    public ShifterMode mode = ShifterMode.MODE_PULSE_SOME;
    public int foreignSlot = -1;
    public boolean exporting = true;
    public byte transferLimit = 64;
    byte cooldown = 0;
    
    @Override
    public FactoryType getFactoryType() {
        return FactoryType.SOCKET_SHIFTER;
    }
    
    @Override
    public FactoryType getParentFactoryType() {
        return FactoryType.SOCKET_EMPTY;
    }
    
    @Override
    public ItemStack getCreatingItem() {
        return Core.registry.socket_shifter;
    }
    
    @Override
    public boolean canUpdate() {
        return true;
    }
    
    @Override
    public IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        exporting = data.as(Share.MUTABLE, "exp").putBoolean(exporting);
        if (data.hasLegacy("strm")) {
            // NORELEASE: Remove branch in 1.8
            mode = data.as(Share.MUTABLE, "strm").putBoolean(true) ? ShifterMode.MODE_STREAM : ShifterMode.MODE_PULSE_EXACT;
        } else {
            mode = data.as(Share.MUTABLE, "mode").putEnum(mode);
        }
        transferLimit = data.as(Share.MUTABLE, "lim").putByte(transferLimit);
        foreignSlot = data.as(Share.MUTABLE, "for").putInt(foreignSlot);
        cooldown = data.as(Share.PRIVATE, "wait").putByte(cooldown);
        if (data.isWriter()) {
            return this;
        }
        //Validate input
        if (mode == ShifterMode.MODE_STREAM && transferLimit != 1) {
            transferLimit = 1;
            data.log("transfer limit must be 1 in stream mode");
        }
        if (foreignSlot < -1) {
            foreignSlot = -1;
            data.log("foreign slot was < -1");
        }
        if (transferLimit > 64) {
            transferLimit = 64;
            data.log("transfer limit was > 64");
        }
        if (transferLimit < 1) {
            transferLimit = 1;
            data.log("transfer limit was < 1");
        }
        return this;
    }
    
    @Override
    public void genericUpdate(ISocketHolder socket, Coord coord, boolean powered) {
        if (worldObj.isRemote) {
            return;
        }
        if (mode == ShifterMode.MODE_STREAM) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
        } else {
            if (!powered && cooldown > 0) {
                cooldown--;
                return;
            }
            if (cooldown > 0) {
                return;
            }
        }
        if (!powered) {
            return;
        }

        FzInv localInv, foreignInv;
        ForgeDirection back = facing.getOpposite();
        if (socket != this) {
            // meaning we're on a Servo
            localInv = InvUtil.openInventory((IInventory) socket, facing);
        } else {
            coord.adjust(back);
            localInv = InvUtil.openInventory(coord.getTE(IInventory.class), facing);
            coord.adjust(facing);
        }
        if (localInv == null) {
            return;
        }
        foreignInv = openForeignInv(socket, coord, back);

        if (foreignInv == null) {
            return;
        }
        
        FzInv pullInv, pushInv;
        int pullStart, pullEnd, pushStart, pushEnd;
        if (foreignSlot >= foreignInv.size()) {
            return;
        }
        if (exporting) {
            pullInv = localInv;
            pushInv = foreignInv;
            pullStart = 0;
            pullEnd = localInv.size() - 1;
            if (foreignSlot == -1) {
                pushStart = 0;
                pushEnd = foreignInv.size() - 1;
            } else {
                pushStart = pushEnd = foreignSlot;
            }
        } else {
            pullInv = foreignInv;
            pushInv = localInv;
            pushStart = 0;
            pushEnd = localInv.size() - 1;
            if (foreignSlot == -1) {
                pullStart = 0;
                pullEnd = foreignInv.size() - 1;
            } else {
                pullStart = pullEnd = foreignSlot;
            }
        }
        
        pushInv.setCallOnInventoryChanged(false);
        pullInv.setCallOnInventoryChanged(false);
        boolean had_change = false;
        if (mode == ShifterMode.MODE_PULSE_SOME) {
            int toMove = transferLimit;
            out: for (int pull = pullStart; pull <= pullEnd; pull++) {
                int firstEmptySlot = -1;
                for (int push = pushStart; push <= pushEnd; push++) {
                    if (pushInv.get(push) == null) {
                        if (firstEmptySlot == -1) {
                            firstEmptySlot = push;
                        }
                        continue;
                    }
                    int delta = pullInv.transfer(pull, pushInv, push, toMove);
                    toMove -= delta;
                    if (delta > 0) {
                        had_change = true;
                    }
                    if (toMove <= 0) {
                        break out;
                    }
                }
                if (toMove <= 0) {
                    break;
                }
                if (firstEmptySlot == -1) {
                    firstEmptySlot = pushStart;
                }
                for (int push = firstEmptySlot; push <= pushEnd; push++) {
                    if (pushInv.get(push) != null) {
                        continue;
                    }
                    int delta = pullInv.transfer(pull, pushInv, push, toMove);
                    toMove -= delta;
                    if (delta > 0) {
                        had_change = true;
                    }
                    if (toMove <= 0) {
                        break out;
                    }
                }
                if (had_change) {
                    break;
                }
            }
        } else { //NOTE: An optimization is available if limit == 1
            boolean[] visitedSlots = new boolean[pullInv.size()];
            out: for (int pull = pullStart; pull <= pullEnd; pull++) {
                if (countItem(pullInv, pull, transferLimit, visitedSlots) < transferLimit) {
                    continue;
                }
                ItemStack is = pullInv.get(pull);
                if (is == null) continue;
                if (is.getMaxStackSize() < transferLimit) {
                    continue; // bottles
                }
                int freeForIs = pushInv.getFreeSpaceFor(is, transferLimit);
                if (freeForIs < transferLimit) {
                    continue;
                }
                //We've found an item to move. We shall move this item. This item will fit.
                //If it doesn't fit, then the inventory is weird and should stop being weird.
                had_change = true;
                int limit = transferLimit;
                for (int i = pull; i <= pullEnd; i++) {
                    if (!ItemUtil.couldMerge(is, pullInv.get(i))) {
                        continue;
                    }
                    while (limit > 0) {
                        int origLimit = limit;
                        //old stack pass
                        for (int push = pushStart; push <= pushEnd; push++) {
                            if (pushInv.get(push) == null) continue;
                            int delta = pullInv.transfer(i, pushInv, push, limit);
                            limit -= delta;
                            if (limit <= 0) break out;
                        }
                        if (limit <= 0) break out;
                        //new stack pass
                        for (int push = pushStart; push <= pushEnd; push++) {
                            if (pushInv.get(push) != null) continue;
                            int delta = pullInv.transfer(i, pushInv, push, limit);
                            limit -= delta;
                            if (limit <= 0) break out;
                        }
                        if (limit == origLimit) break;
                    }
                }
                break;
            }
        }
        if (had_change) {
            pullInv.setCallOnInventoryChanged(true);
            pushInv.setCallOnInventoryChanged(true);
            pullInv.onInvChanged();
            pushInv.onInvChanged();
        }
        cooldown = (byte) (mode == ShifterMode.MODE_STREAM ? 8 : 1);
    }

    private FzInv openForeignInv(ISocketHolder socket, Coord coord, ForgeDirection back) {
        coord.adjust(facing);
        FzInv foreignInv = InvUtil.openInventory(coord.getTE(IInventory.class), back);
        coord.adjust(back);
        if (foreignInv != null) return foreignInv;
        final ForgeDirection top = facing;

        for (IInventory entity : worldObj.getEntitiesWithinAABB(IInventory.class, getEntityBox(socket, coord, top, 0))) {
            foreignInv = InvUtil.openInventory((Entity) entity, false);
            if (foreignInv != null) {
                break;
            }
        }
        if (foreignInv != null) return foreignInv;
        if (!HammerEnabled.ENABLED) return null;
        if (worldObj != DeltaChunk.getServerShadowWorld()) return null;
        Coord target = coord.add(facing);
        for (IDeltaChunk idc : DeltaChunk.getSlicesContainingPoint(target)) {
            final ForgeDirection realBack = idc.shadow2real(back);

            Vec3 v = SpaceUtil.newVec();
            target.setAsVector(v);
            v.xCoord += 0.5;
            v.yCoord += 0.5;
            v.zCoord += 0.5;

            v = idc.shadow2real(v);

            Coord real = new Coord(idc.worldObj, (int) Math.floor(v.xCoord), (int) Math.floor(v.yCoord), (int) Math.floor(v.zCoord));

            //Coord real = idc.shadow2realCoord(target);
            foreignInv = InvUtil.openInventory(real.getTE(IInventory.class), realBack);
            //AabbDebugger.addBox(real);
            if (foreignInv != null) return foreignInv;
        }
        return null;
    }

    int countItem(FzInv inv, int start, int minimum, boolean[] visitedSlots) {
        if (visitedSlots[start]) {
            return 0;
        }
        visitedSlots[start] = true;
        ItemStack seed = inv.get(start);
        if (seed == null || seed.stackSize == 0) {
            return 0;
        }
        if (!inv.canExtract(start, seed)) {
            return 0;
        }
        int count = seed.stackSize;
        if (count >= minimum) {
            return count;
        }
        start += 1;
        for (int i = start; i < inv.size(); i++) {
            if (visitedSlots[i]) continue;
            ItemStack is = inv.get(i);
            if (is == null) {
                visitedSlots[i] = true;
                continue;
            }
            if (ItemUtil.couldMerge(seed, is)) {
                visitedSlots[i] = true;
                if (!inv.canExtract(i, is)) {
                    continue;
                }
                count += is.stackSize;
                if (count >= minimum) {
                    return count;
                }
            }
        }
        return count;
    }
    
    public void probe(ServoMotor motor) {
        Coord at = motor.getCurrentPos();
        ForgeDirection fd = motor.getOrientation().top;
        final ForgeDirection fdOp = fd.getOpposite();
        at.adjust(fd);
        FzInv target = InvUtil.openInventory(at.getTE(IInventory.class), fdOp);
        at.adjust(fdOp);
        if (target == null) {
            motor.getArgStack().push(-1);
            //Instead of:
            //motor.putError("Not pointing at an inventory!");
            return;
        }
        FzInv backInv = InvUtil.openInventory(motor, false);
        
        int targetStart, targetEnd;
        if (foreignSlot == -1) {
            targetStart = 0;
            targetEnd = target.size();
        } else {
            if (foreignSlot >= target.size()) {
                motor.getArgStack().push(-1); // Sure?
                return;
            }
            targetStart = foreignSlot;
            targetEnd = foreignSlot + 1;
        }
        
        int count = 0;
        for (int backIndex = 0; backIndex < backInv.size(); backIndex++) {
            ItemStack is = backInv.get(backIndex);
            for (int i = targetStart; i < targetEnd; i++) {
                ItemStack it = target.get(i);
                if (it != null && ItemUtil.couldMerge(is, it)) {
                    if (target.canInsert(i, it) || target.canExtract(i, it)) {
                        count += it.stackSize;
                    }
                }
            }
        }
        
        motor.getArgStack().push(count);
    }
    
    @Override
    protected boolean isBlockPowered() {
        if (worldObj.isRemote) return false;
        return worldObj.getStrongestIndirectPower(xCoord, yCoord, zCoord) > 0;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(ServoMotor motor, Tessellator tess) {
        BlockRenderHelper block = BlockRenderHelper.instance;
        block.useTextures(BlockIcons.socket$shifter_front, null,
                BlockIcons.socket$shifter_side, BlockIcons.socket$shifter_side,
                BlockIcons.socket$shifter_side, BlockIcons.socket$shifter_side,
                BlockIcons.socket$shifter_side, BlockIcons.socket$shifter_side);
        final float[] minYs = new float[] { 8F/16F, 3F/16F, -2F/16F };
        final float[] ds = new float[] { 4F/16F, 5F/16F, 6F/16F };
        int end = ds.length;
        if (motor != null) end--;
        for (int i = 0; i < end; i++) {
            float d = ds[i];
            float minY = minYs[i];
            block.setBlockBounds(d, minY, d, 1-d, 12F/16F, 1-d);
            block.beginWithMirroredUVs();
            block.rotateCenter(Quaternion.fromOrientation(FzOrientation.fromDirection(facing.getOpposite())));
            block.renderRotated(tess, xCoord, yCoord, zCoord);
        }
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void renderItemOnServo(RenderServoMotor render, ServoMotor motor, ItemStack is, float partial) {
        //super.renderItemOnServo(render, motor, is, partial);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0.7F, 0);
        GL11.glRotatef(90, 1, 0, 0);
        GL11.glRotatef(-90, 0, 0, 1);
        //GL11.glTranslatef(0, -2F/16F, 0);
        float s = 15F/16F;
        GL11.glScalef(s, s, s);
        render.renderItem(is);
        GL11.glPopMatrix();
    }
    
    @Override
    public boolean activate(EntityPlayer player, ForgeDirection side) {
        if (super.activate(player, side)) return true;
        if (worldObj.isRemote) return true;
        if (getCoord().add(facing.getOpposite()).getTE(IInventory.class) == null) {
            new Notice(getCoord(), "factorization.socket.noBackingInventory").sendTo(player);
        }
        return false;
    }
}
