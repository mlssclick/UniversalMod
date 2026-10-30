package universalmod.gui.clickgui;

public record CharacterEvent(char character, int modifiers) {
    public boolean isAllowedChatCharacter() {
        return character >= 32 && character != 127;
    }

    public String codepointAsString() {
        return String.valueOf(character);
    }
}
