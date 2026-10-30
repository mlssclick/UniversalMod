package universalmod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class GlowPropagator {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Field EMPTY = new Field(0, 0, 0, 0, new byte[0]);
    private static volatile Field current = EMPTY;

    private GlowPropagator() {
    }

    private static int toIndex(int x, int y, int z, int size) {
        return (z * size + y) * size + x;
    }

    public static int applyBlockLight(BlockPos pos, int packedLight) {
        int oldBlock = packedLight >> 4 & 15;
        int glow = current.compute(pos);
        if (glow <= oldBlock) return packedLight;
        return packedLight & -241 | glow << 4;
    }

    public static int getGlowLight(BlockPos pos, int light) {
        return Math.max(light, current.compute(pos));
    }

    public static void propagate(BlockAndTintGetter world, BlockPos center, float radius, int light) {
        int r = Mth.ceil(radius);
        if (r <= 0 || light <= 0) {
            clear();
            return;
        }
        int size = r * 2 + 1;
        int total = size * size * size;
        int originX = center.getX() - r;
        int originY = center.getY() - r;
        int originZ = center.getZ() - r;
        byte[] values = new byte[total];
        byte[] distances = new byte[total];
        boolean[] visited = new boolean[total];
        int[] queue = new int[total];
        int start = toIndex(r, r, r, size);
        int head = 0;
        int tail = 1;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        queue[0] = start;
        visited[start] = true;
        while (head < tail) {
            int index = queue[head++];
            int x = index % size;
            int y = index / size % size;
            int z = index / (size * size);
            int distance = Byte.toUnsignedInt(distances[index]);
            values[index] = (byte)Mth.clamp(Math.round(light * Math.max(0.0f, radius + 1.0f - distance) / (radius + 1.0f)), 0, 15);
            if (distance >= r) continue;
            for (Direction direction : DIRECTIONS) {
                int nx = x + direction.getStepX();
                int ny = y + direction.getStepY();
                int nz = z + direction.getStepZ();
                if (nx < 0 || ny < 0 || nz < 0 || nx >= size || ny >= size || nz >= size) continue;
                int next = toIndex(nx, ny, nz, size);
                if (visited[next]) continue;
                int nextDistance = distance + 1;
                if (nextDistance > radius) continue;
                visited[next] = true;
                distances[next] = (byte)nextDistance;
                values[next] = (byte)Mth.clamp(Math.round(light * Math.max(0.0f, radius + 1.0f - nextDistance) / (radius + 1.0f)), 0, 15);
                mutable.set(originX + nx, originY + ny, originZ + nz);
                BlockState state = world.getBlockState(mutable);
                if (state.isSolidRender()) continue;
                queue[tail++] = next;
            }
        }
        current = new Field(originX, originY, originZ, size, values);
    }

    public static void clear() {
        current = EMPTY;
    }

    private static final class Field {
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int size;
        private final byte[] values;

        private Field(int originX, int originY, int originZ, int size, byte[] values) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.size = size;
            this.values = values;
        }

        private int compute(BlockPos pos) {
            int x = pos.getX() - originX;
            int y = pos.getY() - originY;
            int z = pos.getZ() - originZ;
            if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size) return 0;
            return Byte.toUnsignedInt(values[toIndex(x, y, z, size)]);
        }
    }
}
