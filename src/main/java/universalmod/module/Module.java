package universalmod.module;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import universalmod.event.EventBus;
import universalmod.setting.ModeSetting;
import universalmod.setting.Setting;
import universalmod.setting.bind.KeyBind;
import universalmod.integration.minecraft.IMinecraft;
import universalmod.util.QuickLogger;
import universalmod.service.language.LanguageManager;
import universalmod.render.animation.Animation;
import universalmod.render.animation.Easing;

public class Module implements IMinecraft, QuickLogger {
    private final String name;
    private final ModuleCategory category;
    private final KeyBind defaultBind;
    private final String explicitIdentifier;
    private KeyBind bind;
    private boolean enabled;
    private boolean desiredEnabled;
    private BooleanSupplier runtimeAllowed = () -> true;
    private Runnable enableCheckRequest = () -> {};

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
    private boolean hidden;
    private boolean starred;
    private final Animation animation = new Animation(Easing.BACK_OUT, 450L);
    public final MinecraftClient mc = MinecraftClient.getInstance();
    private final List<Setting> settings = new ArrayList<>();
    private final List<Setting> settingsView = Collections.unmodifiableList(settings);
    private final List<ModuleStateListener> stateListeners = new ArrayList<>();
    private EventBus eventBus;
    private boolean settingsInitialized;

    public Module() {
        ModuleInformation information = this.getClass().getAnnotation(ModuleInformation.class);
        if (information == null) {
            throw new IllegalStateException("Missing @ModuleInformation on " + getClass().getName());
        }
        this.name = information.moduleName();
        this.category = information.moduleCategory();
        this.defaultBind = KeyBind.legacy(information.moduleKeybind());
        this.bind = this.defaultBind;
        this.explicitIdentifier = null;
    }

    public Module(String identifier, String name, ModuleCategory category) {
        this.explicitIdentifier = Objects.requireNonNull(identifier, "identifier");
        if (identifier.isBlank()) throw new IllegalArgumentException("Empty module identifier");
        this.name = Objects.requireNonNull(name, "name");
        this.category = Objects.requireNonNull(category, "category");
        this.defaultBind = KeyBind.NONE;
        this.bind = defaultBind;
    }

    protected final <T extends Setting> T register(T setting) {
        Objects.requireNonNull(setting, "setting");
        if (!settings.contains(setting)) {
            setting.setLocalizationOwner(name);
            setting.addChangeListener(this::notifyChanged);
            settings.add(setting);
        }
        return setting;
    }

    public final List<Setting> getSettings() {
        ensureSettingsInitialized();
        return settingsView;
    }

    public final void ensureSettingsInitialized() {
        if (settingsInitialized) return;
        settingsInitialized = true;
        Class<?> type = getClass();
        while (type != null && type != Module.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!Setting.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(this);
                    if (value instanceof Setting setting && !settings.contains(setting)) {
                        setting.setLocalizationOwner(name);
                        settings.add(setting);
                        setting.addChangeListener(this::notifyChanged);
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    public final void setEnabled(boolean enabled) {
        if (enabled && isServerLocked()) return;
        if (enabled) {
            try { enableCheckRequest.run(); } catch (RuntimeException ignored) { }
        }
        boolean changed = this.desiredEnabled != enabled;
        this.desiredEnabled = enabled;
        boolean changedRuntime = this.enabled != (desiredEnabled && isRuntimeAllowed());
        if (changed && !changedRuntime) notifyChanged();
        applyRuntimeState(desiredEnabled && isRuntimeAllowed());
    }

    public boolean isDesiredEnabled() { return desiredEnabled; }

    public boolean isServerLocked() { return !isRuntimeAllowed(); }

    public boolean isVisible() { return !isServerLocked(); }

    public String getIdentifier() {
        if (explicitIdentifier != null) return explicitIdentifier;
        return ORIGINAL_IDENTIFIERS.getOrDefault(getClass().getSimpleName(),
                getClass().getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT));
    }

    final void setRuntimePolicy(BooleanSupplier runtimeAllowed, Runnable enableCheckRequest) {
        this.runtimeAllowed = runtimeAllowed == null ? () -> true : runtimeAllowed;
        this.enableCheckRequest = enableCheckRequest == null ? () -> {} : enableCheckRequest;
        refreshRuntimePolicy();
    }

    final void refreshRuntimePolicy() {
        applyRuntimeState(desiredEnabled && isRuntimeAllowed());
    }

    private boolean isRuntimeAllowed() {
        try { return runtimeAllowed.getAsBoolean(); }
        catch (RuntimeException failure) { return false; }
    }

    private void applyRuntimeState(boolean shouldEnable) {
        if (this.enabled == shouldEnable) return;
        this.enabled = shouldEnable;
        if (shouldEnable) {
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
    }

    protected ModeSetting modeCreate() {
        return new ModeSetting("Мод", "Vanilla", "Vanilla", "Grim", "Polar");
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public final void initialize(EventBus eventBus) {
        this.eventBus = eventBus;
        if (isEnabled() && eventBus != null) {
            eventBus.register(this);
        }
    }

    public void onTick(MinecraftClient client) {
    }

    public void toggle() {
        this.setEnabled(!this.desiredEnabled);
    }

    public final void resetToDefaults() {
        setEnabled(false);
        setBind(defaultBind);
        setHidden(false);
        setStarred(false);
        for (Setting setting : getSettings()) setting.reset();
    }

    public final void addStateListener(ModuleStateListener listener) {
        if (listener != null) stateListeners.add(listener);
    }

    private void notifyChanged() {
        for (ModuleStateListener listener : List.copyOf(stateListeners)) {
            listener.onChanged(this);
        }
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return LanguageManager.moduleName(name);
    }

    public String getDisplayDescription() {
        return LanguageManager.moduleDescription(name);
    }

    public ModuleCategory getCategory() {
        return this.category;
    }

    public KeyBind getBind() {
        return bind;
    }

    public void setBind(KeyBind bind) {
        KeyBind normalized = bind == null ? KeyBind.NONE : bind;
        if (Objects.equals(this.bind, normalized)) return;
        this.bind = normalized;
        notifyChanged();
    }

    public int getKey() {
        return bind == null ? -1 : bind.getCode();
    }

    public void setKey(int key) {
        setBind(KeyBind.legacy(key));
    }

    public boolean isEnabled() {
        return this.enabled && isRuntimeAllowed();
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        if (this.hidden == hidden) return;
        this.hidden = hidden;
        notifyChanged();
    }

    public boolean isStarred() {
        return starred;
    }

    public void setStarred(boolean starred) {
        if (this.starred == starred) return;
        this.starred = starred;
        notifyChanged();
    }

    public Animation getAnimation() {
        return this.animation;
    }

    public MinecraftClient getMc() {
        return this.mc;
    }
}
