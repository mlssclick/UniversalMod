package universalmod.gui.clickgui;

import net.minecraft.client.MinecraftClient;
import universalmod.render.Render2DCoordinateSpace;


final class ClickGuiCoordinateSupport {
    private ClickGuiCoordinateSupport() {
    }

    static MouseButtonEvent toDesignEvent(MouseButtonEvent event) {
        float scale = coordinateScale();
        if (Math.abs(scale - 1.0f) <= 0.0001f) {
            return event;
        }
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, event.button(), event.modifiers());
    }

    static float coordinateScale() {
        return Render2DCoordinateSpace.guiIndependentScale();
    }

    static void updateMousePosition(ClickGuiSession session) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.mouse == null || client.getWindow() == null) {
            return;
        }
        float scale = coordinateScale();
        session.pointerX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth() / scale;
        session.pointerY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight() / scale;
    }

    static boolean inside(double px, double py, float x, float y, float width, float height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }
}
