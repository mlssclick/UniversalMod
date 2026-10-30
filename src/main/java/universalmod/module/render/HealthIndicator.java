package universalmod.module.render;

import universalmod.event.SubscribeEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.render.world.HealthIndicatorOverlay;

@ModuleInformation(
        moduleName = "Health Indicator",
        moduleCategory = ModuleCategory.RENDER
)
public final class HealthIndicator extends Module {
    private final HealthIndicatorOverlay overlay = new HealthIndicatorOverlay();

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        overlay.render(event, mc);
    }
}
