package universalmod.lifecycle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import universalmod.service.media.MediaArtworkReloadListener;
import universalmod.render.font.msdf.MsdfFontReloadListener;
import universalmod.service.media.MusicTracker;
import net.minecraft.client.MinecraftClient;
import universalmod.event.Event;
import universalmod.event.EventBus;
import universalmod.event.type.TickEvent;
import universalmod.command.CommandManager;
import universalmod.config.ConfigManager;
import universalmod.hud.ElementManager;
import universalmod.hud.element.CurrentEventsPanel;
import universalmod.hud.element.CurrentEventsService;
import universalmod.module.ModuleManager;
import universalmod.integration.HwApi.HolyWorldFeatureService;
import universalmod.service.AnarchySwitcher;
import universalmod.feature.friend.FriendRepository;
import universalmod.gui.clickgui.ClickGui;
import universalmod.gui.clickgui.ClickGuiController;
import universalmod.gui.screen.PvpLeaveConfirmScreen;
import universalmod.module.misc.ClickGuiModule;
import universalmod.module.misc.CustomCrosshair;
import universalmod.module.render.ItemReplacer;
import universalmod.feature.savekt.SaveKtManager;
import universalmod.feature.savekt.SaveKtLeaveService;
import universalmod.lifecycle.service.ClientLifecycleRegistry;
import universalmod.module.misc.coinprice.CoinRateService;
import universalmod.service.network.HttpClientService;

public final class Manager {
    private static Manager instance;

    private final ClientLifecycleRegistry coinPriceLifecycle = new ClientLifecycleRegistry();
    private final ClientLifecycleRegistry holyWorldLifecycle = new ClientLifecycleRegistry();
    private HolyWorldFeatureService holyWorldFeatureService;
    private CurrentEventsService currentEventsService;
    private MusicTracker musicTracker;
    private final EventBus eventBus;
    private final ModuleManager moduleManager;
    private final ConfigManager configManager;
    private final CommandManager commandManager;
    private boolean initialized;
    private boolean clickGuiTextWarmed;
    private boolean coinPriceServicesStarted;

    private Manager() {
        instance = this;
        eventBus = new EventBus();
        moduleManager = new ModuleManager(eventBus);
        configManager = new ConfigManager(moduleManager);
        commandManager = new CommandManager();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                configManager.saveAll();
            } catch (Throwable ignored) {
            }
            try {
                ElementManager.getInstance().save();
            } catch (Throwable ignored) {
            }
            FriendRepository.save();
        }, "UniversalMod-Shutdown"));
    }

    public static Manager getInstance() {
        if (instance == null) instance = new Manager();
        return instance;
    }

    public void initClient() {
        if (initialized) return;
        initialized = true;

        FriendRepository.load();

        HttpClientService http = coinPriceLifecycle.register(new HttpClientService());
        currentEventsService = coinPriceLifecycle.register(new CurrentEventsService(http));
        CurrentEventsPanel.getInstance().bindService(currentEventsService);
        musicTracker = MusicTracker.getInstance();
        musicTracker.bindHttpClient(http);
        coinPriceLifecycle.register(musicTracker);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new MediaArtworkReloadListener());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new MsdfFontReloadListener());
        CoinRateService.initialize(http);
        coinPriceLifecycle.register(CoinRateService.instance());
        coinPriceLifecycle.start();
        coinPriceServicesStarted = true;
        moduleManager.init();
        holyWorldFeatureService = holyWorldLifecycle.register(new HolyWorldFeatureService(eventBus, moduleManager));
        moduleManager.setAccessPolicy(holyWorldFeatureService.policy());
        commandManager.init();
        moduleManager.setDirtyListener(configManager::markDirty);
        configManager.init();
        wireUiIntegrations();
        ElementManager.getInstance().load();
        configManager.loadAll();
        eventBus.register(configManager);
        eventBus.register(commandManager);
        holyWorldLifecycle.start();

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            warmClickGuiText();
            ClickGui.validateState(client);
            ClickGui.tickMovementKeys();
            postEvent(new TickEvent.Pre(client));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SaveKtLeaveService.getInstance().tick();
            AnarchySwitcher.getInstance().tick(client);
            postEvent(new TickEvent.Post(client));
        });
    }

    private void wireUiIntegrations() {
        moduleManager.getByType(ClickGuiModule.class).ifPresent(module -> module.setScreenToggle(new ClickGuiModule.ScreenToggle() {
            @Override
            public boolean isOpen() {
                MinecraftClient client = MinecraftClient.getInstance();
                return client != null && client.currentScreen instanceof ClickGui;
            }

            @Override
            public void toggle() {
                toggleClickGui(MinecraftClient.getInstance());
            }
        }));
        moduleManager.getByType(ItemReplacer.class).ifPresent(module ->
                module.setEditorOpener(() -> ClickGuiController.openItemReplacerEditor(module)));
        moduleManager.getByType(CustomCrosshair.class).ifPresent(module ->
                module.setEditorOpener(() -> ClickGuiController.openCustomCrosshairEditor(module)));
        SaveKtManager.setConfirmationScreenOpener(command -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) client.setScreen(new PvpLeaveConfirmScreen(command));
        });
    }

    public synchronized void shutdownCoinPriceServices() {
        if (!coinPriceServicesStarted) return;
        coinPriceServicesStarted = false;
        coinPriceLifecycle.close();
    }

    public void shutdownHolyWorldServices() {
        holyWorldLifecycle.close();
    }

    public MusicTracker getMusicTracker() { return musicTracker; }

    public CurrentEventsService getCurrentEventsService() {
        return currentEventsService;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public static <T extends Event> T postEvent(T event) {
        Manager manager = getInstance();
        if (manager == null || manager.eventBus == null) return event;
        return manager.eventBus.post(event);
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public static ModuleManager getModules() {
        return getInstance().moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public static void toggleClickGui(MinecraftClient client) {
        if (client == null) return;
        if (client.currentScreen instanceof ClickGui screen) {
            screen.close();
            return;
        }
        client.setScreen(new ClickGui());
    }

    public static boolean isClickGuiOpen(MinecraftClient client) {
        return client != null && client.currentScreen instanceof ClickGui;
    }

    private void warmClickGuiText() {
        if (clickGuiTextWarmed) return;
        clickGuiTextWarmed = ClickGui.warmupText();
    }

}
