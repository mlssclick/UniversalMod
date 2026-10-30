package universalmod.gui.clickgui.component;

import universalmod.module.Module;
import universalmod.setting.Setting;
import universalmod.setting.BindSetting;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ButtonSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.MultiModeSetting;
import universalmod.setting.NumberSetting;
import universalmod.setting.StringSetting;
import universalmod.gui.clickgui.component.option.BindOption;
import universalmod.gui.clickgui.component.option.BooleanOption;
import universalmod.gui.clickgui.component.option.ButtonOption;
import universalmod.gui.clickgui.component.option.ClickGuiOption;
import universalmod.gui.clickgui.component.option.ColorOption;
import universalmod.gui.clickgui.component.option.MultiOption;
import universalmod.gui.clickgui.component.option.SingleOption;
import universalmod.gui.clickgui.component.option.SliderOption;
import universalmod.gui.clickgui.component.option.TextOption;

import java.util.ArrayList;
import java.util.List;

public final class ModuleOptionFactory {
    private ModuleOptionFactory() {
    }

    public static ModuleOption create(Module module) {
        return new ModuleOption(module);
    }

    public static ModuleOption create(Module module, ModuleBindingCoordinator bindingCoordinator) {
        return new ModuleOption(module, bindingCoordinator);
    }

    static boolean hasSupportedSettings(Module module) {
        if (module == null) {
            return false;
        }
        for (Setting setting : module.getSettings()) {
            if (isSupported(setting)) {
                return true;
            }
        }
        return false;
    }

    static List<ClickGuiOption> createSettings(Module module) {
        List<ClickGuiOption> options = new ArrayList<>();
        List<ClickGuiOption> buttonOptions = new ArrayList<>();

        for (Setting setting : module.getSettings()) {


            if (isPickerAlphaCompanion(module, setting)) {
                continue;
            }

            ClickGuiOption option;
            if (setting instanceof ColorSetting colorSetting) {
                option = new ColorOption(colorSetting, linkedAlphaSetting(module, colorSetting));
            } else {
                option = createSettingOption(setting);
            }
            if (option == null) {
                continue;
            }
            option.setSetting(setting);
            if (setting instanceof ButtonSetting && !("ServerHelper".equals(module.getClass().getSimpleName())
                    && "EndHelper".equals(setting.getName()))) {
                buttonOptions.add(option);
            } else {
                options.add(option);
            }
        }

        options.addAll(buttonOptions);
        return List.copyOf(options);
    }

    static boolean isSupported(Setting setting) {
        return setting instanceof BindSetting
                || setting instanceof ModeSetting
                || setting instanceof MultiModeSetting
                || setting instanceof NumberSetting
                || setting instanceof BooleanSetting
                || setting instanceof StringSetting
                || setting instanceof ColorSetting
                || setting instanceof ButtonSetting;
    }


    static List<Setting> supportedSettings(Module module) {
        List<Setting> normal = new ArrayList<>();
        List<Setting> buttons = new ArrayList<>();
        if (module == null) return List.of();
        for (Setting setting : module.getSettings()) {
            if (!isSupported(setting) || isPickerAlphaCompanion(module, setting)) continue;
            if (setting instanceof ButtonSetting && !("ServerHelper".equals(module.getClass().getSimpleName())
                    && "EndHelper".equals(setting.getName()))) buttons.add(setting);
            else normal.add(setting);
        }
        normal.addAll(buttons);
        return List.copyOf(normal);
    }

    private static boolean isPickerAlphaCompanion(Module module, Setting setting) {
        if (!(setting instanceof NumberSetting numberSetting) || module == null) {
            return false;
        }
        String moduleName = module.getClass().getSimpleName();
        String name = numberSetting.getName();
        return switch (moduleName) {
            case "BlockOverlay" -> "Fill Opacity".equals(name);
            case "Hands" -> "Alpha".equals(name) || "Outline Opacity".equals(name);
            case "HitColor" -> "Alpha".equals(name);
            default -> false;
        };
    }

    private static NumberSetting linkedAlphaSetting(Module module, ColorSetting colorSetting) {
        if (module == null || colorSetting == null) {
            return null;
        }
        String moduleName = module.getClass().getSimpleName();
        String colorName = colorSetting.getName();
        String configKey = colorSetting.getConfigKey();
        String alphaName = null;

        if ("BlockOverlay".equals(moduleName)) {
            if ("Fill Color".equals(colorName)) {
                alphaName = "Fill Opacity";
            }
        } else if ("Hands".equals(moduleName)) {
            if ("Shader Color".equals(configKey)) {
                alphaName = "Alpha";
            } else if ("Outline Color".equals(colorName) || "Second Color".equals(colorName)) {
                alphaName = "Outline Opacity";
            }
        } else if ("HitColor".equals(moduleName) && "Hit Color".equals(colorName)) {
            alphaName = "Alpha";
        }

        if (alphaName == null) {
            return null;
        }
        for (Setting candidate : module.getSettings()) {
            if (candidate instanceof NumberSetting numberSetting && alphaName.equals(numberSetting.getName())) {
                return numberSetting;
            }
        }
        return null;
    }

    private static ClickGuiOption createSettingOption(Setting setting) {
        if (setting instanceof BindSetting bindSetting) {
            return new BindOption(bindSetting);
        }
        if (setting instanceof ModeSetting modeSetting) {
            return new SingleOption(modeSetting);
        }
        if (setting instanceof MultiModeSetting multiModeSetting) {
            return new MultiOption(multiModeSetting);
        }
        if (setting instanceof NumberSetting numberSetting) {
            return new SliderOption(numberSetting, "");
        }
        if (setting instanceof BooleanSetting booleanSetting) {
            return new BooleanOption(booleanSetting);
        }
        if (setting instanceof StringSetting stringSetting) {
            return new TextOption(stringSetting);
        }
        if (setting instanceof ColorSetting colorSetting) {
            return new ColorOption(colorSetting);
        }
        if (setting instanceof ButtonSetting buttonSetting) {
            return new ButtonOption(buttonSetting, "EndHelper".equals(buttonSetting.getName()) ? "Open / Close" : "Editor");
        }
        return null;
    }
}
