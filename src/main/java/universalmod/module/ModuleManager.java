package universalmod.module;


import universalmod.module.misc.BetterChat;
import universalmod.module.misc.ClickGuiModule;
import universalmod.module.misc.CustomCrosshair;
import universalmod.module.misc.CustomTheme;
import universalmod.module.misc.Fullbright;
import universalmod.module.misc.ShadersButton;
import universalmod.module.misc.TapeMouse;
import universalmod.module.misc.Zoom;
import universalmod.module.misc.ServerHelper;
import universalmod.module.render.Ambience;
import universalmod.module.render.Animations;
import universalmod.module.render.AspectRatio;
import universalmod.module.render.BlockOverlay;
import universalmod.module.render.CustomHitBox;
import universalmod.module.render.FireGlow;
import universalmod.module.render.HealthIndicator;
import universalmod.module.render.Hands;
import universalmod.module.render.HitColor;
import universalmod.module.render.Hud;
import universalmod.module.render.InvisibleTags;
import universalmod.module.render.ItemReplacer;
import universalmod.module.render.NoRender;
import universalmod.module.render.Scoreboard;
import universalmod.module.render.SwingAnimation;
import universalmod.module.render.TwoDItems;
import universalmod.module.render.ViewModel;
import universalmod.module.render.Waypoints;
import universalmod.module.render.WorldLyrics;
import universalmod.module.render.Atmosphere;
import universalmod.module.render.ping.PingNametags;
import universalmod.module.render.richdog.RichDog;
import universalmod.module.utility.AutoSprint;
import universalmod.module.utility.CustomDonate;
import universalmod.module.utility.EffectNotifier;
import universalmod.module.utility.FreeLook;
import universalmod.module.utility.Friends;
import universalmod.module.utility.HotbarCooldowns;
import universalmod.module.utility.ItemScroller;
import universalmod.module.utility.LockSlot;
import universalmod.module.utility.NameProtect;
import universalmod.module.utility.Predictions;
import universalmod.module.utility.SaveKtLeave;
import universalmod.module.utility.TotemCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import universalmod.integration.minecraft.IMinecraft;
import universalmod.event.SubscribeEvent;
import universalmod.event.EventBus;
import universalmod.event.type.DrawEvent;
import universalmod.event.type.KeyEvent;
import universalmod.event.type.TickEvent;
import universalmod.module.misc.coinprice.CoinPrice;
import universalmod.module.utility.SoundReducer;
import universalmod.setting.Setting;
import universalmod.setting.bind.KeyBind;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ThemeSetting;




public final class ModuleManager implements IMinecraft {
    private final EventBus eventBus;
    private final List<Module> modules = new ArrayList<>();
    private final List<Module> registeredModules = new ArrayList<>();
    private volatile ModuleAccessPolicy accessPolicy = ModuleAccessPolicy.ALLOW_ALL;
    private final Map<String, Module> byName = new HashMap<>();
    private final Map<Class<? extends Module>, Module> byType = new HashMap<>();
    private final Map<ModuleCategory, List<Module>> byCategory = new EnumMap<>(ModuleCategory.class);
    private final Map<ModuleCategory, List<Module>> byCategoryViews = new EnumMap<>(ModuleCategory.class);
    private List<Module> modulesView = List.of();
    private long revision;
    private final Map<Module, Boolean> bindStates = new IdentityHashMap<>();
    private final Map<Module, Boolean> notificationStates = new IdentityHashMap<>();
    private Runnable dirtyListener = () -> {
    };
    private int bindHandlingSuspensionDepth;

    public ModuleManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void init() {
        registerDefaults();
        eventBus.register(this);
    }

    public void setAccessPolicy(ModuleAccessPolicy policy) {
        this.accessPolicy = policy == null ? ModuleAccessPolicy.ALLOW_ALL : policy;
        refreshAccessPolicy();
    }

    public void refreshAccessPolicy() {
        for (Module module : registeredModules) module.refreshRuntimePolicy();
        bindStates.keySet().removeIf(module -> !accessPolicy.isVisible(module));
        sortViews();
    }

