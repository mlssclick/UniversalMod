package universalmod.module.misc;

import org.lwjgl.glfw.GLFW;
import universalmod.gui.clickgui.KeyEvent;
import universalmod.gui.clickgui.MouseButtonEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.bind.InputType;
import universalmod.setting.bind.KeyBind;

@ModuleInformation(
        moduleName = "ClickGUI",
        moduleCategory = ModuleCategory.MISC,
        moduleKeybind = GLFW.GLFW_KEY_RIGHT_SHIFT
)
public final class ClickGuiModule extends Module {
    private static ClickGuiModule instance;
    private static boolean preventReopenUntilRelease;
    private boolean openingKeyboardPressHeld;
    private boolean openingMousePressHeld;
    private ScreenToggle screenToggle;

    public ClickGuiModule() {
        instance = this;
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

    public static boolean matchesCloseKey(int key) {
        if (instance == null) return false;
        KeyBind bind = instance.getBind();
        return bind != null
                && bind.getType() == InputType.KEYBOARD
                && bind.getCode() == key
                && !instance.openingKeyboardPressHeld;
    }

    public static boolean matchesCloseMouse(int button) {
        if (instance == null) return false;
        KeyBind bind = instance.getBind();
        return bind != null
                && bind.getType() == InputType.MOUSE
                && bind.getCode() == button
                && !instance.openingMousePressHeld;
    }

    public static boolean matchesCloseKey(KeyEvent event) {
        return event != null && matchesCloseKey(event.key());
    }

    public static boolean matchesCloseMouse(MouseButtonEvent event) {
        return event != null && matchesCloseMouse(event.button());
    }

    public static void onKeyReleased(int key) {
        if (instance == null) return;
        KeyBind bind = instance.getBind();
        if (bind != null && bind.getType() == InputType.KEYBOARD && bind.getCode() == key) {
            instance.openingKeyboardPressHeld = false;
        }
    }

    public static void onMouseReleased(int button) {
        if (instance == null) return;
        KeyBind bind = instance.getBind();
        if (bind != null && bind.getType() == InputType.MOUSE && bind.getCode() == button) {
            instance.openingMousePressHeld = false;
        }
    }

    public static void pollOpeningBindRelease() {
        ClickGuiModule module = instance;
        if (module == null || module.mc == null || module.mc.getWindow() == null) return;
        KeyBind bind = module.getBind();
        if (bind == null || !bind.isBound()) return;
        long window = module.mc.getWindow().getHandle();
        if (bind.getType() == InputType.MOUSE) {
            if (module.openingMousePressHeld && GLFW.glfwGetMouseButton(window, bind.getCode()) != GLFW.GLFW_PRESS) {
                module.openingMousePressHeld = false;
            }
        } else if (bind.getType() == InputType.KEYBOARD) {
            if (module.openingKeyboardPressHeld && GLFW.glfwGetKey(window, bind.getCode()) != GLFW.GLFW_PRESS) {
                module.openingKeyboardPressHeld = false;
            }
        }
    }


    public interface ScreenToggle {
        boolean isOpen();

        void toggle();
    }

    public void setScreenToggle(ScreenToggle screenToggle) {
        this.screenToggle = screenToggle;
    }

    public static void resetOpeningBindLatch() {
        preventReopenUntilRelease = false;
        if (instance != null) {
            instance.openingKeyboardPressHeld = false;
            instance.openingMousePressHeld = false;
        }
    }

    private void captureOpeningBindState() {
        openingKeyboardPressHeld = false;
        openingMousePressHeld = false;
        if (mc == null || mc.getWindow() == null) return;
        KeyBind bind = getBind();
        if (bind == null || !bind.isBound()) return;
        long window = mc.getWindow().getHandle();
        if (bind.getType() == InputType.MOUSE) {
            openingMousePressHeld = GLFW.glfwGetMouseButton(window, bind.getCode()) == GLFW.GLFW_PRESS;
        } else if (bind.getType() == InputType.KEYBOARD) {
            openingKeyboardPressHeld = GLFW.glfwGetKey(window, bind.getCode()) == GLFW.GLFW_PRESS;
        }
    }

    @Override
    public void onEnable() {
        try {
            ScreenToggle toggle = screenToggle;
            boolean guiOpen = toggle != null && toggle.isOpen();
            if (toggle != null && mc != null && (mc.currentScreen == null || guiOpen)) {
                if (!guiOpen) {
                    captureOpeningBindState();
                }
                toggle.toggle();
            }
        } finally {
            setEnabled(false);
        }
    }
}
