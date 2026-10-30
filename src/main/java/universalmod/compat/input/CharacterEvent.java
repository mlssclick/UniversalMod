package universalmod.compat.input;

public record CharacterEvent(int codepoint, int modifiers) {
    public String codepointAsString() {
        return Character.isValidCodePoint(codepoint) ? new String(Character.toChars(codepoint)) : "";
    }
    public static CharacterEvent fromYarn(char c, int modifiers) {
        return new CharacterEvent(c, modifiers);
    }
}
