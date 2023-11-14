package factorization.fzds.network;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLEventChannel;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ServerCustomPacketEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import factorization.api.Coord;
import factorization.api.Quaternion;
import factorization.fzds.DeltaChunk;
import factorization.fzds.DimensionSliceEntity;
import factorization.fzds.interfaces.DeltaCapability;
import factorization.fzds.interfaces.IDeltaChunk;
import factorization.fzds.interfaces.Interpolation;
import factorization.shared.Core;
import factorization.util.PlayerUtil;
import factorization.util.SpaceUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.world.BlockEvent.PlaceEvent;

import java.io.IOException;


public class HammerNet {
    public static HammerNet instance;
    public static final String channelName = "FZDS|Interact"; //NORELEASE: There's another network thingie around here for DSE velocity & stuff!?
    public static FMLEventChannel channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(channelName);
    
    public HammerNet() {
        instance = this;
        channel.register(this);
        Core.loadBus(this);
    }
    
    public static class HammerNetType {
        // Next time, make it an enum.
        public static final byte rotation = 0, rotationVelocity = 1, rotationBoth = 2, rotationCenterOffset = 10, exactPositionAndMotion = 11, orderedRotation = 12,
                rightClickEntity = 3, leftClickEntity = 4, rightClickBlock = 5, leftClickBlock = 6, digStart = 7, digProgress = 8, digFinish = 9;
    }
    
