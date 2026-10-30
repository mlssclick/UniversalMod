package universalmod.module;

import universalmod.service.language.LanguageManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import universalmod.event.EventBus;
import universalmod.module.exception.ModuleException;
import universalmod.setting.Setting;
import universalmod.setting.bind.KeyBind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.lang.reflect.Field;

public class Module {
    protected final MinecraftClient mc = MinecraftClient.getInstance();
    private final String name;
    private final String explicitIdentifier;
    private boolean desiredEnabled;
    private BooleanSupplier runtimeAllowed = () -> true;
    private Runnable enableCheckRequest = () -> {};
    private boolean settingsInitialized;
    private final ModuleCategory category;
    private KeyBind bind = KeyBind.NONE;
    private KeyBind defaultBind = KeyBind.NONE;
    private boolean enabled;
    private boolean hidden;
    private boolean starred;
    private EventBus eventBus;
    private final List<Setting> settings = new ArrayList<>();
    private final List<ModuleStateListener> stateListeners = new ArrayList<>();

    public Module() {
        ModuleInformation information = getClass().getAnnotation(ModuleInformation.class);
        if (information == null) throw new IllegalStateException("Missing @ModuleInformation on " + getClass().getName());
        this.explicitIdentifier = null;
        this.name = information.moduleName();
        this.category = information.moduleCategory();
        this.bind = KeyBind.legacy(information.moduleKeybind());
        this.defaultBind = bind;
    }

    public Module(String name, ModuleCategory category) {
        this(null, name, category);
    }

    public Module(String identifier, String name, ModuleCategory category) {
        if (name == null || name.isBlank()) throw new ModuleException("Module name cannot be empty");
        this.explicitIdentifier = identifier;
        this.name = name;
        this.category = category == null ? ModuleCategory.MISC : category;
    }

    private static final Map<String, String> ORIGINAL_IDENTIFIERS = Map.ofEntries(
            Map.entry("Ambience", "ambience"),
            Map.entry("Animations", "animations"),
            Map.entry("AspectRatio", "aspect_ratio"),
            Map.entry("Atmosphere", "atmosphere"),
            Map.entry("AutoSprint", "auto_sprint"),
            Map.entry("BetterChat", "chat_helper"),
            Map.entry("BlockOverlay", "block_overlay"),
            Map.entry("ClickGuiModule", "click_gui"),
            Map.entry("CoinPrice", "coin_price"),
            Map.entry("CustomCrosshair", "crosshair"),
            Map.entry("CustomDonate", "custom_donate"),
            Map.entry("CustomHitBox", "hitboxcustomizer"),
            Map.entry("CustomTheme", "themes"),
            Map.entry("EffectNotifier", "effect_notifier"),
            Map.entry("FireGlow", "fire_glow"),
            Map.entry("FreeLook", "freelook"),
            Map.entry("Friends", "friends"),
            Map.entry("Fullbright", "full_bright"),
            Map.entry("Hands", "changehand"),
            Map.entry("HealthIndicator", "health_indicator"),
            Map.entry("HitColor", "hitcolor"),
            Map.entry("HotbarCooldowns", "cooldowns"),
            Map.entry("Hud", "hud"),
            Map.entry("InvisibleTags", "invisible_tags"),
            Map.entry("ItemReplacer", "item_replacer"),
            Map.entry("ItemScroller", "item_scroller"),
            Map.entry("LockSlot", "lockslot"),
            Map.entry("NameProtect", "nick_hider"),
            Map.entry("NoRender", "no_render"),
            Map.entry("PingNametags", "ping_nametags"),
            Map.entry("Predictions", "predictions"),
            Map.entry("RichDog", "rich_dog"),
            Map.entry("SaveKtLeave", "save_kt_leave"),
            Map.entry("Scoreboard", "scoreboard_hud"),
            Map.entry("ServerHelper", "holyworld_helper"),
            Map.entry("ShadersButton", "shaders_button"),
            Map.entry("SoundReducer", "sound_reducer"),
            Map.entry("SwingAnimation", "swing_animation"),
            Map.entry("TapeMouse", "mouseclicker"),
            Map.entry("TotemCounter", "totem_tracker"),
            Map.entry("TwoDItems", "two_d_items"),
            Map.entry("ViewModel", "view_model"),
            Map.entry("Waypoints", "waypoints"),
            Map.entry("WorldLyrics", "world_lyrics"),
            Map.entry("Zoom", "zoom")
    );
    public Module add(Setting setting) {
        if (setting != null && !settings.contains(setting)) {
            setting.setLocalizationOwner(name);
            settings.add(setting);
            setting.addListener((changed, oldValue, newValue) -> notifyChanged());
        }
        return this;
    }

    protected final <T extends Setting> T register(T setting) {
        add(setting);
        return setting;
    }

