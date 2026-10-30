package universalmod.compat.input;

public record MouseButtonEvent(double x, double y, MouseButtonInfo info) {
    public int button() { return info.button(); }
    public int modifiers() { return info.modifiers(); }
    public static MouseButtonEvent fromYarn(double x, double y, int button, int modifiers) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers));
    }
}
