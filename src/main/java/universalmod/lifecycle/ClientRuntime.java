package universalmod.lifecycle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import universalmod.command.CommandManager;
import universalmod.config.ConfigManager;
import universalmod.hud.HudManager;
import universalmod.hud.element.CurrentEventsService;
import universalmod.hud.element.CurrentEventsPanel;
import universalmod.hud.notification.HudNotificationEventBridge;
import universalmod.hud.notification.HudNotificationManager;
import universalmod.service.network.HttpClientService;
import universalmod.event.Event;
import universalmod.event.EventBus;
import universalmod.event.type.TickEvent;
import universalmod.module.ModuleManager;
import universalmod.integration.HwApi.HolyWorldFeatureService;
import universalmod.module.misc.ClickGuiModule;
import universalmod.module.misc.CustomCrosshair;
import universalmod.module.misc.coinprice.CoinRateService;
import universalmod.gui.clickgui.ClickGuiController;
import universalmod.gui.clickgui.ClickGui;
import universalmod.service.holyworld.AnarchySwitcher;
import universalmod.feature.friend.FriendRepository;
import universalmod.feature.savekt.SaveKtManager;
import universalmod.feature.savekt.SaveKtLeaveService;
import universalmod.gui.screen.PvpLeaveConfirmScreen;

public final class ClientRuntime {
    private static ClientRuntime instance;
    private EventBus eventBus;
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private boolean initialized;
    private HttpClientService httpService;
    private CurrentEventsService eventsService;
    private HolyWorldFeatureService holyWorldFeatureService;

    public void initClient() {
        if (initialized) return;
        initialized = true;
        instance = this;
        eventBus = new EventBus();
        FriendRepository.load();
        moduleManager = new ModuleManager(eventBus);
        moduleManager.init();
        holyWorldFeatureService = new HolyWorldFeatureService(eventBus, moduleManager);
        moduleManager.setAccessPolicy(holyWorldFeatureService.policy());
        moduleManager.getByType(ClickGuiModule.class).ifPresent(module -> module.setScreenToggle(new ClickGuiModule.ScreenToggle() {
            @Override public boolean isOpen() {
                return MinecraftClient.getInstance().currentScreen instanceof ClickGui;
            }
            @Override public void toggle() {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null) return;
                if (client.currentScreen instanceof ClickGui gui) gui.close();
                else if (client.currentScreen == null) client.setScreen(new ClickGui(moduleManager));
            }
        }));
        moduleManager.getRegisteredByType(CustomCrosshair.class).ifPresent(module ->
                module.setEditorOpener(() -> ClickGuiController.openCustomCrosshairEditor(module)));
        moduleManager.getRegisteredByType(universalmod.module.render.ItemReplacer.class).ifPresent(module ->
                module.setEditorOpener(() -> ClickGuiController.openItemReplacerEditor(module)));
        
        httpService = new HttpClientService();
        CoinRateService.initialize(httpService);
        configManager = new ConfigManager(moduleManager);
        moduleManager.setDirtyListener(configManager::markDirty);
        configManager.init();
        commandManager = new CommandManager(moduleManager, configManager);
        commandManager.init();
        SaveKtManager.setConfirmationScreenOpener(command -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) client.setScreen(new PvpLeaveConfirmScreen(command));
                }
        );
        HudManager.getInstance().load();
        configManager.loadAll();
        eventBus.register(configManager);
        eventBus.register(commandManager);
        eventBus.register(new HudNotificationEventBridge(HudNotificationManager.getInstance()));
        eventsService = new CurrentEventsService(httpService);
        CurrentEventsPanel.getInstance().bindService(eventsService);
        
        
        holyWorldFeatureService.start();
        httpService.start();
        eventsService.start();
        ClientTickEvents.START_CLIENT_TICK.register(client -> postEvent(new TickEvent.Pre(client)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
                    SaveKtLeaveService.getInstance().tick();
                    AnarchySwitcher.getInstance().tick(client);
                    postEvent(new TickEvent.Post(client));
                }
        );
    }

    public void closeServices() {
        if (holyWorldFeatureService != null) holyWorldFeatureService.close();
        if (eventsService != null) eventsService.close();
        CoinRateService.instance().close();
        if (httpService != null) httpService.close();
    }

    public static ClientRuntime getInstance() {
        return instance;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public HolyWorldFeatureService getHolyWorldFeatureService() {
        return holyWorldFeatureService;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public static ModuleManager getModules() {
        return instance == null ? null : instance.moduleManager;
    }

    public static <T extends Event> T postEvent(T event) {
        if (instance == null || instance.eventBus == null) return event;
        return instance.eventBus.post(event);
    }
}
