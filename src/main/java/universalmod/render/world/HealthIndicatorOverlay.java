package universalmod.render.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import org.joml.Quaternionf;
import universalmod.event.type.WorldRenderEvent;
import universalmod.render.ClientPipelines;
import universalmod.render.Render3D;
import universalmod.render.WorldVertex;
import universalmod.render.color.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public final class HealthIndicatorOverlay {
    private static final Identifier HEART_EMPTY = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/container.png");
    private static final Identifier HEART_RED_FULL = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/full.png");
    private static final Identifier HEART_RED_HALF = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/half.png");
    private static final Identifier HEART_YELLOW_FULL = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/absorbing_full.png");
    private static final Identifier HEART_YELLOW_HALF = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/absorbing_half.png");
    private static final RenderType HEART_EMPTY_TYPE = ClientPipelines.WORLD_HEALTH_ICONS.apply(HEART_EMPTY);
    private static final RenderType HEART_RED_FULL_TYPE = ClientPipelines.WORLD_HEALTH_ICONS.apply(HEART_RED_FULL);
    private static final RenderType HEART_RED_HALF_TYPE = ClientPipelines.WORLD_HEALTH_ICONS.apply(HEART_RED_HALF);
    private static final RenderType HEART_YELLOW_FULL_TYPE = ClientPipelines.WORLD_HEALTH_ICONS.apply(HEART_YELLOW_FULL);
    private static final RenderType HEART_YELLOW_HALF_TYPE = ClientPipelines.WORLD_HEALTH_ICONS.apply(HEART_YELLOW_HALF);
    private static final int PASS_EMPTY = 0;
    private static final int PASS_RED_FULL = 1;
    private static final int PASS_RED_HALF = 2;
    private static final int PASS_YELLOW_FULL = 3;
    private static final int PASS_YELLOW_HALF = 4;
    private static final float PIXEL_SIZE = 0.025F;
    private static final float HEART_SIZE = 9.0F;
    private static final int MAX_HEARTS_PER_ROW = 10;
    private static final int WHITE = ColorUtil.rgba(255, 255, 255, 255);
    private static final double MAX_DISTANCE_SQ = 64.0D * 64.0D;
    private static final Quaternionf Y_180 = Axis.YP.rotationDegrees(180.0F);

    private final Quaternionf cameraRotation = new Quaternionf();
    private final List<Player> visiblePlayers = new ArrayList<>();

    public void render(WorldRenderEvent event, Minecraft minecraft) {
        if (event == null || minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.gameRenderer == null) {
            return;
        }

        visiblePlayers.clear();
        for (Player player : minecraft.level.players()) {
            if (shouldRenderPlayer(minecraft, player)) {
                visiblePlayers.add(player);
            }
        }
        if (visiblePlayers.isEmpty()) {
            return;
        }

        PoseStack stack = event.getStack();
        MultiBufferSource.BufferSource provider = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = resolveCameraPos(minecraft);
        Quaternionf rotation = resolveCameraRotation(minecraft);
        float tickDelta = event.getPartialTicks();
        boolean belowNameScoreboard = hasBelowNameScoreboard(minecraft);

        try {
            renderPass(stack, provider, cameraPos, rotation, tickDelta, belowNameScoreboard, PASS_EMPTY, HEART_EMPTY_TYPE);
            renderPass(stack, provider, cameraPos, rotation, tickDelta, belowNameScoreboard, PASS_RED_FULL, HEART_RED_FULL_TYPE);
            renderPass(stack, provider, cameraPos, rotation, tickDelta, belowNameScoreboard, PASS_RED_HALF, HEART_RED_HALF_TYPE);
            renderPass(stack, provider, cameraPos, rotation, tickDelta, belowNameScoreboard, PASS_YELLOW_FULL, HEART_YELLOW_FULL_TYPE);
            renderPass(stack, provider, cameraPos, rotation, tickDelta, belowNameScoreboard, PASS_YELLOW_HALF, HEART_YELLOW_HALF_TYPE);
        } finally {
            visiblePlayers.clear();
        }
    }

    private boolean shouldRenderPlayer(Minecraft minecraft, Player player) {
        if (player == null || player.isRemoved() || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        if (player == minecraft.player && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            return false;
        }
        if (minecraft.player.distanceToSqr(player) > MAX_DISTANCE_SQ) {
            return false;
        }
        return !player.isInvisible() || isVisibleByRaytrace(minecraft, player);
    }

    private boolean isVisibleByRaytrace(Minecraft minecraft, Player player) {
        BlockHitResult hit = minecraft.level.clip(new ClipContext(
                minecraft.player.getEyePosition(1.0F),
                player.getEyePosition(1.0F),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                minecraft.player
        ));
        return hit == null || hit.getType() != HitResult.Type.BLOCK;
    }

    private void renderPass(
            PoseStack stack,
            MultiBufferSource.BufferSource provider,
            Vec3 cameraPos,
            Quaternionf rotation,
            float tickDelta,
            boolean belowNameScoreboard,
            int pass,
            RenderType renderType
    ) {
        VertexConsumer consumer = null;

        for (Player player : visiblePlayers) {
            int healthRed = Mth.ceil(player.getHealth());
            int maxHealth = Mth.ceil(player.getMaxHealth());
            int healthYellow = Mth.ceil(player.getAbsorptionAmount());
            int heartsNormal = Mth.ceil((float) maxHealth / 2.0F);
            int heartsRed = Math.min(Mth.ceil((float) healthRed / 2.0F), heartsNormal);
            int heartsYellow = Mth.ceil((float) healthYellow / 2.0F);
            int heartsTotal = heartsNormal + heartsYellow;
            if (heartsTotal <= 0) {
                continue;
            }

            boolean lastRedHalf = (healthRed & 1) == 1;
            boolean lastYellowHalf = (healthYellow & 1) == 1;
            int redFullCount = heartsRed - (lastRedHalf && heartsRed > 0 ? 1 : 0);
            int yellowFullCount = heartsYellow - (lastYellowHalf && heartsYellow > 0 ? 1 : 0);
            int startHeart;
            int count;

            switch (pass) {
                case PASS_EMPTY -> {
                    startHeart = 0;
                    count = heartsTotal;
                }
                case PASS_RED_FULL -> {
                    startHeart = 0;
                    count = redFullCount;
                }
                case PASS_RED_HALF -> {
                    startHeart = heartsRed - 1;
                    count = lastRedHalf && heartsRed > 0 ? 1 : 0;
                }
                case PASS_YELLOW_FULL -> {
                    startHeart = heartsNormal;
                    count = yellowFullCount;
                }
                case PASS_YELLOW_HALF -> {
                    startHeart = heartsTotal - 1;
                    count = lastYellowHalf && heartsYellow > 0 ? 1 : 0;
                }
                default -> {
                    continue;
                }
            }

            if (count <= 0) {
                continue;
            }
            if (consumer == null) {
                consumer = provider.getBuffer(renderType);
            }

            appendRange(
                    consumer,
                    stack,
                    player,
                    cameraPos,
                    rotation,
                    tickDelta,
                    belowNameScoreboard,
                    startHeart,
                    count,
                    heartsTotal,
                    pass == PASS_EMPTY ? 0.0F : 0.0001F
            );
        }

        if (consumer != null) {
            provider.endBatch(renderType);
        }
    }

    private void appendRange(
            VertexConsumer consumer,
            PoseStack stack,
            Player player,
            Vec3 cameraPos,
            Quaternionf rotation,
            float tickDelta,
            boolean belowNameScoreboard,
            int startHeart,
            int count,
            int heartsTotal,
            float layerOffset
    ) {
        int rowsTotal = (heartsTotal + MAX_HEARTS_PER_ROW - 1) / MAX_HEARTS_PER_ROW;
        int rowOffset = rowsTotal > 1 ? 8 : 10;
        double x = Mth.lerp(tickDelta, player.xOld, player.getX());
        double y = Mth.lerp(tickDelta, player.yOld, player.getY());
        double z = Mth.lerp(tickDelta, player.zOld, player.getZ());
        double dx = x - cameraPos.x;
        double dy = y - cameraPos.y;
        double dz = z - cameraPos.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double yOffset = player.getBbHeight() + 0.65F;
        if (distanceSq <= 4096.0D) {
            yOffset += 0.25875D;
            if (distanceSq < 100.0D && belowNameScoreboard) {
                yOffset += 0.25875D;
            }
        }

        stack.pushPose();
        stack.translate(dx, y + yOffset - cameraPos.y, dz);
        stack.mulPose(rotation);
        stack.mulPose(Y_180);
        PoseStack.Pose pose = stack.last();
        int endHeart = startHeart + count;

        for (int heart = startHeart; heart < endHeart; heart++) {
            int row = heart / MAX_HEARTS_PER_ROW;
            int col = heart % MAX_HEARTS_PER_ROW;
            int rowCount = Math.min(MAX_HEARTS_PER_ROW, heartsTotal - row * MAX_HEARTS_PER_ROW);
            float rowWidth = (rowCount - 1) * 8.0F + HEART_SIZE;
            float heartX = rowWidth * 0.5F - col * 8.0F;
            float heartY = row * (float) rowOffset;
            float heartZ = row * 0.00025F + layerOffset;
            drawHeart(pose, consumer, heartX, heartY, heartZ);
        }

        stack.popPose();
    }

    private void drawHeart(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z) {
        float x0 = x * PIXEL_SIZE;
        float x1 = (x - HEART_SIZE) * PIXEL_SIZE;
        float y0 = y * PIXEL_SIZE;
        float y1 = (y - HEART_SIZE) * PIXEL_SIZE;
        WorldVertex.textured(consumer, pose, x0, y1, z, 0.0F, 1.0F, WHITE);
        WorldVertex.textured(consumer, pose, x1, y1, z, 1.0F, 1.0F, WHITE);
        WorldVertex.textured(consumer, pose, x1, y0, z, 1.0F, 0.0F, WHITE);
        WorldVertex.textured(consumer, pose, x0, y0, z, 0.0F, 0.0F, WHITE);
    }

    private boolean hasBelowNameScoreboard(Minecraft minecraft) {
        return minecraft.level != null
                && minecraft.level.getScoreboard() != null
                && minecraft.level.getScoreboard().getDisplayObjective(DisplaySlot.BELOW_NAME) != null;
    }

    private Vec3 resolveCameraPos(Minecraft minecraft) {
        Vec3 cameraPos = Render3D.lastCameraPos;
        if (cameraPos != null) {
            return cameraPos;
        }
        return minecraft.gameRenderer.getMainCamera().position();
    }

    private Quaternionf resolveCameraRotation(Minecraft minecraft) {
        Quaternionf rotation = Render3D.lastCameraRotation;
        if (rotation != null) {
            return cameraRotation.set(rotation);
        }
        return cameraRotation.set(minecraft.gameRenderer.getMainCamera().rotation());
    }
}
