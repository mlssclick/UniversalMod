package universalmod.event.type;

import universalmod.event.Event;

public final class TextFactoryEvent implements Event {
    private String text;

    public TextFactoryEvent(String text) {
        this.text = text == null ? "" : text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public void replaceText(String target, String replacement) {
        if (target != null && !target.isEmpty() && replacement != null) text = text.replace(target, replacement);
    }
}
