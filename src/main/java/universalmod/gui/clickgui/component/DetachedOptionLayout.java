package universalmod.gui.clickgui.component;

import universalmod.gui.clickgui.component.option.ClickGuiOption;
import universalmod.render.optimize.UiFrameContext;

import java.util.List;


final class DetachedOptionLayout {
    private float[] offsets = new float[0];
    private float[] rowHeights = new float[0];
    private float[] visibility = new float[0];
    private long frame = Long.MIN_VALUE;
    private float factor = Float.NaN;
    private float scrollHeight;

    void prepare(List<ClickGuiOption> settings, float requestedFactor) {
        long currentFrame = UiFrameContext.active() ? UiFrameContext.frameId() : Long.MIN_VALUE;
        if (currentFrame != Long.MIN_VALUE
                && frame == currentFrame
                && Float.compare(factor, requestedFactor) == 0) {
            return;
        }
        ensureCapacity(settings.size());
        float renderOffset = 0.0f;
        float measuredScrollHeight = 0.0f;
        for (int i = 0; i < settings.size(); i++) {
            ClickGuiOption option = settings.get(i);
            float rowVisibility = option.visibilityProgress();
            float rowHeight = option.getHeight() * requestedFactor + 5.0f;
            offsets[i] = renderOffset;
            rowHeights[i] = rowHeight;
            visibility[i] = rowVisibility;
            if (rowVisibility > 0.01f) {
                renderOffset += (rowHeight + 2.5f) * rowVisibility;
                measuredScrollHeight += rowHeight * rowVisibility;
            }
        }
        scrollHeight = measuredScrollHeight;
        factor = requestedFactor;
        frame = currentFrame;
    }

    float offset(int index) {
        return offsets[index];
    }

    float rowHeight(int index) {
        return rowHeights[index];
    }

    float visibility(int index) {
        return visibility[index];
    }

    float scrollHeight() {
        return scrollHeight;
    }

    private void ensureCapacity(int count) {
        if (offsets.length >= count) {
            return;
        }
        int capacity = Math.max(count, Math.max(8, offsets.length * 2));
        offsets = new float[capacity];
        rowHeights = new float[capacity];
        visibility = new float[capacity];
    }
}
