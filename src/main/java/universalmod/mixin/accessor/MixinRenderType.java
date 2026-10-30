package universalmod.mixin.accessor;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderLayer.class)
public interface MixinRenderType {
    @Invoker("of")
    static RenderLayer.MultiPhase universalmod$create(String name,VertexFormat format,VertexFormat.DrawMode mode,int bytes,
            boolean crumbling,boolean translucent,RenderLayer.MultiPhaseParameters setup) {
        throw new AssertionError();
    }
}
