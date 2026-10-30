package universalmod.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

public final class WorldVertex {
    private static final int FULL_BRIGHT = 0x00F000F0;

    private WorldVertex() {
    }

    public static void textured(VertexConsumer consumer, MatrixStack.Entry entry,
                                float x, float y, float z, float u, float v, int color) {
        consumer.vertex(entry.getPositionMatrix(), x, y, z)
                
                .color((color >>> 16) & 255, (color >>> 8) & 255, color & 255, (color >>> 24) & 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(entry.getNormalMatrix(), 0.0F, 0.0F, 1.0F).next();
    }
}
