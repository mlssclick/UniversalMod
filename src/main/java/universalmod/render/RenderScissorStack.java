package universalmod.render;

import net.minecraft.client.gui.GuiGraphics;


final class RenderScissorStack {
    private RenderScissorStack() {
    }

    static void push(GuiGraphics graphics, float x, float y, float width, float height) {
        int left = Render2DCoordinateSpace.toGuiInt(x);
        int top = Render2DCoordinateSpace.toGuiInt(y);
        int right = Render2DCoordinateSpace.toGuiInt(x + width);
        int bottom = Render2DCoordinateSpace.toGuiInt(y + height);
        ScissorUtil.push(left, top, right, bottom);
        graphics.enableScissor(left, top, right, bottom);
    }

    static void pop(GuiGraphics graphics) {
        ScissorUtil.pop();
        graphics.disableScissor();
    }
}
