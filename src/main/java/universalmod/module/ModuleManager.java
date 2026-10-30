package universalmod.module;


import java.util.*;

import universalmod.event.SubscribeEvent;
import universalmod.event.EventBus;
import universalmod.event.type.TickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import universalmod.module.misc.*;
import universalmod.module.misc.coinprice.CoinPrice;
import universalmod.module.render.*;
import universalmod.module.render.ping.PingNametags;
import universalmod.module.render.richdog.RichDog;
import universalmod.module.utility.*;

public final class ModuleManager {
    private final EventBus eventBus;
    private final List<Module> modules = new ArrayList<>();
    private final List<Module> registeredModules = new ArrayList<>();
    private volatile ModuleAccessPolicy accessPolicy = ModuleAccessPolicy.ALLOW_ALL;
    private CustomTheme internalTheme;
    private final Map<Module, Boolean> bindStates = new IdentityHashMap<>();
    private final Map<String, Module> byName = new HashMap<>();
    private final Map<Class<? extends Module>, Module> byType = new HashMap<>();
    private final Map<ModuleCategory, List<Module>> byCategory = new EnumMap<>(ModuleCategory.class);
    private final Map<ModuleCategory, List<Module>> byCategoryViews = new EnumMap<>(ModuleCategory.class);
    private List<Module> modulesView = List.of();
    private Runnable dirtyListener = () -> {
    };
    private int bindHandlingSuspensionDepth;
    private boolean initialized;
    private long revision;

    public ModuleManager() {
        this(new EventBus());
    }

    public ModuleManager(EventBus eventBus) {
        this.eventBus = eventBus == null ? new EventBus() : eventBus;
        build();
        indexModules();
        refreshAccessPolicy();
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        for (Module module : registeredModules) module.initialize(eventBus);
        eventBus.register(this);
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public void setAccessPolicy(ModuleAccessPolicy policy) {
        accessPolicy = policy == null ? ModuleAccessPolicy.ALLOW_ALL : policy;
        refreshAccessPolicy();
    }

    public void refreshAccessPolicy() {
        for (Module module : registeredModules) module.refreshRuntimePolicy();
        bindStates.keySet().removeIf(module -> !isVisible(module));
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

    private void sortViews() {
        modules.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        for (List<Module> category : byCategory.values())
            category.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        modulesView = Collections.unmodifiableList(modules.stream().filter(this::isVisible).toList());
        byCategoryViews.clear();
        for (var entry : byCategory.entrySet())
            byCategoryViews.put(entry.getKey(), Collections.unmodifiableList(entry.getValue().stream().filter(this::isVisible).toList()));
        revision++;
    }

    public void setDirtyListener(Runnable listener) {
        dirtyListener = listener == null ? () -> {
        }
                : listener;
    }

    private void indexModules() {
        byName.clear();
        byType.clear();
        byCategory.clear();
        registeredModules.clear();
        for (Module module : modules) {
            registeredModules.add(module);
            module.ensureSettingsInitialized();
            module.setRuntimePolicy(() -> accessPolicy.isRuntimeAllowed(module),
                    () -> accessPolicy.onEnableRequested(module));
            module.captureCurrentBindAsDefault();
            module.addStateListener(changed -> { revision++; dirtyListener.run(); });
            byName.putIfAbsent(normalize(module.getName()), module);
            byType.putIfAbsent(module.getClass(), module);
            byCategory.computeIfAbsent(module.getCategory(), ignored -> new ArrayList<>()).add(module);
        }
        if (internalTheme != null) {
            byType.putIfAbsent(CustomTheme.class, internalTheme);
            registeredModules.add(internalTheme);
        }
    }

    private void build() {
        registerRenderModules();
        registerMiscModules();
        registerUtilityModules();
    }

    private void registerRenderModules() {
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
        register(new TwoDItems());
    }

    private void registerMiscModules() {
        register(new BetterChat());
        register(new CoinPrice());
        register(new ClickGuiModule());
        register(new CustomCrosshair());
        register(new Fullbright());
        register(new ServerHelper());
        register(new ShadersButton());
        register(new TapeMouse());
        registerInternal(new CustomTheme());
        register(new Zoom());
    }

    private void registerUtilityModules() {
        register(new AutoSprint());
        register(new EffectNotifier());
        register(new HotbarCooldowns());
        register(new SoundReducer());
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

    public void register(Module module) {
        if (module == null) return;
        if (initialized) {
            if (byName.containsKey(normalize(module.getName()))) return;
            module.ensureSettingsInitialized();
            module.setRuntimePolicy(() -> accessPolicy.isRuntimeAllowed(module),
                    () -> accessPolicy.onEnableRequested(module));
            module.addStateListener(changed -> { revision++; dirtyListener.run(); });
            registeredModules.add(module);
            modules.add(module);
            byName.put(normalize(module.getName()), module);
            byType.putIfAbsent(module.getClass(), module);
            byCategory.computeIfAbsent(module.getCategory(), ignored -> new ArrayList<>()).add(module);
            module.initialize(eventBus);
            sortViews();
        } else modules.add(module);
    }

    public List<Module> getModules() {
        return modulesView;
    }

    private void registerInternal(Module module) {
        if (module == null) {
            return;
        }
        if (module instanceof CustomTheme theme) {
            internalTheme = theme;
        }
        byType.putIfAbsent(module.getClass(), module);
        registeredModules.add(module);
        module.ensureSettingsInitialized();
        module.setRuntimePolicy(() -> accessPolicy.isRuntimeAllowed(module),
                () -> accessPolicy.onEnableRequested(module));
        module.addStateListener(changedModule -> { revision++; dirtyListener.run(); });
        module.initialize(eventBus);
    }

    public long revision() {
        return revision;
    }

    public Module get(String n) {
        Module module = byName.get(normalize(n));
        return isVisible(module) ? module : null;
    }

    public Optional<Module> getByName(String n) {
        return Optional.ofNullable(get(n));
    }

    public <T extends Module> Optional<T> getByType(Class<T> type) {
        Module exact = byType.get(type);
        if (type.isInstance(exact) && isVisible(exact)) return Optional.of(type.cast(exact));
        for (Module module : modules) if (type.isInstance(module) && isVisible(module)) return Optional.of(type.cast(module));
        return Optional.empty();
    }

    public List<Module> getByCategory(ModuleCategory category) {
        return byCategoryViews.getOrDefault(category, List.of());
    }

    public boolean isEnabled(Class<? extends Module> type) {
        return getByType(type).filter(Module::isEnabled).isPresent();
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
    private void universalmod$onTick(TickEvent.Post event) {
        tick(event.getClient());
    }

    public void tick(MinecraftClient client) {
        if (client == null || client.getWindow() == null) return;
        if (bindHandlingSuspensionDepth > 0) {
            bindStates.clear();
            return;
        }
        long window = client.getWindow().getHandle();
        for (Module module : modules) {
            if (!isVisible(module)) { bindStates.remove(module); continue; }
            if (!(module instanceof ClickGuiModule)) {
                boolean down = client.currentScreen == null && module.getBind().isDown(window);
                boolean wasDown = bindStates.getOrDefault(module, false);
                if (down && !wasDown) module.toggle();
                bindStates.put(module, down);
            }
            if (module.isEnabled()) module.onTick(client);
        }
    }

    public void renderHud(DrawContext context, float tickDelta) {
        for (Module module : modules) if (module.isEnabled()) module.onHudRender(context, tickDelta);
    }

    public void renderWorld(WorldRenderContext context) {
        for (Module module : modules) if (module.isEnabled()) module.onWorldRender(context);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
