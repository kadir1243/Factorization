package factorization.util;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.shared.Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.glGetError;

public final class RenderUtil {
    @SideOnly(Side.CLIENT)
    private static RenderBlocks rb;

    @SideOnly(Side.CLIENT)
    public static RenderBlocks getRB() {
        if (rb == null) {
            rb = new RenderBlocks();
        }
        rb.blockAccess = Minecraft.getMinecraft().theWorld;
        return rb;
    }

    @SideOnly(Side.CLIENT)
    public static void rotateForDirection(ForgeDirection dir) {
        switch (dir) {
            case EAST -> GL11.glRotatef(180, 0, 1, 0);
            case NORTH -> GL11.glRotatef(-90, 0, 1, 0);
            case SOUTH -> GL11.glRotatef(90, 0, 1, 0);
            case UP -> GL11.glRotatef(-90, 0, 0, 1);
            case DOWN -> GL11.glRotatef(90, 0, 0, 1);
            case WEST, UNKNOWN -> {}
        }
    }

    @SideOnly(Side.CLIENT)
    public static boolean checkGLError(String op) {
        int errSym = glGetError();
        if (errSym != GL_NO_ERROR) {
            Core.logSevere("GL Error @ " + op);
            Core.logSevere(errSym + ": " + GLU.gluErrorString(errSym));
            return true;
        }
        return false;
    }
}
