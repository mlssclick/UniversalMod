package universalmod.hud;

import net.minecraft.client.input.MouseButtonEvent;

public interface HudElement {
    void render();

    default boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return false;
    }

    default boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }
}
