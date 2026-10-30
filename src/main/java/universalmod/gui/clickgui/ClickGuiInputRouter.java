package universalmod.gui.clickgui;


import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.misc.ClickGuiModule;
import universalmod.gui.clickgui.component.ModuleOption;
import universalmod.render.animation.Easings;
import java.util.List;

import static universalmod.gui.clickgui.ClickGuiController.*;

final class ClickGuiInputRouter {
    private ClickGuiInputRouter() {
    }

    public static boolean keyPressed(KeyEvent event) {
        if (ClickGuiModule.matchesCloseKey(event)) {
            startClosing();
            return true;
        }
        if (ClickGuiThemePopup.keyPressed(event)) return true;
        if (SESSION.selectedHwEvents && SESSION.hwEventsPanel.keyPressed(event)) return true;
        if (handleConfigEditKey(event)) return true;
        if (isBodyEditorOpen() && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeBodyEditor();
            return true;
        }
        if (SESSION.itemReplacerEditorOpen && SESSION.itemReplacerPanel.keyPressed(event)) return true;
        if (SESSION.configSearchFocused) {
            editSearchField(event, true);
            return true;
        }
        if (SESSION.searchFocused) {
            editSearchField(event, false);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            startClosing();
            return true;
        }
        ModuleOption bindingOption = SESSION.bindingCoordinator.active();
        if (bindingOption != null && bindingOption.keyPressed(event)) return true;
        if (SESSION.selectedModule != null && optionFor(SESSION.selectedModule).keyPressed(event)) return true;
        return true;
    }

