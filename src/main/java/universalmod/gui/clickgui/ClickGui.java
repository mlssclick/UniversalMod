package universalmod.gui.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import universalmod.gui.clickgui.ClickGuiController;
import universalmod.module.misc.ClickGuiModule;
import universalmod.gui.clickgui.CharacterEvent;
import universalmod.gui.clickgui.KeyEvent;
import universalmod.gui.clickgui.MouseButtonEvent;
import universalmod.service.language.LanguageManager;

import java.nio.file.Path;
import java.util.List;

public final class ClickGui extends Screen {
    private long lastMouseClickTime;
    private int lastMouseClickButton = -1;
    private double lastMouseClickX;
    private double lastMouseClickY;

    public ClickGui() {
        super(Text.literal(LanguageManager.translate("ClickGUI")));
    }

    public ClickGui(universalmod.module.ModuleManager moduleManager) {
        this();
        if (moduleManager != null) {
            
            moduleManager.getByType(universalmod.module.render.ItemReplacer.class).ifPresent(module ->
                    wireButton(module, "Model", () -> ClickGuiController.openItemReplacerEditor(module)));
        }
    }

    private static void wireButton(universalmod.module.Module module, String name, Runnable action) {
        if (module == null) return;
        for (universalmod.setting.Setting setting : module.getSettings()) {
            if (setting instanceof universalmod.setting.ButtonSetting button && name.equals(setting.getName())) {
                button.setAction(action);
                return;
            }
        }
    }

    @Override
    protected void init() {
        ClickGuiController.init();
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        ClickGuiController.renderPanels(graphics, width, height);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClickGuiController.keyPressed(new KeyEvent(keyCode, scanCode, modifiers))) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        ClickGuiModule.onKeyReleased(keyCode);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (ClickGuiController.charTyped(new CharacterEvent(chr, modifiers))) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        long now = System.currentTimeMillis();
        double dx = mouseX - lastMouseClickX;
        double dy = mouseY - lastMouseClickY;
        boolean doubleClicked = button == lastMouseClickButton
                && now - lastMouseClickTime <= 250L
                && dx * dx + dy * dy <= 9.0D;
        lastMouseClickTime = now;
        lastMouseClickButton = button;
        lastMouseClickX = mouseX;
        lastMouseClickY = mouseY;
        return ClickGuiController.mouseClicked(new MouseButtonEvent(mouseX, mouseY, button), doubleClicked)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        ClickGuiModule.onMouseReleased(button);
        return ClickGuiController.mouseReleased(new MouseButtonEvent(mouseX, mouseY, button))
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return ClickGuiController.mouseDragged(new MouseButtonEvent(mouseX, mouseY, button), dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (ClickGuiController.mouseScrolled(mouseX, mouseY, 0.0, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override
    public void filesDragged(List<Path> paths) {
        ClickGuiController.onFilesDrop(paths);
    }

    @Override
    public void close() {
        ClickGuiController.startClosing();
    }

    @Override
    public void removed() {

        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static void renderPanels(DrawContext graphics, int screenWidth, int screenHeight) {
        ClickGuiController.renderPanels(graphics, screenWidth, screenHeight);
    }

    public static boolean warmupText() {
        return ClickGuiController.warmupText();
    }

    public static boolean warmupGraphics(DrawContext graphics) {
        return ClickGuiController.warmupGraphics(graphics);
    }

    public static void startClosing() {
        ClickGuiController.startClosing();
    }

    public static void resetOverlayState() {
        ClickGuiController.resetOverlayState();
    }

    public static boolean shouldRenderOverlay() {
        return ClickGuiController.shouldRenderOverlay();
    }

    public static void validateState(MinecraftClient client) {
        ClickGuiController.validateState(client);
    }

    public static void tickMovementKeys() {
        ClickGuiController.tickMovementKeys();
    }
}
