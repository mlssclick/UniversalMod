package universalmod.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;






public final class RenderLayerFactory {
    private RenderLayerFactory() {
    }

    public static RenderLayer create(
            String name,
            VertexFormat vertexFormat,
            VertexFormat.DrawMode drawMode,
            int expectedBufferSize,
            boolean hasCrumbling,
            boolean translucent,
            Runnable startAction,
            Runnable endAction
    ) {
        return new RenderLayer(
                name,
                vertexFormat,
                drawMode,
                expectedBufferSize,
                hasCrumbling,
                translucent,
                startAction == null ? () -> {
                } : startAction,
                endAction == null ? () -> {
                } : endAction
        ) {
        };
    }
}
