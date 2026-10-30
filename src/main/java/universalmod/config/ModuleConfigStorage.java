package universalmod.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import universalmod.module.Module;
import universalmod.module.ModuleManager;
import universalmod.module.misc.CustomTheme;
import universalmod.setting.Setting;
import universalmod.setting.bind.KeyBind;
import universalmod.setting.BindSetting;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ButtonSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeListSetting;
import universalmod.hud.registry.HudElementId;
import universalmod.hud.registry.HudElementsSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.SliderSetting;
import universalmod.setting.StringSetting;
import universalmod.setting.ThemeSetting;
import universalmod.theme.Theme;

public final class ModuleConfigStorage {
    private final ModuleManager moduleManager;

    public ModuleConfigStorage(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    ModuleManager moduleManager() {
        return moduleManager;
    }

    public JsonObject save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject modules = new JsonObject();
        for (Module module : moduleManager.getRegisteredModules()) {
            modules.add(module.getName(), saveModule(module));
        }
        moduleManager.getRegisteredByType(CustomTheme.class)
                .ifPresent(theme -> modules.add(theme.getName(), saveModule(theme)));
        root.add("modules", modules);
        return root;
    }

    public void load(JsonObject root) {
        if (root == null || !root.has("modules") || !root.get("modules").isJsonObject()) return;
        JsonObject modules = root.getAsJsonObject("modules");
        for (Module module : moduleManager.getRegisteredModules()) {
            JsonObject saved = moduleObject(modules, module.getName());
            if (saved == null && "Custom Scoreboard".equals(module.getName()))
                saved = moduleObject(modules, "Scoreboard");
            if (saved == null && "Scoreboard".equals(module.getName()))
                saved = moduleObject(modules, "Custom Scoreboard");
            if (saved != null) loadModule(module, saved);
        }
        moduleManager.getRegisteredByType(CustomTheme.class).ifPresent(theme -> {
            JsonObject saved = moduleObject(modules, theme.getName());
            if (saved != null) {
                loadModule(theme, saved);
            }
        });
    }

    private static JsonObject moduleObject(JsonObject modules, String name) {
        if (modules == null || name == null || !modules.has(name) || !modules.get(name).isJsonObject()) return null;
        return modules.getAsJsonObject(name);
    }

    private JsonObject saveModule(Module module) {
        JsonObject object = new JsonObject();
        object.addProperty("enabled", module.isDesiredEnabled());
        object.addProperty("hidden", module.isHidden());
        object.addProperty("starred", module.isStarred());
        object.add("bind", module.getBind().toJson());

        JsonObject settings = new JsonObject();
        for (Setting setting : module.getSettings()) {
            if (!setting.isPersistent() || setting instanceof ButtonSetting) continue;
            JsonElement value = settingToJson(setting);
            if (value != null) settings.add(setting.getConfigKey(), value);
        }
        object.add("settings", settings);
        return object;
    }

    private void loadModule(Module module, JsonObject object) {
        if (object.has("bind")) module.setBind(KeyBind.fromJson(object.get("bind")));
        else if (object.has("keybind")) module.setKey(object.get("keybind").getAsInt());
        if (object.has("hidden")) module.setHidden(object.get("hidden").getAsBoolean());
        if (object.has("starred")) module.setStarred(object.get("starred").getAsBoolean());

        if (object.has("settings") && object.get("settings").isJsonObject()) {
            JsonObject settings = object.getAsJsonObject("settings");
            for (Setting setting : module.getSettings()) {
                JsonElement saved = settings.get(setting.getConfigKey());
                if (saved == null && !setting.getConfigKey().equals(setting.getName()))
                    saved = settings.get(setting.getName());
                if (saved != null) settingFromJson(setting, saved);
            }
        }
        if (object.has("enabled")) module.setEnabled(object.get("enabled").getAsBoolean());
    }

