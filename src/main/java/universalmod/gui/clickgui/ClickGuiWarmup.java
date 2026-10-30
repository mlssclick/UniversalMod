package universalmod.gui.clickgui;


import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import universalmod.lifecycle.Manager;
import universalmod.module.Module;
import universalmod.module.ModuleManager;
import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.service.language.LanguageManager;

import java.util.List;


final class ClickGuiWarmup {
    private ClickGuiWarmup() {
    }

    static boolean warmupText() {
        ClickGuiSession session = ClickGuiController.SESSION;
        if (session.textWarmed) {
            return true;
        }
        if (!session.warmupBasePrepared) {
            prepareBase(session);
        }
        int budget = 10;
        while (budget-- > 0 && session.warmupModuleIndex < session.warmupModules.size()) {
            Module module = session.warmupModules.get(session.warmupModuleIndex++);
            Render2D.warmupText(FontType.BOLD, module.getDisplayName(), 7.7f * session.warmupScale);
        }
        if (session.warmupModuleIndex >= session.warmupModules.size()) {
            session.warmupModules = List.of();
            session.textWarmed = true;
        }
        return session.textWarmed;
    }

    static boolean warmupGraphics(DrawContext graphics) {
        ClickGuiSession session = ClickGuiController.SESSION;
        if (session.graphicsWarmupStage >= 4 || graphics == null) {
            return true;
        }
        Render2D.beginFrame(graphics);
        try {
            float x = 0.25f;
            float y = 0.25f;
            switch (session.graphicsWarmupStage) {
                case 0 -> {
                    Render2D.rect(x, y, 1.0f, 1.0f, 0.5f, ColorUtil.rgba(255, 255, 255, 1));
                    Render2D.outline(x, y, 1.0f, 1.0f, 0.5f, 0.25f, ColorUtil.rgba(255, 255, 255, 1));
                }
                case 1 -> Render2D.image(ClickGuiController.SIDEBAR_AVATAR, x, y, 1.0f, 1.0f, 0.0f,
                        ColorUtil.rgba(255, 255, 255, 1));
                case 2 -> Render2D.blur(x, y, 2.0f, 2.0f, 0.5f, 2.0f, 0.55f,
                        ColorUtil.rgba(8, 10, 18, 1));
                case 3 -> {
                    Render2D.text(FontType.BOLD, LanguageManager.translate("clickgui.home"), x, y, 7.2f,
                            ColorUtil.rgba(255, 255, 255, 1));
                    Render2D.text(FontType.SEMIBOLD, LanguageManager.translate("clickgui.search"), x, y, 6.5f,
                            ColorUtil.rgba(255, 255, 255, 1));
                }
                default -> {
                }
            }
        } finally {
            Render2D.flush();
        }
        session.graphicsWarmupStage++;
        return session.graphicsWarmupStage >= 4;
    }

    private static void prepareBase(ClickGuiSession session) {
        session.warmupBasePrepared = true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getWindow() != null) {
            float coordinateScale = ClickGuiController.coordinateScale();
            float designWidth = Math.max(1.0f, client.getWindow().getScaledWidth() / coordinateScale);
            float designHeight = Math.max(1.0f, client.getWindow().getScaledHeight() / coordinateScale);
            session.warmupScale = Math.min(designWidth / ClickGuiController.GUI_WIDTH,
                    designHeight / ClickGuiController.GUI_HEIGHT) * ClickGuiController.DESIGN_SCALE;
            session.warmupScale = Math.min(1.0f, Math.max(0.42f, session.warmupScale));
            try {
                client.getTextureManager().getTexture(
                        new Identifier("universalmod", "images/clickgui/avatar.png"));
            } catch (RuntimeException failure) {
            
        }
        }

        Render2D.warmupText(FontType.SEMIBOLD, LanguageManager.translate("clickgui.search"), 6.5f * session.warmupScale);
        Render2D.warmupText(FontType.SEMIBOLD, LanguageManager.translate("clickgui.home"), 7.2f * session.warmupScale);
        Render2D.warmupText(FontType.BOLD, "Module settings", 7.1f * session.warmupScale);
        Render2D.warmupText(FontType.SEMIBOLD, "No configurable settings", 5.8f * session.warmupScale);
        Render2D.warmupText(FontType.SEMIBOLD, "Enabled", 4.7f * session.warmupScale);
        Render2D.warmupText(FontType.SEMIBOLD, "Module", 4.7f * session.warmupScale);
        Render2D.warmupText(FontType.GUI_ICONS, "RABS", 14.0f * session.warmupScale);
        for (CategoryTab tab : ClickGuiController.TABS) {
            Render2D.warmupText(FontType.BOLD, tab.displayLabel(), 7.65f * session.warmupScale);
        }
        Render2D.warmupText(FontType.SEMIBOLD, "Visuals feature for gameplay", 5.8f * session.warmupScale);
        Render2D.warmupText(FontType.SEMIBOLD, "Utilites for best game experience", 5.8f * session.warmupScale);
        Render2D.warmupText(FontType.SEMIBOLD, "Miscellanius for xz", 5.8f * session.warmupScale);
        Render2D.warmupText(FontType.BOLD, "ConfigВсеСолоДуоТриоКлан", 7.3f * session.warmupScale);
        Render2D.warmupText(FontType.SEMIBOLD, "Search events...", 7.0f * session.warmupScale);
        Render2D.warmupText(FontType.SEMIBOLD, "Search configs...", 7.0f * session.warmupScale);
        Render2D.warmupText(FontType.BOLD, LanguageManager.translate("configs.new"), 8.0f * session.warmupScale);
        Render2D.warmupText(FontType.BOLD, "Отображать ивенты: 11 из 11Анархии", 8.0f * session.warmupScale);
        Render2D.warmupText(FontType.SEMIBOLD, "SoloDuoTrioClan1-1718-3839-5758-74", 6.7f * session.warmupScale);

        ModuleManager manager = Manager.getModules();
        session.warmupModules = manager == null ? List.of() : List.copyOf(manager.getModules());
        session.warmupModuleIndex = 0;
    }
}
