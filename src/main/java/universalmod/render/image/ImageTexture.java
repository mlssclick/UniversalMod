package universalmod.render.image;

import net.minecraft.util.Identifier;

public record ImageTexture(Identifier id, boolean nearestFilter) {
    public universalmod.render.gpu.TextureSetup setup() {
        return universalmod.render.gpu.TextureSetup.single(
                () -> net.minecraft.client.MinecraftClient.getInstance().getTextureManager().getTexture(id).getGlId(), nearestFilter);
    }
}
