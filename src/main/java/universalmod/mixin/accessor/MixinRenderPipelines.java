package universalmod.mixin.accessor;

import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderLayer.class)
public interface MixinRenderPipelines {
    @Invoker("getLines")
    static RenderLayer universalmod$getLinesSnippet() { throw new AssertionError(); }
}
