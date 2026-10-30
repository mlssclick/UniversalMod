package universalmod.module.render;

import universalmod.event.SubscribeEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.render.world.HealthIndicatorOverlay;

public final class HealthIndicator extends Module {
    private final HealthIndicatorOverlay overlay = new HealthIndicatorOverlay();

    public HealthIndicator() {
        super("health_indicator", "Health Indicator", ModuleCategory.RENDER);
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        overlay.render(event, mc);
    }
}