    @SubscribeEvent
    public void messageFromServer(ClientCustomPacketEvent event) {
        EntityPlayer player = Core.proxy.getClientPlayer();
        try {
            handleMessageFromServer(player, event.packet.payload());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void handleMessageFromServer(EntityPlayer player, ByteBuf dis) throws IOException {
        if (player == null || player.worldObj == null) {
            return; //Blah, what...
        }
        World world = player.worldObj;
        byte type = dis.readByte();
        int dse_id = dis.readInt();
        Entity ent = world.getEntityByID(dse_id);
        DimensionSliceEntity dse = null;
        if (ent instanceof DimensionSliceEntity) {
            dse = (DimensionSliceEntity) ent;
        } else {
            if (ent != null) {
                Core.logWarning("Packet %s to non-DSE (ID=%s) %s", type, dse_id, ent);
            }
            return;
        }
        switch (type) {
        case HammerNetType.rotation:
            setRotation(dis, dse);
            break;
        case HammerNetType.rotationVelocity:
            setRotationalVelocity(dis, dse);
            break;
        case HammerNetType.rotationBoth:
            setRotation(dis, dse);
            setRotationalVelocity(dis, dse);
            break;
        case HammerNetType.rotationCenterOffset:
            setCenterOffset(dis, dse);
            break;
        case HammerNetType.exactPositionAndMotion:
            dse.setPosition(dis.readDouble(), dis.readDouble(), dis.readDouble());
            dse.motionX = dis.readDouble();
            dse.motionY = dis.readDouble();
            dse.motionZ = dis.readDouble();
            break;
        case HammerNetType.orderedRotation:
            Quaternion rotationStart = Quaternion.read(dis);
            Quaternion rotationEnd = Quaternion.read(dis);
            int orderTime = dis.readInt();
            byte interpIndex = dis.readByte();
            Interpolation interp = Interpolation.values()[interpIndex];
            if (orderTime < 0) {
                dse.cancelOrderedRotation();
            } else {
                dse.setRotation(rotationStart);
                dse.orderTargetRotation(rotationEnd, orderTime, interp);
            }
            break;
        }
        
    }
    
    void setRotation(ByteBuf dis, DimensionSliceEntity dse) throws IOException {
        Quaternion q = Quaternion.read(dis);
        if (dse != null) {
            dse.setRotation(q);
        }
    }
    
    void setRotationalVelocity(ByteBuf dis, DimensionSliceEntity dse) throws IOException {
        Quaternion q = Quaternion.read(dis);
        if (dse != null) {
            dse.setRotationalVelocity(q);
        }
    }
    
    void setCenterOffset(ByteBuf dis, DimensionSliceEntity dse) throws IOException {
        double x = dis.readDouble();
        double y = dis.readDouble();
        double z = dis.readDouble();
        Vec3 vec = Vec3.createVectorHelper(x, y, z);
        dse.setRotationalCenterOffset(vec);
    }
    
    
    @SubscribeEvent
    public void messageFromClient(ServerCustomPacketEvent event) {
        EntityPlayerMP player = ((NetHandlerPlayServer) event.handler).playerEntity;
        try {
            handleMessageFromClient(player, event.packet.payload());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    void handleMessageFromClient(EntityPlayerMP player, ByteBuf dis) throws IOException {
        byte type = dis.readByte();
        int dse_id = dis.readInt();
        Entity ent = player.worldObj.getEntityByID(dse_id);
        if (!(ent instanceof IDeltaChunk)) {
            throw new IOException("Did not select a DimensionSliceEntity (id = " + dse_id + ", messageType = " + type + ")");
        }
        DimensionSliceEntity idc = (DimensionSliceEntity) ent;
        
        if (!idc.can(DeltaCapability.INTERACT)) {
            if (type == HammerNetType.digFinish || type == HammerNetType.digProgress || type == HammerNetType.digStart || type == HammerNetType.rightClickBlock || type == HammerNetType.leftClickBlock) {
                Core.logWarning("%s tried to interact with IDC that doesn't permit that %s", player, idc);
                return;
            }
        }

        if (type == HammerNetType.digFinish || type == HammerNetType.digProgress || type == HammerNetType.digStart) {
            if (!idc.can(DeltaCapability.BLOCK_MINE)) {
                Core.logWarning("%s tried to mine IDC that doesn't permit that %s", player, idc);
                return;
            }
            int x = dis.readInt();
            int y = dis.readInt();
            int z = dis.readInt();
            byte sideHit = dis.readByte();
            if (type == HammerNetType.digFinish) {
                breakBlock(idc, player, dis, x, y, z, sideHit);
            } else if (type == HammerNetType.digStart) {
                punchBlock(idc, player, dis, x, y, z, sideHit);
            }
            idc.blocksChanged(x, y, z);
        } else if (type == HammerNetType.rightClickBlock) {
            /*if (!idc.can(DeltaCapability.BLOCK_PLACE)) {
                Core.logWarning("%s tried to use an item on IDC that doesn't permit that %s", player, idc);
                return;
            }*/
            int x = dis.readInt();
            int y = dis.readInt();
            int z = dis.readInt();
            byte sideHit = dis.readByte();
            float vecX = dis.readFloat();
            float vecY = dis.readFloat();
            float vecZ = dis.readFloat();
            try {
                dont_check_range = false;
                active_idc = idc;
                clickBlock(idc, player, x, y, z, sideHit, vecX, vecY, vecZ);
            } finally {
                dont_check_range = true;
                active_idc = null;
            }
            idc.blocksChanged(x, y, z);
        } else if (type == HammerNetType.leftClickBlock) {
            int x = dis.readInt();
            int y = dis.readInt();
            int z = dis.readInt();
            byte sideHit = dis.readByte();
            float vecX = dis.readFloat();
            float vecY = dis.readFloat();
            float vecZ = dis.readFloat();
            leftClickBlock(idc, player, dis, x, y, z, sideHit, vecX, vecY, vecZ);
        } else if (type == HammerNetType.rightClickEntity || type == HammerNetType.leftClickEntity) {
            int entId = dis.readInt();
            Entity hitEnt = idc.getCorner().w.getEntityByID(entId);
            if (hitEnt == null) {
                Core.logWarning("%s tried clicking a non-existing entity", player);
                return;
            }
            clickEntity(idc, player, hitEnt, type == HammerNetType.leftClickEntity);
        } else {
            Core.logWarning("%s tried to send an unknown packet %s to IDC %s", player, type, idc);
        }
    }
    
    private boolean dont_check_range = true;
    private IDeltaChunk active_idc = null;
    
    @SubscribeEvent
    public void handlePlace(PlaceEvent event) {
        if (dont_check_range) return;
        if (active_idc == null) return;
        if (!active_idc.can(DeltaCapability.BLOCK_PLACE)) {
            event.setCanceled(true);
            return;
        }
        cancelOutOfRangePlacements(event);
        if (!event.isCanceled()) {
            askController(event);
        }
    }

    void cancelOutOfRangePlacements(PlaceEvent event) {
        Coord min = active_idc.getCorner();
        if (event.world != min.w) return;
        Coord max = active_idc.getFarCorner();
        if (in(min.x, event.x, max.x) && in(min.y, event.y, max.y) && in(min.z, event.z, max.z)) return;
        event.setCanceled(true);
    }

    void askController(PlaceEvent event) {
        if (active_idc.getController().placeBlock(active_idc, event.player, new Coord(event.world, event.x, event.y, event.z))) {
            event.setCanceled(true);
        }
    }
    
    boolean in(int low, int i, int high) {
        return low <= i && i <= high;
    }
    
    boolean blockInReach(IDeltaChunk idc, EntityPlayerMP player, Coord at) {
        double reach_distance = player.theItemInWorldManager.getBlockReachDistance();
        Vec3 playerAt = SpaceUtil.fromEntPos(player);
        playerAt = idc.real2shadow(playerAt);
        double distance = at.createVector().distanceTo(playerAt);
        return distance <= reach_distance;
    }
    
    void breakBlock(IDeltaChunk idc, EntityPlayerMP player, ByteBuf dis, int x, int y, int z, byte sideHit) {
        Coord at = new Coord(DeltaChunk.getServerShadowWorld(), x, y, z);
        if (at.isAir()) return;
        if (!blockInReach(idc, player, at)) return;
        if (idc.getController().breakBlock(idc, player, at, sideHit)) return;
        World origWorld = player.theItemInWorldManager.theWorld;
        player.theItemInWorldManager.theWorld = DeltaChunk.getServerShadowWorld();
        try {
            // NORELEASE: Not quite right; this will send packets to the player, not through the proxy
            player.theItemInWorldManager.tryHarvestBlock(at.x, at.y, at.z);
        } finally {
            player.theItemInWorldManager.theWorld = origWorld;
        }
    }
    
    void punchBlock(IDeltaChunk idc, EntityPlayerMP player, ByteBuf dis, int x, int y, int z, byte sideHit) {
        Coord at = new Coord(DeltaChunk.getServerShadowWorld(), x, y, z);
        if (at.isAir()) return;
        if (!blockInReach(idc, player, at)) return;
        if (idc.getController().hitBlock(idc, player, at, sideHit)) return;
        Block block = at.getBlock();
        WorldServer shadow_world = (WorldServer) DeltaChunk.getServerShadowWorld();
        InteractionLiason liason = getLiason(shadow_world, player, idc);
        block.onBlockClicked(shadow_world, x, y, z, liason);
        liason.finishUsingLiason();
    }

    void leftClickBlock(IDeltaChunk idc, EntityPlayerMP player, ByteBuf dis, int x, int y, int z, byte sideHit, float vecX, float vecY, float vecZ) {
        Coord at = new Coord(DeltaChunk.getServerShadowWorld(), x, y, z);
        if (at.isAir()) return;
        if (!blockInReach(idc, player, at)) return;
        if (idc.getController().hitBlock(idc, player, at, sideHit)) return;
        // TODO: Liason?
        Block block = at.getBlock();
        block.onBlockClicked(at.w, x, y, z, player);
    }

    void clickEntity(IDeltaChunk idc, EntityPlayerMP player, Entity hitEnt, boolean leftClick) {
        InteractionLiason liason = getLiason((WorldServer) idc.getCorner().w, player, idc);
        if (leftClick) {
            liason.attackTargetEntityWithCurrentItem(hitEnt);
        } else {
            liason.interactWith(hitEnt);
        }
        liason.finishUsingLiason();
    }
    
    InteractionLiason getLiason(WorldServer shadowWorld, EntityPlayerMP real_player, IDeltaChunk idc) {
        // NORELEASE: Cache. Constructing fake players is muy expensivo
        InteractionLiason liason = new InteractionLiason(shadowWorld, new ItemInWorldManager(shadowWorld), real_player, idc);
        liason.initializeFor(idc);
        return liason;
    }

    private boolean do_click(IDeltaChunk idc, WorldServer world, EntityPlayerMP player, int x, int y, int z, byte sideHit, float vecX, float vecY, float vecZ) {
        // Copy of PlayerControllerMP.onPlayerRightClick
        ItemStack is = player.getHeldItem();
        if (is != null && is.getItem().onItemUseFirst(is, player, world, x, y, z, sideHit, vecX, vecY, vecZ)) {
            return true;
        }
        boolean ret = false;

        if (!player.isSneaking() || player.getHeldItem() == null
                || player.getHeldItem().getItem().doesSneakBypassUse(world, x, y, z, player)) {
            ret = world.getBlock(x, y, z).onBlockActivated(world, x, y, z, player, sideHit, vecX, vecY, vecZ);
        }

        if (ret) {
            return true;
        } else if (is == null || !idc.can(DeltaCapability.BLOCK_PLACE)) {
            return false;
        } else if (PlayerUtil.isPlayerCreative(player)) {
            int j1 = is.getItemDamage();
            int i1 = is.stackSize;
            boolean flag1 = is.tryPlaceItemIntoWorld(player, world, x, y, z,
                    sideHit, vecX, vecY, vecZ);
            is.setItemDamage(j1);
            is.stackSize = i1;
            return flag1;
        } else {
            if (!is.tryPlaceItemIntoWorld(player, world, x, y, z, sideHit, vecX, vecY, vecZ)) {
                return false;
            }
            if (is.stackSize <= 0) {
                MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, is));
            }
            return true;
        }
    }
    
    void clickBlock(IDeltaChunk idc, EntityPlayerMP real_player, int x, int y, int z, byte sideHit, float vecX, float vecY, float vecZ) throws IOException {
        WorldServer shadowWorld = (WorldServer) DeltaChunk.getServerShadowWorld();
        Coord at = new Coord(shadowWorld, x, y, z);
        if (at.isAir()) return;
        if (!blockInReach(idc, real_player, at)) return;
        if (idc.getController().useBlock(idc, real_player, at, sideHit)) return;
        
        InteractionLiason liason = getLiason(shadowWorld, real_player, idc);
        try {
            do_click(idc, shadowWorld, liason, x, y, z, sideHit, vecX, vecY, vecZ);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        liason.finishUsingLiason();
        
        /*
         * Create an InteractionLiason extends EntityPlayerMP to click the block.
         * If it recieves any packets to open a GUI, or possibly just any strange packets in general, then teleport the player into Hammer-space.
         * The player will be riding a mount.
         * When the player gets off the mount, teleport the player back.
         * Some GUIs might be whitelisted. Crafting tables shouldn't be too hard; chests shouldn't be any harder; furnaces might be achievable.
         */
    }
    
    public static FMLProxyPacket makePacket(byte type, Object... items) {
        ByteArrayDataOutput dos = ByteStreams.newDataOutput();
        dos.writeByte(type);
        for (Object obj : items) {
            if (obj instanceof Quaternion) {
                ((Quaternion) obj).write(dos);
            } else if (obj instanceof Integer) {
                dos.writeInt((Integer) obj);
            } else if (obj instanceof Byte) {
                dos.writeByte((Byte) obj);
            } else if (obj instanceof Float) {
                dos.writeFloat((Float) obj);
            } else if (obj instanceof Double) {
                dos.writeDouble((Double) obj);
            } else if (obj instanceof MovingObjectPosition mop) {
                dos.writeInt(mop.blockX);
                dos.writeInt(mop.blockY);
                dos.writeInt(mop.blockZ);
                dos.writeByte((byte) mop.sideHit);
            } else if (obj instanceof Vec3) {
                Vec3 vec = (Vec3) obj;
                dos.writeDouble(vec.xCoord);
                dos.writeDouble(vec.yCoord);
                dos.writeDouble(vec.zCoord);
            } else {
                throw new IllegalArgumentException("Can only do Quaternions/Integers/Bytes/Floats/Doubles/MovingObjectPosition/Vec3! Not " + obj);
            }
        }
        return new FMLProxyPacket(Unpooled.wrappedBuffer(dos.toByteArray()), channelName);
    }

    @SubscribeEvent
    public void tickLiasons(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        InteractionLiason.updateActiveLiasons();
    }
}
