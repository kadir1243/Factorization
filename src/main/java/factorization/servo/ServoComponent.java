package factorization.servo;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.api.datahelpers.*;
import factorization.servo.instructions.*;
import factorization.shared.Core;
import factorization.util.RenderUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

public abstract class ServoComponent implements IDataSerializable {
    private static Map<String, Supplier<? extends ServoComponent>> componentMap = new HashMap<>(50, 0.5F);
    final private static String componentTagKey = "SCId";

    public static void register(Supplier<? extends ServoComponent> componentClass, List<ItemStack> sortedList) {
        String name;
        ServoComponent decor;
        decor = componentClass.get();
        name = decor.getName();
        componentMap.put(name, new EqualibleSupplier<>(componentClass));
        sortedList.add(decor.toItem());
    }

    private static final class EqualibleSupplier<T> implements Supplier<T> {
        private final Supplier<T> supplier;
        private boolean isCreated;
        private T instance;

        private EqualibleSupplier(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            if (isCreated) return instance;
            T t = supplier.get();
            this.instance = t;
            this.isCreated = true;
            return t;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Supplier<?> s) {
                return Objects.equals(s.get(), obj);
            }
            if (!isCreated) get();
            return Objects.equals(this.instance, obj);
        }

        @Override
        public int hashCode() {
            if (!isCreated) get();
            return Objects.hashCode(this.instance);
        }
    }

    public static Supplier<? extends ServoComponent> getComponent(String name) {
        return componentMap.get(name);
    }

    private static BiMap<Short, ServoComponent> the_idMap = null;
    private static BiMap<Short, ServoComponent> getPacketIdMap() {
        if (the_idMap == null) {
            List<String> names = new ArrayList<>(componentMap.keySet());
            Collections.sort(names);
            ImmutableBiMap.Builder<Short, ServoComponent> builder = ImmutableBiMap.builder();
            for (short i = 0; i < names.size(); i++) {
                builder.put(i, componentMap.get(names.get(i)).get());
            }
            the_idMap = builder.build();
        }
        return the_idMap;
    }
    
    public static Iterable<ServoComponent> getComponents() {
        return getPacketIdMap().values();
    }
    
    public short getNetworkId() {
        BiMap<ServoComponent, Short> map = getPacketIdMap().inverse();
        Short o = map.get(this);
        if (o == null) {
            throw new IllegalArgumentException(getClass() + " is not a registered ServoComponent");
        }
        return o;
    }

    @Override
    public final IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        if (data.isReader()) {
            String componentName;
            if (data.hasLegacy(prefix + componentTagKey)) {
                // This is actually the opposite of legacy!
                componentName = data.asSameShare(prefix + componentTagKey).putString(getName());
            } else {
                componentName = data.asSameShare(componentTagKey).putString(getName());
            }
            Supplier<? extends ServoComponent> componentClass = getComponent(componentName);
            ServoComponent sc = componentClass.get();
            return sc.putData(prefix, data);
        } else {
            data.asSameShare(prefix + componentTagKey).putString(getName());
            return putData(prefix, data);
        }
    }

    protected abstract IDataSerializable putData(String prefix, DataHelper data) throws IOException;

    protected static ServoComponent load(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(componentTagKey)) {
            return null;
        }
        String componentName = tag.getString(componentTagKey);
        Supplier<? extends ServoComponent> componentClass = getComponent(componentName);
        if (componentClass == null) {
            Core.logWarning("Unknown servo component with ID %s. Removing tag info!", componentName);
            Core.logWarning("The tag: %s", tag);
            Thread.dumpStack();
            tag.removeTag(componentTagKey);
            return null;
        }
        try {
            ServoComponent decor = componentClass.get();
            return new DataInNBT(tag).as(Share.VISIBLE, "sc").putIDS(decor);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    protected final void save(NBTTagCompound tag) {
        tag.setString(componentTagKey, getName());
        try {
            (new DataOutNBT(tag)).as(Share.VISIBLE, "sc").putIDS(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    void writeToPacket(DataOutputStream dos) throws IOException {
        dos.writeShort(getNetworkId());
        (new DataOutPacket(dos, Side.SERVER)).as(Share.VISIBLE, "sc").putIDS(this);
    }
    
    static ServoComponent readFromPacket(ByteBuf dis) throws IOException {
        short id = dis.readShort();
        ServoComponent componentClass = getPacketIdMap().get(id);
        if (componentClass == null) {
            Core.logWarning("Unknown servo component with #ID %s", id);
            return null;
        }
        try {
            new DataInByteBuf(dis, Side.CLIENT).as(Share.VISIBLE, "sc").putIDS(componentClass);
            return componentClass;
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public ItemStack toItem() {
        ItemStack ret;
        if (this instanceof Instruction) {
            ret = new ItemStack(Core.registry.servo_widget_instruction);
        } else {
            ret = new ItemStack(Core.registry.servo_widget_decor);
        }
        NBTTagCompound tag = new NBTTagCompound();
        save(tag);
        ret.setTagCompound(tag);
        String name = getName();
        int dmg = Math.abs(name.hashCode()) % (Short.MAX_VALUE);
        ret.setItemDamage(dmg);
        return ret;
    }
    
    public ServoComponent copyComponent() {
        NBTTagCompound tag = new NBTTagCompound();
        save(tag);
        return load(tag);
    }
    
    public static ServoComponent fromItem(ItemStack is) {
        if (!is.hasTagCompound()) {
            return null;
        }
        return load(is.getTagCompound());
    }
    
    //return True if the item should be consumed by a survival-mode player
    public abstract boolean onClick(EntityPlayer player, Coord block, ForgeDirection side);
    public abstract boolean onClick(EntityPlayer player, ServoMotor motor);
    
    /**
     * @return a unique name, something like "modname.componentType.name"
     */
    public abstract String getName();
    
    /**
     * Render to the Tessellator. This must be appropriate for a SimpleBlockRenderingHandler.
     * @param where to render it at in world. If null, it is being rendered in an inventory (or so). Render to 0,0,0.
     * @param rb RenderBlocks
     */
    @SideOnly(Side.CLIENT)
    public abstract void renderStatic(Coord where, RenderBlocks rb);
    
    @SideOnly(Side.CLIENT)
    public void renderDynamic() {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        renderStatic(null, RenderUtil.getRB());
        tess.draw();
    }
    
    @SideOnly(Side.CLIENT)
    public void addInformation(List<String> info) {
    }

    static List<ItemStack> sorted_instructions = new ArrayList<>();
    static List<ItemStack> sorted_decors = new ArrayList<>();

    static {
        @SuppressWarnings("unchecked")
        Supplier<? extends ServoComponent>[] decorations = new Supplier[] {
                WoodenServoGrate::new,
                GlassServoGrate::new,
                IronServoGrate::new,
                ScanColor::new,
        };
        @SuppressWarnings("unchecked")
        Supplier<? extends ServoComponent>[] instructions = new Supplier[] {
                // Color by class, sort by color
                // Cyan: Motion instructions
                EntryControl::new,
                SetDirection::new,
                Spin::new,
                RotateTop::new,
                SetSpeed::new,
                Trap::new,

                // Red: Redstone-ish instructions
                RedstonePulse::new,
                SocketCtrl::new,
                ReadRedstone::new,
                CountItems::new,
                ShifterControl::new,

                // Yellow: Math instructions
                Drop::new,
                Dup::new,
                IntegerValue::new,
                Sum::new,
                Product::new,
                BooleanValue::new,
                Compare::new,

                // White: Computation instructions
                Jump::new,
                SetEntryAction::new,
                SetRepeatedInstruction::new,
                InstructionGroup::new,
        };

        for (Supplier<? extends ServoComponent> cl : decorations) register(cl, sorted_decors);
        for (Supplier<? extends ServoComponent> cl : instructions) register(cl, sorted_instructions);
    }
    
    public static void setupRecipes() {
        for (Supplier<? extends ServoComponent> klazz : componentMap.values()) {
            klazz.get().addRecipes();
        }
    }
    
    protected void addRecipes() {}

    public void onItemUse(Coord here, EntityPlayer player) { }
}
