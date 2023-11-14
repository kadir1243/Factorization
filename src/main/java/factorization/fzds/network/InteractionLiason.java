package factorization.fzds.network;

import cpw.mods.fml.common.network.handshake.NetworkDispatcher;
import factorization.fzds.ShadowPlayerAligner;
import factorization.fzds.interfaces.IDeltaChunk;
import factorization.fzds.interfaces.IFzdsShenanigans;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.WorldServer;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public class InteractionLiason extends EntityPlayerMP implements IFzdsShenanigans {
    static final WeakHashMap<EntityPlayerMP, InteractionLiason> activeLiasons = new WeakHashMap<>();

    //private static final GameProfile liasonGameProfile = new GameProfile(null /*UUID.fromString("69f64f91-665e-457d-ad32-f6082d0b8a71")*/ , "[FzdsInteractionLiason]");
    // Using the real player's GameProfile for things like permissions checks.
    private final InventoryPlayer original_inventory;
    private ShadowPlayerAligner aligner;
    private NetworkManager networkManager;
    private final WeakReference<EntityPlayerMP> realPlayerRef;

    private EmbeddedChannel proxiedChannel = new EmbeddedChannel(new LiasonHandler());

    public InteractionLiason(WorldServer world, ItemInWorldManager itemManager, EntityPlayerMP realPlayer, IDeltaChunk idc) {
        super(world.func_73046_m(/*getServer*/), world, realPlayer.getGameProfile(), itemManager);
        original_inventory = this.inventory;
        realPlayerRef = new WeakReference<>(realPlayer);
        initLiason();
        updateFromPlayerStatus();
    }

    private void initLiason() {
        // We're fairly similar to PacketProxyingPlayer.initWrapping()
        networkManager = new CustomChannelNetworkManager(proxiedChannel, false);
        this.playerNetServerHandler = new NetHandlerPlayServer(mcServer, networkManager, this);
        playerNetServerHandler.netManager.channel().attr(NetworkDispatcher.FML_DISPATCHER).set(new NetworkDispatcher(networkManager));
        //Compare cpw.mods.fml.common.network.FMLOutboundHandler.OutboundTarget.PLAYER.{...}.selectNetworks(Object, ChannelHandlerContext, FMLProxyPacket)
        playerNetServerHandler.netManager.setConnectionState(EnumConnectionState.PLAY);
    }

    void updateFromPlayerStatus() {
        EntityPlayerMP realPlayer = realPlayerRef.get();
        if (realPlayer == null) return;

        this.inventory = realPlayer.inventory;
        this.setSprinting(realPlayer.isSprinting());
        this.setSneaking(realPlayer.isSneaking());
        this.capabilities = realPlayer.capabilities;
    }

    void initializeFor(IDeltaChunk idc) {
        EntityPlayerMP realPlayer = realPlayerRef.get();
        if (realPlayer == null) return;
        aligner = new ShadowPlayerAligner(realPlayer, this, idc);
        aligner.apply();
    }

    void finishUsingLiason() {
        if (openContainer == null) {
            murderLiasonAndShoveHisWretchedBodyOnAPike();
        } else {
            keepLiason();
        }
    }

    void murderLiasonAndShoveHisWretchedBodyOnAPike() {
        // Stuff? Drop our items? Die?
        inventory = original_inventory;
        aligner.unapply();
        setDead();
        realPlayerRef.clear();
    }

    void keepLiason() {
        EntityPlayerMP realPlayer = realPlayerRef.get();
        if (realPlayer == null) return;
        activeLiasons.put(realPlayer, this);
    }

    @Override
    public void closeContainer() {
        super.closeContainer();
        murderLiasonAndShoveHisWretchedBodyOnAPike();
    }

    @Override
    public void closeScreen() {
        super.closeScreen();
        murderLiasonAndShoveHisWretchedBodyOnAPike();
    }

    public static void updateActiveLiasons() {
        for (Iterator<Map.Entry<EntityPlayerMP, InteractionLiason>> it = activeLiasons.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<EntityPlayerMP, InteractionLiason> pair = it.next();
            EntityPlayerMP real = pair.getKey();
            InteractionLiason liason = pair.getValue();
            if (real.isDead || liason.openContainer == null || liason.isDead) {
                liason.murderLiasonAndShoveHisWretchedBodyOnAPike();
                it.remove();
                continue;
            }
            liason.openContainer.detectAndSendChanges();
        }
    }

    private class LiasonHandler extends ChannelOutboundHandlerAdapter implements IFzdsShenanigans {
        // See PacketProxyingPlayer.WrappedMulticastHandler
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            InteractionLiason.this.bouncePacket(msg);
        }
    }

    void bouncePacket(Object msg) {
        EntityPlayerMP realPlayer = realPlayerRef.get();
        if (realPlayer == null) return;
        realPlayer.playerNetServerHandler.sendPacket(PacketProxyingPlayer.wrapMessage(msg));
    }

    public EntityPlayerMP getRealPlayer() {
        return realPlayerRef.get();
    }
}
