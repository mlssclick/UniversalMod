package universalmod.module.render;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.CameraSubmersionType;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.render.winter.WinterWorldRenderer;
import universalmod.render.atmosphere.AtmosphereMoodRenderer;
import universalmod.render.atmosphere.AtmosphereRainRenderer;
import universalmod.render.atmosphere.SakuraWorldRenderer;
import universalmod.render.atmosphere.WeatherWorldRenderer;
import universalmod.render.world.ambience.SkyShaderRenderer;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;
import java.awt.Color;

public final class Atmosphere extends Module {
    private static Atmosphere instance;
    private String lastSeason = "";
    private final ModeSetting mode = register(new ModeSetting("Mode", "Winter", "Winter", "Sakura", "Thunder", "Autumn", "Sunday", "BloodMoon"));

    private final NumberSetting snowIntensity = register(new NumberSetting("Интенсивность снега", "Плотность снежного покрытия на верхних гранях блоков.", 100.0, 0.0, 200.0, 1.0));
    private final BooleanSetting snowflakes = register(new BooleanSetting("Снежинки", "Depth-aware world-space snowfall.", true));
    private final NumberSetting snowflakeSpeed = register(new NumberSetting("Скорость снега", "Скорость падения снежинок.", 1.0, 0.1, 3.0, 0.05));
    private final NumberSetting snowAmount = register(new NumberSetting("Количество снега", "Количество снежинок вокруг камеры.", 100.0, 0.0, 400.0, 5.0));
    private final NumberSetting winterGodRays = register(new NumberSetting("Зимние лучи", "Глубинные лучи солнца из shader_59 с перекрытием блоками.", 75.0, 0.0, 100.0, 1.0));
    private final NumberSetting fogDistance = register(new NumberSetting("Длина тумана", "Дальность зимнего кастомного тумана.", 72.0, 12.0, 256.0, 1.0));

    private final NumberSetting sakuraPetals = register(new NumberSetting("Sakura Petals", "Количество падающих лепестков (запас осевших отдельно ×4).", 170.0, 25.0, 640.0, 10.0));
    private final NumberSetting sakuraFallSpeed = register(new NumberSetting("Sakura Fall Speed", "Скорость мягкого падения лепестков.", 1.0, 0.3, 2.8, 0.05));
    private final NumberSetting sakuraWind = register(new NumberSetting("Sakura Wind", "Плавный ветер и редкие порывы.", 1.0, 0.0, 2.5, 0.05));
    private final NumberSetting sakuraHaze = register(new NumberSetting("Sakura Haze", "Сила розового атмосферного рассеивания.", 52.0, 0.0, 100.0, 1.0));
    private final NumberSetting sakuraSkySpeed = register(new NumberSetting("Sakura Sky Speed", "Скорость движения облаков и слоёв неба.", 1.0, 0.1, 3.0, 0.05));
    private final NumberSetting sakuraBloom = register(new NumberSetting("Sakura Bloom", "Мягкое сияние утреннего неба.", 45.0, 0.0, 100.0, 1.0));

    private final NumberSetting autumnLeaves = register(new NumberSetting("Autumn Leaves", "Количество осенних листьев в мире.", 640.0, 160.0, 1600.0, 10.0));
    private final NumberSetting autumnWind = register(new NumberSetting("Autumn Wind", "Порывы ветра с опадающими листьями.", 1.15, 0.0, 2.8, 0.05));
    private final BooleanSetting autumnRain = register(new BooleanSetting("Autumn Rain", "Мелкий осенний дождь с рябью на земле.", false));
    private final NumberSetting thunderRain = register(new NumberSetting("Thunder Rain", "Плотность трёхмерных падающих капель и брызг.", 75.0, 0.0, 100.0, 1.0));
    private final NumberSetting rainPuddles = register(new NumberSetting("Rain Puddles", "Procedural puddle coverage during rainfall.", 70.0, 0.0, 100.0, 1.0));
    private final NumberSetting rainRipples = register(new NumberSetting("Rain Ripples", "Raindrop wave strength on wet surfaces.", 100.0, 0.0, 200.0, 1.0));
    private final NumberSetting thunderVolume = register(new NumberSetting("Thunder Volume", "Громкость пространственных раскатов грома.", 70.0, 0.0, 100.0, 1.0));
    private final BooleanSetting sundayGodRays = register(new BooleanSetting("Sunday God Rays", "Объемные солнечные лучи, перекрываемые окружением.", true));
    private final NumberSetting sundayRayStrength = register(new NumberSetting("Sunday Ray Strength", "Интенсивность солнечных лучей.", 75.0, 0.0, 100.0, 1.0));
    private final NumberSetting bloodMoonHaze = register(new NumberSetting("BloodMoon Haze", "Серый туман с багровой лунной подсветкой.", 48.0, 0.0, 100.0, 1.0));

