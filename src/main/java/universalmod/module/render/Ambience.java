package universalmod.module.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.world.biome.Biome;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;
import universalmod.render.world.ambience.AmbiencePostRenderer;
import universalmod.render.world.ambience.SkyShaderRenderer;
import universalmod.render.world.ambience.SkyShaderState;

public final class Ambience extends Module {
    private static Ambience instance;
    private final ModeSetting mode = register(new ModeSetting("Mode", "World time mode.", true, "Day", "Day", "Midday", "Night", "Midnight", "Custom"));
    private final NumberSetting customTime = register(new NumberSetting("Time", "Custom day time.", 1000, 0, 24000, 100));
    private final ModeSetting weather = register(new ModeSetting("Weather", "Client weather override.", true, "Sunny", "Sunny", "Rain", "Thunder", "Snow"));
    private final BooleanSetting wet = register(new BooleanSetting("Wet", "Wet surfaces and screen-space reflections.", false));
    private final NumberSetting wetReflectionStrength = register(new NumberSetting("Wet Reflection Strength", "Wet-world reflection intensity.", 70.0, 0.0, 100.0, 5.0));
    private final NumberSetting wetness = register(new NumberSetting("Wetness", "Surface wetness.", 60.0, 0.0, 100.0, 5.0));
    private final ModeSetting wetQuality = register(new ModeSetting("Wet Quality", "Screen-space reflection quality.", true, "Medium", "Low", "Medium", "High"));
    private final NumberSetting saturation = register(new NumberSetting("Saturation", "World saturation multiplier offset.", 0, -1, 1, .05));
    private final BooleanSetting customFog = register(new BooleanSetting("Custom Fog", "Use custom fog color and distance.", false));
    private final ColorSetting customFogColor = register(new ColorSetting("Fog Color", "Custom fog color.", 0xFFC8D6E5));
    private final NumberSetting fogDistance = register(new NumberSetting("Fog Distance", "Distance in blocks where custom fog becomes fully opaque.", 96, 2, 512, 1));
    private final NumberSetting fogDensity = register(new NumberSetting("Fog Density", "Controls how early custom fog starts before its maximum distance.", .5, 0, 1, .01));
    private final BooleanSetting skyShader = register(new BooleanSetting("Sky Shader", "Replaces the vanilla sky with a procedural shader.", false));
    private final ModeSetting skyShaderMode = register(new ModeSetting("Sky Mode", "Procedural sky style.", false, false, "Aurora", "Aurora", "Северное сияние", "Nebula", "Cosmic Veil", "Deep Space", "Void", "Plasma"));
    private final NumberSetting skySpeed = register(new NumberSetting("Sky Speed", "Sky shader animation speed.", 1, .01, 6, .01));
    private final NumberSetting plasmaScale = register(new NumberSetting("Plasma Scale", "Plasma shader scale.", 1, .2, 3, .05));
    private final NumberSetting plasmaSpeed = register(new NumberSetting("Plasma Speed", "Plasma shader animation speed.", 1, 0, 3, .05));
    private final BooleanSetting showStars = register(new BooleanSetting("Stars", "Shows stars inside supported sky shaders.", true));
    private final ColorSetting skyShaderColor = register(new ColorSetting("Shader Color", "Custom sky shader color.", 0xFF6C63D2));
    private boolean weatherSnapshot;
    private boolean oldRaining;
    private float oldRain, oldThunder;
    private ClientWorld weatherSnapshotWorld;

    public Ambience() {
        super("Ambience", ModuleCategory.RENDER);
        instance = this;
        wetReflectionStrength.visibleWhen(wet::getValue);
        wetness.visibleWhen(wet::getValue);
        wetQuality.visibleWhen(wet::getValue);
        customTime.visibleWhen(() -> mode.is("Custom"));
        customFogColor.visibleWhen(customFog::getValue);
        fogDistance.visibleWhen(customFog::getValue);
        fogDensity.visibleWhen(customFog::getValue);
        skyShaderMode.visibleWhen(skyShader::getValue);
        skySpeed.visibleWhen(skyShader::getValue);
        plasmaScale.visibleWhen(() -> skyShader.getValue() && skyShaderMode.is("Plasma"));
        plasmaSpeed.visibleWhen(() -> skyShader.getValue() && skyShaderMode.is("Plasma"));
        showStars.visibleWhen(skyShader::getValue);
        skyShaderColor.visibleWhen(skyShader::getValue);
    }

