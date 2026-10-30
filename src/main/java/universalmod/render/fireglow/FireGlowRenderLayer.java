package universalmod.render.fireglow;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import universalmod.render.gpu.RenderPipeline;

public final class FireGlowRenderLayer {
    private static final RenderPipeline FIRE_MASK=new RenderPipeline("core/fire_glow_outline","core/fire_glow_outline").withBlend(false);
    private FireGlowRenderLayer() { }
    public static RenderPipeline fireMask() { return FIRE_MASK; }
    public static RenderLayer fireBlocks() { return RenderLayer.getEntityCutout(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE); }
    public static void close() { FIRE_MASK.close(); }
}
