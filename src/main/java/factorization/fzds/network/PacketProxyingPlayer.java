package factorization.fzds.network;

import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.network.handshake.NetworkDispatcher;
import cpw.mods.fml.relauncher.Side;
import factorization.api.Coord;
import factorization.api.DeltaCoord;
import factorization.fzds.DeltaChunk;
import factorization.fzds.DimensionSliceEntity;
import factorization.fzds.Hammer;
import factorization.fzds.interfaces.IDeltaChunk;
import factorization.fzds.interfaces.IFzdsEntryControl;
import factorization.fzds.interfaces.IFzdsShenanigans;
import factorization.shared.Core;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;

import java.lang.ref.WeakReference;
import java.util.*;

public class PacketProxyingPlayer extends EntityPlayerMP implements
        IFzdsEntryControl, IFzdsShenanigans {
    WeakReference<DimensionSliceEntity> dimensionSlice = new WeakReference<>(null);
    static boolean useShortViewRadius = true; // true doesn't actually change the view radius

    private Set<EntityPlayerMP> listeningPlayers = new HashSet<>();
    
    
    
    EmbeddedChannel proxiedChannel = new EmbeddedChannel(new WrappedMulticastHandler());
    NetworkManager networkManager = new CustomChannelNetworkManager(proxiedChannel, false);
    
    class WrappedMulticastHandler extends ChannelOutboundHandlerAdapter implements IFzdsShenanigans {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            PacketProxyingPlayer.this.addNettyMessage(msg);
            //promise.setFailure(new UnsupportedOperationException("Sorry!")); // Nooooope, causes spam.
        }
    }

    void preinitWrapping() {
        playerNetServerHandler = new NetHandlerPlayServer(mcServer, networkManager, this);
        playerNetServerHandler.netManager.channel().attr(NetworkDispatcher.FML_DISPATCHER).set(new NetworkDispatcher(this.networkManager));
        //Compare cpw.mods.fml.common.network.FMLOutboundHandler.OutboundTarget.PLAYER.{...}.selectNetworks(Object, ChannelHandlerContext, FMLProxyPacket)
        playerNetServerHandler.netManager.setConnectionState(EnumConnectionState.PLAY);
        /* (misc notes here)
         * We don't need to touch NetworkDispatcher; we need a NetworkManager.
         *
         * NetworkManager.scheduleOutboundPacket is too early I think?
         * What we really want is its channel.
         */
    }

    void initWrapping() {
        registerChunkLoading();
        updateListenerList();
    }


    ForgeChunkManager.Ticket ticket = null;
    void registerChunkLoading() {
        ticket = PPPChunkLoader.instance.register(getChunks());
    }

    void releaseChunkLoading() {
        if (ticket != null) {
            PPPChunkLoader.instance.release(ticket);
            ticket = null;
        }
    }

    private Set<Chunk> getChunks() {
        final HashSet<Chunk> ret = new HashSet<>();
        DimensionSliceEntity dse = dimensionSlice.get();
        if (dse == null) return ret;
        Coord min = dse.getCorner();
        Coord max = dse.getFarCorner();
        Coord.iterateChunks(min, max, here -> ret.add(here.getChunk()));
        return ret;
    }

    private static final UUID proxyUuid = UUID.fromString("69f64f92-665f-457e-ad33-f6082d0b8a75");

    public PacketProxyingPlayer(final DimensionSliceEntity dimensionSlice, World shadowWorld) {
        super(((WorldServer) shadowWorld).func_73046_m(/*getServer*/), (WorldServer) shadowWorld, new GameProfile(proxyUuid, "[FzdsPacket]"), new ItemInWorldManager(shadowWorld));
        invulnerable = true;
        isImmuneToFire = true;
        this.dimensionSlice = new WeakReference<>(dimensionSlice);
        Coord c = dimensionSlice.getCenter();
        c.y = -8; // lurk in the void; we should catch most mod's packets.
        DeltaCoord size = dimensionSlice.getFarCorner().difference(dimensionSlice.getCorner());
        size.y = 0;
        int width = Math.abs(size.x);
        int depth = Math.abs(size.z);
        double blockRadius = (double) Math.max(width, depth) / 2;
        int chunkRadius = (int) ((blockRadius / 16) + 2);
        chunkRadius = Math.max(3, chunkRadius);
        c.setAsEntityLocation(this);
        preinitWrapping();
        ServerConfigurationManager scm = mcServer.getConfigurationManager();
        if (useShortViewRadius) {
            int orig = savePlayerViewRadius();
            restorePlayerViewRadius(chunkRadius);
            try {
                scm.func_72375_a(this, null);
            } finally {
                restorePlayerViewRadius(orig);
                // altho the server might just crash anyways. Then again, there might be a handler higher up.
            }
        } else {
            scm.func_72375_a(this, null);
        }
        initWrapping();
    }
    
    int savePlayerViewRadius() {
        return getServerForPlayer().getPlayerManager().playerViewRadius;
    }

    void restorePlayerViewRadius(int orig) {
        if (orig == -1) {
            return;
        }
        getServerForPlayer().getPlayerManager().playerViewRadius = orig;
    }

    @Override
    public void onUpdate() {
        DimensionSliceEntity dse = dimensionSlice.get();
        if (dse == null || dse.isDead) {
            endProxy();
            return;
        }
        super.onUpdate(); // we probably want to keep this one, just for the EntityMP stuff
        if (worldObj.isRemote) return; // Won't happen.
        boolean should_update = ticksExisted % 20 == 1;
        if (!should_update) return;
        updateListenerList();
    }

    void updateListenerList() {
        List<EntityPlayer> playerList = getTargetablePlayers();
        for (EntityPlayer o : playerList) {
            if (!(o instanceof EntityPlayerMP player)) {
                continue;
            }
            if (isPlayerInUpdateRange(player)) {
                boolean new_player = listeningPlayers.add(player);
                if (new_player && shouldShareChunks()) {
                    // welcome to the club. This may net-lag a bit. (Well, it depends on the chunk's contents. Air compresses well tho.)
                    sendChunkMapDataToPlayer(player);
                }
            } else {
                listeningPlayers.remove(player);
            }
        }
    }

    private List<EntityPlayer> getTargetablePlayers() {
        DimensionSliceEntity dse = dimensionSlice.get();
        if (dse == null) return Collections.emptyList();
        return dse.worldObj.playerEntities;
    }

    boolean isPlayerInUpdateRange(EntityPlayerMP player) {
        DimensionSliceEntity dse = dimensionSlice.get();
        if (dse == null) return false;
        return !player.isDead && dse.getDistanceSqToEntity(player) <= Hammer.DSE_ChunkUpdateRangeSquared;
    }

    boolean shouldShareChunks() {
        return true;
    }

    void sendChunkMapDataToPlayer(EntityPlayerMP target) {
        DimensionSliceEntity dse = dimensionSlice.get();
        if (dse == null) return;
        // Inspired by EntityPlayerMP.onUpdate. Shame we can't just add chunks directly to target's chunkwatcher... but there'd be no wrapper for the packets.
        ArrayList<Chunk> chunks = new ArrayList<>();
        ArrayList<TileEntity> tileEntities = new ArrayList<>();
        World world = DeltaChunk.getServerShadowWorld();

        Coord low = dse.getCorner();
        Coord far = dse.getFarCorner();
        for (int x = low.x - 16; x <= far.x + 16; x += 16) {
            for (int z = low.z - 16; z <= far.z + 16; z += 16) {
                if (!world.blockExists(x + 1, 0, z + 1)) {
                    continue;
                }
                Chunk chunk = world.getChunkFromBlockCoords(x, z);
                chunks.add(chunk);
                tileEntities.addAll(chunk.chunkTileEntityMap.values());
            }
        }

        // NOTE: This has the potential to go badly if there's a large amount of data in the chunks.
        if (!chunks.isEmpty()) {
            Packet toSend = new S26PacketMapChunkBulk(chunks);
            addNettyMessageForPlayer(target, new WrappedPacketFromServer(toSend));
        }
        if (!tileEntities.isEmpty()) {
            for (TileEntity te : tileEntities) {
                Packet description = te.getDescriptionPacket();
                if (description == null) {
                    continue;
                }
                addNettyMessageForPlayer(target, new WrappedPacketFromServer(description));
            }
        }
    }

    boolean canDie = false;

    public void endProxy() {
        if (canDie && isDead) {
            // !!! Recursion death!?
            return;
        }
        canDie = true;
        setDead();
        // From playerNetServerHandler.mcServer.getConfigurationManager().playerLoggedOut(this);
        WorldServer world = getServerForPlayer();
        //world.removeEntity(this); // setEntityDead
        world.playerEntities.remove(this);
        world.getPlayerManager().removePlayer(this); // No comod?
        world.func_73046_m(/*getServer*/).getConfigurationManager().playerEntityList.remove(playerNetServerHandler.playerEntity);
        dimensionSlice.clear();
    }

    boolean shouldForceChunkLoad() { //TODO: Chunk loading!
        return !listeningPlayers.isEmpty();
    }

    private static final NettyPacketConverter wrapped_packet_channel = new NettyPacketConverter(Side.SERVER);
    
    public static Packet wrapMessage(Object msg) {
        if (msg instanceof Packet) {
            return new WrappedPacketFromServer((Packet) msg);
        }
        Packet pkt = wrapped_packet_channel.convert(msg);
        return new WrappedPacketFromServer(pkt);
    }
    
    public void addNettyMessage(Object msg) {
        // Return a future?
        if (listeningPlayers.isEmpty()) {
            return;
        }
        DimensionSliceEntity dse = dimensionSlice.get();
        if (dse == null || dse.isDead) {
            endProxy();
            return;
        }
        Object wrappedMsg = wrapMessage(msg);
        Iterator<EntityPlayerMP> it = listeningPlayers.iterator();
        while (it.hasNext()) {
            EntityPlayerMP player = it.next();
            if (player.isDead || player.worldObj != dse.worldObj) {
                it.remove();
            } else {
                addNettyMessageForPlayer(player, wrappedMsg);
            }
        }
    }
    
    void addNettyMessageForPlayer(EntityPlayerMP player, Object packet) {
        // See NetworkManager.dispatchPacket
        if (player instanceof PacketProxyingPlayer) {
            throw new IllegalStateException("Sending a packet to myself!");
        }
        player.playerNetServerHandler.sendPacket((Packet) packet);
    }

    /**
     * use endProxy()
     */
    @Override
    @Deprecated
    public void setDead() {
        if (worldObj.isRemote) {
            super.setDead();
            return;
        }
        if (!canDie) {
            Core.logWarning("Unexpected PacketProxingPlayer death at " + new Coord(this));
            Thread.dumpStack();
            canDie = true;
        }
        releaseChunkLoading();
        super.setDead();
    }

    // IFzdsEntryControl implementation
    // PPP must stay in the shadow (It stays out of range anyways.)
    @Override
    public boolean canEnter(IDeltaChunk dse) {
        return false;
    }

    @Override
    public boolean canExit(IDeltaChunk dse) {
        return false;
    }

    @Override
    public void onEnter(IDeltaChunk dse) {
    }

    @Override
    public void onExit(IDeltaChunk dse) {
    }
}
