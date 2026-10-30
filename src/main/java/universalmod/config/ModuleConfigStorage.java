package universalmod.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import universalmod.module.Module;
import universalmod.module.ModuleManager;
import universalmod.setting.Setting;
import universalmod.setting.bind.KeyBind;

public final class ModuleConfigStorage {
    public static final int CURRENT_VERSION = 2;
    private final ModuleManager moduleManager;

    public ModuleConfigStorage(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    ModuleManager moduleManager() {
        return moduleManager;
    }

    public JsonObject save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);
        JsonObject modules = new JsonObject();
        for (Module module : moduleManager.getRegisteredModules()) {
            modules.add(module.getId().value(), saveModule(module));
        }
        root.add("modules", modules);
        return root;
    }

    public void load(JsonObject rawRoot) {
        requireCurrentVersion(rawRoot);
        JsonObject root = rawRoot;
        if (!root.has("modules") || !root.get("modules").isJsonObject()) {
            return;
        }

        JsonObject modules = root.getAsJsonObject("modules");
        for (Module module : moduleManager.getRegisteredModules()) {
            JsonObject saved = moduleObject(modules, module.getId().value());
            if ("ambience".equals(module.getId().value())) {
                saved = migrateOldWetMode(modules, saved);
            }
            if (saved != null) {
                loadModule(module, saved);
            }
        }
    }

     




    private static JsonObject migrateOldWetMode(JsonObject modules, JsonObject savedAmbience) {
        JsonObject oldAtmosphere = moduleObject(modules, "atmosphere");
        if (oldAtmosphere == null || !oldAtmosphere.has("settings")
                || !oldAtmosphere.get("settings").isJsonObject()) return savedAmbience;
        JsonObject oldSettings = oldAtmosphere.getAsJsonObject("settings");
        JsonElement oldMode = oldSettings.get("Mode");
        if (oldMode == null || !oldMode.isJsonPrimitive()
                || !"Wet".equalsIgnoreCase(oldMode.getAsString())) return savedAmbience;
        JsonObject updated = savedAmbience == null ? new JsonObject() : savedAmbience.deepCopy();
        JsonObject current = updated.has("settings") && updated.get("settings").isJsonObject()
                ? updated.getAsJsonObject("settings") : new JsonObject();
        if (current.has("Wet")) return updated;
        current.add("Wet", new JsonPrimitive(true));
        for (String name : new String[]{"Wet Reflection Strength", "Wetness", "Wet Quality"}) {
            if (!current.has(name) && oldSettings.has(name)) current.add(name, oldSettings.get(name).deepCopy());
        }
        updated.add("settings", current);
         
        if (oldAtmosphere.has("enabled") && oldAtmosphere.get("enabled").getAsBoolean()) {
            updated.addProperty("enabled", true);
        }
        return updated;
    }

    public static void requireCurrentVersion(JsonObject root) {
        if (root == null || !root.has("version") || !root.get("version").isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing module config version (expected " + CURRENT_VERSION + ")");
        }
        final int version;
        try {
            version = root.get("version").getAsInt();
        } catch (RuntimeException invalidVersion) {
            throw new IllegalArgumentException("Invalid module config version (expected " + CURRENT_VERSION + ")", invalidVersion);
        }
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported module config version " + version
                    + " (expected " + CURRENT_VERSION + ")");
        }
    }

    private static JsonObject moduleObject(JsonObject modules, String id) {
        if (modules == null || id == null || !modules.has(id) || !modules.get(id).isJsonObject()) {
            return null;
        }
        return modules.getAsJsonObject(id);
    }

    private JsonObject saveModule(Module module) {
        JsonObject object = new JsonObject();
        object.addProperty("enabled", module.isDesiredEnabled());
        object.addProperty("hidden", module.isHidden());
        object.addProperty("starred", module.isStarred());
        object.add("bind", module.getBind().toJson());

        JsonObject settings = new JsonObject();
        for (Setting<?> setting : module.getSettings()) {
            if (setting.isPersistent()) {
                settings.add(setting.getId().value(), setting.toJson());
            }
        }
        object.add("settings", settings);
        return object;
    }

    private void loadModule(Module module, JsonObject object) {
        if (object == null) {
            return;
        }

        if (object.has("bind")) {
            module.setBind(KeyBind.fromJson(object.get("bind")));
        }
        if (object.has("hidden")) {
            module.setHidden(object.get("hidden").getAsBoolean());
        }
        if (object.has("starred")) {
            module.setStarred(object.get("starred").getAsBoolean());
        }
        if (object.has("settings") && object.get("settings").isJsonObject()) {
            JsonObject settings = object.getAsJsonObject("settings");
            for (Setting<?> setting : module.getSettings()) {
                JsonElement saved = settings.get(setting.getId().value());
                if (saved != null) {
                    setting.fromJson(saved);
                }
            }
        }
        if (object.has("enabled")) {
            module.setEnabled(object.get("enabled").getAsBoolean());
        }
    }
}
