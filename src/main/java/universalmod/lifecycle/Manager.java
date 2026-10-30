package universalmod.lifecycle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import universalmod.command.CommandManager;
import universalmod.config.ConfigManager;
import universalmod.event.Event;
import universalmod.event.EventBus;
import universalmod.event.type.TickEvent;
import universalmod.feature.FriendHelper;
import universalmod.feature.savekt.SaveKtAttackTracker;
import universalmod.feature.savekt.SaveKtLeaveService;
import universalmod.gui.clickgui.ClickGui;
import universalmod.hud.ElementManager;
import universalmod.hud.element.CurrentEventsPanel;
import universalmod.hud.element.CurrentEventsService;
import universalmod.hud.notification.HudNotificationManager;
import universalmod.hud.notification.HudNotificationEventBridge;
import universalmod.lifecycle.service.ClientLifecycleRegistry;
import universalmod.lifecycle.service.CloseActionService;
import universalmod.integration.HwApi.HolyWorldFeatureService;
import universalmod.module.ModuleManager;
import universalmod.module.Module;
import universalmod.module.misc.ClickGuiModule;
import universalmod.module.misc.coinprice.CoinRateService;
import universalmod.module.render.FireGlow;
import universalmod.render.wetworld.WetWorldRenderer;
import universalmod.render.winter.WinterWorldRenderer;
import universalmod.render.atmosphere.AtmosphereMoodRenderer;
import universalmod.render.world.ambience.SkyShaderRenderer;
import universalmod.service.AnarchySwitcher;
import universalmod.service.language.LanguageManager;
import universalmod.service.media.MusicTracker;
import universalmod.service.network.HttpClientService;

public final class Manager implements AutoCloseable {
    private static Manager instance;

    private final ClientLifecycleRegistry lifecycle = new ClientLifecycleRegistry();
    private EventBus eventBus;
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private HttpClientService httpClientService;
    private CurrentEventsService currentEventsService;
    private MusicTracker musicTracker;
    private HolyWorldFeatureService holyWorldFeatureService;
    private boolean clickGuiTextWarmed;
    private boolean closed;

    public void initClient() {
        instance = this;
        LanguageManager.validateCatalogs();
        eventBus = new EventBus();
        eventBus.setRecoverableFailureHandler((owner, failure) -> {
            if (!(owner instanceof Module module)) {
                return false;
            }
            return true;
        });
        eventBus.register(new HudNotificationEventBridge(HudNotificationManager.getInstance()));
        FriendHelper.load();

        
        httpClientService = lifecycle.register(new HttpClientService());
        CoinRateService.initialize(httpClientService);
        lifecycle.register(CoinRateService.instance());
        currentEventsService = lifecycle.register(new CurrentEventsService(httpClientService));
        CurrentEventsPanel.getInstance().bindService(currentEventsService);
        lifecycle.register(SaveKtLeaveService.getInstance());
        musicTracker = new MusicTracker();
        musicTracker.bindHttpClient(httpClientService);
        lifecycle.register(musicTracker);

        moduleManager = new ModuleManager(eventBus, musicTracker);
        moduleManager.init();
        holyWorldFeatureService = lifecycle.register(new HolyWorldFeatureService(eventBus, moduleManager));
        moduleManager.setAccessPolicy(holyWorldFeatureService.policy());
        configManager = new ConfigManager(moduleManager);
        moduleManager.setDirtyListener(configManager::markDirty);
        configManager.init();
        commandManager = new CommandManager(moduleManager, configManager);
        commandManager.init();
        ElementManager.getInstance().load();
        configManager.loadAll();
        HudNotificationManager.getInstance().setReady(true);
        eventBus.register(configManager);
        eventBus.register(commandManager);

        FireGlow glow = FireGlow.getInstance();
        if (glow != null) {
            lifecycle.register(new CloseActionService("FireGlow", glow::shutdown));
        }
        lifecycle.register(new CloseActionService("WetWorldRenderer", WetWorldRenderer::close));
        lifecycle.register(new CloseActionService("WinterWorldRenderer", WinterWorldRenderer::close));
        lifecycle.register(new CloseActionService("AtmosphereMoodRenderer", AtmosphereMoodRenderer::close));
        lifecycle.register(new CloseActionService("SkyShaderRenderer", SkyShaderRenderer::close));
        lifecycle.start();
        SaveKtAttackTracker.init();

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

    public static Manager getInstance() {
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

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public HttpClientService getHttpClientService() {
        return httpClientService;
    }

    public CurrentEventsService getCurrentEventsService() {
        return currentEventsService;
    }

    public MusicTracker getMusicTracker() {
        return musicTracker;
    }

    public HolyWorldFeatureService getHolyWorldFeatureService() {
        return holyWorldFeatureService;
    }

    public static ModuleManager getModules() {
        return instance == null ? null : instance.moduleManager;
    }

    public static <T extends Event> T postEvent(T event) {
        if (instance == null || instance.eventBus == null) {
            return event;
        }
        return instance.eventBus.post(event);
    }

    public static void toggleClickGui(Minecraft client) {
        if (client == null) {
            return;
        }
        if (client.screen instanceof ClickGui) {
            client.screen.onClose();
            return;
        }
        if (instance == null || instance.moduleManager == null
                || instance.moduleManager.getByType(ClickGuiModule.class).isEmpty()) {
            return;
        }
        client.setScreen(new ClickGui());
    }

    public static boolean isClickGuiOpen(Minecraft client) {
        return client != null && client.screen instanceof ClickGui;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (configManager != null) {
            try {
                configManager.saveAll();
            } catch (RuntimeException failure) {
            }
        }
        try {
            ElementManager.getInstance().save();
        } catch (RuntimeException failure) {
        }
        try {
            lifecycle.close();
        } catch (RuntimeException | LinkageError failure) {
        } finally {
            HudNotificationManager.getInstance().setReady(false);
            if (instance == this) {
                instance = null;
            }
        }
    }

    private void warmClickGuiText() {
        if (clickGuiTextWarmed) {
            return;
        }
        clickGuiTextWarmed = ClickGui.warmupText();
    }
}
