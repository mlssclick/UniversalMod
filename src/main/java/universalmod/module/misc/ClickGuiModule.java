package universalmod.module.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.bind.InputType;
import universalmod.setting.bind.KeyBind;
import universalmod.lifecycle.Manager;

public final class ClickGuiModule extends Module {
    private static ClickGuiModule instance;
    private static boolean preventReopenUntilRelease;

    public ClickGuiModule() {
        super("click_gui", "ClickGUI", ModuleCategory.MISC);
        instance = this;
        setDefaultBind(KeyBind.keyboard(GLFW.GLFW_KEY_RIGHT_SHIFT));
    }

    public static void suppressReopenUntilKeyRelease() {
        preventReopenUntilRelease = true;
    }

    public static boolean shouldSuppressHeldBind(boolean down) {
        if (!down) {
            preventReopenUntilRelease = false;
            return false;
        }
        return preventReopenUntilRelease;
    }

    public static boolean matchesCloseKey(KeyEvent event) {
        return instance != null
                && event != null
                && instance.getBind().getType() == InputType.KEYBOARD
                && instance.getBind().getCode() == event.key();
    }

    public static boolean matchesCloseMouse(MouseButtonEvent event) {
        return instance != null
                && event != null
                && instance.getBind().getType() == InputType.MOUSE
                && instance.getBind().getCode() == event.button();
    }

    @Override
    protected void onEnable() {
        Minecraft client = mc;
        try {
            if (client != null && (client.screen == null || Manager.isClickGuiOpen(client))) {
                Manager.toggleClickGui(client);
            }
        } finally {
            setEnabled(false);
        }
    }
}
