package factorization.fzds;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.relauncher.Side;
import factorization.api.Coord;
import factorization.api.Quaternion;
import factorization.coremodhooks.HookTargetsClient;
import factorization.coremodhooks.IExtraChunkData;
import factorization.fzds.gui.ProxiedGuiContainer;
import factorization.fzds.gui.ProxiedGuiScreen;
import factorization.fzds.interfaces.IDeltaChunk;
import factorization.fzds.network.WrapperAdapter;
import factorization.shared.BlockRenderHelper;
import factorization.shared.Core;
import factorization.util.NumUtil;
import factorization.util.SpaceUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

import java.util.WeakHashMap;

public class HammerClientProxy extends HammerProxy {
    static HammerClientProxy instance;

    public HammerClientProxy() {
        RenderDimensionSliceEntity rwe = new RenderDimensionSliceEntity();
        RenderingRegistry.registerEntityRenderingHandler(DimensionSliceEntity.class, rwe);
        Core.loadBus(rwe);
        HammerClientProxy.instance = this;
    }
    
    //These two classes below make it easy to see in a debugger.
    public static class HammerChunkProviderClient extends ChunkProviderClient {
        public HammerChunkProviderClient(World par1World) {
            super(par1World);
        }
    }
    
    public static class HammerWorldClient extends WorldClient {
        public HammerWorldClient(NetHandlerPlayClient par1NetClientHandler, WorldSettings par2WorldSettings, int par3, EnumDifficulty par4, Profiler par5Profiler) {
            super(par1NetClientHandler, par2WorldSettings, par3, par4, par5Profiler);
        }
        
        @Override
        public void playSoundAtEntity(Entity par1Entity, String par2Str, float par3, float par4) {
            super.playSoundAtEntity(par1Entity, par2Str, par3, par4);
        }
        
        public void clearAccesses() {
            worldAccesses.clear();
        }
        
        public void shadowTick() {
            clientChunkProvider.unloadQueuedChunks();
        }
    }
    
    @Override
    public World getClientRealWorld() {
        if (real_world == null) {
            return Minecraft.getMinecraft().theWorld;
        }
        return real_world;
    }
    
    private static World lastWorld = null;
    static ShadowRenderGlobal shadowRenderGlobal = null;
    void checkForWorldChange() {
        WorldClient currentWorld = Minecraft.getMinecraft().theWorld;
        if (currentWorld == null) {
            createClientShadowWorld();
            return;
        }
        if (currentWorld != lastWorld) {
            lastWorld = currentWorld;
            if (Hammer.worldClient != null) {
                ((HammerWorldClient)Hammer.worldClient).clearAccesses();
                Hammer.worldClient.addWorldAccess(shadowRenderGlobal = new ShadowRenderGlobal(currentWorld));
            }
        }
    }
    
    @SubscribeEvent
    public void tick(ClientTickEvent event) {
        if (event.phase == Phase.END) return;
        checkForWorldChange(); // Is there an event for this?
        runShadowTick();
        if (shadowRenderGlobal != null) {
            shadowRenderGlobal.removeStaleDamage();
        }
    }
    