    public static Ambience getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        clearWeatherSnapshot();
        AmbiencePostRenderer.resetTime();
        universalmod.render.wetworld.WetWorldRenderer.resetTime();
    }

    @Override
    protected void onDisable() {
        restoreWeather(MinecraftClient.getInstance());
        AmbiencePostRenderer.clear();
        universalmod.render.wetworld.WetWorldRenderer.resetTime();
        SkyShaderRenderer.close();
    }

     
    public void syncWeather(ClientWorld world, ClientWorld.Properties properties) {
        if (world == null || properties == null) {
            clearWeatherSnapshot();
            return;
        }
        if (weatherSnapshotWorld != world) clearWeatherSnapshot();
        if (!weatherSnapshot) {
            oldRaining = properties.isRaining();
            oldRain = world.getRainGradient(1f);
            oldThunder = world.getThunderGradient(1f);
            weatherSnapshot = true;
            weatherSnapshotWorld = world;
        }
        boolean raining = weather.is("Rain") || weather.is("Thunder") || weather.is("Snow");
        properties.setRaining(raining);
        world.setRainGradient(raining ? 1f : 0f);
        world.setThunderGradient(weather.is("Thunder") ? 1f : 0f);
    }

    private void restoreWeather(MinecraftClient client) {
        if (weatherSnapshot && weatherSnapshotWorld != null && client != null && client.world == weatherSnapshotWorld) {
            weatherSnapshotWorld.getLevelProperties().setRaining(oldRaining);
            weatherSnapshotWorld.setRainGradient(oldRain);
            weatherSnapshotWorld.setThunderGradient(oldThunder);
        }
        clearWeatherSnapshot();
    }

    private void clearWeatherSnapshot() {
        weatherSnapshot = false;
        weatherSnapshotWorld = null;
    }

    public boolean isWetActive() { return isEnabled() && wet.getValue(); }
    public float wetReflectionStrength() { return wetReflectionStrength.getFloat() / 100.0f; }
    public float wetness() { return wetness.getFloat() / 100.0f; }
    public float wetQualitySteps() {
        if (wetQuality.is("Low")) return 14.0f;
        if (wetQuality.is("High")) return 40.0f;
        return 24.0f;
    }

    public long getInternalTime() {
        if (mode.is("Midday")) return 6000;
        if (mode.is("Night")) return 13000;
        if (mode.is("Midnight")) return 18000;
        if (mode.is("Custom")) return (long) customTime.getValue();
        return 1000;
    }

    public String getWeather() {
        return weather.getValue();
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

    public float getSaturationFactor() {
        return Math.max(0f, Math.min(2f, 1f + saturation.getFloat()));
    }

    public boolean hasCustomFog() {
        return isEnabled() && customFog.getValue();
    }

    public boolean shouldApplyCustomFog(Camera camera) {
        if (!hasCustomFog() || camera == null || camera.getSubmersionType() != CameraSubmersionType.NONE) return false;
        if (camera.getFocusedEntity() instanceof LivingEntity living && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS)))
            return false;
        return true;
    }

    public int getCustomFogColor() {
        return customFogColor.getValue();
    }

    public float getFogDistance() {
        return Math.max(2f, Math.min(512f, fogDistance.getFloat()));
    }

    public float getFogDensity() {
        return Math.max(0f, Math.min(1f, fogDensity.getFloat()));
    }

    public float getFogStart() {
        return getFogDistance() * (1f - getFogDensity());
    }

    public boolean isSkyShaderEnabled() {
        return isEnabled() && skyShader.getValue();
    }

    public String getSkyShaderMode() {
        return skyShaderMode.getValue();
    }

    public float getSkySpeed() {
        return skySpeed.getFloat();
    }

    public float getSkyPlasmaScale() {
        return plasmaScale.getFloat();
    }

    public float getSkyPlasmaSpeed() {
        return plasmaSpeed.getFloat();
    }

    public boolean isSkyStarsEnabled() {
        return showStars.getValue();
    }

    public float getSkyStarDensity() {
        return .7f;
    }

    public float getSkyNebulaStrength() {
        return .8f;
    }

    public int getSkyShaderColor() {
        return skyShaderColor.getValue();
    }

    public SkyShaderState skyShaderState() {
        return new SkyShaderState(isSkyShaderEnabled(), getSkyShaderMode(), getSkySpeed(), getSkyPlasmaScale(), getSkyPlasmaSpeed(), isSkyStarsEnabled(), getSkyStarDensity(), getSkyNebulaStrength(), getSkyShaderColor());
    }
}
