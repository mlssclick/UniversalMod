package universalmod.render;

import net.minecraft.client.gui.DrawContext;

final class RenderScissorStack {
    private RenderScissorStack() {}

    static void push(DrawContext graphics, float x, float y, float width, float height) {
        if (graphics == null) return;
        int left = Render2DCoordinateSpace.toGuiInt(x);
        int top = Render2DCoordinateSpace.toGuiInt(y);
        int right = Render2DCoordinateSpace.toGuiInt(x + width);
        int bottom = Render2DCoordinateSpace.toGuiInt(y + height);
        ScissorUtil.push(left, top, right, bottom);
    }

    static void pop(DrawContext graphics) {
        if (graphics != null) ScissorUtil.pop();
    }
}