    public List<Setting> getSettings() {
        ensureSettingsInitialized();
        return Collections.unmodifiableList(settings);
    }

     
    public final void ensureSettingsInitialized() {
        if (settingsInitialized) return;
        settingsInitialized = true;
        for (Class<?> type = getClass(); type != null && type != Module.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Setting.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(this);
                    if (value instanceof Setting setting) add(setting);
                } catch (IllegalAccessException | SecurityException ignored) { }
            }
        }
    }

    public String getIdentifier() {
        if (explicitIdentifier != null && !explicitIdentifier.isBlank()) return explicitIdentifier;
        return ORIGINAL_IDENTIFIERS.getOrDefault(getClass().getSimpleName(),
                universalmod.identity.StableId.fromClassSimpleName(getClass()));
    }

    public boolean isDesiredEnabled() { return desiredEnabled; }
    public boolean isServerLocked() { return !isRuntimeAllowed(); }
    public boolean isVisible() { return !isServerLocked(); }

    final void setRuntimePolicy(BooleanSupplier allowed, Runnable enableRequest) {
        runtimeAllowed = allowed == null ? () -> true : allowed;
        enableCheckRequest = enableRequest == null ? () -> {} : enableRequest;
        refreshRuntimePolicy();
    }

    final void refreshRuntimePolicy() {
        applyRuntimeState(desiredEnabled && isRuntimeAllowed());
    }

    private boolean isRuntimeAllowed() {
        try { return runtimeAllowed.getAsBoolean(); }
        catch (RuntimeException exception) { return false; }
    }


    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return LanguageManager.moduleName(name);
    }


    public String getDisplayDescription() {
        return LanguageManager.moduleDescription(name);
    }

    public ModuleCategory getCategory() {
        return category;
    }

    public int getKey() {
        return bind.isBound() ? bind.getCode() : -1;
    }

    public void setKey(int key) {
        setBind(fromLegacyCode(key));
    }

    public KeyBind getBind() {
        return bind;
    }

    public void setBind(KeyBind bind) {
        KeyBind normalized = bind == null ? KeyBind.NONE : bind;
        if (this.bind.equals(normalized)) return;
        this.bind = normalized;
        notifyChanged();
    }

    protected final void setDefaultBind(KeyBind bind) {
        defaultBind = bind == null ? KeyBind.NONE : bind;
        this.bind = defaultBind;
    }

    public void captureCurrentBindAsDefault() {
        defaultBind = bind;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        if (enabled && isServerLocked()) return;
        if (enabled) {
            try { enableCheckRequest.run(); } catch (RuntimeException ignored) { }
        }
        boolean requestedChanged = desiredEnabled != enabled;
        desiredEnabled = enabled;
        boolean runtimeChanged = this.enabled != (desiredEnabled && isRuntimeAllowed());
        if (requestedChanged && !runtimeChanged) notifyChanged();
        applyRuntimeState(desiredEnabled && isRuntimeAllowed());
    }

    private void applyRuntimeState(boolean next) {
        if (this.enabled == next) return;
        this.enabled = next;
        if (next) {
            try {
                if (eventBus != null) eventBus.register(this);
                onEnable();
            } catch (RuntimeException | LinkageError failure) {
                this.enabled = false;
                this.desiredEnabled = false;
                try { onDisable(); } catch (RuntimeException | LinkageError cleanup) { failure.addSuppressed(cleanup); }
                finally { if (eventBus != null) eventBus.unregister(this); }
                notifyChanged();
                throw failure;
            }
        } else {
            try { onDisable(); }
            finally { if (eventBus != null) eventBus.unregister(this); }
        }
        notifyChanged();
        universalmod.lifecycle.ClientRuntime.postEvent(new universalmod.event.type.ModuleStateChangedEvent(this, next));
    }

    public final void toggle() { setEnabled(!desiredEnabled); }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        if (this.hidden != hidden) {
            this.hidden = hidden;
            notifyChanged();
        }
    }

    public boolean isStarred() {
        return starred;
    }

    public void setStarred(boolean starred) {
        if (this.starred != starred) {
            this.starred = starred;
            notifyChanged();
        }
    }

    public void addStateListener(ModuleStateListener listener) {
        if (listener != null) stateListeners.add(listener);
    }

    public final void initialize(EventBus eventBus) {
        if (this.eventBus == eventBus) return;
        if (this.eventBus != null) this.eventBus.unregister(this);
        this.eventBus = eventBus;
        if (enabled && eventBus != null) eventBus.register(this);
    }

    public final void resetToDefaults() {
        setEnabled(false);
        setBind(defaultBind);
        setHidden(false);
        setStarred(false);
        for (Setting setting : getSettings()) setting.reset();
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public void onTick(MinecraftClient client) {
    }

    public void onHudRender(DrawContext context, float tickDelta) {
    }

    public void onWorldRender(WorldRenderContext context) {
    }

    public Setting getSetting(String name) {
        if (name == null) return null;
        for (Setting setting : getSettings()) if (name.equalsIgnoreCase(setting.getName())) return setting;
        return null;
    }

    public <T extends Setting> T getSetting(String name, Class<T> type) {
        Setting setting = getSetting(name);
        return type != null && type.isInstance(setting) ? type.cast(setting) : null;
    }

    private void notifyChanged() {
        for (ModuleStateListener listener : List.copyOf(stateListeners)) {
            try {
                listener.onChanged(this);
            } catch (Throwable throwable) {

            }
        }
    }

    private static KeyBind fromLegacyCode(int key) {
        if (key < 0) return KeyBind.NONE;
        if (key <= GLFW.GLFW_MOUSE_BUTTON_LAST) return KeyBind.mouse(key);
        return KeyBind.keyboard(key);
    }
}
