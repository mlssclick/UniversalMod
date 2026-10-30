package universalmod.gui.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import universalmod.render.Render2DCoordinateSpace;


final class ClickGuiCoordinateSupport {
    private ClickGuiCoordinateSupport() {
    }

    static MouseButtonEvent toDesignEvent(MouseButtonEvent event) {
        float scale = coordinateScale();
        if (Math.abs(scale - 1.0f) <= 0.0001f) {
            return event;
        }
        MouseButtonInfo info = new MouseButtonInfo(event.button(), event.modifiers());
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, info);
    }

    static float coordinateScale() {
        return Render2DCoordinateSpace.guiIndependentScale();
    }

    static void updateMousePosition(ClickGuiSession session) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            return;
        }
        float scale = coordinateScale();
        session.pointerX = client.mouseHandler.getScaledXPos(client.getWindow()) / scale;
        session.pointerY = client.mouseHandler.getScaledYPos(client.getWindow()) / scale;
    }

    static boolean inside(double px, double py, float x, float y, float width, float height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }
}
