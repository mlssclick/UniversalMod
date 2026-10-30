package universalmod.render.world.ambience;

public record SkyShaderState(boolean enabled, String mode, float speed, float plasmaScale, float plasmaSpeed,
                             boolean starsEnabled, float starDensity, float nebulaStrength, int color) {
}