    public Atmosphere() {
        super("atmosphere", "Atmosphere", ModuleCategory.RENDER);
        instance = this;
        mode.setDescription("Seasonal world atmosphere.");

        snowIntensity.visibleWhen(this::isWinterMode);
        snowflakes.visibleWhen(this::isWinterMode);
        snowflakeSpeed.visibleWhen(() -> isWinterMode() && snowflakes.getValue());
        snowAmount.visibleWhen(() -> isWinterMode() && snowflakes.getValue());
        winterGodRays.visibleWhen(this::isWinterMode);
        fogDistance.visibleWhen(this::isWinterMode);
        sakuraPetals.visibleWhen(this::isSakuraMode);
        sakuraFallSpeed.visibleWhen(this::isSakuraMode);
        sakuraWind.visibleWhen(this::isSakuraMode);
        sakuraHaze.visibleWhen(this::isSakuraMode);
        sakuraBloom.visibleWhen(this::isSakuraMode);
        sakuraSkySpeed.visibleWhen(this::isSakuraMode);
        autumnLeaves.visibleWhen(this::isAutumnMode);
        autumnWind.visibleWhen(this::isAutumnMode);
        autumnRain.visibleWhen(this::isAutumnMode);
        thunderRain.visibleWhen(this::isThunderMode);
        rainPuddles.visibleWhen(() -> isThunderMode() || (isAutumnMode() && autumnRain.getValue()));
        rainRipples.visibleWhen(() -> isThunderMode() || (isAutumnMode() && autumnRain.getValue()));
        thunderVolume.visibleWhen(this::isThunderMode);
        sundayGodRays.visibleWhen(this::isSundayMode);
        sundayRayStrength.visibleWhen(() -> isSundayMode() && sundayGodRays.getValue());
        bloodMoonHaze.visibleWhen(this::isBloodMoonMode);
    }

