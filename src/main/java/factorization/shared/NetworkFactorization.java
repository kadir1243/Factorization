package factorization.shared;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import factorization.api.datahelpers.DataInByteBuf;
import factorization.artifact.ContainerForge;
import factorization.notify.Notice;
import factorization.util.DataUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.util.Random;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import factorization.api.Coord;
import factorization.api.DeltaCoord;
import factorization.api.IEntityMessage;
import factorization.api.Quaternion;
import factorization.api.VectorUV;
import factorization.common.Command;
import factorization.common.FactoryType;
import factorization.utiligoo.ItemGoo;

public class NetworkFactorization {
    public static final ItemStack EMPTY_ITEMSTACK = new ItemStack(Blocks.air);
    
    private void writeObjects(ByteBuf output, Object... items) throws IOException {
        for (Object item : items) {
            if (item == null) {
                throw new RuntimeException("Argument is null!");
            }
            if (item instanceof Integer) {
                output.writeInt((Integer) item);
            } else if (item instanceof Byte) {
                output.writeByte((Byte) item);
            } else if (item instanceof Short) {
                output.writeShort((Short) item);
            } else if (item instanceof String) {
                ByteBufUtils.writeUTF8String(output, (String) item);
            } else if (item instanceof Boolean) {
                output.writeBoolean((Boolean) item);
            } else if (item instanceof Float) {
                output.writeFloat((Float) item);
            } else if (item instanceof Double) {
                output.writeDouble((Double) item);
            } else if (item instanceof ItemStack) {
                ItemStack is = (ItemStack) item;
                if (is == EMPTY_ITEMSTACK) is = null;
                ByteBufUtils.writeItemStack(output, is);
            } else if (item instanceof VectorUV) {
                VectorUV v = (VectorUV) item;
                output.writeFloat((float) v.x);
                output.writeFloat((float) v.y);
                output.writeFloat((float) v.z);
            } else if (item instanceof DeltaCoord) {
                DeltaCoord dc = (DeltaCoord) item;
                dc.write(output);
            } else if (item instanceof Quaternion) {
                Quaternion q = (Quaternion) item;
                q.write(output);
            } else if (item instanceof byte[]) {
                byte[] b = (byte[]) item;
                output.writeBytes(b);
            } else if (item instanceof MessageType) {
                MessageType mt = (MessageType) item;
                mt.write(output);
            } else if (item instanceof NBTTagCompound) {
                NBTTagCompound tag = (NBTTagCompound) item;
                ByteBufUtils.writeTag(output, tag);
            } else {
                throw new RuntimeException("Don't know how to serialize " + item.getClass() + " (" + item + ")");
            }
        }
    }
    
    public void prefixTePacket(ByteBuf output, Coord src, MessageType messageType) throws IOException {
        messageType.write(output);
        output.writeInt(src.x);
        output.writeInt(src.y);
        output.writeInt(src.z);
    }
    
