package factorization.util;

import com.google.common.collect.Multimap;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.shared.Core;
import factorization.weird.TileEntityDayBarrel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.BaseAttributeMap;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class FzUtil {
    public static <E extends Enum<E>> E shiftEnum(E current, E[] values, int delta) {
        int next = current.ordinal() + delta;
        if (next < 0) {
            return values[values.length - 1];
        }
        if (next >= values.length) {
            return values[0];
        }
        return values[next];
    }
    
    
    //Liquid tank handling


    public static int getWorldDimension(World world) {
        return world.provider.dimensionId;
    }

    public static World getWorld(int dimensionId) {
        return DimensionManager.getWorld(dimensionId);
    }

    @SideOnly(Side.CLIENT)
    public static void copyStringToClipboard(String text) {
        StringSelection stringselection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringselection, null);
    }
    
    public static <E> List<E> copyWithoutNull(Collection<E> orig) {
        List<E> ret = new ArrayList<>();
        if (orig == null) return ret;
        for (E e : orig) {
            if (e != null) ret.add(e);
        }
        return ret;
    }

    public static void closeNoisily(String msg, InputStream is) {
        if (is == null) return;
        try {
            is.close();
        } catch (IOException e) {
            Core.logError(msg, e);
        }
    }
    
    public static boolean stringsEqual(String a, String b) {
        return Objects.equals(a, b);
    }

    public static void spawn(Entity ent) {
        if (ent == null) return;
        ent.worldObj.spawnEntityInWorld(ent);
    }

    public static double rateDamage(ItemStack is) {
        if (is == null) return 0;
        Multimap<String, AttributeModifier> attrs = is.getItem().getAttributeModifiers(is);
        if (attrs == null) return 0;
        BaseAttributeMap test = new ServersideAttributeMap();
        test.applyAttributeModifiers(attrs);
        IAttributeInstance attr = test.getAttributeInstance(SharedMonsterAttributes.attackDamage);
        if (attr == null) return 0;
        return attr.getAttributeValue();
    }

    public static ItemStack getReifiedBarrel(Coord at) {
        if (at == null) return null;
        if (at.w == null) return null;
        TileEntityDayBarrel barrel = at.getTE(TileEntityDayBarrel.class);
        if (barrel == null) return null;
        return barrel.item;
    }

    public static String toRpm(double velocity) {
        return (int) (Math.toDegrees(velocity) * 10 / 3) + " RPM";
    }

    // Enh, really belongs in NumUtil maybe?
    // Probably UnitUtil, with the map compass stuff as well
    private static class UnitBase {
        final long ratio;
        final String unit;

        private UnitBase(long ratio, String unit) {
            this.ratio = ratio;
            this.unit = "factorization.unit." + unit;
        }
    }

    private static final UnitBase[] unit_time = new UnitBase[] {
            new UnitBase(20L * 60 * 60 * 24 * 365 * 1000 * 1000, "time.eons"),
            new UnitBase(20L * 60 * 60 * 24 * 365 * 1000, "time.millenia"),
            new UnitBase(20L * 60 * 60 * 24 * 365 * 100, "time.centuries"),
            new UnitBase(20L * 60 * 60 * 24 * 365, "time.years"),
            new UnitBase(20L * 60 * 60 * 24 * 30, "time.months"), // Mostly! :D
            new UnitBase(20L * 60 * 60 * 24 * 7, "time.weeks"),
            new UnitBase(20L * 60 * 60 * 24, "time.irldays"),
            new UnitBase(20L * 60 * 60, "time.hours"),
            //new UnitBase(20L * 60 * 20, "time.mcdays"), // skipped due to confusingness
            new UnitBase(20L * 60, "time.minutes"),
            new UnitBase(20L, "time.seconds"),
            new UnitBase(1L, "time.ticks"),
    };
    private static final UnitBase[] unit_distance_px = new UnitBase[] {
            new UnitBase(16L * 1000, "distance.kilometers"),
            new UnitBase(16L * 16, "distance.chunks"),
            new UnitBase(16L, "distance.blocks"),
            new UnitBase(1L, "distance.pixels"),
    };

    private static UnitBase best(UnitBase[] bases, long value) {
        boolean wasAbove = false;
        for (UnitBase base : bases) {
            if (base.ratio <= value && wasAbove) {
                return base;
            } else if (base.ratio >= value) {
                wasAbove = true;
            }
        }
        return bases[bases.length - 1];
    }

    public static String unitify(String unitName, long value, int max_len) {
        UnitBase[] base;
        if (unitName.equals("time")) {
            base = unit_time;
        } else if (unitName.equals("distance")) {
            base = unit_distance_px;
        } else {
            return "Unknown unit " + unitName + "@" + value;
        }
        return unitify(base, value, max_len);
    }

    private static String unitify(UnitBase[] bases, long value, int max_len) {
        StringBuilder r = new StringBuilder();
        while (max_len-- != 0) {
            UnitBase best = best(bases, value);
            long l = value / best.ratio;
            value -= best.ratio * l;
            if (l > 0) {
                if (r.length() > 0) r.append(' ');
                String unit = LangUtil.translateExact(best.unit + "." + l);
                if (unit != null) {
                    r.append(unit);
                } else {
                    r.append(l).append(' ').append(LangUtil.translateThis(best.unit));
                }
            } else if (value == 0 && r.length() > 0) {
                return r.toString();
            }
            if (best.ratio == 1 || max_len == 0) break;
        }
        return r.toString();
    }

    public static String unitTranslateTimeTicks(long value, int max_len) {
        return "§UNIT§ time " + max_len + " " + value;
    }

    public static String unitTranslateDistancePixels(long value, int max_len) {
        return "§UNIT§ distance " + max_len + " " + value;
    }

    public static void debugBytes(String header, byte[] d) {
        System.out.println(header + " #" + d.length);
        for (byte b : d) {
            System.out.print(" " + Integer.toString(b));
        }
        System.out.println();
    }

    public static void setCoreParent(FMLPreInitializationEvent event) {
        final String FZ = "factorization";
        if (Loader.isModLoaded(FZ)) {
            event.getModMetadata().parent = FZ;
        }
    }
}
