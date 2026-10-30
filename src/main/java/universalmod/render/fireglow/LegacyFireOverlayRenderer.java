package universalmod.render.fireglow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import universalmod.access.FireGlowEntityStateCarrier;
import universalmod.render.gpu.*;

public final class LegacyFireOverlayRenderer {
    private static final Identifier FIRE_0=Identifier.ofVanilla("block/fire_0"), FIRE_1=Identifier.ofVanilla("block/fire_1");
    private static final float LAYER_DROP=.45f, WIDTH_SHRINK=.9f, DEPTH_STEP=-.03f;
    private static final int FULL_BRIGHT=0x00F000F0, MAX_FIRE_LAYERS=4096;
    private LegacyFireOverlayRenderer() { }
    @FunctionalInterface private interface Sink { void vertex(float x,float y,float z,float u,float v,int color); }

    public static void submit(MatrixStack matrices,VertexConsumerProvider consumers,EntityRenderState state,Quaternionf rotation) {
        var atlas=MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        Sprite fire0=atlas.getSprite(FIRE_0), fire1=atlas.getSprite(FIRE_1);
        float scale=state.width*1.4f;
        if(!Float.isFinite(scale) || scale<=0)scale=1;
        float height=state.height/scale;
        if(!Float.isFinite(height) || height<=0)return;
        matrices.push();
        try {
            matrices.scale(scale,scale,scale); matrices.multiply(rotation);
            matrices.translate(0,0,.3f-((int)height)*.02f);
            int outline=FireGlowConfig.hasCustomColor()?0xFF000000|FireGlowConfig.getRed()<<16|FireGlowConfig.getGreen()<<8|FireGlowConfig.getBlue()
                    :((FireGlowEntityStateCarrier)state).universalmod$getFireOutlineColor();
            MaskState mask=new MaskState(new Matrix4f(matrices.peek().getPositionMatrix()),fire0,fire1,height,outline);
            FireGlowFramebuffer.withMaskTarget(() -> GuiStateRenderer.getInstance().submitGuiElement(mask));
            FireGlowFramebuffer.markMaskWritten();
            VertexConsumer vertices=consumers.getBuffer(FireGlowRenderLayer.fireBlocks());
            MatrixStack.Entry entry=matrices.peek();
            renderFireStack((x,y,z,u,v,color) -> vertices.vertex(entry,x,y,z).texture(u,v).color(color)
                    .overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(entry,0,1,0),fire0,fire1,height,0xFFFFFFFF);
        } finally { matrices.pop(); }
    }

    private static void renderFireStack(Sink sink,Sprite fire0,Sprite fire1,float remaining,int color) {
        float halfWidth=.5f,y=0,depth=0; int layer=0;
        while(remaining>0 && layer<MAX_FIRE_LAYERS) {
            Sprite sprite=(layer&1)==0?fire0:fire1;
            float minU=sprite.getMinU(),maxU=sprite.getMaxU(),minV=sprite.getMinV(),maxV=sprite.getMaxV();
            if(((layer/2)&1)==0) { float swap=maxU;maxU=minU;minU=swap; }
            sink.vertex(-halfWidth,y,depth,maxU,maxV,color);sink.vertex(halfWidth,y,depth,minU,maxV,color);
            sink.vertex(halfWidth,1.4f+y,depth,minU,minV,color);sink.vertex(-halfWidth,1.4f+y,depth,maxU,minV,color);
            remaining-=LAYER_DROP;y+=LAYER_DROP;halfWidth*=WIDTH_SHRINK;depth+=DEPTH_STEP;layer++;
        }
        if(remaining>0)throw new IllegalStateException("Fire geometry exceeds layer limit");
    }

    private record MaskState(Matrix4f pose,Sprite fire0,Sprite fire1,float height,int color) implements GuiElementRenderState {
        @Override public void buildVertices(GuiVertexConsumer vertices) {
            renderFireStack((x,y,z,u,v,c) -> vertices.addVertex(pose,x,y,z).setUv(u,v).setColor(c),fire0,fire1,height,color);
        }
        @Override public RenderPipeline pipeline() { return FireGlowRenderLayer.fireMask(); }
        @Override public TextureSetup textureSetup() {
            return TextureSetup.single(() -> MinecraftClient.getInstance().getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getGlId(),false);
        }
        @Override public ScreenRectangle scissorArea() { return null; }
        @Override public ScreenRectangle bounds() { return new ScreenRectangle(0,0,1,1); }
    }
}
