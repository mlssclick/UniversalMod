package universalmod.module.render;

import java.awt.Color;

import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.world.biome.Biome;
import net.minecraft.client.world.ClientWorld;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.SliderSetting;
import universalmod.render.world.ambience.SkyShaderRenderer;
import universalmod.render.world.ambience.SkyShaderState;
import universalmod.render.ClientPipelines;

@ModuleInformation(
        moduleName = "Ambience",
        moduleCategory = ModuleCategory.RENDER
)
public final class Ambience extends Module {
    private static Ambience instance;

    public final ModeSetting mode = new ModeSetting("Mode", "Day", "Day", "Midday", "Night", "Midnight", "Custom");
    public final SliderSetting customTime = new SliderSetting("Time", 1000.0, 0.0, 24000.0, 100.0);
    public final ModeSetting weather = new ModeSetting("Weather", "Sunny", "Sunny", "Rain", "Thunder", "Snow");

    public final BooleanSetting wet = new BooleanSetting("Wet", "Wet surfaces and screen-space reflections.", false);
    public final SliderSetting wetReflectionStrength = new SliderSetting("Wet Reflection Strength", 70.0, 0.0, 100.0, 5.0);
    public final SliderSetting wetness = new SliderSetting("Wetness", 60.0, 0.0, 100.0, 5.0);
    public final ModeSetting wetQuality = new ModeSetting("Wet Quality", "Medium", "Low", "Medium", "High");
    public final SliderSetting saturation = new SliderSetting("Saturation", 0.0, -1.0, 1.0, 0.05);
    public final BooleanSetting customFog = new BooleanSetting("Custom Fog", false);
    public final ColorSetting customFogColor = new ColorSetting("Fog Color", new Color(200, 214, 229, 255).getRGB());
    public final SliderSetting fogDistance = new SliderSetting("Fog Distance", 96.0, 2.0, 512.0, 1.0);
    public final SliderSetting fogDensity = new SliderSetting("Fog Density", 0.50, 0.0, 1.0, 0.01);
    public final BooleanSetting skyShader = new BooleanSetting("Sky Shader", false);
    public final ModeSetting skyShaderMode = new ModeSetting(
            "Sky Mode",
            "Aurora",
            "Aurora", "Северное сияние", "Nebula", "Cosmic Veil", "Deep Space", "Void", "Plasma"
    );
    public final SliderSetting skySpeed = new SliderSetting("Sky Speed", 1.0, 0.01, 6.0, 0.01);
    public final SliderSetting plasmaScale = new SliderSetting("Plasma Scale", 1.0, 0.2, 3.0, 0.05);
    public final SliderSetting plasmaSpeed = new SliderSetting("Plasma Speed", 1.0, 0.0, 3.0, 0.05);
    public final BooleanSetting showStars = new BooleanSetting("Stars", true);
    public final ColorSetting skyShaderColor = new ColorSetting("Shader Color", new Color(108, 99, 210, 255).getRGB());

    private boolean weatherOverrideActive;
    private boolean cachedServerRaining;
    private float cachedRainGradient;
    private float cachedThunderGradient;
    private ClientWorld weatherSnapshotWorld;

    public Ambience() {
        instance = this;
        customTime.setVisible(() -> mode.is("Custom"));
        wetReflectionStrength.setVisible(wet::getValue);
        wetness.setVisible(wet::getValue);
        wetQuality.setVisible(wet::getValue);
        wetReflectionStrength.setDescription("Wet-world reflection intensity.");
        wetness.setDescription("Surface wetness.");
        wetQuality.setDescription("Screen-space reflection quality.");
        customFogColor.setVisible(customFog::getValue);
        fogDistance.setVisible(customFog::getValue);
        fogDensity.setVisible(customFog::getValue);
        skyShaderMode.setVisible(skyShader::getValue);
        skySpeed.setVisible(skyShader::getValue);
        plasmaScale.setVisible(() -> skyShader.getValue() && skyShaderMode.is("Plasma"));
        plasmaSpeed.setVisible(() -> skyShader.getValue() && skyShaderMode.is("Plasma"));
        showStars.setVisible(skyShader::getValue);
        skyShaderColor.setVisible(skyShader::getValue);
    }

