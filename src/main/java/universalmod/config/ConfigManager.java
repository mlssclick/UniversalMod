package universalmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import universalmod.config.exception.ConfigException;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.TickEvent;
import universalmod.module.ModuleManager;
import universalmod.storage.json.JsonDocumentStore;

import java.io.IOException;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigManager {
    public static final String ROOT_FOLDER = "universalmod";
    public static final String SYSTEM_FOLDER = "system";
    public static final String CONFIGS_FOLDER = "configs";
    public static final String CONFIG_EXTENSION = ".universalmod";
    public static final String DEFAULT_PROFILE = "default";
    private static final long AUTO_SAVE_DELAY_MS = 1_000L;
    private static final long AUTO_SAVE_RETRY_DELAY_MS = 10_000L;
    private static final String PROFILE_METADATA_KEY = "profile";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final JsonDocumentStore documentStore = new JsonDocumentStore(gson);
    private final Path systemDirectory;
    private final Path profilesDirectory;
    private final Path activeProfileFile;
    private final ModuleConfigStorage moduleStorage;
    private final Map<String, CachedProfileInfo> profileInfoCache = new HashMap<>();
    private final ConfigAutosaveState autosave = new ConfigAutosaveState(AUTO_SAVE_DELAY_MS, AUTO_SAVE_RETRY_DELAY_MS);
    private boolean applyingProfile;
    private String activeProfile = DEFAULT_PROFILE;

    public ConfigManager(ModuleManager moduleManager) {
        this.systemDirectory = systemDirectory();
        this.profilesDirectory = systemDirectory.resolve(CONFIGS_FOLDER);
        this.activeProfileFile = systemDirectory.resolve("active_config" + CONFIG_EXTENSION);
        this.moduleStorage = new ModuleConfigStorage(moduleManager);
    }

    public static Path rootDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(ROOT_FOLDER);
    }

    public static Path systemDirectory() {
        return rootDirectory().resolve(SYSTEM_FOLDER);
    }

    public void init() {
        try {
            Files.createDirectories(systemDirectory);
            Files.createDirectories(profilesDirectory);
        } catch (IOException exception) {
            throw new ConfigException("Failed to create config directories under: " + rootDirectory(), exception);
        }
    }

    public void loadAll() {
        ensureDefaultProfile();
        activeProfile = readActiveProfile();
        if (!Files.isRegularFile(profileFile(activeProfile))) {
            throw new ConfigException("Active profile does not exist: " + activeProfile);
        }
        Path file = profileFile(activeProfile);
        if (!Files.exists(file)) {
            saveModules();
        } else {
            applyProfile(readModules(file));
        }
        autosave.markClean();
    }

    public void saveAll() {
        saveModules();
        autosave.markClean();
    }

    public void markDirty() {
        if (applyingProfile) {
            return;
        }


        autosave.markDirty(System.currentTimeMillis());
    }

    public List<String> profiles() {
        ensureDefaultProfile();
        try {
            if (!Files.exists(profilesDirectory)) {
                return List.of(DEFAULT_PROFILE);
            }
            List<String> result = new ArrayList<>();
            try (var stream = Files.list(profilesDirectory)) {
                stream.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(CONFIG_EXTENSION))
                        .map(name -> name.substring(0, name.length() - CONFIG_EXTENSION.length()))
                        .map(ProfileFileOps::sanitize)
                        .filter(name -> !name.isBlank())
                        .distinct()
                        .sorted(Comparator.comparing(name -> DEFAULT_PROFILE.equals(name) ? "" : name.toLowerCase(Locale.ROOT)))
                        .forEach(result::add);
            }
            if (!result.contains(DEFAULT_PROFILE)) {
                result.add(0, DEFAULT_PROFILE);
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new ConfigException("Failed to list profiles: " + profilesDirectory, exception);
        }
    }

    public String activeProfile() {
        return activeProfile;
    }

    public boolean revealProfileFile(String name) {
        String sanitized = ProfileFileOps.sanitize(name);
        if (sanitized.isBlank()) {
            return false;
        }
        Path file = profileFile(sanitized).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            return false;
        }

        try {
            String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (operatingSystem.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select," + file).start();
                return true;
            }
            if (operatingSystem.contains("mac")) {
                new ProcessBuilder("open", "-R", file.toString()).start();
                return true;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                    desktop.browseFileDirectory(file.toFile());
                    return true;
                }
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(file.getParent().toFile());
                    return true;
                }
            }
            new ProcessBuilder("xdg-open", file.getParent().toString()).start();
            return true;
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            return false;
        }
    }

    public ProfileInfo profileInfo(String name) {
        String sanitized = ProfileFileOps.sanitize(name);
        if (sanitized.isBlank()) {
            sanitized = DEFAULT_PROFILE;
        }

        Path file = profileFile(sanitized);
        long modifiedAt = fileModifiedAt(file);
        CachedProfileInfo cached = profileInfoCache.get(sanitized);
        if (cached != null && cached.modifiedAt() == modifiedAt) {
            return cached.info();
        }

        ProfileInfo info = readProfileInfo(sanitized, file);
        profileInfoCache.put(sanitized, new CachedProfileInfo(info, modifiedAt));
        return info;
    }

    public String createProfile() {
        JsonObject previousState = moduleStorage.save();
        String previousProfile = activeProfile;
        saveAll();
        String name = ProfileFileOps.uniqueName(profilesDirectory, CONFIG_EXTENSION, "config");
        try {
            applyProfile(null);
            activeProfile = name;
            saveModules();
            writeActiveProfile();
            autosave.markClean();
            return name;
        } catch (RuntimeException exception) {
            discardFailedProfile(name, exception);
            restoreProfile(previousState, previousProfile, exception);
            throw exception;
        }
    }

    private void discardFailedProfile(String name, RuntimeException failure) {
        profileInfoCache.remove(name);
        try {
            Files.deleteIfExists(profileFile(name));
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    public boolean selectProfile(String name) {
        String sanitized = ProfileFileOps.sanitize(name);
        if (sanitized.isBlank() || sanitized.equals(activeProfile)) {
            return false;
        }

        Path targetFile = profileFile(sanitized);
        if (!Files.isRegularFile(targetFile)) {
            return false;
        }

        JsonObject target = readModules(targetFile);
        JsonObject previousState = moduleStorage.save();
        String previousProfile = activeProfile;
        saveAll();
        try {
            applyProfile(target);
            activeProfile = sanitized;
            writeActiveProfile();
            autosave.markClean();
            return true;
        } catch (RuntimeException exception) {
            restoreProfile(previousState, previousProfile, exception);
            throw exception;
        }
    }

    public boolean deleteProfile(String name) {
        String sanitized = ProfileFileOps.sanitize(name);
        if (sanitized.isBlank() || DEFAULT_PROFILE.equals(sanitized)) {
            return false;
        }
        JsonObject previousState = null;
        String previousProfile = activeProfile;
        try {
            if (sanitized.equals(activeProfile)) {
                previousState = moduleStorage.save();
                Path defaultFile = profileFile(DEFAULT_PROFILE);
                JsonObject defaultState = Files.isRegularFile(defaultFile)
                        ? readModules(defaultFile)
                        : null;
                applyProfile(defaultState);
                activeProfile = DEFAULT_PROFILE;
                writeActiveProfile();
                autosave.markClean();
            }
            Files.deleteIfExists(profileFile(sanitized));
            profileInfoCache.remove(sanitized);
            return true;
        } catch (IOException exception) {
            if (previousState != null) {
                restoreProfile(previousState, previousProfile, exception);
            }
            return false;
        } catch (RuntimeException exception) {
            if (previousState != null) {
                restoreProfile(previousState, previousProfile, exception);
            }
            throw exception;
        }
    }

    public boolean renameProfile(String oldName, String newName) {
        String oldSanitized = ProfileFileOps.sanitize(oldName);
        String newSanitized = ProfileFileOps.sanitize(newName);
        if (oldSanitized.isBlank() || newSanitized.isBlank()
                || DEFAULT_PROFILE.equals(oldSanitized)
                || oldSanitized.equals(newSanitized)
                || Files.exists(profileFile(newSanitized))) {
            return false;
        }

        Path oldFile = profileFile(oldSanitized);
        Path newFile = profileFile(newSanitized);
        if (!Files.isRegularFile(oldFile)) {
            return false;
        }

        if (!oldSanitized.equals(activeProfile)) {
            try {
                ProfileFileOps.move(oldFile, newFile);
                invalidateProfileInfo(oldSanitized, newSanitized);
                return true;
            } catch (IOException moveFailure) {
                return false;
            }
        }

        saveAll();
        try {
            ProfileFileOps.copyAtomically(oldFile, newFile);
        } catch (IOException copyFailure) {
            return false;
        }

        activeProfile = newSanitized;
        try {
            writeActiveProfile();
        } catch (RuntimeException pointerFailure) {
            activeProfile = oldSanitized;
            try {
                Files.deleteIfExists(newFile);
            } catch (IOException cleanupFailure) {
                pointerFailure.addSuppressed(cleanupFailure);
            }
            throw pointerFailure;
        }

        try {
            Files.deleteIfExists(oldFile);
        } catch (IOException cleanupFailure) {
        }
        invalidateProfileInfo(oldSanitized, newSanitized);
        return true;
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (!autosave.shouldAttempt(now)) {
            return;
        }
        try {
            saveAll();
        } catch (ConfigException saveFailure) {
            autosave.recordFailure(now);
        }
    }

    private JsonObject readModules(Path file) {
        try {
            JsonObject root = documentStore.readObject(file);
            if (root == null || !root.has("modules") || !root.get("modules").isJsonObject()) {
                throw new ConfigException("Module config has no valid modules object: " + file);
            }
            try {
                ModuleConfigStorage.requireCurrentVersion(root);
            } catch (IllegalArgumentException invalidVersion) {
                throw new ConfigException("Invalid module config format: " + file, invalidVersion);
            }
            return root;
        } catch (IOException exception) {
            throw new ConfigException("Failed to load module config: " + file, exception);
        }
    }

    private void applyProfile(JsonObject root) {
        ModuleManager moduleManager = moduleStorage.moduleManager();
        boolean wasApplying = applyingProfile;
        applyingProfile = true;
        moduleManager.beginConfigApply();
        try {
            resetModulesToDefaults();
            if (root != null) {
                moduleStorage.load(root);
            }
        } finally {
            moduleManager.endConfigApply();
            applyingProfile = wasApplying;
        }
    }

    private void restoreProfile(JsonObject state, String profile, Throwable failure) {
        try {
            applyProfile(state);
            activeProfile = profile;
            writeActiveProfile();
            autosave.markClean();
        } catch (RuntimeException rollbackException) {
            failure.addSuppressed(rollbackException);
        }
    }

    private void saveModules() {
        try {
            Files.createDirectories(profilesDirectory);
            Path file = profileFile(activeProfile);
            ProfileInfo info = profileInfo(activeProfile);
            JsonObject root = moduleStorage.save();
            root.add(PROFILE_METADATA_KEY, profileMetadata(info));
            documentStore.writeObject(file, root);
            profileInfoCache.put(activeProfile, new CachedProfileInfo(info, fileModifiedAt(file)));
        } catch (IOException exception) {
            throw new ConfigException("Failed to save module config: " + profileFile(activeProfile), exception);
        }
    }

    private void ensureDefaultProfile() {
        try {
            Files.createDirectories(profilesDirectory);
            Path defaultFile = profileFile(DEFAULT_PROFILE);
            if (!Files.exists(defaultFile)) {
                ProfileInfo info = new ProfileInfo(DEFAULT_PROFILE, System.currentTimeMillis(), currentCreatorName());
                JsonObject root = moduleStorage.save();
                root.add(PROFILE_METADATA_KEY, profileMetadata(info));
                documentStore.writeObject(defaultFile, root);
            }
            if (!Files.exists(activeProfileFile)) {
                writeActiveProfile();
            }
        } catch (IOException exception) {
            throw new ConfigException("Failed to initialize config profiles: " + profilesDirectory, exception);
        }
    }

    private void resetModulesToDefaults() {
        for (var module : moduleStorage.moduleManager().getRegisteredModules()) {
            module.resetToDefaults();
        }
    }

    private String readActiveProfile() {
        if (!Files.exists(activeProfileFile)) {
            return DEFAULT_PROFILE;
        }
        try {
            JsonObject root = documentStore.readObject(activeProfileFile);
            String name = root != null && root.has("active") ? root.get("active").getAsString() : DEFAULT_PROFILE;
            String sanitized = ProfileFileOps.sanitize(name);
            return sanitized.isBlank() ? DEFAULT_PROFILE : sanitized;
        } catch (IOException | RuntimeException exception) {
            throw new ConfigException("Failed to read active profile: " + activeProfileFile, exception);
        }
    }

    private void writeActiveProfile() {
        try {
            Files.createDirectories(systemDirectory);
            JsonObject root = new JsonObject();
            root.addProperty("active", activeProfile);
            documentStore.writeObject(activeProfileFile, root);
        } catch (IOException exception) {
            throw new ConfigException("Failed to save active config profile: " + activeProfileFile, exception);
        }
    }

    private void invalidateProfileInfo(String... names) {
        for (String name : names) {
            profileInfoCache.remove(name);
        }
    }

    private Path profileFile(String name) {
        return ProfileFileOps.profileFile(profilesDirectory, CONFIG_EXTENSION, name);
    }

    private ProfileInfo readProfileInfo(String name, Path file) {
        long createdAt = fileCreatedAt(file);
        String createdBy = currentCreatorName();

        if (Files.isRegularFile(file)) {
            try {
                JsonObject root = documentStore.readObject(file);
                if (root != null && root.has(PROFILE_METADATA_KEY) && root.get(PROFILE_METADATA_KEY).isJsonObject()) {
                    JsonObject metadata = root.getAsJsonObject(PROFILE_METADATA_KEY);
                    if (metadata.has("createdAt")) {
                        long savedCreatedAt = metadata.get("createdAt").getAsLong();
                        if (savedCreatedAt > 0L) {
                            createdAt = savedCreatedAt;
                        }
                    }
                    if (metadata.has("createdBy")) {
                        String savedCreatedBy = metadata.get("createdBy").getAsString().trim();
                        if (!savedCreatedBy.isBlank()) {
                            createdBy = savedCreatedBy;
                        }
                    }
                }
            } catch (IOException | RuntimeException exception) {
            
        }
        }

        return new ProfileInfo(name, createdAt, createdBy);
    }

    private static JsonObject profileMetadata(ProfileInfo info) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("createdAt", info.createdAt());
        metadata.addProperty("createdBy", info.createdBy());
        return metadata;
    }

    private static long fileCreatedAt(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            long createdAt = attributes.creationTime().toMillis();
            if (createdAt > 0L) {
                return createdAt;
            }
            long modifiedAt = attributes.lastModifiedTime().toMillis();
            if (modifiedAt > 0L) {
                return modifiedAt;
            }
        } catch (IOException exception) {
            
        }
        return System.currentTimeMillis();
    }

    private static long fileModifiedAt(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            return -1L;
        }
    }

    private static String currentCreatorName() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getUser() != null) {
            String name = client.getUser().getName();
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }
        return "Player";
    }

    public record ProfileInfo(String name, long createdAt, String createdBy) {
    }

    private record CachedProfileInfo(ProfileInfo info, long modifiedAt) {
    }
}
