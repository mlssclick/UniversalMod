package universalmod.render.atmosphere;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL33;
import java.nio.ByteBuffer;

final class RainHeightMap implements AutoCloseable {
    static final int SIZE = 321;
    private static final int REFRESH_ROWS = 12, NEAR_RADIUS = 15, HEIGHT_UNITS = 16;
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();
    private final ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
    private int texture, originX, originZ, refreshRow;
    private long lastTick = Long.MIN_VALUE;
    private ClientWorld world;

    int texture() { return texture; }
    int originX() { return originX; }
    int originZ() { return originZ; }

    void update(ClientWorld current, Vec3d camera) {
        int x = MathHelper.floor(camera.x), z = MathHelper.floor(camera.z);
        int nextX = Math.floorDiv(x,16)*16 - SIZE/2;
        int nextZ = Math.floorDiv(z,16)*16 - SIZE/2;
        boolean fresh = world != current || texture == 0;
        if (fresh) {
            close();
            world=current;
            texture=GL33.glGenTextures();
            GL33.glBindTexture(GL33.GL_TEXTURE_2D, texture);
            GL33.glTexImage2D(GL33.GL_TEXTURE_2D,0,GL33.GL_RGBA8,SIZE,SIZE,0,GL33.GL_RGBA,
                    GL33.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_MIN_FILTER,GL33.GL_NEAREST);
            GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_MAG_FILTER,GL33.GL_NEAREST);
            GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_WRAP_S,GL33.GL_CLAMP_TO_EDGE);
            GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_WRAP_T,GL33.GL_CLAMP_TO_EDGE);
        }
        boolean moved = nextX != originX || nextZ != originZ;
        if (fresh || moved) {
            int oldX=originX,oldZ=originZ;
            originX=nextX;originZ=nextZ;
            for(int dz=0;dz<SIZE;dz++) for(int dx=0;dx<SIZE;dx++) {
                int wx=originX+dx,wz=originZ+dz;
                if(fresh || wx<oldX || wx>=oldX+SIZE || wz<oldZ || wz>=oldZ+SIZE) sample(wx,wz);
            }
        }
        long tick=current.getTime();
        if(tick!=lastTick || moved || fresh) {
            for(int dz=-NEAR_RADIUS;dz<=NEAR_RADIUS;dz++) for(int dx=-NEAR_RADIUS;dx<=NEAR_RADIUS;dx++) sample(x+dx,z+dz);
            for(int row=0;row<REFRESH_ROWS;row++) {
                int wz=originZ+refreshRow;
                for(int dx=0;dx<SIZE;dx++) sample(originX+dx,wz);
                refreshRow=(refreshRow+1)%SIZE;
            }
            lastTick=tick;
            GL33.glBindTexture(GL33.GL_TEXTURE_2D,texture);
            pixels.position(0);
            int unpackAlignment=GL33.glGetInteger(GL33.GL_UNPACK_ALIGNMENT);
            GL33.glPixelStorei(GL33.GL_UNPACK_ALIGNMENT,1);
            try {
                GL33.glTexSubImage2D(GL33.GL_TEXTURE_2D,0,0,0,SIZE,SIZE,GL33.GL_RGBA,GL33.GL_UNSIGNED_BYTE,pixels);
            } finally { GL33.glPixelStorei(GL33.GL_UNPACK_ALIGNMENT,unpackAlignment); }
        }
    }
    private void sample(int x,int z) {
        int solid=0, canopy=0;
        if(world.isChunkLoaded(x>>4,z>>4)) {
            solid=encode(surface(x,world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z),z));
            canopy=encode(surface(x,world.getTopY(Heightmap.Type.MOTION_BLOCKING,x,z),z));
        }
        int offset=(Math.floorMod(z,SIZE)*SIZE + Math.floorMod(x,SIZE))*4;
        pixels.put(offset,(byte)solid);
        pixels.put(offset+1,(byte)(solid>>8));
        pixels.put(offset+2,(byte)canopy);
        pixels.put(offset+3,(byte)(canopy>>8));
    }
    private double surface(int x,int height,int z) {
        for(int y=height-1;y>=Math.max(world.getBottomY(),height-24);y--) {
            cursor.set(x,y,z);
            VoxelShape shape=world.getBlockState(cursor).getCollisionShape(world,cursor);
            double top=shape.isEmpty()?0.0:shape.getMax(Direction.Axis.Y);
            if(!world.getFluidState(cursor).isEmpty()) top=Math.max(top,world.getFluidState(cursor).getHeight(world,cursor));
            if(top>0.0) return y+top;
        }
        return height;
    }
    private int encode(double height) {
        return MathHelper.clamp((int)Math.round((height-world.getBottomY()+1.0)*HEIGHT_UNITS),1,65535);
    }
    @Override public void close() {
        if(texture!=0) GL33.glDeleteTextures(texture);
        texture=0;world=null;lastTick=Long.MIN_VALUE;refreshRow=0;
    }
}