    private static void editSearchField(KeyEvent event, boolean config) {
        int key = event.key();
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        String value = config ? SESSION.configQuery : SESSION.query;
        int cursor = Math.max(0, Math.min(config ? SESSION.configSearchCursor : SESSION.searchCursor, value.length()));
        boolean selectedAll = config ? SESSION.configSearchSelectAll : SESSION.searchSelectAll;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (config) SESSION.configSearchFocused = false; else SESSION.searchFocused = false;
            return;
        }
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            if (config) SESSION.configSearchSelectAll = true; else SESSION.searchSelectAll = true;
            setSearchCursor(config, value.length());
            return;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            if (selectedAll) setClipboard(value);
            return;
        }
        if (ctrl && key == GLFW.GLFW_KEY_X) {
            if (selectedAll) {
                setClipboard(value);
                setSearchValue(config, "", 0, false);
            }
            return;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip = clipboard().replace("\r", "").replace("\n", " ");
            if (!clip.isEmpty()) {
                if (selectedAll) value = clip;
                else value = value.substring(0, cursor) + clip + value.substring(cursor);
                cursor = selectedAll ? clip.length() : cursor + clip.length();
                setSearchValue(config, value, cursor, false);
            }
            return;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (selectedAll) {
                setSearchValue(config, "", 0, false);
            } else if (cursor > 0) {
                int from = ctrl ? previousWordBoundary(value, cursor) : cursor - 1;
                setSearchValue(config, value.substring(0, from) + value.substring(cursor), from, false);
            }
            return;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (selectedAll) {
                setSearchValue(config, "", 0, false);
            } else if (cursor < value.length()) {
                int to = ctrl ? nextWordBoundary(value, cursor) : cursor + 1;
                setSearchValue(config, value.substring(0, cursor) + value.substring(to), cursor, false);
            }
            return;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            if (selectedAll) cursor = key == GLFW.GLFW_KEY_LEFT ? 0 : value.length();
            else if (key == GLFW.GLFW_KEY_LEFT) cursor = ctrl ? previousWordBoundary(value, cursor) : Math.max(0, cursor - 1);
            else cursor = ctrl ? nextWordBoundary(value, cursor) : Math.min(value.length(), cursor + 1);
            setSearchValue(config, value, cursor, false);
            return;
        }
        if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
            setSearchValue(config, value, key == GLFW.GLFW_KEY_HOME ? 0 : value.length(), false);
        }
    }

    private static int previousWordBoundary(String text, int cursor) {
        int i = Math.max(0, Math.min(cursor, text.length()));
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) i--;
        return i;
    }
    private static int nextWordBoundary(String text, int cursor) {
        int i = Math.max(0, Math.min(cursor, text.length()));
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }
    private static void setSearchCursor(boolean config, int cursor) {
        if (config) SESSION.configSearchCursor = cursor; else SESSION.searchCursor = cursor;
    }
    private static void setSearchValue(boolean config, String value, int cursor, boolean selectedAll) {
        if (config) {
            SESSION.configQuery = value;
            SESSION.configSearchCursor = Math.max(0, Math.min(cursor, value.length()));
            SESSION.configSearchSelectAll = selectedAll;
        } else {
            SESSION.query = value;
            SESSION.searchCursor = Math.max(0, Math.min(cursor, value.length()));
            SESSION.searchSelectAll = selectedAll;
            SESSION.visibleModulesCacheQuery = null;
        }
        resetScroll();
    }
    private static String clipboard() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return "";
        String value = GLFW.glfwGetClipboardString(client.getWindow().handle());
        return value == null ? "" : value;
    }
    private static void setClipboard(String value) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getWindow() != null) GLFW.glfwSetClipboardString(client.getWindow().handle(), value == null ? "" : value);
    }
    public static boolean charTyped(CharacterEvent event) {
        if (SESSION.selectedHwEvents && SESSION.hwEventsPanel.charTyped(event)) {
            return true;
        }
        if (SESSION.editingConfigName != null) {
            if (event.isAllowedChatCharacter()) {
                String text = event.codepointAsString();
                if (text != null && !text.isEmpty() && SESSION.editingConfigValue.length() + text.length() <= 32) {
                    int cursor = Math.max(0, Math.min(SESSION.editingConfigCursor, SESSION.editingConfigValue.length()));
                    SESSION.editingConfigValue = SESSION.editingConfigSelectAll
                            ? text
                            : SESSION.editingConfigValue.substring(0, cursor) + text + SESSION.editingConfigValue.substring(cursor);
                    SESSION.editingConfigCursor = SESSION.editingConfigSelectAll ? text.length() : cursor + text.length();
                    SESSION.editingConfigSelectAll = false;
                }
            }
            return true;
        }
        if (SESSION.itemReplacerEditorOpen) {
            SESSION.itemReplacerPanel.charTyped(event);
            return true;
        }
        if (isBodyEditorOpen()) {
            return true;
        }
        if (SESSION.configSearchFocused) {
            if (event.isAllowedChatCharacter()) {
                String text = event.codepointAsString();
                if (text != null && !text.isEmpty() && SESSION.configQuery.length() + text.length() <= 48) {
                    insertSearchText(text, true);
                }
            }
            return true;
        }
        if (SESSION.searchFocused) {
            if (event.isAllowedChatCharacter()) {
                String text = event.codepointAsString();
                if (text != null && !text.isEmpty() && SESSION.query.length() + text.length() <= 48) {
                    insertSearchText(text, false);
                }
            }
            return true;
        }
        if (SESSION.selectedModule != null && optionFor(SESSION.selectedModule).charTyped(event)) {
            return true;
        }
        return true;
    }
    private static void insertSearchText(String text, boolean config) {
        if (text == null || text.isEmpty()) return;
        String value = config ? SESSION.configQuery : SESSION.query;
        int cursor = Math.max(0, Math.min(config ? SESSION.configSearchCursor : SESSION.searchCursor, value.length()));
        boolean all = config ? SESSION.configSearchSelectAll : SESSION.searchSelectAll;
        String next = all ? text : value.substring(0, cursor) + text + value.substring(cursor);
        int nextCursor = all ? text.length() : cursor + text.length();
        setSearchValue(config, next, nextCursor, false);
    }

    public static boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        event = toDesignEvent(event);
        SESSION.pointerX = event.x();
        SESSION.pointerY = event.y();
        if (ClickGuiThemePopup.mouseClicked(event)) return true;
        if (ClickGuiModule.matchesCloseMouse(event)) {
            startClosing();
            return true;
        }
        if (SESSION.closing) return true;
        ModuleOption bindingOption = SESSION.bindingCoordinator.active();
        if (bindingOption != null && bindingOption.captureExternalMouseBind(event)) {
            return true;
        }
        if (SESSION.selectedHwEvents && SESSION.hwEventsPanel.mouseClicked(event, doubled)) {
            SESSION.searchFocused = false;
            SESSION.configSearchFocused = false;
            return true;
        }
        if (handleTabClick(event)) {
            return true;
        }
        if (SESSION.customCrosshairEditorOpen) {
            SESSION.searchFocused = false;
            SESSION.configSearchFocused = false;
            SESSION.customCrosshairPanel.mouseClicked(event.x(), event.y(), event.button());
            return true;
        }
        if (SESSION.itemReplacerEditorOpen) {
            SESSION.searchFocused = false;
            SESSION.configSearchFocused = false;
            SESSION.itemReplacerPanel.mouseClicked(event.x(), event.y(), event.button());
            return true;
        }
        if (handleConfigPanelClick(event, doubled)) {
            return true;
        }
        if (handleSearchClick(event)) {
            return true;
        }
        if (handleModuleClick(event, doubled)) {
            return true;
        }
        if (SESSION.selectedModule != null && optionFor(SESSION.selectedModule).mouseClicked(event, doubled)) {
            return true;
        }
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        return true;
    }
    public static boolean mouseReleased(MouseButtonEvent event) {
        event = toDesignEvent(event);
        if (ClickGuiThemePopup.mouseReleased(event)) return true;
        if (SESSION.customCrosshairEditorOpen) {
            SESSION.customCrosshairPanel.mouseReleased();
            return true;
        }
        if (SESSION.itemReplacerEditorOpen) {
            return true;
        }
        if (SESSION.selectedModule != null && optionFor(SESSION.selectedModule).mouseReleased(event)) {
            return true;
        }
        return true;
    }
    public static boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        event = toDesignEvent(event);
        dragX /= coordinateScale();
        dragY /= coordinateScale();
        if (ClickGuiThemePopup.mouseDragged(event)) return true;
        if (SESSION.customCrosshairEditorOpen) {
            SESSION.customCrosshairPanel.mouseDragged(event.x(), event.y());
            return true;
        }
        if (SESSION.itemReplacerEditorOpen) {
            return true;
        }
        if (SESSION.selectedModule != null && optionFor(SESSION.selectedModule).mouseDragged(event, dragX, dragY)) {
            return true;
        }
        return true;
    }
    public static boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX /= coordinateScale();
        mouseY /= coordinateScale();
        SESSION.pointerX = mouseX;
        SESSION.pointerY = mouseY;
        if (ClickGuiThemePopup.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (SESSION.selectedHwEvents && SESSION.hwEventsPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (SESSION.customCrosshairEditorOpen
                && inside(mouseX, mouseY, SESSION.lastGuiX + SIDE_WIDTH * SESSION.lastGuiScale, SESSION.lastGuiY + HEADER_HEIGHT * SESSION.lastGuiScale, MAIN_WIDTH * SESSION.lastGuiScale, BODY_HEIGHT * SESSION.lastGuiScale)) {
            SESSION.customCrosshairPanel.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        if (SESSION.itemReplacerEditorOpen
                && inside(mouseX, mouseY, SESSION.lastGuiX + SIDE_WIDTH * SESSION.lastGuiScale, SESSION.lastGuiY + HEADER_HEIGHT * SESSION.lastGuiScale, MAIN_WIDTH * SESSION.lastGuiScale, BODY_HEIGHT * SESSION.lastGuiScale)) {
            SESSION.itemReplacerPanel.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        if (SESSION.selectedModule != null && optionFor(SESSION.selectedModule).mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        float bodyX = SESSION.lastGuiX + SIDE_WIDTH * SESSION.lastGuiScale;
        float bodyY = SESSION.lastGuiY + HEADER_HEIGHT * SESSION.lastGuiScale;
        float inset = MAIN_PADDING * SESSION.lastGuiScale;
        float listX = bodyX + inset;
        float listY = bodyY + 1.0f * SESSION.lastGuiScale;
        float listW = MODULE_LIST_WIDTH * SESSION.lastGuiScale;
        float viewportH = BODY_HEIGHT * SESSION.lastGuiScale - 2.0f * SESSION.lastGuiScale;
        float settingsX = listX + listW + PANE_GAP * SESSION.lastGuiScale;
        float settingsW = SETTINGS_PANEL_WIDTH * SESSION.lastGuiScale;
        if (inside(mouseX, mouseY, settingsX, listY, settingsW, viewportH)) {
            SESSION.settingsScrollTarget = clamp(SESSION.settingsScrollTarget - (float) scrollY * SCROLL_STEP, 0.0f, SESSION.settingsScrollLimit);
            SESSION.settingsScrollAnimation.run(SESSION.settingsScrollTarget, 0.20, Easings.CUBIC_OUT, true);
            return true;
        }
        if (inside(mouseX, mouseY, listX, listY, listW, viewportH)) {
            SESSION.scrollTarget = clamp(SESSION.scrollTarget - (float) scrollY * SCROLL_STEP, 0.0f, SESSION.scrollLimit);
            SESSION.scrollAnimation.run(SESSION.scrollTarget, 0.20, Easings.CUBIC_OUT, true);
            return true;
        }
        return true;
    }
    public static boolean onFilesDrop(List<java.nio.file.Path> paths) {


        float cardX = SESSION.lastGuiX + 8.0f * SESSION.lastGuiScale;
        float cardY = SESSION.lastGuiY + (GUI_HEIGHT - 43.0f) * SESSION.lastGuiScale;
        float cardW = (SIDE_WIDTH - 16.0f) * SESSION.lastGuiScale;
        float cardH = 39.0f * SESSION.lastGuiScale;
        if (inside(SESSION.pointerX, SESSION.pointerY, cardX, cardY, cardW, cardH)) {
            float avatarX = cardX + 6.0f * SESSION.lastGuiScale;
            float avatarY = cardY + 6.0f * SESSION.lastGuiScale;
            float avatarSize = 27.0f * SESSION.lastGuiScale;
            boolean avatarTarget = inside(SESSION.pointerX, SESSION.pointerY,
                    avatarX, avatarY, avatarSize, avatarSize);
            if (ClickGuiProfileMedia.importDropped(paths, avatarTarget)) {
                return true;
            }
        }
        return false;
    }
    private static boolean handleModuleClick(MouseButtonEvent event, boolean doubled) {
        if (SESSION.selectedHwEvents || (SESSION.selectedCategory == ModuleCategory.CONFIGS && (SESSION.query == null || SESSION.query.isBlank())) || isBodyEditorOpen()) return false;

        float bodyX = SESSION.lastGuiX + SIDE_WIDTH * SESSION.lastGuiScale;
        float bodyY = SESSION.lastGuiY + HEADER_HEIGHT * SESSION.lastGuiScale;
        float inset = MAIN_PADDING * SESSION.lastGuiScale;
        float listX = bodyX + inset;
        float listY = bodyY + 1.0f * SESSION.lastGuiScale;
        float listW = MODULE_LIST_WIDTH * SESSION.lastGuiScale;
        float viewportH = BODY_HEIGHT * SESSION.lastGuiScale - 2.0f * SESSION.lastGuiScale;
        float rowW = listW;
        float rowH = MODULE_ROW_HEIGHT * SESSION.lastGuiScale;
        float gap = MODULE_ROW_GAP * SESSION.lastGuiScale;

        if ((event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                || event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                && inside(event.x(), event.y(), listX, listY, listW, viewportH)) {
            List<Module> modules = visibleModules();
            float firstRowY = listY - SESSION.scrollAnimation.get() * SESSION.lastGuiScale;
            float stride = rowH + gap;
            int index = (int) Math.floor((event.y() - firstRowY) / Math.max(stride, 0.0001f));
            if (index >= 0 && index < modules.size()) {
                float rowY = firstRowY + index * stride;
                if (inside(event.x(), event.y(), listX, rowY, rowW, rowH)) {
                    Module module = modules.get(index);
                    ModuleOption option = optionFor(module);
                    boolean bindBadgeClick = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                            && ModuleBindBadge.hit(event.x(), event.y(), listX, rowY, rowW, rowH,
                            option, SESSION.lastGuiScale);
                    boolean settingsIconClick = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                            && ModuleSettingsIcon.hit(event.x(), event.y(), listX, rowY, rowW, rowH,
                            option, SESSION.lastGuiScale);

                    if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || bindBadgeClick) {
                        option.beginExternalBinding();
                    } else if (settingsIconClick) {
                        selectModule(module);
                    } else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        module.toggle();
                    } else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        selectModule(module);
                    }
                    SESSION.searchFocused = false;
                    SESSION.configSearchFocused = false;
                    return true;
                }
            }
        }
        return false;
    }

    static boolean handleTabClick(MouseButtonEvent event) {
        if (event.button() != 0) return false;
        float x = SESSION.lastGuiX + 8.0f * SESSION.lastGuiScale;
        float y = SESSION.lastGuiY + SIDEBAR_CATEGORY_TOP * SESSION.lastGuiScale;
        float width = (SIDE_WIDTH - 16.0f) * SESSION.lastGuiScale;
        float rowH = SIDEBAR_CATEGORY_HEIGHT * SESSION.lastGuiScale;
        float gap = SIDEBAR_CATEGORY_GAP * SESSION.lastGuiScale;
        for (CategoryTab tab : TABS) {
            if (inside(event.x(), event.y(), x, y, width, rowH)) {
                SESSION.selectedCategory = tab.category();
                SESSION.selectedHwEvents = tab.hwEvents();
                SESSION.selectedModule = null;
                SESSION.customCrosshairEditorOpen = false;
                SESSION.itemReplacerEditorOpen = false;
                SESSION.searchFocused = false;
                SESSION.configSearchFocused = false;
                SESSION.hwEventsPanel.resetInteraction();
                SESSION.query = "";
                SESSION.searchCursor = 0;
                SESSION.searchSelectAll = false;
                SESSION.visibleModulesCacheCategory = null;
                resetScroll();
                return true;
            }
            y += rowH + gap;
        }
        return false;
    }
}
