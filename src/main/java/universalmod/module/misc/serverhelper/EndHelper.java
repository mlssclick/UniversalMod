package universalmod.module.misc.serverhelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.Vec3d;
import universalmod.render.Render3D;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.NumberSetting;
import universalmod.theme.ThemeColors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EndHelper {
    private static final int MAX_CACHED_CHUNKS = 6000;
    private static final double HISTORY_RENDER_DISTANCE_SQ = 9216.0D; 
    private static final float HISTORY_TOP_OFFSET = 0.05F;

    private static final float TRAJECTORY_MAX_DISTANCE = 150.0F;
    private static final float TRAJECTORY_FORWARD_STEP = 6.0F;
    private static final float TRAJECTORY_MARK_STEP = 10.0F;
    private static final double TRAJECTORY_HALF_MARK = 1.5D;
    private static final double VERTICAL_THRESHOLD = 1.5D;
    private static final double VERTICAL_STEP = 3.0D;

    private final BooleanSetting trajectory;
    private final NumberSetting trajectoryY;
    private final BooleanSetting history;
    private final ColorSetting historyColor;
    private final NumberSetting historyOpacity;

    private ClientWorld lastLevel;
    private final LinkedHashMap<Long, float[]> chunkHistory = new LinkedHashMap<>();
    private float smoothedYaw;
    private boolean yawInitialized;
    private long lastTrajectoryNanos;

    public EndHelper(BooleanSetting trajectory, NumberSetting trajectoryY,
                     BooleanSetting history, ColorSetting historyColor, NumberSetting historyOpacity) {
        this.trajectory = trajectory;
        this.trajectoryY = trajectoryY;
        this.history = history;
        this.historyColor = historyColor;
        this.historyOpacity = historyOpacity;
    }

    public void onTick(MinecraftClient client) {
        ClientWorld level = client == null ? null : client.world;
        if (level != lastLevel) {
            clearRuntimeState();
            lastLevel = level;
        }
        if (level == null || client.player == null || !isEnd(level)) {
            return;
        }
        if (history.getValue()) {
            scanNearbyChunks(client, level);
        }
    }

    public void render(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null || !isEnd(client.world)) {
            return;
        }
        Vec3d cameraPos = Render3D.lastCameraPos;
        if (history.getValue() && !chunkHistory.isEmpty()) {
            renderHistory(cameraPos);
        }
        if (trajectory.getValue()) {
            renderTrajectory(client, cameraPos);
        }
    }

    private boolean isEnd(World level) {
        return level != null && level.getRegistryKey() == World.END;
    }

    public void reset() {
        clearRuntimeState();
        lastLevel = null;
        lastTrajectoryNanos = 0L;
    }

    private void clearRuntimeState() {
        chunkHistory.clear();
        yawInitialized = false;
    }

    private void scanNearbyChunks(MinecraftClient client, ClientWorld level) {
        int playerChunkX = MathHelper.floor(client.player.getX()) >> 4;
        int playerChunkZ = MathHelper.floor(client.player.getZ()) >> 4;

        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                int chunkX = playerChunkX + offsetX;
                int chunkZ = playerChunkZ + offsetZ;
                long key = ChunkPos.toLong(chunkX, chunkZ);
                if (chunkHistory.containsKey(key)) {
                    continue;
                }

                Chunk loaded = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (!(loaded instanceof WorldChunk chunk)) {
                    continue;
                }

                chunkHistory.put(key, scanChunk(chunk));
                if (chunkHistory.size() > MAX_CACHED_CHUNKS) {
                    Iterator<Long> iterator = chunkHistory.keySet().iterator();
                    if (iterator.hasNext()) {
                        iterator.next();
                        iterator.remove();
                    }
                }
            }
        }
    }

    private static float[] scanChunk(WorldChunk chunk) {
        List<float[]> segments = new ArrayList<>();
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        int baseX = chunk.getPos().getStartX();
        int baseZ = chunk.getPos().getStartZ();
        int minY = chunk.getBottomY();

        for (int localZ = 0; localZ < 16; localZ++) {
            int runStartX = -1;
            int previousHeight = Integer.MIN_VALUE;

            for (int localX = 0; localX <= 16; localX++) {
                int height = Integer.MIN_VALUE;
                if (localX < 16) {
                    int surfaceHeight = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, localX, localZ);
                    if (surfaceHeight > minY) {
                        mutablePos.set(baseX + localX, surfaceHeight - 1, baseZ + localZ);
                        if (chunk.getBlockState(mutablePos).isOf(Blocks.END_STONE)) {
                            height = surfaceHeight;
                        }
                    }
                }

                if (height != previousHeight) {
                    if (runStartX >= 0 && previousHeight != Integer.MIN_VALUE) {
                        segments.add(new float[]{
                                baseX + runStartX,
                                baseX + localX,
                                baseZ + localZ,
                                previousHeight + HISTORY_TOP_OFFSET
                        });
                    }
                    runStartX = localX;
                    previousHeight = height;
                }
            }
        }

        float[] flattened = new float[segments.size() * 4];
        for (int i = 0; i < segments.size(); i++) {
            System.arraycopy(segments.get(i), 0, flattened, i * 4, 4);
        }
        return flattened;
    }

    private void renderHistory(Vec3d cameraPos) {
        int alpha = MathHelper.clamp(Math.round(historyOpacity.getFloat() / 100.0F * 255.0F), 8, 220);
        int rgb = historyColor.getValue() & 0x00FFFFFF;
        int color = rgb | (alpha << 24);

        for (Map.Entry<Long, float[]> entry : chunkHistory.entrySet()) {
            float[] segments = entry.getValue();
            if (segments.length == 0) {
                continue;
            }

            long key = entry.getKey();
            double centerX = (ChunkPos.getPackedX(key) << 4) + 8.0D;
            double centerZ = (ChunkPos.getPackedZ(key) << 4) + 8.0D;
            double dx = centerX - cameraPos.x;
            double dz = centerZ - cameraPos.z;
            if (dx * dx + dz * dz > HISTORY_RENDER_DISTANCE_SQ) {
                continue;
            }

            for (int i = 0; i < segments.length; i += 4) {
                double x1 = segments[i];
                double x2 = segments[i + 1];
                double z = segments[i + 2];
                double y = segments[i + 3];
                Render3D.drawQuad(
                        new Vec3d(x1, y, z),
                        new Vec3d(x1, y + 1.0D, z),
                        new Vec3d(x2, y + 1.0D, z),
                        new Vec3d(x2, y, z),
                        color,
                        true
                );
            }
        }
    }

    private void renderTrajectory(MinecraftClient client, Vec3d cameraPos) {
        long now = System.nanoTime();
        float deltaTime = lastTrajectoryNanos == 0L
                ? 0.016F
                : MathHelper.clamp((now - lastTrajectoryNanos) / 1_000_000_000.0F, 0.0F, 0.1F);
        lastTrajectoryNanos = now;

        float currentYaw = client.player.getYaw();
        if (!yawInitialized) {
            smoothedYaw = currentYaw;
            yawInitialized = true;
        } else {
            smoothedYaw += MathHelper.wrapDegrees(currentYaw - smoothedYaw) * Math.min(1.0F, deltaTime * 6.0F);
        }

        float radians = smoothedYaw * ((float) Math.PI / 180.0F);
        double directionX = -MathHelper.sin(radians);
        double directionZ = MathHelper.cos(radians);
        double sideX = -directionZ;
        double sideZ = directionX;
        double targetY = trajectoryY.getFloat();

        int accent = ThemeColors.hudAccentColor(255);
        int red = (accent >> 16) & 0xFF;
        int green = (accent >> 8) & 0xFF;
        int blue = accent & 0xFF;

        for (float distance = 6.0F; distance < TRAJECTORY_MAX_DISTANCE; distance += TRAJECTORY_FORWARD_STEP) {
            float t = distance / TRAJECTORY_MAX_DISTANCE;
            int alpha = Math.round(200.0F * (1.0F - 0.75F * t));
            int color = argb(alpha, red, green, blue);

            emitRibbonSegment(
                    cameraPos,
                    cameraPos.x + directionX * distance,
                    targetY,
                    cameraPos.z + directionZ * distance,
                    cameraPos.x + directionX * (distance + TRAJECTORY_FORWARD_STEP),
                    targetY,
                    cameraPos.z + directionZ * (distance + TRAJECTORY_FORWARD_STEP),
                    1.0F,
                    color
            );
        }

        for (float distance = 10.0F; distance <= TRAJECTORY_MAX_DISTANCE; distance += TRAJECTORY_MARK_STEP) {
            float t = distance / TRAJECTORY_MAX_DISTANCE;
            int alpha = Math.round(160.0F * (1.0F - 0.75F * t));
            int color = argb(alpha, red, green, blue);
            double centerX = cameraPos.x + directionX * distance;
            double centerZ = cameraPos.z + directionZ * distance;

            emitRibbonSegment(
                    cameraPos,
                    centerX - sideX * TRAJECTORY_HALF_MARK,
                    targetY,
                    centerZ - sideZ * TRAJECTORY_HALF_MARK,
                    centerX + sideX * TRAJECTORY_HALF_MARK,
                    targetY,
                    centerZ + sideZ * TRAJECTORY_HALF_MARK,
                    0.6F,
                    color
            );
        }

        double verticalX = cameraPos.x + directionX * 10.0D;
        double verticalZ = cameraPos.z + directionZ * 10.0D;
        double playerY = client.player.getY();
        if (Math.abs(playerY - targetY) > VERTICAL_THRESHOLD) {
            double minY = Math.min(playerY, targetY);
            double maxY = Math.max(playerY, targetY);
            int verticalColor = argb(110, red, green, blue);

            for (double y = minY; y < maxY; y += VERTICAL_STEP) {
                double nextY = Math.min(y + 1.5D, maxY);
                emitRibbonSegment(
                        cameraPos,
                        verticalX, y, verticalZ,
                        verticalX, nextY, verticalZ,
                        0.5F,
                        verticalColor
                );
            }
        }
    }

    
    private static void emitRibbonSegment(Vec3d cameraPos,
                                          double x1, double y1, double z1,
                                          double x2, double y2, double z2,
                                          float baseWidth,
                                          int color) {
        float rx1 = (float) (x1 - cameraPos.x);
        float ry1 = (float) (y1 - cameraPos.y);
        float rz1 = (float) (z1 - cameraPos.z);
        float rx2 = (float) (x2 - cameraPos.x);
        float ry2 = (float) (y2 - cameraPos.y);
        float rz2 = (float) (z2 - cameraPos.z);

        float segmentX = rx2 - rx1;
        float segmentY = ry2 - ry1;
        float segmentZ = rz2 - rz1;
        float midpointX = (rx1 + rx2) * 0.5F;
        float midpointY = (ry1 + ry2) * 0.5F;
        float midpointZ = (rz1 + rz2) * 0.5F;

        float normalX = segmentY * midpointZ - segmentZ * midpointY;
        float normalY = segmentZ * midpointX - segmentX * midpointZ;
        float normalZ = segmentX * midpointY - segmentY * midpointX;
        float normalLength = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (normalLength < 0.0001F) {
            return;
        }

        float midpointDistance = (float) Math.sqrt(midpointX * midpointX + midpointY * midpointY + midpointZ * midpointZ);
        float width = (0.05F + midpointDistance * 0.006F) * baseWidth;
        normalX = normalX / normalLength * width;
        normalY = normalY / normalLength * width;
        normalZ = normalZ / normalLength * width;

        Render3D.drawQuad(
                new Vec3d(x1 - normalX, y1 - normalY, z1 - normalZ),
                new Vec3d(x1 + normalX, y1 + normalY, z1 + normalZ),
                new Vec3d(x2 + normalX, y2 + normalY, z2 + normalZ),
                new Vec3d(x2 - normalX, y2 - normalY, z2 - normalZ),
                color,
                false
        );
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
