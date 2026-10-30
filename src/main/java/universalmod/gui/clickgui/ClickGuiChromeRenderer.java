package universalmod.gui.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import universalmod.module.ModuleCategory;
import universalmod.module.misc.serverhelper.ServerInfoService;
import universalmod.service.language.LanguageManager;
import universalmod.gui.clickgui.component.option.OptionStyle;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.theme.ThemeColors;

import static universalmod.gui.clickgui.ClickGuiController.*;

final class ClickGuiChromeRenderer {
    private ClickGuiChromeRenderer() {
    }

    static void renderHeaders(GuiGraphics graphics, float guiX, float guiY, float scale, float alpha) {
        float mainX = guiX + SIDE_WIDTH * scale;
        float headerY = guiY;
        float inset = 10.5f * scale;


        float textSize = 9.8f * scale;
        int textColor = ThemeColors.clickGuiTextColor(255, alpha);
        float textY = OptionStyle.centeredTextY(
                FontType.BOLD, LanguageManager.translate("clickgui.home"), textSize, headerY, HEADER_HEIGHT * scale
        );

        float cursorX = mainX + inset;
        cursorX = renderBreadcrumbToken(LanguageManager.translate("clickgui.home"), cursorX, textY, textSize, textColor, scale);
        cursorX = renderBreadcrumbSeparator(cursorX, textY, textSize, textColor, scale);
        cursorX = renderBreadcrumbToken(activeTitle(), cursorX, textY, textSize, textColor, scale);

        if (!SESSION.selectedHwEvents && SESSION.selectedCategory != ModuleCategory.CONFIGS && SESSION.selectedModule != null) {
            cursorX = renderBreadcrumbSeparator(cursorX, textY, textSize, textColor, scale);
            cursorX = renderBreadcrumbToken(SESSION.selectedModule.getDisplayName(), cursorX, textY, textSize, textColor, scale);
        }

        if (isBodyEditorOpen()) {
            cursorX = renderBreadcrumbSeparator(cursorX, textY, textSize, textColor, scale);
            renderBreadcrumbToken(LanguageManager.translate("clickgui.editor"), cursorX, textY, textSize, textColor, scale);
        }
    }

    private static float renderBreadcrumbToken(String text, float x, float y, float size, int color, float scale) {
        String safe = text == null ? "" : text;
        Render2D.text(FontType.BOLD, safe, x, y, size, color);
        return x + Render2D.textWidth(FontType.BOLD, safe, size) + 7.0f * scale;
    }

    private static float renderBreadcrumbSeparator(float x, float y, float size, int color, float scale) {
        Render2D.text(FontType.BOLD, ">", x, y, size, color);
        return x + Render2D.textWidth(FontType.BOLD, ">", size) + 7.0f * scale;
    }

