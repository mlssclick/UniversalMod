package universalmod;

import net.fabricmc.api.ClientModInitializer;
import universalmod.module.render.FireGlow;
import universalmod.lifecycle.Manager;
import universalmod.feature.savekt.SaveKtAttackTracker;

public final class Engine implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Manager.getInstance().initClient();
        SaveKtAttackTracker.init();
    }

    public static void shutdown() {
        Manager activeManager = Manager.getInstance();
        if (activeManager != null && activeManager.getConfigManager() != null) {
            try {
                activeManager.getConfigManager().saveAll();
            } catch (RuntimeException exception) {

            }
        }

        FireGlow glow = FireGlow.getInstance();
        if (glow != null) {
            glow.shutdown();
        }

        universalmod.render.Render2D.close();
        universalmod.render.Render3D.close();
        universalmod.render.world.ambience.SkyShaderRenderer.close();

        universalmod.render.wetworld.WetWorldRenderer.close();
        universalmod.render.winter.WinterWorldRenderer.close();
        universalmod.render.atmosphere.AtmosphereMoodRenderer.close();
        universalmod.render.atmosphere.AtmosphereRainRenderer.reset();
        universalmod.render.atmosphere.SakuraWorldRenderer.reset();
        universalmod.render.atmosphere.WeatherWorldRenderer.reset();

        if (activeManager != null) {
            activeManager.shutdownHolyWorldServices();
            activeManager.shutdownCoinPriceServices();
        }
    }
}
