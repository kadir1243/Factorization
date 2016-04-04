package factorization.sockets;

import factorization.api.Coord;
import factorization.api.FzOrientation;
import factorization.api.IMeterInfo;
import factorization.api.Quaternion;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.api.datahelpers.Share;
import factorization.api.energy.EnergyCategory;
import factorization.api.energy.IWorker;
import factorization.api.energy.IWorkerContext;
import factorization.api.energy.WorkUnit;
import factorization.common.FactoryType;
import factorization.common.FzConfig;
import factorization.net.StandardMessageType;
import factorization.notify.Notice;
import factorization.oreprocessing.TileEntityGrinder;
import factorization.oreprocessing.TileEntityGrinder.GrinderRecipe;
import factorization.servo.RenderServoMotor;
import factorization.servo.ServoMotor;
import factorization.shared.*;
import factorization.util.InvUtil;
import factorization.util.ItemUtil;
import factorization.util.NumUtil;
import factorization.util.PlayerUtil;
import factorization.weird.barrel.TileEntityDayBarrel;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SocketLacerator extends TileEntitySocketBase implements IMeterInfo, ICaptureDrops, IWorker<IWorkerContext> {
    short spinning_ticks = 0; // Maximum is SPIN_TICKS_PER_UNIT * 2 - 1
    byte power_buffer = 0;
    static final byte POWER_BUFFER_MAX = 2;
    static final short SPIN_TICKS_PER_UNIT = 20 * 30;

    short speed = 0;
    short progress;
    short last_shared_speed = 0;
    boolean grab_items = false, grind_items = false;
    long targetHash = -1;
    boolean ticked = false;
    boolean isPowered = false;
    
    final static byte grind_time = 25;
    final static short max_speed = 200;
    final static short min_speed = max_speed/10;
    ArrayList<ItemStack> buffer = new ArrayList<ItemStack>();
    
    private float rotation = 0, prev_rotation = 0;
    
    MovingObjectPosition present_breaking_target = null, previous_breaking_target = null;
    
    @Override
    public FactoryType getFactoryType() {
        return FactoryType.SOCKET_LACERATOR;
    }
    
    @Override
    public ItemStack getCreatingItem() {
        return new ItemStack(Core.registry.diamond_cutting_head);
    }
    
    @Override
    public FactoryType getParentFactoryType() {
        return FactoryType.SOCKET_BARE_MOTOR;
    }

    @Override
    public String getInfo() {
        int s = speed*100/max_speed;
        String msg = s + "% speed";
        if (!buffer.isEmpty()) {
            msg += "\nBuffered output";
        }
        return msg;
    }

    @Override
    public boolean activate(EntityPlayer player, EnumFacing side) {
        if (worldObj.isRemote) {
            return false;
        }
        if (!buffer.isEmpty()) {
            new Notice(this, "%s items buffered", "" + buffer.size()).sendTo(player);
            return false;
        }
        if (getBackingInventory(this) == null) {
            new Notice(getCoord(), "factorization.socket.noOutputInventory").sendTo(player);
        }
        return false;
    }
    
    void slowDown() {
        speed = (short) Math.max(0, speed - 1);
    }
    
    void destroyPartially(MovingObjectPosition mop, int amount) {
        if (mop == null) return;
        worldObj.sendBlockBreakProgress(hashCode(), mop.getBlockPos(), amount);
    }
    
    @Override
    public void genericUpdate(ISocketHolder socket, Coord coord, boolean powered) {
        if (worldObj.isRemote) {
            prev_rotation = rotation;
            rotation += speed / 4F;
            ticked = true;
            return;
        }
        isPowered = powered;
        genericUpdate_implementation(socket, coord, powered);
        if (NumUtil.significantChange(last_shared_speed, speed)) {
            socket.sendMessage(StandardMessageType.SetSpeed, speed);
            last_shared_speed = speed;
        }
        
        if (previous_breaking_target != null && present_breaking_target != null) {
            if (!previous_breaking_target.getBlockPos().equals(present_breaking_target.getBlockPos())) {
                destroyPartially(previous_breaking_target, 99);
            }
        } else if (present_breaking_target == null) {
            destroyPartially(previous_breaking_target, 99);
        }
        
        previous_breaking_target = present_breaking_target;
        if (progress == 0) {
            destroyPartially(present_breaking_target, 99);
            present_breaking_target = null;
        }
    }
    
    private void genericUpdate_implementation(ISocketHolder socket, Coord coord, boolean powered) {
        if (getBackingInventory(socket) == null) {
            slowDown();
            return;
        }
        if (socket.dumpBuffer(buffer)) {
            for (Iterator<ItemStack> iterator = buffer.iterator(); iterator.hasNext();) {
                ItemStack is = iterator.next();
                if (ItemUtil.normalize(is) == null) {
                    iterator.remove();
                }
            }
            slowDown();
            return;
        }
        FzOrientation orientation = FzOrientation.fromDirection(facing).getSwapped();
        if (powered) {
            slowDown();
            progress = 0;
        } else if (workTick()) {
            RayTracer tracer = new RayTracer(this, socket, coord, orientation, powered).onlyFrontBlock().checkEnts();
            if (!tracer.trace()) {
                slowDown();
                progress = 0;
            }
        }
        if (grab_items) {
            grab_items = false;
            
            for (EntityItem ei : (Iterable<EntityItem>)worldObj.getEntitiesWithinAABB(EntityItem.class, getEntityBox(socket, coord, orientation.top, 1.75))) {
                if (ei.isDead) continue;
                if (ei.ticksExisted > 1) continue;
                ItemStack is = ei.getEntityItem();
                processCollectedItem(ItemUtil.normalize(is));
                ei.setDead();
            }
        }
    }

    boolean workTick() {
        if (!isPowered && spinning_ticks > 0) {
            spinning_ticks--;
            speed = (short) Math.min(max_speed, speed + 32);
            if (speed == max_speed) {
                return false;
            }
        } else {
            slowDown();
        }
        return workCheck();
    }

    boolean workCheck() {
        return speed > min_speed && worldObj.rand.nextInt(max_speed) < speed/4;
    }

    @Override
    public boolean handleMessageFromServer(Enum messageType, ByteBuf input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        if (messageType == StandardMessageType.SetSpeed) {
            speed = input.readShort();
            return true;
        }
        return false;
    }
    
    void processCollectedItem(ItemStack is) {
        if (!grind_items) {
            buffer.add(is);
            return;
        }
        boolean appliedRecipe = false;
        ArrayList<GrinderRecipe> recipes = TileEntityGrinder.recipes;
        for (int i = 0; i < recipes.size(); i++) {
            GrinderRecipe gr = recipes.get(i);
            if (ItemUtil.oreDictionarySimilar(gr.getOreDictionaryInput(), is)) {
                ItemStack output = gr.output.copy();
                output.stackSize = 0;
                int min = (int) gr.probability;
                output.stackSize += min;
                output.stackSize += worldObj.rand.nextFloat() < (gr.probability - min) ? 1 : 0;
                is = output;
                appliedRecipe = true;
                break;
            }
        }
        if (appliedRecipe) {
            grind_items = false; //We only grind 1 drop, to prevent hax
        }
        buffer.add(is);
    }

    public static final DamageSource laceration = new DamageSource("laceration") {
        @Override
        public IChatComponent getDeathMessage(EntityLivingBase victim) {
            String ret = "death.attack.laceration.";
            if (victim.worldObj != null) {
                long now = victim.worldObj.getTotalWorldTime();
                ret += (now % 6) + 1;
            } else {
                ret += "1";
            }
            
            EntityLivingBase attacker = victim.func_94060_bK(); // combat tracker's attacking entity
            String fightingMessage = ret + ".player";
            if (attacker != null && StatCollector.canTranslate(fightingMessage)) {
                return new ChatComponentTranslation(fightingMessage, victim.getDisplayName(), attacker.getDisplayName());
            } else {
                return new ChatComponentTranslation(ret, victim.getDisplayName());
            }
        }
    };
    
    @Override
    public boolean handleRay(ISocketHolder socket, MovingObjectPosition mop, World mopWorld, boolean mopIsThis, boolean powered) {
        DropCaptureHandler.startCapture(this, new Coord(mopWorld, mop), 3);
        try {
            return _handleRay(socket, mop, mopWorld, mopIsThis, powered);
        } finally {
            DropCaptureHandler.endCapture();
        }
    }
    
    @Override
    public boolean captureDrops(List<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack is = stacks.get(i);
            processCollectedItem(is.copy());
            is.stackSize = 0;
        }
        return true;
    }
    
    private boolean _handleRay(ISocketHolder socket, MovingObjectPosition mop, World mopWorld, boolean mopIsThis, boolean powered) {
        if (mop == null) return false;
        if (mopIsThis) return false;
        
        if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            if (!(mop.entityHit instanceof EntityLivingBase)) {
                return false;
            }
            EntityLivingBase elb = (EntityLivingBase) mop.entityHit;
            if (elb.isDead || elb.getHealth() <= 0) return false;
            float damage = 4F*speed/max_speed;
            if (elb.getHealth() <= damage && worldObj.rand.nextInt(20) == 1) {
                elb.recentlyHit = 100;
            }
            if (elb.attackEntityFrom(laceration, damage)) {
                if (elb.getHealth() <= 0) {
                    grab_items = true;
                }
                targetHash = -1;
            }
            progress = 0;
            return true;
        } else if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            IBlockState bs = mopWorld.getBlockState(mop.getBlockPos());
            Block block = bs.getBlock();
            if (block == null || block.isAir(mopWorld, mop.getBlockPos())) {
                return false;
            }
            if (!block.canCollideCheck(bs, false)) {
                return false;
            }
            if (!FzConfig.lacerator_block_graylist.passes(block)) {
                return false;
            }
            TileEntity te = null;
            TileEntityDayBarrel barrel = null;
            if (block instanceof BlockFactorization) {
                te = mopWorld.getTileEntity(mop.getBlockPos());
                if (te instanceof TileEntityDayBarrel) {
                    barrel = (TileEntityDayBarrel) te;
                    if (barrel.item == null) {
                        return false;
                    }
                }
            }

            //Below: A brief demonstration of why Coord exists
            long foundHash = mop.getBlockPos().hashCode();
            float hardness = block.getBlockHardness(mopWorld, mop.getBlockPos());
            if (hardness < 0) {
                speed -= max_speed/5; // a bad case of the jitters!
                return true;
            }
            foundHash = (foundHash << 4) + block.hashCode();
            if (barrel != null) {
                foundHash += barrel.item.hashCode()*5 + barrel.item.getItemDamage()*10;
            }
            if (foundHash != targetHash) {
                targetHash = foundHash;
                progress = 0;
            } else {
                progress++;
                if (socket == this && worldObj.rand.nextInt(4) == 0) {
                    // Torsion!?
                    TileEntity partner = mopWorld.getTileEntity(mop.getBlockPos().offset(facing));
                    if (partner instanceof SocketLacerator) {
                        SocketLacerator pardner = (SocketLacerator) partner;
                        if (pardner.workCheck()) {
                            progress += 16;
                        }
                    }
                }
            }
            boolean doBreak = progress >= grind_time*hardness || Core.cheat;
            if (barrel == null && !doBreak) {
                float perc = progress/((float)grind_time*hardness);
                int breakage = (int) (perc*10);
                if (mopWorld == worldObj) {
                    destroyPartially(mop, breakage);
                }
                present_breaking_target = mop;
            } else if (doBreak) {
                if (mopWorld == worldObj) {
                    destroyPartially(mop, 99);
                }
            }
            if (doBreak) {
                grab_items = true;
                grind_items = true;
                if (barrel == null) {
                    mopWorld.playAuxSFX(2001, mop.getBlockPos(), Block.getIdFromBlock(block));
                    
                    EntityPlayer player = getFakePlayer();
                    ItemStack pick = new ItemStack(Items.diamond_pickaxe);
                    pick.addEnchantment(Enchantment.silkTouch, 1);
                    player.inventory.mainInventory[0] = pick;
                    {
                        boolean canHarvest = block.canHarvestBlock(mopWorld, mop.getBlockPos(), player);

                        boolean didRemove = removeBlock(player, block, bs, mopWorld, mop.getBlockPos());
                        if (didRemove) {
                            block.harvestBlock(mopWorld, player, mop.getBlockPos(), bs, te);
                        }
                    }
                    block.onBlockHarvested(mopWorld, mop.getBlockPos(), bs, player);
                    if (block.removedByPlayer(mopWorld, mop.getBlockPos(), player, true)) {
                        block.onBlockDestroyedByPlayer(mopWorld, mop.getBlockPos(), bs);
                        block.harvestBlock(mopWorld, player, mop.getBlockPos(), bs, te);
                    }
                    player.inventory.mainInventory[0] = null;
                    PlayerUtil.recycleFakePlayer(player);
                } else if (barrel.getItemCount() > 0) {
                    ItemStack is = barrel.item.copy();
                    is.stackSize = 1;
                    barrel.changeItemCount(-1);
                    grind_items = true;
                    processCollectedItem(is);
                }
                progress = 0;
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Accepted accept(IWorkerContext context, WorkUnit unit, boolean simulate) {
        if (unit.category != EnergyCategory.ELECTRIC && unit.category != EnergyCategory.ROTATIONAL) return Accepted.NEVER;
        if (spinning_ticks < SPIN_TICKS_PER_UNIT) {
            if (simulate) return Accepted.NOW;
            spinning_ticks += SPIN_TICKS_PER_UNIT;
            return Accepted.NOW;
        }
        if (power_buffer < POWER_BUFFER_MAX) {
            if (simulate) return Accepted.NOW;
            power_buffer++;
        }
        return Accepted.LATER;
    }

    private boolean removeBlock(EntityPlayer thisPlayerMP, Block block, IBlockState bs, World mopWorld, BlockPos pos) {
        if (block == null) return false;
        block.onBlockHarvested(mopWorld, pos, bs, thisPlayerMP);
        if (block.removedByPlayer(mopWorld, pos, thisPlayerMP, false)) {
            block.onBlockDestroyedByPlayer(mopWorld, pos, bs);
            return true;
        }
        return false;
    }
    
    @Override
    public IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        speed = data.as(Share.VISIBLE, "spd").putShort(speed);
        progress = data.as(Share.PRIVATE, "prg").putShort(progress);
        buffer = data.as(Share.PRIVATE, "buf").putItemList(buffer);
        grab_items = data.as(Share.PRIVATE, "grb").putBoolean(grab_items);
        targetHash = data.as(Share.PRIVATE, "hsh").putLong(targetHash);
        grind_items = data.as(Share.PRIVATE, "grn").putBoolean(grind_items);
        // Why are those names short? To save NBT space? An unworthy goal. But not worth fixing.
        spinning_ticks = data.as(Share.PRIVATE, "spinningTicks").putShort(spinning_ticks);
        power_buffer = data.as(Share.PRIVATE, "powerBuffer").putByte(power_buffer);
        return this;
    }
    
    @Override
    protected void onRemove() {
        super.onRemove();
        Coord here = getCoord();
        for (ItemStack is : buffer) {
            InvUtil.spawnItemStack(here, is);
        }
    }
    
    @Override
    public void click(EntityPlayer entityplayer) {
        InvUtil.emptyBuffer(entityplayer, buffer, this);
    }
    
    
    //Render code
    @Override
    @SideOnly(Side.CLIENT)
    public void renderTesr(ServoMotor motor, float partial) {
        float d = 0.5F;
        GL11.glTranslatef(d, d, d);
        Quaternion.fromOrientation(FzOrientation.fromDirection(facing.getOpposite())).glRotate();
        float turn = NumUtil.interp(prev_rotation, rotation, partial) / 5.0F;
        GL11.glRotatef(turn, 0, 1, 0);
        float sd = motor == null ? 1F/16F : 3F/16F;
        GL11.glTranslatef(0, -4F/16F + sd + (float) Math.abs(Math.sin(turn/800))/32F, 0);
        diamondCuttingHead.draw();
        if (ticked) {
            ticked = false;
            if (speed > max_speed/3) {
                addParticles();
            }
        }
    }

    static FzModel diamondCuttingHead = new FzModel("socket/diamondCuttingHead");

    @SideOnly(Side.CLIENT)
    static EffectRenderer particleTweaker, origER;
    @SideOnly(Side.CLIENT)
    static SocketLacerator me;
    @SideOnly(Side.CLIENT)
    static double facex, facey, facez;
    
    @SideOnly(Side.CLIENT)
    void addParticles() {
        Minecraft mc = Minecraft.getMinecraft();
        if (particleTweaker == null) {
            particleTweaker = new ParticleWarper(worldObj, mc.renderEngine);
        }
        BlockPos forward = pos.offset(facing);

        EnumFacing op = facing.getOpposite();

        Block b = worldObj.getBlockState(forward).getBlock();
        if (b == null) {
            return;
        }
        origER = mc.effectRenderer;
        me = this;
        mc.effectRenderer = particleTweaker;
        try {
            for (int i = 0; i < 1; i++) {
                particleTweaker.addBlockHitEffects(pos, op);
            }
        } finally {
            me = null;
            mc.effectRenderer = origER;
        }
    }

    static class ParticleWarper extends EffectRenderer {

        public ParticleWarper(World world, TextureManager textureManager) {
            super(world, textureManager);
        }
        
        @Override
        public void addEffect(EntityFX particle) {
            if (particle == null || origER == null || me == null) {
                return;
            }
            EnumFacing fd = me.facing;
            if (fd.getDirectionVec().getY() != 0) {
                origER.addEffect(particle);
                return;
            }
            particle.posX = facex;
            particle.posY = facey;
            particle.posZ = facez;
            float theta = (float) (Math.random()*Math.PI*2);
            double dist = 4.0/16.0;
            
            
            Vec3 dir = new Vec3(0, dist, 0);
            
            dir = Quaternion.getRotationQuaternionRadians(theta, fd).applyRotation(dir);
            particle.posX += dir.xCoord;
            particle.posY += dir.yCoord;
            particle.posZ += dir.zCoord;
            theta = (float) (Math.PI/2);
            dir = Quaternion.getRotationQuaternionRadians(theta, fd).applyRotation(dir);
            float speed = 0.8F*me.speed/max_speed;
            particle.motionX = dir.xCoord*speed;
            particle.motionY = dir.yCoord*speed;
            particle.motionZ = dir.zCoord*speed;
            if (particle.motionY > speed/4) {
                particle.motionY *= 3;
            }
            particle.multipleParticleScaleBy(1 + worldObj.rand.nextFloat()*2/3);
            origER.addEffect(particle);
        }
    }
    
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void resetEffectRenderer(WorldEvent.Unload loadEvent) {
        particleTweaker = null;
    }
    
    @Override
    public void uninstall() {
        if (!worldObj.isRemote) {
            destroyPartially(present_breaking_target, 99);
            destroyPartially(previous_breaking_target, 99);
        }
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void renderItemOnServo(RenderServoMotor render, ServoMotor motor, ItemStack is, float partial) {
        //super.renderItemOnServo(render, motor, is, partial);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 6F/16F, 0);
        float turn = NumUtil.interp(prev_rotation, rotation, partial) / 5.0F;
        GL11.glRotatef(-turn, 0, 1, 0);
        float s = 12F/16F;
        GL11.glScalef(s, s, s);
        int count = 6;

        for (int i = 0; i < motor.getSizeInventory(); i++) {
            is = motor.getStackInSlot(i);
            if (is == null) continue;
            GL11.glPushMatrix();
            GL11.glRotatef(360*i/4, 0, 1, 0);
            GL11.glTranslatef(0, count--/16F, 5F/16F);
            render.renderItem(is);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
    }
    
    @Override
    public void installedOnServo(ServoMotor servoMotor) {
        super.installedOnServo(servoMotor);
        servoMotor.resizeInventory(4);
    }

    @Override
    public void onLoaded(ISocketHolder holder) {
        IWorker.construct(holder.getContext());
    }
}
