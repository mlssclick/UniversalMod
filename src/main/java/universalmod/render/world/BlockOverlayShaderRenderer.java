package universalmod.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import universalmod.render.UiShaders;

import java.util.List;

public final class BlockOverlayShaderRenderer {
    public enum ShaderMode {
        NONE, NEBULA, COBWEB, PLASMA, STARFIELD;

        public static ShaderMode fromSetting(String s) {
            if (s == null) return NONE;
            try {
                return valueOf(s.toUpperCase());
            } catch (Exception ignored) {
                return NONE;
            }
        }
    }

    public boolean renderFill(MatrixStack matrices, Vec3d origin, List<Box> localBoxes, ShaderMode mode, int color, float alpha, float speed, boolean throughWalls) {
        if (matrices == null || origin == null || localBoxes == null || localBoxes.isEmpty() || mode == ShaderMode.NONE || alpha <= .001f)
            return false;
        ShaderProgram shader = UiShaders.bind(UiShaders.BLOCK_OVERLAY, "block_overlay");
        if (shader == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        try {
            var ss = shader.getUniform("ScreenSize");
            if (ss != null)
                ss.set((float) mc.getWindow().getFramebufferWidth(), (float) mc.getWindow().getFramebufferHeight());
            var time = shader.getUniform("Time");
            if (time != null) time.set((System.currentTimeMillis() % 1_000_000L) / 1000f * Math.max(0f, speed));
            var mo = shader.getUniform("Mode");
            if (mo != null) mo.set((float) mode.ordinal());
            var tint = shader.getUniform("Tint");
            if (tint != null)
                tint.set(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, Math.max(0, Math.min(1, alpha)) * ((color >>> 24) & 255) / 255f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            if (throughWalls) RenderSystem.disableDepthTest();
            else RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (Box local : localBoxes) {
                Box box = local.offset(origin);
                box(b, matrix, box);
            }
            BufferRenderer.drawWithGlobalProgram(b.end());
            return true;
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            matrices.pop();
        }
    }

    private static void box(BufferBuilder b, Matrix4f m, Box q) {
        float x1 = (float) q.minX, y1 = (float) q.minY, z1 = (float) q.minZ, x2 = (float) q.maxX, y2 = (float) q.maxY, z2 = (float) q.maxZ;
        v(b, m, x1, y1, z2);
        v(b, m, x2, y1, z2);
        v(b, m, x2, y2, z2);
        v(b, m, x1, y2, z2);
        v(b, m, x2, y1, z1);
        v(b, m, x1, y1, z1);
        v(b, m, x1, y2, z1);
        v(b, m, x2, y2, z1);
        v(b, m, x1, y1, z1);
        v(b, m, x1, y1, z2);
        v(b, m, x1, y2, z2);
        v(b, m, x1, y2, z1);
        v(b, m, x2, y1, z2);
        v(b, m, x2, y1, z1);
        v(b, m, x2, y2, z1);
        v(b, m, x2, y2, z2);
        v(b, m, x1, y2, z2);
        v(b, m, x2, y2, z2);
        v(b, m, x2, y2, z1);
        v(b, m, x1, y2, z1);
        v(b, m, x1, y1, z1);
        v(b, m, x2, y1, z1);
        v(b, m, x2, y1, z2);
        v(b, m, x1, y1, z2);
    }

    private static void v(BufferBuilder b, Matrix4f m, float x, float y, float z) {
        b.vertex(m, x, y, z).color(255, 255, 255, 255).next();
    }

    public static void close() {
    }
}
