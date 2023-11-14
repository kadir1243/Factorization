package factorization.coremodhooks;

import factorization.api.Coord;
import factorization.shared.Core;
import net.minecraft.command.IEntitySelector;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

public class HookTargetsServer {
    public static void diamondExploded(Object dis, World world, int x, int y, int z) {
        if (dis != Blocks.diamond_block) return;
        if (world.isRemote) {
            return;
        }
        Coord c = new Coord(world, x, y, z);
        //if (c.isAir()) return;
        c.setAir();
        int i = 18;
        while (i > 0) {
            int spawn = world.rand.nextInt(3) + 2;
            spawn = Math.min(spawn, i);
            i -= spawn;
            Entity ent = c.spawnItem(new ItemStack(Core.registry.diamond_shard, spawn));
            ent.invulnerable = true;
            ent.motionX = randShardVelocity(world);
            ent.motionY = randShardVelocity(world);
            ent.motionZ = randShardVelocity(world);
        }
    }
    
    private static double randShardVelocity(World world) {
        double r = world.rand.nextGaussian()/4;
        double max = 0.3;
        if (r > max) {
            r = max;
        } else if (r < -max) {
            r = -max;
        }
        return r;
    }

    public static void addConstantColliders(Object me, Entity collider, AxisAlignedBB box, List<Entity> found, IEntitySelector filter) {
        Entity[] constant_colliders = ((IExtraChunkData) me).getConstantColliders();
        if (constant_colliders == null) return;
        for (Entity ent : constant_colliders) {
            if (ent == collider) continue;
            if (filter == null || filter.isEntityApplicable(ent)) {
                //AxisAlignedBB ebox = ent.getBoundingBox();
                //if (ebox == null) continue;
                //if (!box.intersectsWith(ebox)) continue;
                // NOTE: The boxes are being compared backwards from the usual way so that the (presumably) vanilla box can get a crude check
                found.add(ent);
                Entity[] parts = ent.getParts();
                if (parts == null) continue;
                for (Entity part : parts) {
                    //AxisAlignedBB pbox = ent.getBoundingBox();
                    //if (pbox == null) continue;
                    //if (!pbox.intersectsWith(box)) continue;
                    // Swapped again
                    found.add(part);
                }
            }
        }
    }

    public static void addConstantCollidersCOFH(Object me, Entity collider, AxisAlignedBB box, List found) {
        Entity[] constant_colliders = ((IExtraChunkData) me).getConstantColliders();
        if (constant_colliders == null) return;
        for (Entity ent : constant_colliders) {
            if (ent == collider) continue;
            //AxisAlignedBB ebox = ent.getBoundingBox();
            //if (ebox == null) continue;
            //if (!box.intersectsWith(ebox)) continue;
            // NOTE: The boxes are being compared backwards from the usual way so that the (presumably) vanilla box can get a crude check
            AxisAlignedBB bb = ent.getBoundingBox();
            if (bb != null && bb.intersectsWith(box)) found.add(bb);
            Entity[] parts = ent.getParts();
            if (parts == null) continue;
            for (Entity part : parts) {
                //AxisAlignedBB pbox = ent.getBoundingBox();
                //if (pbox == null) continue;
                //if (!pbox.intersectsWith(box)) continue;
                // Swapped again
                bb = part.getBoundingBox();
                if (bb != null && bb.intersectsWith(box)) found.add(bb);
            }
        }
    }

    public static boolean checkHammerCollision(World world, AxisAlignedBB box) {
        int minX = MathHelper.floor_double(box.minX);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxX = MathHelper.floor_double(box.maxX + 1);
        int maxZ = MathHelper.floor_double(box.maxZ + 1);
        if (box.minX < 0) minX--;
        if (box.minZ < 0) minZ--;

        boolean expanded = false;

        Chunk c1 = world.getChunkFromBlockCoords(minX, minZ);
        if (workableChunk(c1)) {
            expanded = true;
            box = box.expand(1, 1, 1);
            if (collides((IExtraChunkData) c1, box)) return true;
        }
        Chunk c2 = world.getChunkFromBlockCoords(minX, maxZ);
        if (c2 != c1 && workableChunk(c2)) {
            if (!expanded) {
                expanded = true;
                box = box.expand(1, 1, 1);
            }
            if (collides((IExtraChunkData) c2, box)) return true;
        }
        Chunk c3 = world.getChunkFromBlockCoords(maxX, minZ);
        if (c3 != c2 && c3 != c1 && workableChunk(c3)) {
            if (!expanded) {
                expanded = true;
                box = box.expand(1, 1, 1);
            }
            if (collides((IExtraChunkData) c3, box)) return true;
        }
        Chunk c4 = world.getChunkFromBlockCoords(maxX, maxZ);
        if (c4 != c3 && c4 != c2 && c4 != c1 && workableChunk(c4)) {
            if (!expanded) {
                expanded = true;
                box = box.expand(1, 1, 1);
            }
            if (collides((IExtraChunkData) c4, box)) return true;
        }

        return false;
    }

    private static boolean workableChunk(Chunk c) {
        Entity[] colliders = ((IExtraChunkData) c).getConstantColliders();
        return colliders != null;
    }

    private static boolean collides(IExtraChunkData data, AxisAlignedBB box) {
        Entity[] colliders = data.getConstantColliders(); // This method can return null, but it will have already been checked by workableChunk
        for (Entity ent : colliders) {
            if (ent.getBoundingBox().intersectsWith(box)) return true;
        }
        return false;
    }

    public static double clipExplosionResistance(Entity ent, double dmg) {
        double ret = EnchantmentProtection.func_92092_a(ent, dmg);
        if (ret < 0) return 0;
        return ret;
    }
}