    public FMLProxyPacket TEmessagePacket(Coord src, MessageType messageType, Object... items) {
        try {
            ByteBuf output = Unpooled.buffer();
            prefixTePacket(output, src, messageType);
            writeObjects(output, items);
            return FzNetDispatch.generate(output);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public FMLProxyPacket playerMessagePacket(MessageType messageType, Object... items) {
        try {
            ByteBuf output = Unpooled.buffer();
            messageType.write(output);
            writeObjects(output, items);
            return FzNetDispatch.generate(output);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public void prefixEntityPacket(ByteBuf output, Entity to, MessageType messageType) throws IOException {
        messageType.write(output);
        output.writeInt(to.getEntityId());
    }
    
    public FMLProxyPacket entityPacket(ByteBuf output) throws IOException {
        return FzNetDispatch.generate(output);
    }
    
    public FMLProxyPacket entityPacket(Entity to, MessageType messageType, Object ...items) {
        try {
            ByteBuf output = Unpooled.buffer();
            prefixEntityPacket(output, to, messageType);
            writeObjects(output, items);
            return entityPacket(output);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public void sendCommand(EntityPlayer player, Command cmd, int arg) {
        ByteBuf out = Unpooled.buffer();
        MessageType.factorizeCmdChannel.write(out);
        out.writeByte(cmd.id);
        out.writeInt(arg);
        FzNetDispatch.addPacket(FzNetDispatch.generate(out), player);
    }

    public void sendPlayerMessage(EntityPlayer player, MessageType messageType, Object... msg) {
        FzNetDispatch.addPacket(playerMessagePacket(messageType, msg), player);
    }

    public void broadcastMessage(EntityPlayer who, Coord src, MessageType messageType, Object... msg) {
        if (who != null) {
            FMLProxyPacket toSend = TEmessagePacket(src, messageType, msg);
            FzNetDispatch.addPacket(toSend, who);
        } else {
            FMLProxyPacket toSend = TEmessagePacket(src, messageType, msg);
            FzNetDispatch.addPacketFrom(toSend, src);
        }
    }

    public void broadcastPacket(EntityPlayer who, Coord src, FMLProxyPacket toSend) {
        if (who != null) {
            FzNetDispatch.addPacket(toSend, who);
        } else {
            FzNetDispatch.addPacketFrom(toSend, src);
        }
    }

    void handleTE(ByteBuf input, MessageType messageType, EntityPlayer player) {
        try {
            World world = player.worldObj;
            int x = input.readInt();
            int y = input.readInt();
            int z = input.readInt();
            Coord here = new Coord(world, x, y, z);
            
            if (Core.debug_network) {
                if (world.isRemote) {
                    new Notice(here, messageType.name()).sendTo(player);
                } else {
                    Core.logFine("FactorNet: " + messageType + "      " + here);
                }
            }

            if (!here.blockExists() && world.isRemote) {
                // I suppose we can't avoid this.
                // (Unless we can get a proper server-side check)
                return;
            }
            
            if (messageType == MessageType.DescriptionRequest && !world.isRemote) {
                TileEntityCommon tec = here.getTE(TileEntityCommon.class);
                if (tec != null) {
                    FzNetDispatch.addPacket(tec.getDescriptionPacket(), player);
                }
                return;
            }
            
            if (messageType == MessageType.RedrawOnClient && world.isRemote) {
                world.markBlockForUpdate(x, y, z);
                return;
            }

            if (messageType == MessageType.FactoryType && world.isRemote) {
                //create a Tile Entity of that type there.

                byte ftId = input.readByte();
                FactoryType ft = FactoryType.fromMd(ftId);
                if (ft == null) {
                    Core.logSevere("Got invalid FactoryType ID %s", ftId);
                    return;
                }
                TileEntityCommon spawn = here.getTE(TileEntityCommon.class);
                if (spawn != null && spawn.getFactoryType() != ft) {
                    world.removeTileEntity(x, y, z);
                    spawn = null;
                }
                if (spawn == null) {
                    spawn = ft.makeTileEntity();
                    if (spawn == null) {
                        Core.logSevere("Tried to spawn FactoryType with no associated TE %s", ft);
                        return;
                    }
                    spawn.setWorldObj(world);
                    world.setTileEntity(x, y, z, spawn);
                }

                DataInByteBuf data = new DataInByteBuf(input, Side.CLIENT);
                spawn.putData(data);
                spawn.spawnPacketReceived();
                if (spawn.redrawOnSync()) {
                    here.redraw();
                }
                return;
            }

            if (messageType == null) {
                return;
            }

            TileEntityCommon tec = here.getTE(TileEntityCommon.class);
            if (tec == null) {
                handleForeignMessage(world, x, y, z, tec, messageType, input);
                return;
            }
            boolean handled;
            if (here.w.isRemote) {
                handled = tec.handleMessageFromServer(messageType, input);
            } else {
                handled = tec.handleMessageFromClient(messageType, input);
            }
            if (!handled) {
                handleForeignMessage(world, x, y, z, tec, messageType, input);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void handleForeignMessage(World world, int x, int y, int z, TileEntity ent, MessageType messageType, ByteBuf input) throws IOException {
        if (!world.isRemote) {
            //Nothing for the server to deal with
        } else {
            Coord here = new Coord(world, x, y, z);
            switch (messageType) {
            case PlaySound:
                Sound.receive(here, input);
                break;
            default:
                if (here.blockExists()) {
                    //Core.logFine("Got unhandled message: " + messageType + " for " + here);
                } else {
                    //XXX: Need to figure out how to keep the server from sending these things!
                    Core.logFine("Got message to unloaded chunk: " + messageType + " for " + here);
                }
                break;
            }
        }

    }
    
    boolean handleForeignEntityMessage(Entity ent, MessageType messageType, ByteBuf input) throws IOException {
        if (messageType == MessageType.EntityParticles) {
            Random rand = new Random();
            double px = rand.nextGaussian() * 0.02;
            double py = rand.nextGaussian() * 0.02;
            double pz = rand.nextGaussian() * 0.02;
            
            byte count = input.readByte();
            String type = ByteBufUtils.readUTF8String(input);
            for (int i = 0; i < count; i++) {
                ent.worldObj.spawnParticle(type,
                        ent.posX + rand.nextFloat() * ent.width * 2.0 - ent.width,
                        ent.posY + 0.5 + rand.nextFloat() * ent.height,
                        ent.posZ + rand.nextFloat() * ent.width * 2.0 - ent.width,
                        px, py, pz);
            }
            return true;
        } else if (messageType == MessageType.UtilityGooState) {
            ItemGoo.handlePacket(input);
            return true;
        }
        return false;
    }
    
    void handleCmd(ByteBuf data, EntityPlayer player) {
        byte s = data.readByte();
        int arg = data.readInt();
        Command.fromNetwork(player, s, arg);
    }
    
    void handleEntity(MessageType messageType, ByteBuf input, EntityPlayer player) {
        try {
            World world = player.worldObj;
            int entityId = input.readInt();
            Entity to = world.getEntityByID(entityId);
            if (to == null) {
                if (Core.dev_environ) {
                    Core.logFine("Packet to unknown entity #%s: %s", entityId, messageType);
                }
                return;
            }
            
            if (!(to instanceof IEntityMessage)) {
                if (!player.worldObj.isRemote) {
                    Core.logSevere("Sending the server messages to non-IEntityMessages is not allowed, %s!", player);
                    return;
                }
                if (!handleForeignEntityMessage(to, messageType, input)) {
                    Core.logFine("Packet to inappropriate entity #%s: %s", entityId, messageType);
                }
                return;
            }
            IEntityMessage iem = (IEntityMessage) to;
            
            if (Core.debug_network) {
                Core.logFine("EntityNet: " + messageType + "      " + to);
            }
            
            boolean handled;
            if (world.isRemote) {
                handled = iem.handleMessageFromServer(messageType, input);
            } else {
                handled = iem.handleMessageFromClient(messageType, input);
            }
            if (!handled) {
                if (!handleForeignEntityMessage(to, messageType, input)) {
                    Core.logFine("Got unhandled message: " + messageType + " for " + iem);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    private static byte message_type_count = 0;

    public void handlePlayer(MessageType mt, ByteBuf input, EntityPlayer player) {
        if (mt == MessageType.ArtifactForgeName) {
            String name = ByteBufUtils.readUTF8String(input);
            String lore = ByteBufUtils.readUTF8String(input);
            if (player.openContainer instanceof ContainerForge) {
                ContainerForge forge = (ContainerForge) player.openContainer;
                forge.forge.name = name;
                forge.forge.lore = lore;
                forge.forge.markDirty();
                forge.detectAndSendChanges();
            }
        } else if (mt == MessageType.ArtifactForgeError) {
            String err = ByteBufUtils.readUTF8String(input);
            if (player.openContainer instanceof ContainerForge) {
                ContainerForge forge = (ContainerForge) player.openContainer;
                forge.forge.error_message = err;
                input.readBytes(forge.forge.warnings);
            }
        }
    }

    public enum MessageType {
        factorizeCmdChannel,
        PlaySound, EntityParticles(true),
        
        DrawActive, FactoryType, DescriptionRequest, DataHelperEdit, RedrawOnClient, DataHelperEditOnEntity(true), OpenDataHelperGui, OpenDataHelperGuiOnEntity(true),
        TileEntityMessageOnEntity(true),
        BarrelDescription, BarrelItem, BarrelCount, BarrelDoubleClickHack,
        BatteryLevel, LeydenjarLevel,
        MirrorDescription,
        TurbineWater, TurbineSpeed,
        HeaterHeat,
        LaceratorSpeed,
        MixerSpeed, FanturpellerSpeed,
        CrystallizerInfo,
        WireFace,
        SculptDescription, SculptNew, SculptMove, SculptRemove, SculptState,
        ExtensionInfo, RocketState,
        ServoRailDecor, ServoRailEditComment,
        CompressionCrafter, CompressionCrafterBeginCrafting, CompressionCrafterBounds,
        ScissorState,
        GeneratorParticles,
        BoilerHeat,
        ShaftGenState,
        MillVelocity,
        MisanthropicSpawn, MisanthropicCharge,
        
        // Messages to entities; (true) marks that they are entity messages.
        servo_brief(true), servo_item(true), servo_complete(true), servo_stopped(true),
        entity_sync(true),
        UtilityGooState(true),

        // Messages to/from the player
        ArtifactForgeName(false, true), ArtifactForgeError(false, true);
        
        public final boolean isEntityMessage;
        public final boolean isPlayerMessage;
        private static final MessageType[] valuesCache = values();
        
        private final byte id;
        MessageType() {
            this(false, false);
        }
        
        MessageType(boolean isEntity, boolean isPlayer) {
            id = message_type_count++;
            if (id < 0) {
                throw new IllegalArgumentException("Too many message types!");
            }
            isEntityMessage = isEntity;
            isPlayerMessage = isPlayer;
        }

        MessageType(boolean isEntity) {
            this(true, false);
        }
        
        private static MessageType fromId(byte id) {
            if (id < 0 || id >= valuesCache.length) {
                return null;
            }
            return valuesCache[id];
        }
        
        public static MessageType read(ByteBuf in) {
            byte b = in.readByte();
            return fromId(b);
        }

        public void write(ByteBuf out) {
            out.writeByte(id);
        }
        
    }
    
    public static ItemStack nullItem(ItemStack is) {
        return is == null ? EMPTY_ITEMSTACK : is;
    }
    
    public static ItemStack denullItem(ItemStack is) {
        if (is == null) return null;
        if (DataUtil.getId(is) == DataUtil.getId(Blocks.air)) return null;
        return is;
    }
}
