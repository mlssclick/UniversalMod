package universalmod.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import universalmod.event.type.WorldRenderEvent;
import universalmod.render.ClientPipelines;

import java.util.ArrayList;
import java.util.List;

public final class HealthIndicatorOverlay {
    private static final Identifier HEART_EMPTY = Identifier.ofVanilla("textures/gui/sprites/hud/heart/container.png");
    private static final Identifier HEART_RED_FULL = Identifier.ofVanilla("textures/gui/sprites/hud/heart/full.png");
    private static final Identifier HEART_RED_HALF = Identifier.ofVanilla("textures/gui/sprites/hud/heart/half.png");
    private static final Identifier HEART_YELLOW_FULL = Identifier.ofVanilla("textures/gui/sprites/hud/heart/absorbing_full.png");
    private static final Identifier HEART_YELLOW_HALF = Identifier.ofVanilla("textures/gui/sprites/hud/heart/absorbing_half.png");
    private static final int PASS_EMPTY = 0;
    private static final int PASS_RED_FULL = 1;
    private static final int PASS_RED_HALF = 2;
    private static final int PASS_YELLOW_FULL = 3;
    private static final int PASS_YELLOW_HALF = 4;
    private static final float PIXEL_SIZE = 0.025F;
    private static final float HEART_SIZE = 9.0F;
    private static final int MAX_HEARTS_PER_ROW = 10;
    private static final double MAX_DISTANCE_SQ = 64.0D * 64.0D;
    private static final Quaternionf Y_180 = RotationAxis.POSITIVE_Y.rotationDegrees(180.0F);

    private final Quaternionf cameraRotation = new Quaternionf();
    private final List<PlayerEntity> visiblePlayers = new ArrayList<>();

    public void render(WorldRenderEvent event, MinecraftClient client) {
        if (event == null || client == null || client.world == null || client.player == null || client.gameRenderer == null) {
            return;
        }

        visiblePlayers.clear();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (shouldRenderPlayer(client, player)) {
                visiblePlayers.add(player);
            }
        }
        if (visiblePlayers.isEmpty()) {
            return;
        }

        ShaderProgram shader = ClientPipelines.bind(ClientPipelines.HEALTH_ICON, "render_health_icon");
        if (shader == null) {
            visiblePlayers.clear();
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();
        Quaternionf rotation = cameraRotation.set(camera.getRotation());
        MatrixStack matrices = event.getMatrixStack();
        float tickDelta = event.getTickDelta();
        boolean belowNameScoreboard = hasBelowNameScoreboard(client);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        try {
            renderPass(matrices, cameraPos, rotation, tickDelta, belowNameScoreboard, PASS_EMPTY, HEART_EMPTY);
            renderPass(matrices, cameraPos, rotation, tickDelta, belowNameScoreboard, PASS_RED_FULL, HEART_RED_FULL);
            renderPass(matrices, cameraPos, rotation, tickDelta, belowNameScoreboard, PASS_RED_HALF, HEART_RED_HALF);
            renderPass(matrices, cameraPos, rotation, tickDelta, belowNameScoreboard, PASS_YELLOW_FULL, HEART_YELLOW_FULL);
            renderPass(matrices, cameraPos, rotation, tickDelta, belowNameScoreboard, PASS_YELLOW_HALF, HEART_YELLOW_HALF);
        } finally {
            visiblePlayers.clear();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private boolean shouldRenderPlayer(MinecraftClient client, PlayerEntity player) {
        if (player == null || player.isRemoved() || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        if (player == client.player && client.options.getPerspective() == Perspective.FIRST_PERSON) {
            return false;
        }
        if (client.player.squaredDistanceTo(player) > MAX_DISTANCE_SQ) {
            return false;
        }
        return !player.isInvisible() || isVisibleByRaytrace(client, player);
    }

    private boolean isVisibleByRaytrace(MinecraftClient client, PlayerEntity player) {
        HitResult hit = client.world.raycast(new RaycastContext(
                client.player.getEyePos(),
                player.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        return hit == null || hit.getType() != HitResult.Type.BLOCK;
    }

    private void renderPass(
            MatrixStack matrices,
            Vec3d cameraPos,
            Quaternionf rotation,
            float tickDelta,
            boolean belowNameScoreboard,
            int pass,
            Identifier texture
    ) {
        BufferBuilder buffer = null;
        for (PlayerEntity player : visiblePlayers) {
            int healthRed = MathHelper.ceil(player.getHealth());
            int maxHealth = MathHelper.ceil(player.getMaxHealth());
            int healthYellow = MathHelper.ceil(player.getAbsorptionAmount());
            int heartsNormal = MathHelper.ceil(maxHealth / 2.0F);
            int heartsRed = Math.min(MathHelper.ceil(healthRed / 2.0F), heartsNormal);
            int heartsYellow = MathHelper.ceil(healthYellow / 2.0F);
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
            if (buffer == null) {
                RenderSystem.setShaderTexture(0, texture);
                buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            }

            appendRange(
                    buffer,
                    matrices,
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

        if (buffer != null) {
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }

    private void appendRange(
            BufferBuilder buffer,
            MatrixStack matrices,
            PlayerEntity player,
            Vec3d cameraPos,
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
        double x = MathHelper.lerp(tickDelta, player.prevX, player.getX());
        double y = MathHelper.lerp(tickDelta, player.prevY, player.getY());
        double z = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());
        double dx = x - cameraPos.x;
        double dy = y - cameraPos.y;
        double dz = z - cameraPos.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double yOffset = player.getHeight() + 0.65F;
        if (distanceSq <= 4096.0D) {
            yOffset += 0.25875D;
            if (distanceSq < 100.0D && belowNameScoreboard) {
                yOffset += 0.25875D;
            }
        }

        matrices.push();
        matrices.translate(dx, y + yOffset - cameraPos.y, dz);
        matrices.multiply(rotation);
        matrices.multiply(Y_180);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int endHeart = startHeart + count;

        for (int heart = startHeart; heart < endHeart; heart++) {
            int row = heart / MAX_HEARTS_PER_ROW;
            int col = heart % MAX_HEARTS_PER_ROW;
            int rowCount = Math.min(MAX_HEARTS_PER_ROW, heartsTotal - row * MAX_HEARTS_PER_ROW);
            float rowWidth = (rowCount - 1) * 8.0F + HEART_SIZE;
            float heartX = rowWidth * 0.5F - col * 8.0F;
            float heartY = row * (float) rowOffset;
            float heartZ = row * 0.00025F + layerOffset;
            emitHeart(buffer, matrix, heartX, heartY, heartZ);
        }

        matrices.pop();
    }

    private void emitHeart(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z) {
        float x0 = x * PIXEL_SIZE;
        float x1 = (x - HEART_SIZE) * PIXEL_SIZE;
        float y0 = y * PIXEL_SIZE;
        float y1 = (y - HEART_SIZE) * PIXEL_SIZE;
        buffer.vertex(matrix, x0, y1, z).texture(0.0F, 1.0F).color(255, 255, 255, 255);
        buffer.vertex(matrix, x1, y1, z).texture(1.0F, 1.0F).color(255, 255, 255, 255);
        buffer.vertex(matrix, x1, y0, z).texture(1.0F, 0.0F).color(255, 255, 255, 255);
        buffer.vertex(matrix, x0, y0, z).texture(0.0F, 0.0F).color(255, 255, 255, 255);
    }

    private boolean hasBelowNameScoreboard(MinecraftClient client) {
        return client.world != null
                && client.world.getScoreboard() != null
                && client.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME) != null;
    }
}
