package universalmod.hud;

public interface HudElement {
    void render();

    default boolean mouseClicked(HudMouseEvent event, boolean doubled) {
        return false;
    }

    default boolean mouseReleased(HudMouseEvent event) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }
}
