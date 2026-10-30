package universalmod.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import universalmod.module.Module;
import universalmod.module.ModuleManager;
import universalmod.module.misc.CustomTheme;
import universalmod.setting.Setting;
import universalmod.setting.bind.KeyBind;
import universalmod.setting.*;
import universalmod.theme.Theme;

import java.util.HashSet;
import java.util.Set;

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
        for (Module module : moduleManager.getRegisteredModules()) modules.add(module.getName(), saveModule(module));
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
            JsonElement value = saveSetting(setting);
            if (value != null) settings.add(setting.getConfigKey(), value);
        }
        object.add("settings", settings);
        return object;
    }

    private void loadModule(Module module, JsonObject object) {
        if (object.has("bind")) {
            JsonElement e = object.get("bind");
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) module.setKey(e.getAsInt());
            else module.setBind(KeyBind.fromJson(e));
        } else if (object.has("keybind")) module.setKey(object.get("keybind").getAsInt());
        if (object.has("hidden")) module.setHidden(object.get("hidden").getAsBoolean());
        if (object.has("starred")) module.setStarred(object.get("starred").getAsBoolean());
        if (object.has("settings") && object.get("settings").isJsonObject()) {
            JsonObject settings = object.getAsJsonObject("settings");
            for (Setting setting : module.getSettings()) {
                JsonElement saved = settings.get(setting.getConfigKey());
                if (saved == null) saved = settings.get(setting.getName());
                if (saved != null) loadSetting(setting, saved);
            }
        }
        if (object.has("enabled")) module.setEnabled(object.get("enabled").getAsBoolean());
    }

    private static JsonElement saveSetting(Setting setting) {
        if (setting instanceof BooleanSetting s) return new JsonPrimitive(s.getValue());
        if (setting instanceof BindSetting s) return s.getValue().toJson();
        if (setting instanceof ColorSetting s) return new JsonPrimitive(s.getValue());
        if (setting instanceof ModeSetting s) return new JsonPrimitive(s.getValue());
        if (setting instanceof NumberSetting s) return new JsonPrimitive(s.getValue());
        if (setting instanceof StringSetting s) return new JsonPrimitive(s.getValue());
        if (setting instanceof ThemeSetting s && s.getValue() != null) return new JsonPrimitive(s.getValue().name);
        if (setting instanceof MultiModeSetting s) {
            JsonArray a = new JsonArray();
            for (BooleanSetting option : s.getSettings()) if (option.getValue()) a.add(option.getName());
            return a;
        }
        return new JsonPrimitive(setting.getValueAsString());
    }

    private static void loadSetting(Setting setting, JsonElement value) {
        try {
            if (setting instanceof BooleanSetting s) {
                s.setValue(value.getAsBoolean());
                return;
            }
            if (setting instanceof BindSetting s) {
                s.setValue(universalmod.setting.bind.KeyBind.fromJson(value));
                return;
            }
            if (setting instanceof ColorSetting s) {
                s.setValue(value.getAsInt());
                return;
            }
            if (setting instanceof ModeSetting s) {
                s.setValue(value.getAsString());
                return;
            }
            if (setting instanceof NumberSetting s) {
                s.setValue(value.getAsDouble());
                return;
            }
            if (setting instanceof StringSetting s) {
                s.setValue(value.getAsString());
                return;
            }
            if (setting instanceof ThemeSetting s) {
                String n = value.getAsString();
                for (Theme t : s.getThemes())
                    if (t.name.equals(n)) {
                        s.setValue(t);
                        break;
                    }
                return;
            }
            if (setting instanceof MultiModeSetting s) {
                Set<String> enabled = new HashSet<>();
                if (value.isJsonArray()) for (JsonElement e : value.getAsJsonArray())
                    enabled.add(e.getAsString().toLowerCase(java.util.Locale.ROOT));
                else for (String part : value.getAsString().split(","))
                    enabled.add(part.trim().toLowerCase(java.util.Locale.ROOT));
                for (BooleanSetting option : s.getSettings())
                    option.setValue(enabled.contains(option.getName().toLowerCase(java.util.Locale.ROOT)));
                return;
            }
            setting.setValueFromString(value.getAsString());
        } catch (RuntimeException exception) {

        }
    }
}
