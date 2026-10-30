package universalmod.hud;

import net.minecraft.client.gui.DrawContext;

 
public final class HudManager {
    private static final HudManager INSTANCE = new HudManager();
    private HudManager() { }
    public static HudManager getInstance() { return INSTANCE; }
    public ElementComponent register(String id, String title, float x, float y) {
        return ElementManager.getInstance().register(id, title, x, y);
    }
    public void load() { ElementManager.getInstance().load(); }
    public void save() { ElementManager.getInstance().save(); }
    public void frame(ElementScreen screen) { ElementManager.getInstance().frame(screen); }
    public boolean handleMouseClicked(HudMouseEvent event) {
        return ElementManager.getInstance().handleMouseClicked(event);
    }
    public boolean handleMouseDragged(double x, double y) {
        return ElementManager.getInstance().handleMouseDragged(x, y);
    }
    public boolean handleMouseReleased(HudMouseEvent event) {
        return ElementManager.getInstance().handleMouseReleased(event);
    }
    public void updateActiveElementFromMouse() { ElementManager.getInstance().updateActiveElementFromMouse(); }
    public void cancelActiveElement() { ElementManager.getInstance().cancelActiveElement(); }
    public void renderEditorOverlay(DrawContext graphics, ElementScreen screen) {
        ElementManager.getInstance().renderEditorOverlay(graphics, screen);
    }
}