    public static Ambience getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        ClientPipelines.ensurePreloaded();
        SkyShaderRenderer.resetTime();
        universalmod.render.wetworld.WetWorldRenderer.resetTime();
        SkyShaderRenderer.updateConfig(skyShaderState());
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.world instanceof ClientWorld world) {
            restoreWeather(world, world.getLevelProperties());
        } else {
            clearWeatherSnapshot();
        }
        universalmod.render.wetworld.WetWorldRenderer.resetTime();
        super.onDisable();
    }

    @Override
    public void onTick(net.minecraft.client.MinecraftClient tickClient) {
        if (skyShader.getValue()) {
            SkyShaderRenderer.updateConfig(skyShaderState());
        }
    }

    public boolean isWetActive() { return isEnabled() && wet.getValue(); }
    public float wetReflectionStrength() { return wetReflectionStrength.getFloatValue() / 100.0f; }
    public float wetness() { return wetness.getFloatValue() / 100.0f; }
    public float wetQualitySteps() {
        if (wetQuality.is("Low")) return 14.0f;
        if (wetQuality.is("High")) return 40.0f;
        return 24.0f;
    }

    public long getInternalTime() {
        if (mode.is("Day")) return 1000L;
        if (mode.is("Midday")) return 6000L;
        if (mode.is("Night")) return 13000L;
        if (mode.is("Midnight")) return 18000L;
        return (long) customTime.getValue();
    }

    public long getCustomTime() {
        return getInternalTime();
    }

    public boolean isSkyShaderEnabled() {
        return isEnabled() && skyShader.getValue();
    }

    public String getSkyShaderMode() {
        return skyShaderMode.getValue();
    }

    public boolean isSkyStarsEnabled() {
        return showStars.getValue();
    }

    public float getSkyStarDensity() {
        return 0.7F;
    }

    public float getSkyNebulaStrength() {
        return 0.8F;
    }

    public float getSkyPlasmaScale() {
        return plasmaScale.getFloatValue();
    }

    public float getSkyPlasmaSpeed() {
        return plasmaSpeed.getFloatValue();
    }

    public float getSkySpeed() {
        return skySpeed.getFloatValue();
    }

    public int getSkyShaderColor() {
        return skyShaderColor.getValue();
    }

    public SkyShaderState skyShaderState() {
        return new SkyShaderState(
                isSkyShaderEnabled(), getSkyShaderMode(), getSkySpeed(), getSkyPlasmaScale(), getSkyPlasmaSpeed(),
                isSkyStarsEnabled(), getSkyStarDensity(), getSkyNebulaStrength(), getSkyShaderColor()
        );
    }

    public float getSaturationFactor() {
        return Math.max(0.0F, Math.min(2.0F, 1.0F + saturation.getFloatValue()));
    }

    public boolean hasCustomFog() {
        return isEnabled() && customFog.getValue();
    }

    public boolean shouldApplyCustomFog(Camera camera) {
        if (!hasCustomFog() || camera == null || camera.getSubmersionType() != CameraSubmersionType.NONE) {
            return false;
        }
        if (camera.getFocusedEntity() instanceof LivingEntity living) {
            if (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS)) {
                return false;
            }
        }
        return true;
    }

    public int getCustomFogColor() {
        return customFogColor.getValue();
    }

    public float getFogDistance() {
        return Math.max(2.0F, Math.min(512.0F, fogDistance.getFloatValue()));
    }

    public float getFogDensity() {
        return Math.max(0.0F, Math.min(1.0F, fogDensity.getFloatValue()));
    }

    public float getFogStart() {
        float distance = getFogDistance();
        return distance * (1.0F - getFogDensity());
    }

    public Biome.Precipitation getForcedPrecipitation() {
        if (!isEnabled()) return null;
        if (weather.is("Snow")) return Biome.Precipitation.SNOW;
        if (weather.is("Rain") || weather.is("Thunder")) return Biome.Precipitation.RAIN;
        return Biome.Precipitation.NONE;
    }

    public boolean shouldForceSnow() {
        return getForcedPrecipitation() == Biome.Precipitation.SNOW;
    }

    public void syncWeather(ClientWorld world, ClientWorld.Properties properties) {
        if (world == null || properties == null) {
            clearWeatherSnapshot();
            return;
        }

        if (weatherSnapshotWorld != world) {
            clearWeatherSnapshot();
        }

        if (!weatherOverrideActive) {
            cachedServerRaining = properties.isRaining();
            cachedRainGradient = world.getRainGradient(1.0F);
            cachedThunderGradient = world.getThunderGradient(1.0F);
            weatherOverrideActive = true;
            weatherSnapshotWorld = world;
        }

        boolean raining = weather.is("Rain") || weather.is("Thunder") || weather.is("Snow");
        properties.setRaining(raining);
        world.setRainGradient(raining ? 1.0F : 0.0F);
        world.setThunderGradient(weather.is("Thunder") ? 1.0F : 0.0F);
    }

    private void restoreWeather(ClientWorld world, ClientWorld.Properties properties) {
        if (!weatherOverrideActive || world == null || properties == null) {
            return;
        }
        properties.setRaining(cachedServerRaining);
        world.setRainGradient(cachedRainGradient);
        world.setThunderGradient(cachedThunderGradient);
        clearWeatherSnapshot();
    }

    private void clearWeatherSnapshot() {
        weatherOverrideActive = false;
        weatherSnapshotWorld = null;
    }
}
