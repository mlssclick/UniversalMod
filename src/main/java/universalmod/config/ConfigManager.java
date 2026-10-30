package universalmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import universalmod.config.exception.ConfigException;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.TickEvent;
import universalmod.module.ModuleManager;

import java.awt.Desktop;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public final class ConfigManager {
    public static final String ROOT_FOLDER = "universalmod", SYSTEM_FOLDER = "system", CONFIGS_FOLDER = "configs", CONFIG_EXTENSION = ".universalmod", DEFAULT_PROFILE = "default";
    private static final long AUTO_SAVE_DELAY_MS = 1000L;
    private static final long AUTO_SAVE_RETRY_DELAY_MS = 3000L;
    private static final String PROFILE_METADATA_KEY = "profile";
    private static volatile ConfigManager activeInstance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path systemDirectory, profilesDirectory, activeProfileFile;
    private final ModuleConfigStorage moduleStorage;
    private final Map<String, CachedProfileInfo> profileInfoCache = new HashMap<>();
    private final ConfigAutosaveState autosave = new ConfigAutosaveState(AUTO_SAVE_DELAY_MS, AUTO_SAVE_RETRY_DELAY_MS);
    private boolean applyingProfile;
    private String activeProfile = DEFAULT_PROFILE;

    public ConfigManager(ModuleManager moduleManager) {
        activeInstance = this;
        systemDirectory = systemDirectory();
        profilesDirectory = systemDirectory.resolve(CONFIGS_FOLDER);
        activeProfileFile = systemDirectory.resolve("active_config" + CONFIG_EXTENSION);
        moduleStorage = new ModuleConfigStorage(moduleManager);
    }

    public static Path rootDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(ROOT_FOLDER);
    }

    public static Path systemDirectory() {
        return rootDirectory().resolve(SYSTEM_FOLDER);
    }

    public static Path legacyDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("universalmod");
    }

    public void init() {
        try {
            Files.createDirectories(systemDirectory);
            Files.createDirectories(profilesDirectory);
            migrateLegacyProfiles();
        } catch (IOException e) {
            throw new ConfigException("Failed to create config directories under: " + rootDirectory(), e);
        }
    }

    public void loadAll() {
        ensureDefaultProfile();
        activeProfile = readActiveProfile();
        if (!Files.isRegularFile(profileFile(activeProfile))) {
            activeProfile = DEFAULT_PROFILE;
            writeActiveProfile();
        }
        Path file = resolveModulesFileForRead();
        if (!Files.exists(file)) saveModules();
        else applyProfile(readModules(file));
        autosave.markClean();
    }

    public void saveAll() {
        saveModules();
        autosave.markClean();
    }

    public void markDirty() {
        if (applyingProfile) return;
        autosave.markDirty(System.currentTimeMillis());
    }

    public List<String> profiles() {
        ensureDefaultProfile();
        try {
            List<String> out = new ArrayList<>();
            try (var stream = Files.list(profilesDirectory)) {
                stream.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).filter(n -> n.endsWith(CONFIG_EXTENSION)).map(n -> n.substring(0, n.length() - CONFIG_EXTENSION.length())).map(ConfigManager::sanitizeProfileName).filter(n -> !n.isBlank()).distinct().sorted(Comparator.comparing(n -> DEFAULT_PROFILE.equals(n) ? "" : n.toLowerCase(Locale.ROOT))).forEach(out::add);
            }
            if (!out.contains(DEFAULT_PROFILE)) out.add(0, DEFAULT_PROFILE);
            return List.copyOf(out);
        } catch (IOException e) {
            return List.of(DEFAULT_PROFILE);
        }
    }

    public String activeProfile() {
        return activeProfile;
    }

    public ProfileInfo getProfileInfo(String name) {
        String n = sanitizeProfileName(name);
        if (n.isBlank()) n = DEFAULT_PROFILE;
        Path file = profileFile(n);
        long modified = fileModifiedAt(file);
        CachedProfileInfo cached = profileInfoCache.get(n);
        if (cached != null && cached.modifiedAt() == modified) return cached.info();
        ProfileInfo info = readProfileInfo(n, file);
        profileInfoCache.put(n, new CachedProfileInfo(info, modified));
        return info;
    }

    public String createProfile() {
        JsonObject previous = moduleStorage.save();
        String prev = activeProfile;
        saveAll();
        String name = uniqueProfileName("config");
        try {
            applyProfile(null);
            activeProfile = name;
            writeActiveProfile();
            saveAll();
            return name;
        } catch (RuntimeException e) {
            restoreProfile(previous, prev, e);
            throw e;
        }
    }

    public boolean saveAs(String name) {
        String n = sanitizeProfileName(name);
        if (n.isBlank()) return false;
        try {
            activeProfile = n;
            writeActiveProfile();
            saveAll();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean selectProfile(String name) {
        String n = sanitizeProfileName(name);
        if (n.isBlank() || n.equals(activeProfile)) return false;
        Path target = profileFile(n);
        if (!Files.isRegularFile(target)) return false;
        JsonObject targetState = readModules(target), previous = moduleStorage.save();
        String prev = activeProfile;
        saveAll();
        try {
            applyProfile(targetState);
            activeProfile = n;
            writeActiveProfile();
            autosave.markClean();
            return true;
        } catch (RuntimeException e) {
            restoreProfile(previous, prev, e);
            throw e;
        }
    }

    public boolean deleteProfile(String name) {
        String n = sanitizeProfileName(name);
        if (n.isBlank() || DEFAULT_PROFILE.equals(n)) return false;
        JsonObject previous = null;
        String prev = activeProfile;
        try {
            if (n.equals(activeProfile)) {
                previous = moduleStorage.save();
                Path df = profileFile(DEFAULT_PROFILE);
                applyProfile(Files.isRegularFile(df) ? readModules(df) : null);
                activeProfile = DEFAULT_PROFILE;
                writeActiveProfile();
                autosave.markClean();
            }
            Files.deleteIfExists(profileFile(n));
            profileInfoCache.remove(n);
            return true;
        } catch (IOException e) {
            if (previous != null) restoreProfile(previous, prev, e);
            return false;
        } catch (RuntimeException e) {
            if (previous != null) restoreProfile(previous, prev, e);
            throw e;
        }
    }

    public boolean renameProfile(String oldName, String newName) {
        String o = sanitizeProfileName(oldName), n = sanitizeProfileName(newName);
        if (o.isBlank() || n.isBlank() || DEFAULT_PROFILE.equals(o) || o.equals(n) || Files.exists(profileFile(n)))
            return false;
        try {
            if (o.equals(activeProfile)) saveAll();
            ProfileFileOps.move(profileFile(o), profileFile(n));
            profileInfoCache.remove(o);
            profileInfoCache.remove(n);
            if (o.equals(activeProfile)) {
                activeProfile = n;
                writeActiveProfile();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean revealProfileFile(String name) {
        String n = sanitizeProfileName(name);
        if (n.isBlank()) return false;
        Path file = profileFile(n).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) return false;
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select," + file).start();
                return true;
            }
            if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", file.toString()).start();
                return true;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop d = Desktop.getDesktop();
                if (d.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                    d.browseFileDirectory(file.toFile());
                    return true;
                }
                if (d.isSupported(Desktop.Action.OPEN)) {
                    d.open(file.getParent().toFile());
                    return true;
                }
            }
            new ProcessBuilder("xdg-open", file.getParent().toString()).start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (!autosave.shouldAttempt(now)) return;
        try {
            saveAll();
        } catch (ConfigException failure) {
            autosave.recordFailure(now);
        }
    }

    private JsonObject readModules(Path file) {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (!root.has("modules") || !root.get("modules").isJsonObject())
                throw new ConfigException("Module config has no valid modules object: " + file);
            return root;
        } catch (Exception e) {
            if (e instanceof ConfigException ce) throw ce;
            throw new ConfigException("Failed to load module config: " + file, e);
        }
    }

    private void applyProfile(JsonObject root) {
        ModuleManager mm = moduleStorage.moduleManager();
        boolean was = applyingProfile;
        applyingProfile = true;
        mm.beginConfigApply();
        try {
            for (var module : mm.getRegisteredModules()) module.resetToDefaults();
            if (root != null) moduleStorage.load(root);
        } finally {
            mm.endConfigApply();
            applyingProfile = was;
        }
    }

    private void restoreProfile(JsonObject state, String profile, Throwable failure) {
        try {
            applyProfile(state);
            activeProfile = profile;
            writeActiveProfile();
            autosave.markClean();
        } catch (RuntimeException rollback) {
            failure.addSuppressed(rollback);
        }
    }

    private void saveModules() {
        try {
            Files.createDirectories(profilesDirectory);
            Path file = profileFile(activeProfile);
            ProfileInfo info = getProfileInfo(activeProfile);
            JsonObject root = moduleStorage.save();
            root.add(PROFILE_METADATA_KEY, profileMetadata(info));
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(root, w);
            }
            profileInfoCache.put(activeProfile, new CachedProfileInfo(info, fileModifiedAt(file)));
        } catch (IOException e) {
            throw new ConfigException("Failed to save module config: " + profileFile(activeProfile), e);
        }
    }

    private Path resolveModulesFileForRead() {
        Path active = profileFile(activeProfile);
        if (Files.exists(active)) return active;
        Path old = systemDirectory.resolve("modules" + CONFIG_EXTENSION);
        if (Files.exists(old)) return old;
        Path legacy = legacyDirectory().resolve("modules.json");
        if (Files.exists(legacy)) return legacy;
        return active;
    }

    private void ensureDefaultProfile() {
        try {
            Files.createDirectories(profilesDirectory);
            Path df = profileFile(DEFAULT_PROFILE);
            if (!Files.exists(df)) {
                Path old = systemDirectory.resolve("modules" + CONFIG_EXTENSION);
                if (Files.exists(old)) Files.copy(old, df);
                else {
                    ProfileInfo info = new ProfileInfo(DEFAULT_PROFILE, System.currentTimeMillis(), currentCreatorName());
                    JsonObject root = moduleStorage.save();
                    root.add(PROFILE_METADATA_KEY, profileMetadata(info));
                    try (Writer w = Files.newBufferedWriter(df, StandardCharsets.UTF_8)) {
                        gson.toJson(root, w);
                    }
                }
            }
            if (!Files.exists(activeProfileFile)) writeActiveProfile();
        } catch (IOException e) {
            throw new ConfigException("Failed to initialize config profiles: " + profilesDirectory, e);
        }
    }

    private String readActiveProfile() {
        if (!Files.exists(activeProfileFile)) return DEFAULT_PROFILE;
        try (Reader r = Files.newBufferedReader(activeProfileFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            String n = root.has("active") ? root.get("active").getAsString() : DEFAULT_PROFILE;
            n = sanitizeProfileName(n);
            return n.isBlank() ? DEFAULT_PROFILE : n;
        } catch (Exception e) {
            return DEFAULT_PROFILE;
        }
    }

    private void writeActiveProfile() {
        try {
            Files.createDirectories(systemDirectory);
            JsonObject root = new JsonObject();
            root.addProperty("active", activeProfile);
            try (Writer w = Files.newBufferedWriter(activeProfileFile, StandardCharsets.UTF_8)) {
                gson.toJson(root, w);
            }
        } catch (IOException e) {
            throw new ConfigException("Failed to save active config profile: " + activeProfileFile, e);
        }
    }

    private String uniqueProfileName(String base) {
        return ProfileFileOps.uniqueName(profilesDirectory, CONFIG_EXTENSION, base);
    }

    private Path profileFile(String name) {
        return ProfileFileOps.profileFile(profilesDirectory, CONFIG_EXTENSION, name);
    }

    private ProfileInfo readProfileInfo(String name, Path file) {
        long created = fileCreatedAt(file);
        String by = currentCreatorName();
        if (Files.isRegularFile(file)) try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has(PROFILE_METADATA_KEY) && root.get(PROFILE_METADATA_KEY).isJsonObject()) {
                JsonObject m = root.getAsJsonObject(PROFILE_METADATA_KEY);
                if (m.has("createdAt") && m.get("createdAt").getAsLong() > 0) created = m.get("createdAt").getAsLong();
                if (m.has("createdBy") && !m.get("createdBy").getAsString().isBlank())
                    by = m.get("createdBy").getAsString().trim();
            }
        } catch (Exception ignored) {
        }
        return new ProfileInfo(name, created, by);
    }

    private static JsonObject profileMetadata(ProfileInfo info) {
        JsonObject m = new JsonObject();
        m.addProperty("createdAt", info.createdAt());
        m.addProperty("createdBy", info.createdBy());
        return m;
    }

    private static long fileCreatedAt(Path file) {
        try {
            BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class);
            long c = a.creationTime().toMillis();
            return c > 0 ? c : a.lastModifiedTime().toMillis();
        } catch (IOException e) {
            return System.currentTimeMillis();
        }
    }

    private static long fileModifiedAt(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return -1L;
        }
    }

    private static String currentCreatorName() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getSession() != null) {
                String n = mc.getSession().getUsername();
                if (n != null && !n.isBlank()) return n.trim();
            }
        } catch (Throwable ignored) {
        }
        return "Player";
    }

    private static String sanitizeProfileName(String name) {
        return ProfileFileOps.sanitize(name);
    }

    private void migrateLegacyProfiles() throws IOException {
        Path old = FabricLoader.getInstance().getGameDir().resolve("Universal").resolve("configs");
        if (!Files.isDirectory(old)) return;
        try (var stream = Files.list(old)) {
            for (Path json : stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json")).toList()) {
                String base = json.getFileName().toString();
                base = sanitizeProfileName(base.substring(0, base.length() - 5));
                if (base.isBlank()) continue;
                Path target = profileFile(base);
                if (Files.exists(target)) continue;
                try (Reader r = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
                    JsonObject legacy = JsonParser.parseReader(r).getAsJsonObject();
                    JsonObject converted = new JsonObject();
                    converted.addProperty("version", 1);
                    JsonObject modules = new JsonObject();
                    for (String key : legacy.keySet())
                        if (!PROFILE_METADATA_KEY.equals(key) && legacy.get(key).isJsonObject())
                            modules.add(key, legacy.get(key).deepCopy());
                    converted.add("modules", modules);
                    if (legacy.has(PROFILE_METADATA_KEY))
                        converted.add(PROFILE_METADATA_KEY, legacy.get(PROFILE_METADATA_KEY).deepCopy());
                    try (Writer w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                        gson.toJson(converted, w);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    public record ProfileInfo(String name, long createdAt, String createdBy) {
    }

    private record CachedProfileInfo(ProfileInfo info, long modifiedAt) {
    }

    private static ConfigManager active() {
        return activeInstance;
    }

    public static List<String> getConfigs() {
        ConfigManager c = active();
        return c == null ? List.of(DEFAULT_PROFILE) : c.profiles();
    }

    public static String getActiveConfig() {
        ConfigManager c = active();
        return c == null ? DEFAULT_PROFILE : c.activeProfile();
    }

    public static Path directory() {
        ConfigManager c = active();
        return c == null ? systemDirectory().resolve(CONFIGS_FOLDER) : c.profilesDirectory;
    }

    public static ProfileInfo profileInfo(String name) {
        ConfigManager c = active();
        return c == null ? new ProfileInfo(name, System.currentTimeMillis(), currentCreatorName()) : c.getProfileInfo(name);
    }

    public static String createConfig() {
        ConfigManager c = active();
        return c == null ? "" : c.createProfile();
    }

    public static void save(String name) {
        ConfigManager c = active();
        if (c != null) c.saveAs(name);
    }

    public static void load(String name) {
        ConfigManager c = active();
        if (c != null) c.selectProfile(name);
    }

    public static boolean delete(String name) {
        ConfigManager c = active();
        return c != null && c.deleteProfile(name);
    }

    public static boolean rename(String oldName, String newName) {
        ConfigManager c = active();
        return c != null && c.renameProfile(oldName, newName);
    }

    public static boolean reveal(String name) {
        ConfigManager c = active();
        return c != null && c.revealProfileFile(name);
    }
}
