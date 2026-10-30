package universalmod.mixin.accessor;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.BuiltChunkStorage;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WorldRenderer.class)
public interface WorldRendererFullbrightAccessor {
    @Accessor("chunks")
    BuiltChunkStorage universalmod$getChunks();

    @Invoker("scheduleChunkRender")
    void universalmod$scheduleChunkRender(int x, int y, int z, boolean important);
}