    private static JsonElement settingToJson(Setting setting) {
        if (setting instanceof BooleanSetting value) return new JsonPrimitive(value.getValue());
        if (setting instanceof BindSetting value) return value.getValue().toJson();
        if (setting instanceof ColorSetting value) {
            Color color = new Color(value.getValue(), true);
            JsonObject object = new JsonObject();
            object.addProperty("red", color.getRed());
            object.addProperty("green", color.getGreen());
            object.addProperty("blue", color.getBlue());
            object.addProperty("alpha", color.getAlpha());
            object.addProperty("rgba", color.getRGB());
            return object;
        }
        if (setting instanceof ModeSetting value) return new JsonPrimitive(value.getValue());
        if (setting instanceof SliderSetting value) return new JsonPrimitive(value.getValue());
        if (setting instanceof StringSetting value) return new JsonPrimitive(value.getValue());
        if (setting instanceof ThemeSetting value) return new JsonPrimitive(value.getValueAsString());

        if (setting instanceof HudElementsSetting value) {
            JsonArray array = new JsonArray();
            for (String id : value.persistentIds()) array.add(id);
            return array;
        }
        if (setting instanceof ModeListSetting value) {
            JsonArray array = new JsonArray();
            for (String mode : value.getEnabledModules()) array.add(mode);
            return array;
        }
        return new JsonPrimitive(setting.getValueAsString());
    }

    private static void settingFromJson(Setting setting, JsonElement element) {
        try {
            if (setting instanceof BooleanSetting value) {
                if (element.isJsonPrimitive()) value.setValue(element.getAsBoolean());
                return;
            }
            if (setting instanceof BindSetting value) {
                if (element.isJsonObject()) value.setValue(KeyBind.fromJson(element));
                else if (element.isJsonPrimitive()) value.setValue(element.getAsInt());
                return;
            }
            if (setting instanceof ColorSetting value) {
                if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    if (object.has("rgba")) value.setValue(object.get("rgba").getAsInt());
                    else {
                        int r = object.has("red") ? object.get("red").getAsInt() : 255;
                        int g = object.has("green") ? object.get("green").getAsInt() : 255;
                        int b = object.has("blue") ? object.get("blue").getAsInt() : 255;
                        int a = object.has("alpha") ? object.get("alpha").getAsInt() : 255;
                        value.setValue(new Color(clamp(r), clamp(g), clamp(b), clamp(a)).getRGB());
                    }
                } else if (element.isJsonPrimitive()) value.setValue(element.getAsInt());
                return;
            }
            if (setting instanceof ModeSetting value) {
                if (element.isJsonPrimitive()) value.setValue(element.getAsString());
                return;
            }
            if (setting instanceof SliderSetting value) {
                if (element.isJsonObject() && element.getAsJsonObject().has("value"))
                    value.setValue(element.getAsJsonObject().get("value").getAsDouble());
                else if (element.isJsonPrimitive()) value.setValue(element.getAsDouble());
                return;
            }
            if (setting instanceof StringSetting value) {
                if (element.isJsonPrimitive()) value.setValue(element.getAsString());
                return;
            }
            if (setting instanceof ThemeSetting value) {
                if (element.isJsonPrimitive()) {
                    String name = element.getAsString();
                    for (Theme theme : value.getThemes())
                        if (theme.name.equals(name)) {
                            value.setValue(theme);
                            break;
                        }
                }
                return;
            }

            if (setting instanceof HudElementsSetting value && element.isJsonArray()) {
                java.util.EnumSet<HudElementId> selected = java.util.EnumSet.noneOf(HudElementId.class);
                for (JsonElement item : element.getAsJsonArray()) {
                    if (item.isJsonPrimitive()) {
                        HudElementId.fromPersistentOrLegacy(item.getAsString()).ifPresent(selected::add);
                    }
                }
                for (HudElementId id : HudElementId.values()) value.setSelected(id, selected.contains(id));
                return;
            }
            if (setting instanceof ModeListSetting value && element.isJsonArray()) {
                List<String> selected = new ArrayList<>();
                for (JsonElement item : element.getAsJsonArray())
                    if (item.isJsonPrimitive()) selected.add(item.getAsString());
                for (BooleanSetting child : value.getSettings())
                    child.setValue(selected.stream().anyMatch(name -> name.equalsIgnoreCase(child.getName())));
                return;
            }
            if (element.isJsonPrimitive()) setting.setValueFromString(element.getAsString());
        } catch (RuntimeException ignored) {

        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
