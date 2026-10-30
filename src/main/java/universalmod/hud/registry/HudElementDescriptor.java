package universalmod.hud.registry;

import universalmod.hud.HudElement;

import java.util.Objects;


public record HudElementDescriptor(HudElementId id, HudElement element) {
    public HudElementDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(element, "element");
    }

    public String optionName() {
        return id.optionName();
    }

    public String translationKey() {
        return id.translationKey();
    }

    public boolean selectedByDefault() {
        return id.selectedByDefault();
    }
}
