package universalmod.render.font.msdf;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class MsdfFontReloadListener implements SimpleSynchronousResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath("universalmod", "msdf_fonts");
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        try {
            MsdfIconRenderer.applyResourceReload(manager);
        } catch (RuntimeException invalidPack) {
            MsdfIconRenderer.getInstance().invalidateTextureBindings();
        }
    }
}
