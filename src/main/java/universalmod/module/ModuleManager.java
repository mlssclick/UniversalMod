package universalmod.module;

import net.minecraft.client.Minecraft;
import universalmod.event.SubscribeEvent;
import universalmod.event.EventBus;
import universalmod.event.type.TickEvent;
import universalmod.module.utility.AutoSprint;
import universalmod.module.utility.EffectNotifier;
import universalmod.module.utility.HotbarCooldowns;
import universalmod.module.utility.SoundReducer;
import universalmod.module.misc.BetterChat;
import universalmod.module.misc.ClickGuiModule;
import universalmod.module.misc.CustomCrosshair;
import universalmod.module.misc.Fullbright;
import universalmod.module.misc.ServerHelper;
import universalmod.module.misc.ShadersButton;
import universalmod.module.misc.TapeMouse;
import universalmod.module.misc.coinprice.CoinPrice;
import universalmod.module.utility.FreeLook;
import universalmod.module.utility.Friends;
import universalmod.module.utility.NameProtect;
import universalmod.module.utility.ItemScroller;
import universalmod.module.utility.LockSlot;
import universalmod.module.render.Ambience;
import universalmod.module.render.Animations;
import universalmod.module.render.AspectRatio;
import universalmod.module.render.BlockOverlay;
import universalmod.module.render.CustomHitBox;
import universalmod.module.utility.CustomDonate;
import universalmod.module.misc.CustomTheme;
import universalmod.module.exception.ModuleException;
import universalmod.module.render.Hands;
import universalmod.module.render.HealthIndicator;
import universalmod.module.render.HitColor;
import universalmod.module.render.Hud;
import universalmod.module.render.ItemReplacer;
import universalmod.module.render.InvisibleTags;
import universalmod.module.render.NoRender;
import universalmod.module.utility.Predictions;
import universalmod.module.render.FireGlow;
import universalmod.module.render.richdog.RichDog;
import universalmod.module.render.Scoreboard;
import universalmod.module.render.SwingAnimation;
import universalmod.module.utility.TotemCounter;
import universalmod.module.utility.SaveKtLeave;
import universalmod.module.render.TwoDItems;
import universalmod.module.render.ViewModel;
import universalmod.module.render.Atmosphere;
import universalmod.module.render.Waypoints;
import universalmod.module.render.WorldLyrics;
import universalmod.module.misc.Zoom;
import universalmod.module.render.ping.PingNametags;
import universalmod.setting.bind.KeyBind;
import universalmod.service.media.MusicTracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ModuleManager {
    private final EventBus eventBus;
    private final MusicTracker musicTracker;
    private final List<Module> modules = new ArrayList<>();
    private final List<Module> registeredModules = new ArrayList<>();
    private final Collection<Module> registeredModulesView = Collections.unmodifiableList(registeredModules);
    private final Map<String, Module> byName = new HashMap<>();
    private final Map<ModuleId, Module> byId = new HashMap<>();
    private final Map<String, Module> byIdentifier = new HashMap<>();
    private final Map<Class<? extends Module>, Module> byType = new HashMap<>();
    private final Map<ModuleCategory, List<Module>> byCategory = new EnumMap<>(ModuleCategory.class);
    private final Map<ModuleCategory, List<Module>> byCategoryViews = new EnumMap<>(ModuleCategory.class);
    private Collection<Module> modulesView = List.of();
    private final Map<Module, Boolean> bindStates = new IdentityHashMap<>();
    private volatile ModuleAccessPolicy accessPolicy = ModuleAccessPolicy.ALLOW_ALL;
    private Runnable dirtyListener = () -> {};
    private long revision;
    private int bindHandlingSuspensionDepth;

    public ModuleManager(EventBus eventBus, MusicTracker musicTracker) {
        this.eventBus = eventBus;
        this.musicTracker = java.util.Objects.requireNonNull(musicTracker, "musicTracker");
    }

    public void init() {
        registerDefaults();
        eventBus.register(this);
    }

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {} : dirtyListener;
    }

    public void setAccessPolicy(ModuleAccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy == null ? ModuleAccessPolicy.ALLOW_ALL : accessPolicy;
        refreshAccessPolicy();
    }

    
    public void refreshAccessPolicy() {
        for (Module module : registeredModules) {
            module.refreshRuntimePolicy();
        }
        rebuildPublicViews();
        bindStates.keySet().removeIf(module -> !this.accessPolicy.isVisible(module));
        revision++;
    }

    public boolean isVisible(Module module) {
        return module != null && accessPolicy.isVisible(module);
    }

    public void register(Module module) {
        registerModule(module, true);
    }

    private void registerInternal(Module module) {
        registerModule(module, false);
    }

    private void registerModule(Module module, boolean listed) {
        if (module == null) {
            throw new ModuleException("Cannot register a null module");
        }

        String key = normalize(module.getName());
        if (byId.containsKey(module.getId())) {
            throw new ModuleException("Duplicate module id: " + module.getId().value());
        }
        if (byType.containsKey(module.getClass())) {
            throw new ModuleException("Duplicate module type: " + module.getClass().getName());
        }
        if (byIdentifier.containsKey(module.getIdentifier())) {
            throw new ModuleException("Duplicate module identifier: " + module.getIdentifier());
        }
        if (listed && byName.containsKey(key)) {
            throw new ModuleException("Duplicate module name: " + module.getName());
        }

        module.setRuntimePolicy(
                () -> accessPolicy.isRuntimeAllowed(module),
                () -> accessPolicy.onEnableRequested(module)
        );
        try {
            module.initialize(eventBus);
        } catch (RuntimeException | Error failure) {
            eventBus.unregister(module);
            throw failure;
        }

        module.addStateListener(changedModule -> dirtyListener.run());
        registeredModules.add(module);
        byId.put(module.getId(), module);
        byIdentifier.put(module.getIdentifier(), module);
        byType.put(module.getClass(), module);
        if (listed) {
            modules.add(module);
            byName.put(key, module);
            byCategory.computeIfAbsent(module.getCategory(), ignored -> new ArrayList<>()).add(module);
            sortViews();
        }
        revision++;
    }

    public long revision() {
        return revision;
    }

    public Collection<Module> getModules() {
        return modulesView;
    }

    
    public Collection<Module> getRegisteredModules() {
        return registeredModulesView;
    }

    public List<Module> getByCategory(ModuleCategory category) {
        return byCategoryViews.getOrDefault(category, List.of());
    }

    public Optional<Module> getByName(String name) {
        Module module = byName.get(normalize(name));
        return module != null && accessPolicy.isVisible(module) ? Optional.of(module) : Optional.empty();
    }

    public Optional<Module> getById(ModuleId id) {
        Module module = byId.get(id);
        return module != null && accessPolicy.isVisible(module) ? Optional.of(module) : Optional.empty();
    }

    
    public Optional<Module> getRegisteredById(ModuleId id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Module> getById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return getById(new ModuleId(id));
        } catch (IllegalArgumentException invalidValue) {
            return Optional.empty();
        }
    }

    public <T extends Module> Optional<T> getByType(Class<T> type) {
        return getRegisteredByType(type).filter(accessPolicy::isVisible);
    }

    
    public <T extends Module> Optional<T> getRegisteredByType(Class<T> type) {
        Module module = byType.get(type);
        if (type.isInstance(module)) {
            return Optional.of(type.cast(module));
        }
        for (Module candidate : registeredModules) {
            if (type.isInstance(candidate)) {
                return Optional.of(type.cast(candidate));
            }
        }
        return Optional.empty();
    }

    public boolean isEnabled(Class<? extends Module> type) {
        return getByType(type).filter(Module::isEnabled).isPresent();
    }

    public void beginConfigApply() {
        bindHandlingSuspensionDepth++;
        bindStates.clear();
    }

    public void endConfigApply() {
        if (bindHandlingSuspensionDepth > 0) {
            bindHandlingSuspensionDepth--;
        }
        bindStates.clear();
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        Minecraft client = event.getClient();
        handleBinds(client);
        for (Module module : modulesView) {
            if (module.isEnabled()) {
                try {
                    module.onTick(client);
                } catch (RuntimeException | LinkageError failure) {
                }
            }
        }
    }

    private void registerDefaults() {
        register(new AutoSprint());
        register(new EffectNotifier());
        register(new HotbarCooldowns());
        register(new SoundReducer());
        register(new BetterChat());
        register(new ClickGuiModule());
        register(new CustomCrosshair());
        register(new Fullbright());
        register(new ServerHelper());
        register(new ShadersButton());
        register(new TapeMouse());
        register(new CoinPrice());
        register(new Ambience());
        register(new Animations());
        register(new AspectRatio());
        register(new BlockOverlay());
        register(new CustomHitBox());
        register(new CustomDonate());
        registerInternal(new CustomTheme());
        register(new HealthIndicator());
        register(new HitColor());
        register(new Hud(musicTracker));
        register(new WorldLyrics(musicTracker));
        register(new ItemReplacer());
        register(new PingNametags());
        register(new TwoDItems());
        register(new InvisibleTags());
        register(new Predictions());
        register(new FireGlow());
        register(new Atmosphere());
        register(new RichDog());
        register(new Scoreboard());
        register(new Hands());
        register(new SwingAnimation());
        register(new ViewModel());
        register(new Waypoints());
        register(new Zoom());
        register(new TotemCounter());
        register(new SaveKtLeave());
        register(new NoRender());
        register(new FreeLook());
        register(new Friends());
        register(new NameProtect());
        register(new ItemScroller());
        register(new LockSlot());
    }

    private void handleBinds(Minecraft client) {
        if (bindHandlingSuspensionDepth > 0) {
            bindStates.clear();
            return;
        }
        if (client == null || client.getWindow() == null) {
            bindStates.clear();
            return;
        }
        if (client.screen != null) {
            bindStates.clear();
            getByType(ClickGuiModule.class).ifPresent(gui ->
                    ClickGuiModule.shouldSuppressHeldBind(gui.getBind().isDown(client.getWindow().handle())));
            return;
        }

        long handle = client.getWindow().handle();
        for (Module module : modulesView) {
            KeyBind bind = module.getBind();
            if (bind == null || !bind.isBound()) {
                bindStates.remove(module);
                continue;
            }
            boolean down = bind.isDown(handle);
            if (module instanceof ClickGuiModule && ClickGuiModule.shouldSuppressHeldBind(down)) {
                bindStates.put(module, true);
                continue;
            }
            boolean wasDown = bindStates.getOrDefault(module, false);
            if (down && !wasDown) {
                module.toggle();
            }
            bindStates.put(module, down);
        }
    }


    private void sortViews() {
        modules.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        for (List<Module> categoryModules : byCategory.values()) {
            categoryModules.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        }
        rebuildPublicViews();
    }

    private void rebuildPublicViews() {
        modulesView = modules.stream()
                .filter(accessPolicy::isVisible)
                .toList();
        byCategoryViews.clear();
        for (Map.Entry<ModuleCategory, List<Module>> entry : byCategory.entrySet()) {
            byCategoryViews.put(entry.getKey(), entry.getValue().stream()
                    .filter(accessPolicy::isVisible)
                    .toList());
        }
    }

    private String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
