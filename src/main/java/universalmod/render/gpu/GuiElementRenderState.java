package universalmod.render.gpu;

public interface GuiElementRenderState {
    void buildVertices(GuiVertexConsumer consumer);
    RenderPipeline pipeline();
    TextureSetup textureSetup();
    ScreenRectangle scissorArea();
    ScreenRectangle bounds();
}
