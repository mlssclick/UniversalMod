package universalmod.render.effect;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.gpu.*;

public final class EffectIconRenderer implements AutoCloseable {
    public static final RenderPipeline EFFECT_ICON_PIPELINE = new RenderPipeline("ui/effect_icon/effect_icon","ui/effect_icon/effect_icon");
    private static final EffectIconRenderer INSTANCE = new EffectIconRenderer();
    private DrawContext graphics;
    private EffectIconRenderer() { }
    public static EffectIconRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext graphics) { this.graphics=graphics; }
    public void barrier() { GuiStateRenderer.getInstance().flush(); }
    public void flush() { barrier(); graphics=null; }
    public void enqueue(BuiltEffectIcon value) {
        EffectIconState state=state(graphics,value);
        if(state!=null) GuiStateRenderer.getInstance().queueGuiElement(state);
    }
    public void draw(DrawContext graphics,BuiltEffectIcon value) {
        EffectIconState state=state(graphics,value);
        if(state!=null) GuiStateRenderer.getInstance().submitGuiElement(state);
    }
    private static EffectIconState state(DrawContext graphics,BuiltEffectIcon value) {
        if(value==null || !value.visible()) return null;
        var sprite=MinecraftClient.getInstance().getStatusEffectSpriteManager().getSprite(value.effect());
        if(sprite==null) return null;
        EffectIconState state=new EffectIconState(Render2DCoordinateSpace.pose(graphics),new EffectIconTexture(sprite));
        state.add(new EffectIconQuad(value.x(),value.y(),value.size(),value.color(),sprite.getMinU(),sprite.getMinV(),sprite.getMaxU(),sprite.getMaxV()));
        return state;
    }
    @Override public void close() { barrier(); graphics=null; EFFECT_ICON_PIPELINE.close(); }
}
