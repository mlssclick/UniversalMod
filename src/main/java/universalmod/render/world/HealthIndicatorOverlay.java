package universalmod.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import universalmod.event.type.WorldRenderEvent;

public final class HealthIndicatorOverlay {
    private static final Identifier ICONS = new Identifier("textures/gui/icons.png");
    private static final int U_CONTAINER = 16;
    private static final int U_RED_FULL = 52;
    private static final int U_RED_HALF = 61;
    private static final int U_ABSORB_FULL = 160;
    private static final int U_ABSORB_HALF = 169;
    private static final int V_HEART = 0;
    private static final float HEART = 9.0F;
    private static final float STEP = 8.0F;
    private static final float PIXEL = 0.025F;
    private static final int MAX_HEARTS_PER_ROW = 10;
    private static final double MAX_DISTANCE_SQ = 64.0D * 64.0D;

    private final Quaternionf cameraRotation = new Quaternionf();

    public void render(WorldRenderEvent event, MinecraftClient client) {
        if (event == null || client == null || client.world == null || client.player == null || client.gameRenderer == null) {
            return;
        }

        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Quaternionf rotation = cameraRotation.set(client.gameRenderer.getCamera().getRotation());
        MatrixStack matrices = event.getStack();
        boolean belowNameScoreboard = hasBelowNameScoreboard(client);
        BufferBuilder buffer = null;
        boolean rendering = false;

        try {
            for (PlayerEntity player : client.world.getPlayers()) {
                if (!shouldRender(client, player)) {
                    continue;
                }

                if (buffer == null) {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableCull();
                    RenderSystem.disableDepthTest();
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                    RenderSystem.setShaderTexture(0, ICONS);
                    buffer = Tessellator.getInstance().getBuffer();
                    buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                    rendering = true;
                }

                appendPlayer(buffer, matrices, player, camera, rotation, event.getPartialTicks(), belowNameScoreboard);
            }

            if (buffer != null) {
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
        } finally {
            if (rendering) {
                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    private boolean shouldRender(MinecraftClient client, PlayerEntity player) {
        if (player == null || player.isRemoved() || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        if (player == client.player && client.options.getPerspective().isFirstPerson()) {
            return false;
        }
        if (client.player.squaredDistanceTo(player) > MAX_DISTANCE_SQ) {
            return false;
        }
        return !player.isInvisible() || visible(client, player);
    }

    private boolean visible(MinecraftClient client, PlayerEntity player) {
        if (client.world == null || client.player == null) {
            return false;
        }
        BlockHitResult hit = client.world.raycast(new RaycastContext(
                client.player.getEyePos(),
                player.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        return hit == null || hit.getType() != HitResult.Type.BLOCK;
    }

    private void appendPlayer(
            BufferBuilder buffer,
            MatrixStack matrices,
            PlayerEntity player,
            Vec3d camera,
            Quaternionf rotation,
            float tickDelta,
            boolean belowNameScoreboard
    ) {
        int health = MathHelper.ceil(player.getHealth());
        int maxHealth = MathHelper.ceil(player.getMaxHealth());
        int absorption = MathHelper.ceil(player.getAbsorptionAmount());
        int normalHearts = MathHelper.ceil(maxHealth / 2.0F);
        int redHearts = Math.min(MathHelper.ceil(health / 2.0F), normalHearts);
        int absorptionHearts = MathHelper.ceil(absorption / 2.0F);
        int total = normalHearts + absorptionHearts;
        if (total <= 0) {
            return;
        }

        boolean redHalf = (health & 1) == 1;
        boolean absorptionHalf = (absorption & 1) == 1;
        int rows = (total + MAX_HEARTS_PER_ROW - 1) / MAX_HEARTS_PER_ROW;
        double x = MathHelper.lerp(tickDelta, player.prevX, player.getX());
        double baseY = MathHelper.lerp(tickDelta, player.prevY, player.getY());
        double z = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());
        double dx = x - camera.x;
        double dy = baseY - camera.y;
        double dz = z - camera.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double yOffset = player.getHeight() + 0.65D;
        if (distanceSq <= 4096.0D) {
            yOffset += 0.25875D;
            if (distanceSq < 100.0D && belowNameScoreboard) {
                yOffset += 0.25875D;
            }
        }

        matrices.push();
        matrices.translate(x - camera.x, baseY + yOffset - camera.y, z - camera.z);
        matrices.multiply(rotation);
        matrices.scale(PIXEL, -PIXEL, PIXEL);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (int i = 0; i < total; i++) {
            int logicalRow = i / MAX_HEARTS_PER_ROW;
            int visualRow = rows - 1 - logicalRow;
            int col = i % MAX_HEARTS_PER_ROW;
            int count = Math.min(MAX_HEARTS_PER_ROW, total - logicalRow * MAX_HEARTS_PER_ROW);
            float rowWidth = (count - 1) * STEP + HEART;
            float px = col * STEP - rowWidth * 0.5F;
            float py = visualRow * (rows > 1 ? 8.0F : 10.0F);
            heart(buffer, matrix, px, py, 0.0F, U_CONTAINER);

            if (i < redHearts) {
                int u = i == redHearts - 1 && redHalf ? U_RED_HALF : U_RED_FULL;
                heart(buffer, matrix, px, py, -0.01F, u);
            } else if (i >= normalHearts) {
                int u = i == total - 1 && absorptionHalf ? U_ABSORB_HALF : U_ABSORB_FULL;
                heart(buffer, matrix, px, py, -0.01F, u);
            }
        }

        matrices.pop();
    }

    private boolean hasBelowNameScoreboard(MinecraftClient client) {
        return client.world != null
                && client.world.getScoreboard() != null
                && client.world.getScoreboard().getObjectiveForSlot(net.minecraft.scoreboard.Scoreboard.BELOW_NAME_DISPLAY_SLOT_ID) != null;
    }

    private static void heart(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, int u) {
        float u0 = u / 256.0F;
        float u1 = (u + 9) / 256.0F;
        float v0 = V_HEART / 256.0F;
        float v1 = (V_HEART + 9) / 256.0F;
        buffer.vertex(matrix, x, y + HEART, z).texture(u0, v1).color(255, 255, 255, 255).next();
        buffer.vertex(matrix, x + HEART, y + HEART, z).texture(u1, v1).color(255, 255, 255, 255).next();
        buffer.vertex(matrix, x + HEART, y, z).texture(u1, v0).color(255, 255, 255, 255).next();
        buffer.vertex(matrix, x, y, z).texture(u0, v0).color(255, 255, 255, 255).next();
    }
}
