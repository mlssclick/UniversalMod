package universalmod.compat.input;

public record KeyEvent(int key, int scancode, int modifiers) {
    public static KeyEvent fromYarn(int key, int scancode, int modifiers) {
        return new KeyEvent(key, scancode, modifiers);
    }
}
