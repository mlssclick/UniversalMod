package universalmod.gui.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.module.ModuleCategory;
import universalmod.config.ConfigManager;
import universalmod.lifecycle.Manager;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2D;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.font.FontType;
import universalmod.theme.ThemeColors;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static universalmod.gui.clickgui.ClickGuiController.*;

final class ClickGuiConfigPanel {
    private static final List<ConfigManager.ProfileInfo> PROFILE_INFO_SCRATCH = new ArrayList<>();
    private static List<String> filteredProfilesCache = List.of();
    private static String filteredProfilesNeedle = "";
    private static int filteredProfilesFingerprint;

    private ClickGuiConfigPanel() {
    }

    static void renderConfigsPanel(GuiGraphics graphics, float panelX, float panelY, float scale, float alpha) {
        float padding = MODULE_PANEL_PADDING_X * scale;
        float toolbarY = panelY + CONFIGS_TOOLBAR_TOP * scale;
        float toolbarH = CONFIGS_TOOLBAR_HEIGHT * scale;
        float radius = 7.0f * scale;
        float searchX = panelX + padding;
        float searchW = MAIN_WIDTH * scale - padding * 2.0f;
        boolean focused = SESSION.configSearchFocused;


        Render2D.blur(searchX, toolbarY, searchW, toolbarH, radius, 4.0f * scale, 0.42f,
                color(34, 34, 40, focused ? 220 : 196, alpha));
        Render2D.rect(searchX, toolbarY, searchW, toolbarH, radius,
                color(focused ? 58 : 45, focused ? 56 : 44, focused ? 64 : 50, focused ? 238 : 224, alpha));
        Render2D.outline(searchX, toolbarY, searchW, toolbarH, radius,
                (focused ? 0.75f : 0.42f) * scale,
                focused ? ThemeColors.clickGuiAccentColor(178, alpha) : ThemeColors.clickGuiMutedColor(66, alpha));

        String display = SESSION.configQuery.isEmpty() && !focused ? universalmod.service.language.LanguageManager.translate("configs.search") : SESSION.configQuery;
        int textColor = SESSION.configQuery.isEmpty() ? ThemeColors.clickGuiMutedColor(205, alpha) : ThemeColors.clickGuiTextColor(250, alpha);
        float textSize = 7.0f * scale;
        float textX = searchX + 10.0f * scale;
        float textY = toolbarY + (toolbarH - Render2D.textHeight(FontType.SEMIBOLD, display, textSize)) * 0.5f;
        Render2D.pushScissor(graphics, textX, toolbarY, Math.max(1.0f, searchW - 18.0f * scale), toolbarH);
        try {
            Render2D.text(FontType.SEMIBOLD, display, textX, textY, textSize, textColor);
        } finally {
            Render2D.popScissor(graphics);
        }
        if (focused && (UiFrameContext.nowMillis() / 340L) % 2L == 0L) {
            int caretIndex = Math.max(0, Math.min(SESSION.configSearchCursor, SESSION.configQuery.length()));
            float caretX = textX + Render2D.textWidth(FontType.SEMIBOLD, SESSION.configQuery.substring(0, caretIndex), textSize);
            float caretH = Math.min(toolbarH - 8.0f * scale, 10.0f * scale);
            float caretY = toolbarY + (toolbarH - caretH) * 0.5f;
            Render2D.rect(Math.min(caretX + 0.8f * scale, searchX + searchW - 8.0f * scale), caretY,
                    0.8f * scale, caretH, 0.22f * scale, color(255, 255, 255, 245, alpha));
        }

        renderConfigCards(graphics, panelX, panelY, scale, alpha, searchX,
                toolbarY + toolbarH + CONFIG_CARD_GAP_Y * scale);
    }
    static void renderConfigCards(GuiGraphics graphics, float panelX, float panelY, float scale, float alpha,
                                          float x, float y) {
        ConfigManager configManager = configManager();
        if (configManager == null) {
            return;
        }
        PROFILE_INFO_SCRATCH.clear();
        for (String profile : filteredConfigProfiles(configManager)) {
            PROFILE_INFO_SCRATCH.add(configManager.profileInfo(profile));
        }
        List<ConfigManager.ProfileInfo> profiles = PROFILE_INFO_SCRATCH;
        float cardsBottom = panelY + BODY_HEIGHT * scale;
        Render2D.pushScissor(graphics, panelX, y, MAIN_WIDTH * scale, Math.max(1.0f, cardsBottom - y));
        try {
            int itemCount = profiles.size() + 1;
            for (int i = 0; i < itemCount; i++) {
                int row = i / CONFIG_CARD_COLUMNS;
                int column = i % CONFIG_CARD_COLUMNS;
                float cardX = x + column * (CONFIG_CARD_WIDTH + CONFIG_CARD_GAP_X) * scale;
                float cardY = y + row * (CONFIG_CARD_HEIGHT + CONFIG_CARD_GAP_Y) * scale
                        - SESSION.scrollAnimation.get() * scale;
                if (cardY + CONFIG_CARD_HEIGHT * scale >= panelY && cardY <= panelY + BODY_HEIGHT * scale) {
                    if (i == profiles.size()) {
                        renderNewConfigCard(cardX, cardY, CONFIG_CARD_WIDTH * scale,
                                CONFIG_CARD_HEIGHT * scale, scale, alpha);
                    } else {
                        renderConfigCard(graphics, profiles.get(i), cardX, cardY, CONFIG_CARD_WIDTH * scale,
                                CONFIG_CARD_HEIGHT * scale, scale, alpha, configManager);
                    }
                }
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }
    static void renderNewConfigCard(float x, float y, float width, float height,
                                            float scale, float alpha) {
        double mouseX = SESSION.pointerX;
        double mouseY = SESSION.pointerY;
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        float radius = CONFIG_CARD_RADIUS * scale;
        Render2D.rect(x, y, width, height, radius,
                color(7, 10, 21, hovered ? 78 : 54, alpha));
        ClickGuiOutline.draw(x, y, width, height, radius, 0.72f * scale,
                ThemeColors.clickGuiMutedColor(hovered ? 92 : 54, alpha));

        String label = universalmod.service.language.LanguageManager.translate("configs.new");
        float iconSize = 10.0f * scale;
        float textSize = 8.0f * scale;
        float gap = 4.0f * scale;
        float textWidth = Render2D.textWidth(FontType.BOLD, label, textSize);
        float groupWidth = iconSize + gap + textWidth;
        float iconX = x + (width - groupWidth) * 0.5f;
        float iconY = y + (height - iconSize) * 0.5f;
        float textY = y + (height - textSize) * 0.5f - 0.45f * scale;
        int contentColor = color(255, 255, 255, hovered ? 255 : 238, alpha);
        renderIconNewGlyph("z", iconX, iconY, iconSize, contentColor);
        Render2D.text(FontType.BOLD, label, iconX + iconSize + gap, textY, textSize, contentColor);
    }
    static void renderConfigCard(GuiGraphics graphics, ConfigManager.ProfileInfo profileInfo,
                                         float x, float y, float width, float height,
                                         float scale, float alpha, ConfigManager configManager) {
        double mouseX = SESSION.pointerX;
        double mouseY = SESSION.pointerY;
        String profile = profileInfo.name();
        boolean active = profile.equals(configManager.activeProfile());
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        float radius = CONFIG_CARD_RADIUS * scale;
        float selected = active ? 1.0f : 0.0f;
        Render2D.rect(x, y, width, height, radius,
                color(7, 10, 21, Math.round(54.0f + 26.0f * selected), alpha));
        int outline = active
                ? ThemeColors.clickGuiAccentColor(112, alpha)
                : ThemeColors.clickGuiMutedColor(hovered ? 82 : 48, alpha);
        ClickGuiOutline.draw(x, y, width, height, radius, 0.72f * scale, outline);

        boolean deletable = !ConfigManager.DEFAULT_PROFILE.equals(profile);
        float actionSize = CONFIG_ACTION_SIZE * scale;
        float actionY = y + (height - actionSize) * 0.5f;
        float trashX = x + width - actionSize - CONFIG_CARD_SIDE_PADDING * scale;
        float folderX = deletable
                ? trashX - actionSize - CONFIG_ACTION_GAP * scale
                : trashX;
        float contentRight = folderX - 5.0f * scale;
        float contentLeft = x + 7.0f * scale;
        float renameSize = CONFIG_RENAME_SIZE * scale;
        float textY = y + 4.5f * scale;


        float renameY = textY + 1.0f * scale;

        boolean editing = profile.equals(SESSION.editingConfigName);
        String text = editing ? SESSION.editingConfigValue : profile;
        float textSize = 8.6f * scale;
        float textWidth = Render2D.textWidth(FontType.BOLD, text, textSize);
        float availableTitleWidth = Math.max(1.0f, contentRight - contentLeft);
        float renameGap = deletable ? CONFIG_RENAME_GAP * scale : 0.0f;
        float maxVisibleTextWidth = Math.max(1.0f, availableTitleWidth - (deletable ? renameSize + renameGap : 0.0f));
        float renameX = contentLeft;
        float textX = deletable ? renameX + renameSize + renameGap : contentLeft;
        Render2D.pushScissor(graphics, contentLeft, y,
                Math.max(1.0f, contentRight - contentLeft), height);
        try {
            Render2D.text(FontType.BOLD, text, textX, textY, textSize,
                    color(240, 240, 250, active ? 255 : 232, alpha));

            String details = profileDate(profileInfo) + universalmod.service.language.LanguageManager.translate("configs.created_by") + profileInfo.createdBy();
            float detailsSize = 5.6f * scale;
            Render2D.text(FontType.SEMIBOLD, details, contentLeft, y + 23.5f * scale,
                    detailsSize, color(245, 245, 250, active ? 238 : 212, alpha));
        } finally {
            Render2D.popScissor(graphics);
        }

        if (editing && !SESSION.editingConfigSelectAll && (UiFrameContext.nowMillis() / 340L) % 2L == 0L) {
            int caretIndex = Math.max(0, Math.min(SESSION.editingConfigCursor, text.length()));
            float caretAdvance = Render2D.textWidth(FontType.BOLD, text.substring(0, caretIndex), textSize);
            float caretX = Math.min(textX + caretAdvance + 1.0f * scale, contentRight);
            float caretH = 10.0f * scale;
            Render2D.rect(caretX, y + (height - caretH) * 0.5f - 5.0f * scale, 0.6f * scale, caretH,
                    0.3f * scale, color(255, 255, 255, 220, alpha));
        }
        if (editing && SESSION.editingConfigSelectAll && !text.isEmpty()) {
            float selectW = Math.min(textWidth + 2.0f * scale, Math.max(0.0f, contentRight - textX));
            Render2D.rect(textX - 1.0f * scale, y + 3.0f * scale, selectW, 11.5f * scale,
                    1.5f * scale, color(152, 125, 224, 72, alpha));
        }

        if (deletable) {
            boolean renameHovered = inside(mouseX, mouseY, renameX, renameY, renameSize, renameSize);
            float renameHover = animateConfigHover(SESSION.configRenameHoverAnimations, profile, renameHovered, 0.12);
            int renameColor = ColorUtil.lerpColor(
                    color(188, 187, 201, 205, alpha), color(255, 255, 255, 255, alpha), renameHover);
            renderDeltaActionGlyph("K", renameX, renameY, renameSize, renameColor);
        }

        boolean folderHovered = inside(mouseX, mouseY, folderX, actionY, actionSize, actionSize);
        float folderHover = animateConfigHover(SESSION.configFolderHoverAnimations, profile, folderHovered, 0.12);
        int folderColor = ColorUtil.lerpColor(
                color(255, 255, 255, 245, alpha), color(178, 180, 190, 235, alpha), folderHover);
        renderDeltaActionGlyph("x", folderX, actionY, actionSize, folderColor);

        if (deletable) {
            boolean closeHovered = inside(mouseX, mouseY, trashX, actionY, actionSize, actionSize);
            float deleteHover = animateConfigHover(SESSION.configDeleteHoverAnimations, profile, closeHovered, 0.14);
            int iconColor = ColorUtil.lerpColor(
                    color(240, 240, 246, 235, alpha), color(255, 32, 62, 255, alpha), deleteHover);
            renderDeltaActionGlyph("Z", trashX, actionY, actionSize, iconColor);
        }
    }
    static boolean handleConfigPanelClick(MouseButtonEvent event, boolean doubled) {
        if (SESSION.selectedCategory != ModuleCategory.CONFIGS || SESSION.selectedHwEvents || isBodyEditorOpen()
                || (SESSION.query != null && !SESSION.query.isBlank())) {
            return false;
        }
        ConfigManager configManager = configManager();
        if (configManager == null) {
            return false;
        }
        float panelX = SESSION.lastGuiX + SIDE_WIDTH * SESSION.lastGuiScale;
        float panelY = SESSION.lastGuiY + HEADER_HEIGHT * SESSION.lastGuiScale;
        float padding = MODULE_PANEL_PADDING_X * SESSION.lastGuiScale;
        float toolbarY = panelY + CONFIGS_TOOLBAR_TOP * SESSION.lastGuiScale;
        float toolbarH = CONFIGS_TOOLBAR_HEIGHT * SESSION.lastGuiScale;
        float searchX = panelX + padding;
        float searchW = MAIN_WIDTH * SESSION.lastGuiScale - padding * 2.0f;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inside(event.x(), event.y(), searchX, toolbarY, searchW, toolbarH)) {
            commitConfigRename();
            SESSION.searchFocused = false;
            SESSION.configSearchFocused = true;
            SESSION.configSearchCursor = SESSION.configQuery.length();
            SESSION.configSearchSelectAll = false;
            return true;
        }
        float cardsX = panelX + padding;
        float cardsY = toolbarY + toolbarH + CONFIG_CARD_GAP_Y * SESSION.lastGuiScale;
        float cardW = CONFIG_CARD_WIDTH * SESSION.lastGuiScale;
        float cardH = CONFIG_CARD_HEIGHT * SESSION.lastGuiScale;

        List<String> profiles = filteredConfigProfiles(configManager);
        int newIndex = profiles.size();
        int newRow = newIndex / CONFIG_CARD_COLUMNS;
        int newColumn = newIndex % CONFIG_CARD_COLUMNS;
        float newCardX = cardsX + newColumn * (CONFIG_CARD_WIDTH + CONFIG_CARD_GAP_X) * SESSION.lastGuiScale;
        float newCardY = cardsY + newRow * (CONFIG_CARD_HEIGHT + CONFIG_CARD_GAP_Y) * SESSION.lastGuiScale
                - SESSION.scrollAnimation.get() * SESSION.lastGuiScale;
        float cardsBottom = panelY + BODY_HEIGHT * SESSION.lastGuiScale;
        boolean insideCardsViewport = inside(event.x(), event.y(), panelX, cardsY,
                MAIN_WIDTH * SESSION.lastGuiScale, Math.max(1.0f, cardsBottom - cardsY));
        if (event.button() == 0 && insideCardsViewport
                && inside(event.x(), event.y(), newCardX, newCardY, cardW, cardH)) {
            SESSION.searchFocused = false;
            SESSION.configSearchFocused = false;
            String created = configManager.createProfile();
            SESSION.editingConfigName = created;
            SESSION.editingConfigValue = created;
            SESSION.editingConfigCursor = created.length();
            SESSION.editingConfigSelectAll = false;
            return true;
        }

        if (event.button() != 0 || !inside(event.x(), event.y(), panelX, cardsY,
                MAIN_WIDTH * SESSION.lastGuiScale, Math.max(1.0f, cardsBottom - cardsY))) {
            return false;
        }

        if (handleExistingProfileClick(event, doubled, configManager, profiles, cardsX, cardsY, cardW, cardH)) {
            return true;
        }

        commitConfigRename();
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        return true;
    }
    private static boolean handleExistingProfileClick(
            MouseButtonEvent event,
            boolean doubled,
            ConfigManager configManager,
            List<String> profiles,
            float cardsX,
            float cardsY,
            float cardW,
            float cardH
    ) {
        for (int i = 0; i < profiles.size(); i++) {
            String profile = profiles.get(i);
            int row = i / CONFIG_CARD_COLUMNS;
            int column = i % CONFIG_CARD_COLUMNS;
            float cardX = cardsX + column * (CONFIG_CARD_WIDTH + CONFIG_CARD_GAP_X) * SESSION.lastGuiScale;
            float cardY = cardsY + row * (CONFIG_CARD_HEIGHT + CONFIG_CARD_GAP_Y) * SESSION.lastGuiScale
                    - SESSION.scrollAnimation.get() * SESSION.lastGuiScale;
            if (!inside(event.x(), event.y(), cardX, cardY, cardW, cardH)) {
                continue;
            }
            SESSION.searchFocused = false;
            SESSION.configSearchFocused = false;
            boolean deletable = !ConfigManager.DEFAULT_PROFILE.equals(profile);
            float actionSize = CONFIG_ACTION_SIZE * SESSION.lastGuiScale;
            float actionY = cardY + (cardH - actionSize) * 0.5f;
            float trashX = cardX + cardW - actionSize - CONFIG_CARD_SIDE_PADDING * SESSION.lastGuiScale;
            float folderX = deletable
                    ? trashX - actionSize - CONFIG_ACTION_GAP * SESSION.lastGuiScale
                    : trashX;

            if (inside(event.x(), event.y(), folderX, actionY, actionSize, actionSize)) {
                configManager.revealProfileFile(profile);
                return true;
            }
            if (deletable && inside(event.x(), event.y(), trashX, actionY, actionSize, actionSize)) {
                deleteProfile(configManager, profile);
                return true;
            }
            if (deletable && insideRenameAction(event, cardX, cardY)) {
                beginProfileRename(profile);
                return true;
            }
            if (doubled && deletable) {
                beginProfileRename(profile);
                return true;
            }
            commitConfigRename();
            configManager.selectProfile(profile);
            resetScroll();
            return true;
        }
        return false;
    }

    private static boolean insideRenameAction(MouseButtonEvent event, float cardX, float cardY) {
        float renameSize = CONFIG_RENAME_SIZE * SESSION.lastGuiScale;
        float renameX = cardX + 7.0f * SESSION.lastGuiScale;
        float renameY = cardY + 5.5f * SESSION.lastGuiScale;
        return inside(event.x(), event.y(), renameX, renameY, renameSize, renameSize);
    }

    private static void deleteProfile(ConfigManager configManager, String profile) {
        if (!configManager.deleteProfile(profile)) {
            return;
        }
        SESSION.configDeleteHoverAnimations.remove(profile);
        SESSION.configFolderHoverAnimations.remove(profile);
        SESSION.configRenameHoverAnimations.remove(profile);
        if (profile.equals(SESSION.editingConfigName)) {
            SESSION.editingConfigName = null;
            SESSION.editingConfigValue = "";
        }
    }

    private static void beginProfileRename(String profile) {
        SESSION.editingConfigName = profile;
        SESSION.editingConfigValue = profile;
        SESSION.editingConfigCursor = profile.length();
        SESSION.editingConfigSelectAll = false;
    }

    static ConfigManager configManager() {
        Manager manager = Manager.getInstance();
        return manager == null ? null : manager.getConfigManager();
    }
    static boolean handleConfigEditKey(KeyEvent event) {
        if (SESSION.editingConfigName == null) return false;
        int key = event.key();
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        int cursor = Math.max(0, Math.min(SESSION.editingConfigCursor, SESSION.editingConfigValue.length()));
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            SESSION.editingConfigName = null; SESSION.editingConfigValue = ""; SESSION.editingConfigCursor = 0; SESSION.editingConfigSelectAll = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commitConfigRename(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_A) { SESSION.editingConfigSelectAll = true; SESSION.editingConfigCursor = SESSION.editingConfigValue.length(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_C) { if (SESSION.editingConfigSelectAll) setClipboard(SESSION.editingConfigValue); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_X) {
            if (SESSION.editingConfigSelectAll) { setClipboard(SESSION.editingConfigValue); SESSION.editingConfigValue=""; SESSION.editingConfigCursor=0; SESSION.editingConfigSelectAll=false; }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip = clipboard().replace("\r", "").replace("\n", " ");
            if (!clip.isEmpty()) {
                int room = Math.max(0, 32 - (SESSION.editingConfigSelectAll ? 0 : SESSION.editingConfigValue.length()));
                if (clip.length() > room) clip = clip.substring(0, room);
                SESSION.editingConfigValue = SESSION.editingConfigSelectAll ? clip
                        : SESSION.editingConfigValue.substring(0,cursor)+clip+SESSION.editingConfigValue.substring(cursor);
                SESSION.editingConfigCursor = SESSION.editingConfigSelectAll ? clip.length() : cursor+clip.length();
                SESSION.editingConfigSelectAll=false;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (SESSION.editingConfigSelectAll) { SESSION.editingConfigValue=""; SESSION.editingConfigCursor=0; SESSION.editingConfigSelectAll=false; }
            else if (cursor>0) { int from=ctrl?previousWordBoundary(SESSION.editingConfigValue,cursor):cursor-1; SESSION.editingConfigValue=SESSION.editingConfigValue.substring(0,from)+SESSION.editingConfigValue.substring(cursor); SESSION.editingConfigCursor=from; }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (SESSION.editingConfigSelectAll) { SESSION.editingConfigValue=""; SESSION.editingConfigCursor=0; SESSION.editingConfigSelectAll=false; }
            else if (cursor<SESSION.editingConfigValue.length()) { int to=ctrl?nextWordBoundary(SESSION.editingConfigValue,cursor):cursor+1; SESSION.editingConfigValue=SESSION.editingConfigValue.substring(0,cursor)+SESSION.editingConfigValue.substring(to); }
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            if (SESSION.editingConfigSelectAll) cursor = key==GLFW.GLFW_KEY_LEFT?0:SESSION.editingConfigValue.length();
            else if (key==GLFW.GLFW_KEY_LEFT) cursor=ctrl?previousWordBoundary(SESSION.editingConfigValue,cursor):Math.max(0,cursor-1);
            else cursor=ctrl?nextWordBoundary(SESSION.editingConfigValue,cursor):Math.min(SESSION.editingConfigValue.length(),cursor+1);
            SESSION.editingConfigCursor=cursor; SESSION.editingConfigSelectAll=false; return true;
        }
        if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) { SESSION.editingConfigCursor=key==GLFW.GLFW_KEY_HOME?0:SESSION.editingConfigValue.length(); SESSION.editingConfigSelectAll=false; return true; }
        return true;
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
    private static int previousWordBoundary(String text, int cursor) {
        int i=Math.max(0,Math.min(cursor,text.length())); while(i>0&&Character.isWhitespace(text.charAt(i-1)))i--; while(i>0&&!Character.isWhitespace(text.charAt(i-1)))i--; return i;
    }
    private static int nextWordBoundary(String text, int cursor) {
        int i=Math.max(0,Math.min(cursor,text.length())); while(i<text.length()&&!Character.isWhitespace(text.charAt(i)))i++; while(i<text.length()&&Character.isWhitespace(text.charAt(i)))i++; return i;
    }
    static void commitConfigRename() {
        if (SESSION.editingConfigName == null) {
            return;
        }
        ConfigManager configManager = configManager();
        if (configManager != null) {
            String value = SESSION.editingConfigValue == null ? "" : SESSION.editingConfigValue.trim();
            if (!value.isBlank() && !value.equals(SESSION.editingConfigName)) {
                configManager.renameProfile(SESSION.editingConfigName, value);
            }
        }
        SESSION.editingConfigName = null;
        SESSION.editingConfigValue = "";
        SESSION.editingConfigCursor = 0;
        SESSION.editingConfigSelectAll = false;
    }
    static List<String> filteredConfigProfiles(ConfigManager configManager) {
        Collection<String> source = configManager.profiles();
        String needle = SESSION.configQuery == null ? "" : SESSION.configQuery.trim().toLowerCase(Locale.ROOT);
        int fingerprint = 1;
        for (String name : source) {
            fingerprint = 31 * fingerprint + (name == null ? 0 : name.hashCode());
        }
        fingerprint = 31 * fingerprint + source.size();

        if (needle.equals(filteredProfilesNeedle) && fingerprint == filteredProfilesFingerprint) {
            return filteredProfilesCache;
        }

        ArrayList<String> result = new ArrayList<>(source.size());
        for (String name : source) {
            if (name == null) {
                continue;
            }
            if (needle.isBlank() || name.toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(name);
            }
        }
        result.sort(ClickGuiController::compareNaturally);
        filteredProfilesNeedle = needle;
        filteredProfilesFingerprint = fingerprint;
        filteredProfilesCache = List.copyOf(result);
        return filteredProfilesCache;
    }
    static int compareNaturally(String left, String right) {
        if (ConfigManager.DEFAULT_PROFILE.equals(left)) {
            return ConfigManager.DEFAULT_PROFILE.equals(right) ? 0 : -1;
        }
        if (ConfigManager.DEFAULT_PROFILE.equals(right)) {
            return 1;
        }

        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftRunStart = leftIndex;
                int rightRunStart = rightIndex;
                while (leftIndex < left.length() && Character.isDigit(left.charAt(leftIndex))) leftIndex++;
                while (rightIndex < right.length() && Character.isDigit(right.charAt(rightIndex))) rightIndex++;

                int leftNumberStart = leftRunStart;
                int rightNumberStart = rightRunStart;
                while (leftNumberStart + 1 < leftIndex && left.charAt(leftNumberStart) == '0') leftNumberStart++;
                while (rightNumberStart + 1 < rightIndex && right.charAt(rightNumberStart) == '0') rightNumberStart++;

                int lengthCompare = Integer.compare(leftIndex - leftNumberStart, rightIndex - rightNumberStart);
                if (lengthCompare != 0) return lengthCompare;
                for (int offset = 0; offset < leftIndex - leftNumberStart; offset++) {
                    int digitCompare = Character.compare(
                            left.charAt(leftNumberStart + offset), right.charAt(rightNumberStart + offset));
                    if (digitCompare != 0) return digitCompare;
                }
                int zeroCompare = Integer.compare(leftIndex - leftRunStart, rightIndex - rightRunStart);
                if (zeroCompare != 0) return zeroCompare;
                continue;
            }

            int characterCompare = Character.compare(
                    Character.toLowerCase(leftChar), Character.toLowerCase(rightChar));
            if (characterCompare != 0) return characterCompare;
            leftIndex++;
            rightIndex++;
        }
        int lengthCompare = Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
        return lengthCompare != 0 ? lengthCompare : left.compareTo(right);
    }
    static float animateConfigHover(Map<String, SmoothAnimation> animations, String profile,
                                            boolean hovered, double durationSeconds) {
        SmoothAnimation animation = animations.computeIfAbsent(profile, ignored -> new SmoothAnimation());
        animation.run(hovered ? 1.0 : 0.0, durationSeconds, Easings.CUBIC_OUT, true);
        animation.update();
        return clamp(animation.get(), 0.0f, 1.0f);
    }
    static String todayDate() {
        return LocalDate.now().format(FOOTER_DATE_FORMAT);
    }
    static String profileDate(ConfigManager.ProfileInfo profileInfo) {
        long createdAt = Math.max(0L, profileInfo.createdAt());
        return Instant.ofEpochMilli(createdAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(PROFILE_DATE_FORMAT);
    }
}
