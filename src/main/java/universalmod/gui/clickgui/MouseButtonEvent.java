package universalmod.gui.clickgui;

public record MouseButtonEvent(double x, double y, int button, int modifiers) {
    public MouseButtonEvent(double x, double y, int button) {
        this(x, y, button, 0);
    }
}
