package universalmod.render.world;

import universalmod.render.Render3D;
import universalmod.render.ClientPipelines;

import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Color;
import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;




public final class BlockOverlayShaderRenderer {
    private static final double SHAPE_EPSILON = 0.002D;

    public boolean renderFill(
            MatrixStack matrices,
            Vec3d origin,
            VoxelShape shape,
            ShaderMode mode,
            Color color,
            float transparency,
            float speed,
            boolean throughWalls
    ) {
        if (matrices == null || origin == null || shape == null || shape.isEmpty()
                || mode == null || mode == ShaderMode.NONE || color == null) {
            return false;
        }

        float alpha = MathHelper.clamp((color.getAlpha() / 255.0F) * transparency, 0.0F, 1.0F);
        if (alpha <= 0.001F) {
            return false;
        }

        ShaderProgramKey key = mode.key();
        ShaderProgram shader = ClientPipelines.bind(key, mode.shaderName());
        if (shader == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        float time = (float) (System.nanoTime() / 1.0E9D) * speed;
        float width = Math.max(1.0F, client.getWindow().getFramebufferWidth());
        float height = Math.max(1.0F, client.getWindow().getFramebufferHeight());

        set4(shader, "NebulaData", time, width, height, alpha);
        set4(shader, "NebulaTint",
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F,
                1.0F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (throughWalls) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }

        try {
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            int vertexColor = (Math.round(alpha * 255.0F) << 24) | (color.getRGB() & 0x00FFFFFF);
            for (Box local : shape.getBoundingBoxes()) {
                Box relative = new Box(
                        origin.x + local.minX - SHAPE_EPSILON - camera.x,
                        origin.y + local.minY - SHAPE_EPSILON - camera.y,
                        origin.z + local.minZ - SHAPE_EPSILON - camera.z,
                        origin.x + local.maxX + SHAPE_EPSILON - camera.x,
                        origin.y + local.maxY + SHAPE_EPSILON - camera.y,
                        origin.z + local.maxZ + SHAPE_EPSILON - camera.z
                );
                Render3D.boxVertices(matrix, buffer, relative, vertexColor);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        return true;
    }

    public static void close() {

    }

    private static void set4(ShaderProgram shader, String name, float a, float b, float c, float d) {
        var uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(a, b, c, d);
        }
    }

    public enum ShaderMode {
        NONE(null, null),
        NEBULA(ClientPipelines.BLOCK_NEBULA, "render_block_overlay_nebula"),
        COBWEB(ClientPipelines.BLOCK_COBWEB, "render_block_overlay_cobweb"),
        PLASMA(ClientPipelines.BLOCK_PLASMA, "render_block_overlay_plasma"),
        STARFIELD(ClientPipelines.BLOCK_STARFIELD, "render_block_overlay_starfield");

        private final ShaderProgramKey key;
        private final String shaderName;

        ShaderMode(ShaderProgramKey key, String shaderName) {
            this.key = key;
            this.shaderName = shaderName;
        }

        public ShaderProgramKey key() {
            return key;
        }

        public String shaderName() {
            return shaderName;
        }

        public static ShaderMode fromSetting(String value) {
            if (value == null) {
                return NONE;
            }
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return NONE;
            }
        }
    }
}
