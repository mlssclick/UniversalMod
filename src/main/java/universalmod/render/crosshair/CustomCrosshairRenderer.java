package universalmod.render.crosshair;

import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Color;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class CustomCrosshairRenderer {
    private CustomCrosshairRenderer() {
    }

    public static boolean shouldRender(MinecraftClient client, CrosshairRenderState state) {
        if (client == null || state == null || !state.enabled()) return false;
        if (client.options == null || client.options.hudHidden) return false;
        if (client.player == null || client.world == null) return false;
        return client.options.getPerspective().isFirstPerson() || state.displayInThirdPerson();
    }

    public static void render(DrawContext context, MinecraftClient client, CrosshairRenderState state) {
        if (context == null || !shouldRender(client, state)) return;
        CrosshairCanvas canvas = state.canvas();
        int originX = client.getWindow().getScaledWidth() / 2 - CrosshairCanvas.SIZE / 2;
        int originY = client.getWindow().getScaledHeight() / 2 - CrosshairCanvas.SIZE / 2;
        if (state.vanillaBlending()) {
            renderVanillaBlended(context, canvas, originX, originY);
            return;
        }
        int color = resolveDynamicColor(client, state).getRGB();
        for (int y = 0; y < CrosshairCanvas.SIZE; y++) {
            for (int x = 0; x < CrosshairCanvas.SIZE; x++) {
                if (canvas.isPixelActive(x, y)) {
                    context.fill(originX + x, originY + y, originX + x + 1, originY + y + 1, color);
                }
            }
        }
    }

    private static void renderVanillaBlended(DrawContext context, CrosshairCanvas canvas, int originX, int originY) {
        boolean oldBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean oldDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int oldSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int oldDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int oldSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int oldDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(775, 769, 1, 0);
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            boolean hasPixels = false;
            for (int y = 0; y < CrosshairCanvas.SIZE; y++) {
                for (int x = 0; x < CrosshairCanvas.SIZE; x++) {
                    if (!canvas.isPixelActive(x, y)) continue;
                    float left = originX + x;
                    float top = originY + y;
                    float right = left + 1.0F;
                    float bottom = top + 1.0F;
                    buffer.vertex(matrix, left, bottom, 0.0F).color(255, 255, 255, 255).next();
                    buffer.vertex(matrix, right, bottom, 0.0F).color(255, 255, 255, 255).next();
                    buffer.vertex(matrix, right, top, 0.0F).color(255, 255, 255, 255).next();
                    buffer.vertex(matrix, left, top, 0.0F).color(255, 255, 255, 255).next();
                    hasPixels = true;
                }
            }
            if (hasPixels) BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (oldDepth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (oldBlend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(oldSrcRgb, oldDstRgb, oldSrcAlpha, oldDstAlpha);
        }
    }

    private static Color resolveDynamicColor(MinecraftClient client, CrosshairRenderState state) {
        Color base = state.crosshairColor();
        if (!state.dynamicColor()) return base;
        Entity target = null;
        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHitResult) target = entityHitResult.getEntity();
        if (!(target instanceof LivingEntity living)) return base;
        if (living instanceof PlayerEntity) return state.playersColor();
        if (living instanceof HostileEntity) return state.hostileColor();
        return state.neutralColor();
    }
}