    public boolean isVisible(Module module) {
        return module != null && accessPolicy.isVisible(module);
    }

    public List<Module> getRegisteredModules() { return List.copyOf(registeredModules); }

    public <T extends Module> Optional<T> getRegisteredByType(Class<T> type) {
        Module direct = byType.get(type);
        if (type.isInstance(direct)) return Optional.of(type.cast(direct));
        for (Module candidate : registeredModules) if (type.isInstance(candidate)) return Optional.of(type.cast(candidate));
        return Optional.empty();
    }

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {
        } : dirtyListener;
    }

    public void register(Module module) {
        if (module == null) return;
        String key = normalize(module.getName());
        if (byName.containsKey(key)) return;

        module.addStateListener(changed -> this.dirtyListener.run());
        notificationStates.put(module, module.isEnabled());
        module.addStateListener(changed -> {
            Boolean was = notificationStates.put(changed, changed.isEnabled());
            if (was != null && was != changed.isEnabled()) {
                universalmod.hud.notification.HudNotificationManager.getInstance()
                        .onModuleState(changed, changed.isEnabled());
            }
        });
        module.ensureSettingsInitialized();
        module.setRuntimePolicy(() -> accessPolicy.isRuntimeAllowed(module),
                () -> accessPolicy.onEnableRequested(module));
        registeredModules.add(module);
        modules.add(module);
        byName.put(key, module);
        byType.putIfAbsent(module.getClass(), module);
        byCategory.computeIfAbsent(module.getCategory(), ignored -> new ArrayList<>()).add(module);
        module.initialize(eventBus);
        sortViews();
    }

    private void registerInternal(Module module) {
        if (module == null) return;
        module.addStateListener(changed -> this.dirtyListener.run());
        notificationStates.put(module, module.isEnabled());
        module.addStateListener(changed -> {
            Boolean was = notificationStates.put(changed, changed.isEnabled());
            if (was != null && was != changed.isEnabled()) {
                universalmod.hud.notification.HudNotificationManager.getInstance()
                        .onModuleState(changed, changed.isEnabled());
            }
        });
        module.ensureSettingsInitialized();
        module.setRuntimePolicy(() -> accessPolicy.isRuntimeAllowed(module),
                () -> accessPolicy.onEnableRequested(module));
        registeredModules.add(module);
        byType.putIfAbsent(module.getClass(), module);
        module.initialize(eventBus);
    }

    public List<Module> getModules() {
        return modulesView;
    }

    public long revision() {
        return revision;
    }

    public List<Module> getByCategory(ModuleCategory category) {
        return byCategoryViews.getOrDefault(category, List.of());
    }

    public Optional<Module> getByName(String name) {
        Module module = byName.get(normalize(name));
        return module != null && isVisible(module) ? Optional.of(module) : Optional.empty();
    }

    public <T extends Module> Optional<T> getByType(Class<T> type) {
        Module direct = byType.get(type);
        if (type.isInstance(direct) && isVisible(direct)) return Optional.of(type.cast(direct));
        for (Module module : modules) if (type.isInstance(module) && isVisible(module)) return Optional.of(type.cast(module));
        return Optional.empty();
    }

    public boolean isEnabled(Class<? extends Module> type) {
        return getByType(type).filter(Module::isEnabled).isPresent();
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(String name) {
        return (T) getByName(name).orElse(null);
    }

    public <T extends Module> T get(Class<T> clazz) {
        return getByType(clazz).orElse(null);
    }

    public List<Module> get(ModuleCategory category) {
        return getByCategory(category);
    }

    public void beginConfigApply() {
        bindHandlingSuspensionDepth++;
        bindStates.clear();
    }

    public void endConfigApply() {
        if (bindHandlingSuspensionDepth > 0) bindHandlingSuspensionDepth--;
        bindStates.clear();
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        MinecraftClient client = event.getClient();
        handleBinds(client);
        for (Module module : modules) {
            if (module.isEnabled()) module.onTick(client);
        }
    }

    @SubscribeEvent
    private void onDraw(DrawEvent event) {
        if (event.getLayer() != DrawEvent.Layer.GAME) return;
        for (Module module : modules) {
            module.getAnimation().run(module.isEnabled());
            for (Setting setting : module.getSettings()) {
                if (setting instanceof BooleanSetting booleanSetting) {
                    booleanSetting.getAnimation().run(booleanSetting.getValue());
                }
                if (setting instanceof ThemeSetting themeSetting && themeSetting.getValue() != null) {
                    themeSetting.getValue().animation.run(1.0F);
                }
            }
        }
    }

    @SubscribeEvent
    private void onKey(KeyEvent event) {
        if (event == null || event.screen() != null || bindHandlingSuspensionDepth > 0 || event.action() != GLFW.GLFW_PRESS)
            return;
        int code = event.key();
        for (Module module : modules) {
            if (!isVisible(module)) continue;
            for (Setting setting : module.getSettings()) {
                if (setting instanceof BooleanSetting booleanSetting && booleanSetting.getKey() == code) {
                    booleanSetting.toggle();
                }
            }
        }
    }

    private void handleBinds(MinecraftClient client) {
        if (bindHandlingSuspensionDepth > 0) {
            bindStates.clear();
            return;
        }
        if (client == null || client.getWindow() == null || client.currentScreen != null) {
            bindStates.clear();
            return;
        }

        long handle = client.getWindow().getHandle();
        for (Module module : modules) {
            if (!isVisible(module)) { bindStates.remove(module); continue; }
            KeyBind bind = module.getBind();
            if (bind == null || !bind.isBound()) {
                bindStates.remove(module);
                continue;
            }
            boolean down = bind.isDown(handle);
            boolean wasDown = bindStates.getOrDefault(module, false);
            if (module instanceof universalmod.module.misc.ClickGuiModule
                    && universalmod.module.misc.ClickGuiModule.shouldSuppressHeldBind(down)) {
                bindStates.put(module, down);
                continue;
            }
            if (down && !wasDown) module.toggle();
            bindStates.put(module, down);
        }
    }

    private void registerDefaults() {
        register(new Ambience());
        register(new Animations());
        register(new AspectRatio());
        register(new BlockOverlay());
        register(new CustomHitBox());
        register(new HealthIndicator());
        register(new HitColor());
        register(new Hud());
        register(new ItemReplacer());
        register(new PingNametags());
        register(new TwoDItems());
        register(new InvisibleTags());
        register(new FireGlow());
        register(new Atmosphere());
        register(new RichDog());
        register(new Scoreboard());
        register(new Hands());
        register(new SwingAnimation());
        register(new ViewModel());
        register(new Waypoints());
        register(new WorldLyrics());
        register(new NoRender());

        register(new BetterChat());
        register(new ClickGuiModule());
        register(new CustomCrosshair());
        register(new Fullbright());
        register(new ServerHelper());
        register(new ShadersButton());
        register(new TapeMouse());
        register(new CoinPrice());
        registerInternal(new CustomTheme());
        register(new Zoom());

        register(new AutoSprint());
        register(new SoundReducer());
        register(new EffectNotifier());
        register(new HotbarCooldowns());
        register(new CustomDonate());
        register(new Predictions());
        register(new TotemCounter());
        register(new SaveKtLeave());
        register(new FreeLook());
        register(new Friends());
        register(new NameProtect());
        register(new ItemScroller());
        register(new LockSlot());
    }

    private void sortViews() {
        modules.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        for (List<Module> categoryModules : byCategory.values()) {
            categoryModules.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        }
        modulesView = Collections.unmodifiableList(modules.stream().filter(accessPolicy::isVisible).toList());
        revision++;
        byCategoryViews.clear();
        for (Map.Entry<ModuleCategory, List<Module>> entry : byCategory.entrySet()) {
            byCategoryViews.put(entry.getKey(), Collections.unmodifiableList(entry.getValue().stream().filter(accessPolicy::isVisible).toList()));
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
