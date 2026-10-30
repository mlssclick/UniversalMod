package universalmod.event.type;

import net.minecraft.client.gui.screen.Screen;
import universalmod.event.Event;
import universalmod.setting.bind.InputType;

public record KeyEvent(Screen screen, InputType type, int key, int action) implements Event {
}