    static void renderTabs(GuiGraphics graphics, float guiX, float guiY, float scale, float alpha) {
        double mouseX = SESSION.pointerX;
        double mouseY = SESSION.pointerY;
        float x = guiX + 8.0f * scale;
        float y = guiY + SIDEBAR_CATEGORY_TOP * scale;
        float width = (SIDE_WIDTH - 16.0f) * scale;
        float rowH = SIDEBAR_CATEGORY_HEIGHT * scale;
        float gap = SIDEBAR_CATEGORY_GAP * scale;
        float step = rowH + gap;
        int activeIndex = activeTabIndex();
        if (!SESSION.categorySelectionInitialized) {
            SESSION.categorySelectionAnimation.set(activeIndex);
            SESSION.categorySelectionInitialized = true;
        } else if (Math.abs(SESSION.categorySelectionAnimation.getToValue() - activeIndex) > 0.001D) {
            SESSION.categorySelectionAnimation.run(activeIndex, 0.19D, Easings.QUART_OUT);
        }
        SESSION.categorySelectionAnimation.update();
        float animatedIndex = SESSION.categorySelectionAnimation.get();
        float selectorY = y + animatedIndex * step;

        new GuiSurface(
                x, selectorY, width, rowH,
                rowH * 0.29f,
                alpha * 0.98f, 0.48f,
                ColorUtil.rgba(38, 37, 43, 255),
                ThemeColors.clickGuiAccentColor(255, 1.0f)
        ).render(graphics);
        Render2D.rect(x, selectorY, width, rowH, rowH * 0.29f,
                ThemeColors.clickGuiAccentColor(26, alpha));

        for (int index = 0; index < TABS.size(); index++) {
            CategoryTab tab = TABS.get(index);
            boolean active = tab.active();
            boolean hovered = inside(mouseX, mouseY, x, y, width, rowH);
            if (!active && hovered) {
                new GuiSurface(
                        x, y, width, rowH,
                        4.5f * scale,
                        alpha * 0.72f,
                        0.16f,
                        ColorUtil.rgba(23, 22, 29, 255),
                        ColorUtil.rgba(246, 245, 248, 255)
                ).render(graphics);
            }

            float selectionWeight = 1.0f - Math.min(1.0f, Math.abs(animatedIndex - index));
            float iconSize = 14.5f * scale;
            renderCategoryIcon(tab.label(), x + 7.0f * scale, y + (rowH - iconSize) * 0.5f,
                    iconSize, ColorUtil.lerpColor(
                            ThemeColors.clickGuiMutedColor(240, alpha),
                            ThemeColors.clickGuiAccentColor(255, alpha),
                            selectionWeight
                    ));
            String tabLabel = tab.displayLabel();
            float tabTextSize = 9.25f * scale;
            float tabTextY = OptionStyle.centeredTextY(FontType.BOLD, tabLabel, tabTextSize, y, rowH);
            Render2D.text(FontType.BOLD, tabLabel, x + 26.5f * scale, tabTextY, tabTextSize,
                    ColorUtil.lerpColor(
                            ThemeColors.clickGuiMutedColor(245, alpha),
                            ThemeColors.clickGuiTextColor(255, alpha),
                            selectionWeight
                    ));
            y += rowH + gap;
        }
        renderFooter(graphics, guiX, guiY, scale, alpha);
    }

