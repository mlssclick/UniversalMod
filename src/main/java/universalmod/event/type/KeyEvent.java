package universalmod.event.type;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import universalmod.event.Event;

public record KeyEvent(Screen screen, InputUtil.Type type, int key, int action) implements Event {
}