    public static Atmosphere getInstance() { return instance; }
    public boolean isWinterMode() { return mode.is("Winter"); }
    public boolean isSakuraMode() { return mode.is("Sakura"); }
    public boolean isThunderMode() { return mode.is("Thunder"); }
    public boolean isAutumnMode() { return mode.is("Autumn"); }
    public boolean isSundayMode() { return mode.is("Sunday"); }
    public boolean isBloodMoonMode() { return mode.is("BloodMoon"); }
    public boolean isThunderActive() { return isEnabled() && isThunderMode(); }
    public boolean isAutumnActive() { return isEnabled() && isAutumnMode(); }
    public boolean isSundayActive() { return isEnabled() && isSundayMode(); }
    public boolean isBloodMoonActive() { return isEnabled() && isBloodMoonMode(); }
    public boolean isRainActive() { return isThunderActive() || (isAutumnActive() && autumnRain.getValue()); }
    public float getRainPuddles() { return rainPuddles.getFloat() / 100f; }
    public float getRainRipples() { return rainRipples.getFloat() / 100f; }
    public float getRainAmount() { return isThunderMode() ? thunderRain.getFloat()/100f : autumnRain.getValue() ? .48f : 0f; }
    public float getThunderVolume() { return thunderVolume.getFloat()/100f; }
    public boolean isWinterActive() { return isEnabled() && isWinterMode(); }
    public boolean isSakuraActive() { return isEnabled() && isSakuraMode(); }
    public boolean isMoodActive() { return isEnabled() && !isWinterMode(); }
    public int getMoodKind() { return isSakuraMode()?5:isThunderMode()?6:isAutumnMode()?7:isSundayMode()?8:9; }
    public float getMoodScatterHeight() { return 76.0f; }
    public float getMoodParticles() { return 0.0f; }
    public float getMoodWindSpeed() { return isAutumnMode() ? autumnWind.getFloat() : sakuraWind.getFloat(); }
    public float getMoodHaze() {
        return isSakuraMode() ? sakuraHaze.getFloat()/100f : isThunderMode() ? .76f :
               isAutumnMode()? .56f : isSundayMode()? .17f : bloodMoonHaze.getFloat()/100f;
    }
    public float getMoodDensity() { return 1.0f; }
    public float getMoodGrade() { return isSundayMode()?0.19f:isSakuraMode()?0.36f:0.32f; }
    public float getMoodVignette() { return isThunderMode()?0.13f:isBloodMoonMode()?0.11f:0.045f; }
    public float getMoodGodRays() { return isSundayMode() && sundayGodRays.getValue() ? sundayRayStrength.getFloat()/100f : isSakuraMode()?sakuraBloom.getFloat()/100f:0f; }
    public Color getMoodSunColor() {
        return isSundayMode()?new Color(255,228,165):isThunderMode()?new Color(125,149,183):
               isBloodMoonMode()?new Color(211,57,65):isAutumnMode()?new Color(232,175,104):new Color(255,206,171);
    }
    public float getSakuraSkySpeed() { return sakuraSkySpeed.getFloat(); }
    public float getSakuraBloom() { return sakuraBloom.getFloat() / 100.0f; }
    public int getSakuraPetals() { return Math.round(isAutumnMode()?autumnLeaves.getFloat():sakuraPetals.getFloat()); }
    public float getSakuraFallSpeed() { return isAutumnMode()?1.1f:sakuraFallSpeed.getFloat(); }
    public float getSakuraWind() { return isAutumnMode()?autumnWind.getFloat():sakuraWind.getFloat(); }

    public float getWinterGodRays() { return winterGodRays.getFloat() / 100.0f; }
    public float getSnowIntensity() { return snowIntensity.getFloat() / 100.0f; }
    public boolean isSnowflakesEnabled() { return snowflakes.getValue(); }
    public float getSnowflakeSpeed() { return snowflakeSpeed.getFloat(); }
    public float getSnowAmount() { return snowAmount.getFloat() / 100.0f; }
    public float getFogDistance() { return fogDistance.getFloat(); }
    public float getFogStart() { return getFogDistance() * 0.50f; }
    public int getFogColor() { return (200 << 16) | (214 << 8) | 229; }
    public boolean shouldApplyWinterFog(Camera camera) {
        if (!isWinterActive() || camera == null || camera.getSubmersionType() != CameraSubmersionType.NONE) return false;
        if (camera.getFocusedEntity() instanceof LivingEntity living &&
            (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS))) return false;
        return true;
    }

    @Override
    public void onEnable() {
        lastSeason = mode.getValue();
        WinterWorldRenderer.resetTime();
        AtmosphereMoodRenderer.resetTime();
        SkyShaderRenderer.resetTime();
        SakuraWorldRenderer.reset();
        WeatherWorldRenderer.reset();
    }

    @Override
    public void onTick(net.minecraft.client.MinecraftClient client) {
        String current = mode.getValue();
        if (!current.equals(lastSeason)) {

            if ("Winter".equals(current)) {
                WinterWorldRenderer.resetTime();
            } else if ("Winter".equals(lastSeason)) {
                WinterWorldRenderer.reset();
            }
            AtmosphereMoodRenderer.resetTime();
            SkyShaderRenderer.resetTime();
            SakuraWorldRenderer.reset();
            WeatherWorldRenderer.reset();
            lastSeason = current;
        }
        if (client != null && client.world != null) {
            WeatherWorldRenderer.tick(this, client.world,
                    client.player == null ? net.minecraft.util.math.Vec3d.ZERO : client.player.getPos());
        }
    }

    @Override
    public void onDisable() {
        WinterWorldRenderer.reset();
        AtmosphereMoodRenderer.resetTime();
        SkyShaderRenderer.resetTime();
        SakuraWorldRenderer.reset();
        WeatherWorldRenderer.reset();
    }
}
