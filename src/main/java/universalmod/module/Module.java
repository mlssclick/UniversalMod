package universalmod.module;

import net.minecraft.client.Minecraft;
import universalmod.integration.minecraft.IMinecraft;
import universalmod.event.EventBus;
import universalmod.event.type.ModuleStateChangedEvent;
import universalmod.module.exception.ModuleException;
import universalmod.setting.Setting;
import universalmod.setting.bind.KeyBind;
import universalmod.setting.BindSetting;
import universalmod.service.language.LanguageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public abstract class Module implements IMinecraft {
    private String name;
    private String identifier;
    private ModuleId id;
    private ModuleCategory category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Setting<?>> settingsView = Collections.unmodifiableList(settings);
    private final List<ModuleStateListener> listeners = new ArrayList<>();
    private final BindSetting bindSetting = new BindSetting("Bind", "Module key bind.", KeyBind.NONE, false);
    private KeyBind defaultBind = KeyBind.NONE;
    private EventBus eventBus;
    private BooleanSupplier runtimeAllowed = () -> true;
    private Runnable enableCheckRequest = () -> {};
    private boolean desiredEnabled;
    private boolean enabled;
    private boolean hidden;
    private boolean starred;

    protected Module() {
        bindSetting.addListener((changedSetting, oldValue, newValue) -> notifyChanged());
    }

    
    protected Module(String identifier, String name, ModuleCategory category) {
        this();
        configure(identifier, name, category);
    }

    final void configure(String identifier, String name, ModuleCategory category) {
        if (name == null || name.isBlank()) {
            throw new ModuleException("Module name cannot be empty");
        }
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier.isEmpty()) {
            throw new ModuleException("Module identifier cannot be empty for " + getClass().getName());
        }
        this.identifier = normalizedIdentifier;
        this.name = name;
        this.id = ModuleId.forType(getClass());
        bindSetting.setLocalizationOwner(name);
        this.category = category == null ? ModuleCategory.MISC : category;
    }

    public final String getName() {
        return name;
    }

    public final ModuleId getId() {
        return id;
    }

    
    public final String getIdentifier() {
        return identifier;
    }

    public final String getDisplayName() {
        return LanguageManager.moduleName(name);
    }


    public final String getDisplayDescription() {
        return LanguageManager.moduleDescription(name);
    }

    public final ModuleCategory getCategory() {
        return category;
    }

    public final List<Setting<?>> getSettings() {
        return settingsView;
    }

    public final BindSetting getBindSetting() {
        return bindSetting;
    }

    public final KeyBind getBind() {
        return bindSetting.getValue();
    }

    public final void setBind(KeyBind bind) {
        bindSetting.setValue(bind);
    }

    protected final void setDefaultBind(KeyBind bind) {
        defaultBind = bind == null ? KeyBind.NONE : bind;
        setBind(defaultBind);
    }

    
    public final boolean isEnabled() {
        return enabled && isRuntimeAllowed();
    }

    
    public final boolean isServerLocked() {
        return !isRuntimeAllowed();
    }

    public final boolean isVisible() {
        return !isServerLocked();
    }

    
    public final boolean isDesiredEnabled() {
        return desiredEnabled;
    }

    public final void setEnabled(boolean enabled) {
        
        if (enabled && isServerLocked()) {
            return;
        }
        
        if (enabled) {
            try {
                enableCheckRequest.run();
            } catch (RuntimeException failure) {
            }
        }

        boolean desiredChanged = this.desiredEnabled != enabled;
        this.desiredEnabled = enabled;
        if (desiredChanged) {
            notifyChanged();
        }
        applyRuntimeState(desiredEnabled && isRuntimeAllowed(), desiredChanged);
    }

    public final void toggle() {
        setEnabled(!desiredEnabled);
    }

    final void setRuntimePolicy(BooleanSupplier runtimeAllowed, Runnable enableCheckRequest) {
        this.runtimeAllowed = runtimeAllowed == null ? () -> true : runtimeAllowed;
        this.enableCheckRequest = enableCheckRequest == null ? () -> {} : enableCheckRequest;
        refreshRuntimePolicy();
    }

    final void refreshRuntimePolicy() {
        applyRuntimeState(desiredEnabled && isRuntimeAllowed(), false);
    }

    private boolean isRuntimeAllowed() {
        try {
            return runtimeAllowed.getAsBoolean();
        } catch (RuntimeException policyFailure) {
            return false;
        }
    }

    private void applyRuntimeState(boolean enabled, boolean userVisibleTransition) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            try {
                if (eventBus != null) {
                    eventBus.register(this);
                }
                onEnable();
            } catch (RuntimeException | LinkageError failure) {
                this.enabled = false;
                this.desiredEnabled = false;
                try {
                    onDisable();
                } catch (RuntimeException | LinkageError cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                } finally {
                    if (eventBus != null) {
                        eventBus.unregister(this);
                    }
                }
                notifyChanged();
                throw new ModuleException("Failed to enable " + getName(), failure);
            }
        } else {
            try {
                onDisable();
            } finally {
                if (eventBus != null) {
                    eventBus.unregister(this);
                }
            }
        }
        if (userVisibleTransition && eventBus != null) {
            eventBus.post(new ModuleStateChangedEvent(this, enabled));
        }
    }

    public final void resetToDefaults() {


        setEnabled(false);
        setBind(defaultBind);
        setHidden(false);
        setStarred(false);
        for (Setting<?> setting : settings) {
            setting.reset();
        }
    }

    public final boolean isHidden() {
        return hidden;
    }

    public final void setHidden(boolean hidden) {
        if (this.hidden == hidden) {
            return;
        }
        this.hidden = hidden;
        notifyChanged();
    }

    public final boolean isStarred() {
        return starred;
    }

    public final void setStarred(boolean starred) {
        if (this.starred == starred) {
            return;
        }
        this.starred = starred;
        notifyChanged();
    }

    public final void addStateListener(ModuleStateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public final void initialize(EventBus eventBus) {
        this.eventBus = eventBus;
        if (enabled && eventBus != null) {
            eventBus.register(this);
        }
    }

    public void onTick(Minecraft client) {
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected final <S extends Setting<?>> S register(S setting) {
        if (setting == null) {
            throw new ModuleException("Cannot register null setting in " + name);
        }
        setting.setLocalizationOwner(name);
        settings.add(setting);
        setting.addListener((changedSetting, oldValue, newValue) -> notifyChanged());
        return setting;
    }

    private static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }

    private void notifyChanged() {
        for (ModuleStateListener listener : listeners) {
            listener.onChanged(this);
        }
    }
}
