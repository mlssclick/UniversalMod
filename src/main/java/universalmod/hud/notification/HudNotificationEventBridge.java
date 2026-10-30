package universalmod.hud.notification;

import universalmod.event.SubscribeEvent;
import universalmod.event.type.ModuleStateChangedEvent;


public final class HudNotificationEventBridge {
    private final HudNotificationManager notifications;

    public HudNotificationEventBridge(HudNotificationManager notifications) {
        this.notifications = notifications;
    }

    @SubscribeEvent
    private void onModuleStateChanged(ModuleStateChangedEvent event) {
        notifications.onModuleState(event.module(), event.enabled());
    }
}
