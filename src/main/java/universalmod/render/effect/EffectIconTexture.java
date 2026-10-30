package universalmod.render.effect;

import net.minecraft.client.texture.Sprite;

public record EffectIconTexture(Sprite sprite) {
    public universalmod.render.gpu.TextureSetup setup() {
        return universalmod.render.gpu.TextureSetup.single(
                () -> net.minecraft.client.MinecraftClient.getInstance().getTextureManager().getTexture(sprite.getAtlasId()).getGlId(), false);
    }
}
