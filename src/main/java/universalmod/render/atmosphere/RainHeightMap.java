package universalmod.render.atmosphere;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

 
public final class RainHeightMap implements AutoCloseable {
    public static final int SIZE = 321;
    private static final int REFRESH_ROWS = 12;
    private static final int NEAR_RADIUS = 15;
    private static final int HEIGHT_UNITS = 16;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private NativeImage image;
    private GpuTexture texture;
    private GpuTextureView view;
    private ClientLevel level;
    private int originX, originZ, refreshRow;
    private long lastTick = Long.MIN_VALUE;

    public GpuTextureView view() { return view; }
    public int originX() { return originX; }
    public int originZ() { return originZ; }

    public void update(ClientLevel current, Vec3 camera, CommandEncoder encoder) {
        int x = Mth.floor(camera.x), z = Mth.floor(camera.z);
        int nextX = Math.floorDiv(x, 16) * 16 - SIZE / 2;
        int nextZ = Math.floorDiv(z, 16) * 16 - SIZE / 2;
        boolean fresh = level != current || texture == null || texture.isClosed();
        if (fresh) {
            close();
            level = current;
            image = new NativeImage(SIZE, SIZE, false);
            texture = RenderSystem.getDevice().createTexture(() -> "universalmod:rain_height",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, SIZE, SIZE, 1, 1);
            view = RenderSystem.getDevice().createTextureView(texture);
        }
        boolean moved = nextX != originX || nextZ != originZ;
        if (fresh || moved) {
            int oldX = originX, oldZ = originZ;
            originX = nextX;
            originZ = nextZ;
             
            for (int dz = 0; dz < SIZE; dz++) {
                for (int dx = 0; dx < SIZE; dx++) {
                    int wx = originX + dx, wz = originZ + dz;
                    if (fresh || wx < oldX || wx >= oldX + SIZE || wz < oldZ || wz >= oldZ + SIZE) sample(wx, wz);
                }
            }
        }
        long tick = current.getGameTime();
        if (tick != lastTick || moved || fresh) {
             
            for (int dz = -NEAR_RADIUS; dz <= NEAR_RADIUS; dz++)
                for (int dx = -NEAR_RADIUS; dx <= NEAR_RADIUS; dx++) sample(x + dx, z + dz);
            for (int row = 0; row < REFRESH_ROWS; row++) {
                int wz = originZ + refreshRow;
                for (int dx = 0; dx < SIZE; dx++) sample(originX + dx, wz);
                refreshRow = (refreshRow + 1) % SIZE;
            }
            lastTick = tick;
            encoder.writeToTexture(texture, image);
        }
    }

    private void sample(int x, int z) {
        int pixel = 0;
        if (level.hasChunk(x >> 4, z >> 4)) {
            int solid = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int canopy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int a = encode(surface(x, solid, z));
            int b = encode(surface(x, canopy, z));
             
            pixel = (a & 255) | ((a >> 8) << 8) | ((b & 255) << 16) | ((b >> 8) << 24);
        }
        image.setPixelABGR(Math.floorMod(x, SIZE), Math.floorMod(z, SIZE), pixel);
    }

    private double surface(int x, int height, int z) {
        int min = level.getMinY();
        for (int y = height - 1; y >= Math.max(min, height - 24); y--) {
            cursor.set(x, y, z);
            VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            double top = shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
            if (!level.getFluidState(cursor).isEmpty())
                top = Math.max(top, level.getFluidState(cursor).getHeight(level, cursor));
            if (top > 0.0) return y + top;
        }
        return height;
    }

    private int encode(double height) {
        return Mth.clamp((int)Math.round((height - level.getMinY() + 1.0) * HEIGHT_UNITS), 1, 65535);
    }

    @Override
    public void close() {
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
        if (image != null) { image.close(); image = null; }
        level = null;
        lastTick = Long.MIN_VALUE;
        refreshRow = 0;
    }
}