    @Override
    public void createClientShadowWorld() {
        final Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null || mc.thePlayer == null) {
            Hammer.worldClient = null;
            send_queue = null;
            fake_player = null;
            shadowRenderGlobal = null;
            return;
        }
        send_queue = mc.thePlayer.sendQueue;
        WorldInfo wi = world.getWorldInfo();
        try {
            HookTargetsClient.clientWorldLoadEventAbort.set(true);
            Hammer.worldClient = new HammerWorldClient(send_queue,
                    new WorldSettings(wi),
                    DeltaChunk.getDimensionId(),
                    world.difficultySetting,
                    Core.proxy.getProfiler());
        } finally {
            HookTargetsClient.clientWorldLoadEventAbort.remove();
        }
        Hammer.worldClient.addWorldAccess(shadowRenderGlobal = new ShadowRenderGlobal(mc.theWorld));
    }
    
    
    @SubscribeEvent
    public void onClientLogout(ClientDisconnectionFromServerEvent event) {
        cleanupClientWorld();
    }

    @Override
    public void cleanupClientWorld() {
        //TODO: what else we can do here to cleanup?
        if (FMLCommonHandler.instance().getEffectiveSide() != Side.CLIENT) {
            return;
        }
        if (Hammer.worldClient != null) {
            ((HammerWorldClient)Hammer.worldClient).clearAccesses();
        }
        Hammer.worldClient = null;
        send_queue = null;
        fake_player = null;
        shadowRenderGlobal = null;
        lastWorld = null;
        real_player = null;
        real_world = null;
        fake_player = null;
        real_renderglobal = null;
        _shadowSelected = null;
        _rayTarget = null;
        _selectionBlockBounds = null;
        _hitSlice = null;
        Hammer.clientSlices.clear();
    }

    private static NetHandlerPlayClient send_queue;
    private boolean send_queue_spam = false;
    private void setSendQueueWorld(WorldClient wc) {
        if (send_queue == null) {
            if (!send_queue_spam) {
                Core.logSevere("send_queue is null!?");
                send_queue_spam = true;
            }
            return;
        }
        send_queue.clientWorldController = wc;
    }

    private void setWorldAndPlayer(WorldClient wc, EntityClientPlayerMP player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (wc == null || player == null) {
            throw new NullPointerException("Tried setting world/player to null!");
        }
        //For logic
        if (mc.renderViewEntity == mc.thePlayer) {
            mc.renderViewEntity = player;
        }
        mc.theWorld = wc;
        mc.thePlayer = player;
        mc.thePlayer.worldObj = wc;
        setSendQueueWorld(wc);
        
        //For rendering
        mc.renderViewEntity = player;
        if (TileEntityRendererDispatcher.instance.field_147550_f != null) {
            TileEntityRendererDispatcher.instance.field_147550_f = wc;
        }
        if (RenderManager.instance.worldObj != null) {
            RenderManager.instance.worldObj = wc;
        }
        renderglobal_cache.put(mc.renderGlobal.theWorld, mc.renderGlobal);
        mc.renderGlobal = getRenderGlobalForWorld(wc);
        if (mc.renderGlobal == null) {
            throw new NullPointerException("mc.renderGlobal");
        }
    }

    private final WeakHashMap<World, RenderGlobal> renderglobal_cache = new WeakHashMap<World, RenderGlobal>();
    private RenderGlobal getRenderGlobalForWorld(WorldClient wc) {
        RenderGlobal cached = renderglobal_cache.get(wc);
        if (cached != null) return cached;
        RenderGlobal ret = new RenderGlobal(Minecraft.getMinecraft());
        ret.setWorldAndLoadRenderers(wc);
        renderglobal_cache.put(wc, ret);
        return ret;
    }

    public static RenderGlobal getRealRenderGlobal() {
        if (instance.real_renderglobal == null) {
            return Minecraft.getMinecraft().renderGlobal;
        }
        return instance.real_renderglobal;
    }

    @Override
    public EntityPlayer getRealPlayerWhileInShadow() {
        return real_player;
    }

    @Override
    public EntityPlayer getFakePlayerWhileInShadow() {
        if (real_player != null) return fake_player;
        return null;
    }
    
    private EntityClientPlayerMP real_player = null;
    private WorldClient real_world = null;
    private EntityClientPlayerMP fake_player = null;
    private RenderGlobal real_renderglobal = null;
    
    @Override
    public void setShadowWorld() {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient w = (WorldClient) DeltaChunk.getClientShadowWorld();
        assert w != null;
        if (real_player != null || real_world != null) {
            throw new IllegalStateException("Tried to switch to Shadow world, but we're already in the shadow world");
        }
        real_player = mc.thePlayer;
        if (real_player == null) {
            throw new IllegalStateException("Swapping out to hammer world, but thePlayer is null");
        }
        real_world = mc.theWorld;
        if (real_world == null) {
            throw new IllegalStateException("Swapping out to hammer world, but theWorld is null");
        }
        real_player.worldObj = w;
        if (fake_player == null || w != fake_player.worldObj) {
            fake_player = new EntityClientPlayerMP(
                    mc,
                    mc.theWorld /* why is this real world? NORELEASE: world leakage? */,
                    mc.getSession(), real_player.sendQueue /* not sure about this one. */,
                    real_player.getStatFileWriter());
            fake_player.movementInput = real_player.movementInput;
        }
        real_renderglobal = mc.renderGlobal;
        setWorldAndPlayer(w, fake_player);
        WrapperAdapter.setShadow(true);
        fake_player.inventory = real_player.inventory;
    }
    
    @Override
    public void restoreRealWorld() {
        setWorldAndPlayer(real_world, real_player);
        real_world = null;
        real_player = null;
        real_renderglobal = null;
        WrapperAdapter.setShadow(false);
    }
    
    @Override
    public boolean isInShadowWorld() {
        return real_world != null;
    }
    
    void runShadowTick() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.isGamePaused()) {
            return;
        }
        final WorldClient mcWorld = mc.theWorld;
        if (mcWorld == null) {
            return;
        }
        final EntityPlayer mcPlayer = mc.thePlayer;
        if (mcPlayer == null) {
            return;
        }
        if (mc.getNetHandler() == null) {
            return;
        }
        HammerWorldClient w = (HammerWorldClient) DeltaChunk.getClientShadowWorld();
        if (w == null) {
            return;
        }
        int range = 10;
        AxisAlignedBB nearby = mcPlayer.boundingBox.expand(range, range, range);
        Iterable<IDeltaChunk> nearbyChunks = mcWorld.getEntitiesWithinAABB(IDeltaChunk.class, nearby);
        setShadowWorld();
        Core.profileStart("FZDStick");
        try {
            //Inspired by Minecraft.runTick()
            w.updateEntities();
            Vec3 playerPos = Vec3.createVectorHelper(mcPlayer.posX, mcPlayer.posY, mcPlayer.posZ);
            for (IDeltaChunk idc : nearbyChunks) {
                Vec3 center = idc.real2shadow(playerPos);
                w.doVoidFogParticles((int) center.xCoord, (int) center.yCoord, (int) center.zCoord);
            }
            w.shadowTick();
        } finally {
            Core.profileEnd();
            restoreRealWorld();
        }
    }
    
    @Override
    public void clientInit() {
        
    }
    
    final Minecraft mc = Minecraft.getMinecraft();
    
    @SubscribeEvent
    public void resetTracing(ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        /*_shadowSelected = null;
        _rayTarget = null;
        _selectionBlockBounds = null;
        _hitSlice = null;*/
        _distance = Double.POSITIVE_INFINITY;
    }
    
    public void offerHit(MovingObjectPosition mop, DseRayTarget rayTarget, AxisAlignedBB moppedBounds) {
        double d = rayTarget.getDistanceSqToEntity(mc.thePlayer);
        if (d > _distance) return;
        _shadowSelected = mop;
        _rayTarget = rayTarget;
        _selectionBlockBounds = moppedBounds;
        _hitSlice = rayTarget.parent;
        _distance = d;
    }
    
    double _distance;
    MovingObjectPosition _shadowSelected;
    DseRayTarget _rayTarget;
    AxisAlignedBB _selectionBlockBounds;
    DimensionSliceEntity _hitSlice;
    
    @SubscribeEvent
    public void renderSelection(DrawBlockHighlightEvent event) {
        if (!(event.target.entityHit instanceof DseRayTarget)) {
            return;
        }
        AxisAlignedBB box = _selectionBlockBounds;
        MovingObjectPosition shadowSelected = _shadowSelected;
        if (box == null || shadowSelected.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        if (event.isCanceled()) {
            return;
        }
        Coord here = new Coord(DeltaChunk.getClientShadowWorld(), shadowSelected.blockX, shadowSelected.blockY, shadowSelected.blockZ);
        Block hereBlock = BlockRenderHelper.instance; //here.getBlock();
        hereBlock.setBlockBounds(
                (float) (box.minX - here.x), (float) (box.minY - here.y), (float) (box.minZ - here.z),
                (float) (box.maxX - here.x), (float) (box.maxY - here.y), (float) (box.maxZ - here.z)
        );
        EntityPlayer player = event.player;
        //RenderGlobal rg = event.context;
        ItemStack is = event.currentItem;
        float partialTicks = event.partialTicks;
        DimensionSliceEntity dse = _hitSlice;
        Coord corner = dse.getCorner();
        Quaternion rotation = dse.prevTickRotation.slerp(dse.getRotation(), event.partialTicks);
        rotation.incrNormalize();
        try {
            GL11.glPushMatrix();
            setShadowWorld();
            RenderGlobal rg = mc.renderGlobal;
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            /*if (Core.dev_environ) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glColorMask(false, true, true, true);
            }*/
            GL11.glTranslated(
                    NumUtil.interp(dse.lastTickPosX - player.lastTickPosX, dse.posX - player.posX, partialTicks),
                    NumUtil.interp(dse.lastTickPosY - player.lastTickPosY, dse.posY - player.posY, partialTicks),
                    NumUtil.interp(dse.lastTickPosZ - player.lastTickPosZ, dse.posZ - player.posZ, partialTicks));
            rotation.glRotate();
            Vec3 centerOffset = dse.getRotationalCenterOffset();
            GL11.glTranslated(
                    -centerOffset.xCoord - corner.x,
                    -centerOffset.yCoord - corner.y,
                    -centerOffset.zCoord - corner.z);
            
            double savePlayerX = player.posX;
            double savePlayerY = player.posY;
            double savePlayerZ = player.posZ;
            partialTicks = 1;
            player.posX = player.posY = player.posZ = 0;
            if (!ForgeHooksClient.onDrawBlockHighlight(rg, player, shadowSelected, shadowSelected.subHit, is, partialTicks)) {
                rg.drawSelectionBox(player, shadowSelected, 0, partialTicks);
            }
            player.posX = savePlayerX;
            player.posY = savePlayerY;
            player.posZ = savePlayerZ;
        } finally {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            restoreRealWorld();
            GL11.glPopMatrix();
            /*if (Core.dev_environ) {
                GL11.glColorMask(true, true, true, true);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }*/
        }
    }
    
    @Override
    void updateRayPosition(DseRayTarget ray) {
        if (ray.parent.metaAABB == null) return;
        // If we didn't care about entities, we could call:
        //    mc.renderViewEntity.rayTrace(reachDistance, partialTicks)
        // But we need entities, so we'll just invoke MC's ray trace code.
        
        // Save values
        MovingObjectPosition origMouseOver = mc.objectMouseOver;
        mc.objectMouseOver = null; // The ray trace function will do the wrong thing if this isn't nulled.
        
        boolean got_hit = false;
        
        try {
            MovingObjectPosition mop = null;
            AxisAlignedBB mopBox = null;
            EntityPlayer realPlayer = mc.thePlayer;
            setShadowWorld();
            EntityPlayer fakePlayer = mc.thePlayer;
            try {
                ShadowPlayerAligner aligner = new ShadowPlayerAligner(realPlayer, fakePlayer, ray.parent);
                aligner.apply(); // Change the player's look to shadow rotation
                WorldSettings.GameType origType = mc.playerController.currentGameType;
                try {
                    mc.entityRenderer.getMouseOver(1F);
                } finally {
                    mc.playerController.currentGameType = origType;
                }
                aligner.unapply();
                mop = mc.objectMouseOver;
                if (mop == null) {
                    return;
                }
                switch (mop.typeOfHit) {
                case ENTITY:
                    mopBox = mop.entityHit.boundingBox;
                    break;
                case BLOCK:
                    World w = DeltaChunk.getClientShadowWorld();
                    Block block = w.getBlock(mop.blockX, mop.blockY, mop.blockZ);
                    mopBox = block.getSelectedBoundingBoxFromPool(w, mop.blockX, mop.blockY, mop.blockZ);
                    break;
                default: return;
                }
            } finally {
                restoreRealWorld();
            }
            if (mopBox == null) {
                return;
            }
            // Create a realbox that contains the entirety of the shadowbox
            Vec3 min, max;
            {
                final DimensionSliceEntity rayParent = ray.parent;
                Vec3 corners[] = SpaceUtil.getCorners(mopBox);
                min = rayParent.shadow2real(corners[0]);
                max = SpaceUtil.copy(min);
                for (int i = 1; i < corners.length; i++) {
                    Vec3 c = rayParent.shadow2real(corners[i]);
                    min.xCoord = Math.min(c.xCoord, min.xCoord);
                    min.yCoord = Math.min(c.yCoord, min.yCoord);
                    min.zCoord = Math.min(c.zCoord, min.zCoord);
                    max.xCoord = Math.max(c.xCoord, max.xCoord);
                    max.yCoord = Math.max(c.yCoord, max.yCoord);
                    max.zCoord = Math.max(c.zCoord, max.zCoord);
                }
            }
            ray.setPosition((min.xCoord + max.xCoord) / 2, (min.yCoord + max.yCoord) / 2, (min.zCoord + max.zCoord) / 2);
            SpaceUtil.setMin(ray.boundingBox, min);
            SpaceUtil.setMax(ray.boundingBox, max);
            //AabbDebugger.addBox(ray.boundingBox); // It's always nice to see this.
            offerHit(mop, ray, mopBox);
            got_hit = true;
        } finally {
            mc.objectMouseOver = origMouseOver;
            if (!got_hit) {
                ray.setPosition(0, -1000, 0);
            }
        }
    }
    
    @Override
    public MovingObjectPosition getShadowHit() {
        return _shadowSelected;
    }
    
    @Override
    IDeltaChunk getHitIDC() {
        return _hitSlice;
    }
    
    @SubscribeEvent
    public void showUniversalCollidersInfo(RenderGameOverlayEvent.Text event) {
        if (mc.thePlayer == null) return;
        if (!mc.gameSettings.showDebugInfo) return;
        Coord at = new Coord(mc.thePlayer);
        IExtraChunkData ed = (IExtraChunkData) at.getChunk();
        Entity[] objs = ed.getConstantColliders();
        if (objs == null) return;
        event.left.add("uc: " + objs.length);
    }

    @SubscribeEvent
    public void addWrapperAdapter(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        WrapperAdapter.addToPipeline(event.manager);
    }

    @Override
    public boolean guiCheckStart() {
        return Minecraft.getMinecraft().currentScreen == null;
    }

    @Override
    public void guiCheckEnd(boolean oldState) {
        if (oldState && !guiCheckStart()) {
            GuiScreen wrap = Minecraft.getMinecraft().currentScreen;
            if (wrap instanceof GuiContainer) {
                GuiContainer gc = (GuiContainer) wrap;
                Minecraft.getMinecraft().displayGuiScreen(new ProxiedGuiContainer(gc.inventorySlots, gc));
            } else if (wrap != null) {
                Minecraft.getMinecraft().displayGuiScreen(new ProxiedGuiScreen(wrap));
            }
        }
    }
}
