package universalmod.gui.ui.control;

import java.awt.Color;
import universalmod.render.optimize.UiFrameContext;
import java.util.Objects;
import java.util.function.Consumer;
public final class ColorPickerInteraction {
    public enum HueAxis { HORIZONTAL, VERTICAL }
    public enum Region { NONE, PALETTE, HUE, ALPHA }

    private final ColorPickerModel model = new ColorPickerModel();
    private final ColorPickerModel visual = new ColorPickerModel();
    private long previousVisualNanos;
    private boolean visualInitialized;
    private ColorPickerGeometry geometry = new ColorPickerGeometry(
            new Bounds(0, 0, 0, 0), new Bounds(0, 0, 0, 0), new Bounds(0, 0, 0, 0));
    private Region dragRegion = Region.NONE;

    public ColorPickerModel model() {
        return model;
    }

    public ColorPickerModel visual() {
        return visual;
    }

    public void resetVisual() {
        visual.set(model.hue(), model.saturation(), model.brightness(), model.alpha());
        visualInitialized = true;
        previousVisualNanos = 0L;
    }

    public void animate() {
        long now = UiFrameContext.nowNanos();
        if (!visualInitialized) {
            resetVisual();
            previousVisualNanos = now;
            return;
        }
        if (previousVisualNanos == now) {
            return;
        }
        float elapsed = previousVisualNanos == 0L ? 0.0F
                : Math.max(0.0F, Math.min(0.10F, (now - previousVisualNanos) / 1_000_000_000.0F));
        previousVisualNanos = now;
        float blend = 1.0F - (float) Math.exp(-16.0F * elapsed);
        float hueDifference = model.hue() - visual.hue();
        hueDifference -= (float) Math.floor(hueDifference + 0.5F);
        float hue = visual.hue() + hueDifference * blend;
        if (hue < 0.0F) {
            hue += 1.0F;
        } else if (hue >= 1.0F) {
            hue -= 1.0F;
        }
        visual.set(hue,
                approach(visual.saturation(), model.saturation(), blend),
                approach(visual.brightness(), model.brightness(), blend),
                approach(visual.alpha(), model.alpha(), blend));
    }

    private static float approach(float from, float to, float blend) {
        float next = from + (to - from) * blend;
        return Math.abs(to - next) <= 0.00015F ? to : next;
    }

    public ColorPickerGeometry geometry() {
        return geometry;
    }

    public Bounds palette() { return geometry.palette(); }
    public Bounds hue() { return geometry.hue(); }
    public Bounds alpha() { return geometry.alpha(); }
    public Region dragRegion() { return dragRegion; }
    public boolean dragging() { return dragRegion != Region.NONE; }

    public void geometry(ColorPickerGeometry geometry) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
    }

    public void sync(Color color) {
        if (color != null) {
            model.sync(color, color.getAlpha() / 255.0F);
            if (!visualInitialized) {
                resetVisual();
            }
        }
    }

    public void cancelDrag() {
        dragRegion = Region.NONE;
    }

    public boolean press(double mouseX, double mouseY, HueAxis hueAxis, float alphaVerticalExpansion,
                         Consumer<Color> sink) {
        Region region = hit(mouseX, mouseY, alphaVerticalExpansion);
        if (region == Region.NONE) {
            return false;
        }
        dragRegion = region;
        update(mouseX, mouseY, hueAxis, sink);
        return true;
    }

    public void drag(double mouseX, double mouseY, HueAxis hueAxis, Consumer<Color> sink) {
        if (dragRegion != Region.NONE) {
            update(mouseX, mouseY, hueAxis, sink);
        }
    }

    public Region hit(double mouseX, double mouseY, float alphaVerticalExpansion) {
        if (geometry.palette().contains(mouseX, mouseY)) {
            return Region.PALETTE;
        }
        if (geometry.hue().contains(mouseX, mouseY)) {
            return Region.HUE;
        }
        if (geometry.alpha().expanded(0.0F, Math.max(0.0F, alphaVerticalExpansion)).contains(mouseX, mouseY)) {
            return Region.ALPHA;
        }
        return Region.NONE;
    }

    private void update(double mouseX, double mouseY, HueAxis hueAxis, Consumer<Color> sink) {
        switch (dragRegion) {
            case PALETTE -> model.setPaletteFromPoint(mouseX, mouseY, geometry.palette());
            case HUE -> {
                if (hueAxis == HueAxis.VERTICAL) {
                    model.setHueFromVerticalPoint(mouseY, geometry.hue());
                } else {
                    model.setHueFromPoint(mouseX, geometry.hue());
                }
            }
            case ALPHA -> model.setAlphaFromPoint(mouseX, geometry.alpha());
            case NONE -> { return; }
        }
        if (sink != null) {
            sink.accept(model.color());
        }
    }
}
