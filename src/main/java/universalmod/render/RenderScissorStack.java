package universalmod.render;

 
final class RenderScissorStack {
    private static int ownedDepth;

    private RenderScissorStack() {}

    static void push(float x, float y, float width, float height) {
        int left = Render2DCoordinateSpace.toGuiInt(x);
        int top = Render2DCoordinateSpace.toGuiInt(y);
        int right = Render2DCoordinateSpace.toGuiInt(x + width);
        int bottom = Render2DCoordinateSpace.toGuiInt(y + height);
        ScissorUtil.push(left, top, right, bottom);
        ScissorUtil.ScissorBox area = ScissorUtil.currentBox();
        
        UiRender.pushScissor(area.left(), area.top(), area.width(), area.height());
        ownedDepth++;
    }

    static void pop() {
        if (ownedDepth == 0) return;
        ownedDepth--;
        ScissorUtil.pop();
        UiRender.popScissor();
    }

    static void clear() {
        while (ownedDepth > 0) pop();
        ScissorUtil.clear();
    }
}