    private static int activeTabIndex() {
        for (int index = 0; index < TABS.size(); index++) {
            if (TABS.get(index).active()) {
                return index;
            }
        }
        return 0;
    }
    static void renderPanelShader(GuiGraphics graphics, float x, float y, float scale, float alpha) {
        float width = GUI_WIDTH * scale;
        float height = GUI_HEIGHT * scale;
        float radius = 8.0f * scale;


        Render2D.blur(x, y, width, height, radius, 9.0f * scale, 0.72f,
                ThemeColors.clickGuiBlurColor(232, alpha));
        new GuiSurface(
                x, y, width, height, radius, alpha,
                0.42f,
                ThemeColors.clickGuiPanelColor(255, 1.0f),
                ThemeColors.clickGuiAccentColor(255, 1.0f)
        ).render(graphics);

    }
    static void renderFooter(GuiGraphics graphics, float guiX, float guiY, float scale, float alpha) {
        float cardX = guiX + 8.0f * scale;
        float cardY = guiY + (GUI_HEIGHT - 43.0f) * scale;
        float cardW = (SIDE_WIDTH - 16.0f) * scale;
        float cardH = 39.0f * scale;
        float radius = 5.5f * scale;

        Render2D.rect(cardX, cardY, cardW, cardH, radius, color(11, 12, 17, 242, alpha));
        Render2D.rect(cardX, cardY, cardW, cardH, radius, color(30, 26, 44, 70, alpha));

        float avatarX = cardX + 6.0f * scale;
        float avatarY = cardY + 6.0f * scale;
        float avatarSize = 27.0f * scale;
        renderCurrentPlayerHead(avatarX, avatarY, avatarSize, alpha);

        float textX = avatarX + avatarSize + 6.5f * scale;
        Render2D.text(FontType.BOLD, currentPlayerName(), textX, cardY + 8.0f * scale,
                8.15f * scale, ThemeColors.clickGuiTextColor(252, alpha));
        Render2D.text(FontType.SEMIBOLD, currentPresence(), textX, cardY + 20.0f * scale,
                5.75f * scale, ThemeColors.clickGuiMutedColor(232, alpha));
    }
    static void renderCurrentPlayerHead(float x, float y, float size, float alpha) {
        Identifier customAvatar = ClickGuiProfileMedia.avatar();
        if (customAvatar != null) {
            Render2D.image(customAvatar.toString(), x, y, size, size, 4.8f * SESSION.lastGuiScale,
                    color(255, 255, 255, 255, alpha));
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player instanceof AbstractClientPlayer player) {
            String texture = player.getSkin().body().texturePath().toString();
            int tint = color(255, 255, 255, 255, alpha);
            Render2D.imageUvNearest(texture, x, y, size, size, 4.8f * SESSION.lastGuiScale,
                    1.0f, 0.125f, 0.125f, 0.25f, 0.25f, tint);
            Render2D.imageUvNearest(texture, x, y, size, size, 4.8f * SESSION.lastGuiScale,
                    1.0f, 0.625f, 0.125f, 0.75f, 0.25f, tint);
            return;
        }
        Render2D.image(SIDEBAR_AVATAR, x, y, size, size, 4.8f * SESSION.lastGuiScale,
                color(255, 255, 255, 255, alpha));
    }
    static String currentPlayerName() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            String playerName = client.player.getName().getString();
            if (playerName != null && !playerName.isBlank()) {
                return playerName;
            }
        }
        if (client != null && client.getUser() != null) {
            String userName = client.getUser().getName();
            if (userName != null && !userName.isBlank()) {
                return userName;
            }
        }
        return LanguageManager.translate("clickgui.player_fallback");
    }
    static String currentPresence() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.hasSingleplayerServer()) {
            try {
                if (client.getSingleplayerServer() != null) {
                    String levelName = client.getSingleplayerServer().getWorldData().getLevelName();
                    if (levelName != null && !levelName.isBlank()) {
                        return levelName;
                    }
                }
            } catch (RuntimeException ignored) {
            }
            return LanguageManager.translate("clickgui.singleplayer");
        }
        return ServerInfoService.serverAddress();
    }

    static String categoryIcon(String label) {
        return switch (label) {
            case "Render" -> "#";
            case "Utils" -> "A";
            case "Misc" -> "B";
            case "Hw Events" -> "S";
            case "Configs" -> "h";
            default -> "A";
        };
    }
    static void renderCategoryIcon(String label, float x, float y, float size, int color) {
        String icon = categoryIcon(label);
        FontType font = switch (icon) {
            case "#" -> FontType.DELTA_ICONS;
            case "h" -> FontType.ICONNEW;
            default -> FontType.GUI_ICONS;
        };


        float visible = switch (label) {
            case "Render" -> 0.68f;
            case "Hw Events" -> 0.70f;
            default -> 0.66f;
        };
        ClickGuiMsdfIcons.drawCentered(font, icon, x, y, size, size, visible, color);
    }

    static void renderIconNewGlyph(String icon, float x, float y, float size, int color) {
        float visible = "z".equals(icon) ? 0.72f : 0.76f;
        ClickGuiMsdfIcons.drawCentered(FontType.ICONNEW, icon, x, y, size, size, visible, color);
    }

    static void renderDeltaActionGlyph(String glyph, float x, float y, float size, int color) {
        ClickGuiMsdfIcons.drawCentered(FontType.DELTA_ICONS, glyph, x, y, size, size, 0.76f, color);
    }

    static void renderRect(float rootX, float rootY, float x, float y, float width, float height, float radius, float scale, int color) {
        Render2D.rect(rootX + x * scale, rootY + y * scale, width * scale, height * scale, radius * scale, color);
    }
    static void renderRect(float x, float y, float width, float height, float r1, float r2, float r3, float r4, float scale, int color) {
        Render2D.rect(x, y, width * scale, height * scale, r1 * scale, r2 * scale, r3 * scale, r4 * scale, color);
    }
    static void renderSeparator(float x, float y, float width, float height, int color) {
        Render2D.rect(x, y, width, height, 0.0f, color);
    }
    static int color(int red, int green, int blue, int alpha, float alphaMultiplier) {
        return ColorUtil.rgba(red, green, blue, Math.round(alpha * alphaMultiplier));
    }
}
