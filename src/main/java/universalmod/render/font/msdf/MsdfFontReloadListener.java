package universalmod.render.font.msdf;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.util.Identifier;
import net.minecraft.resource.ResourceManager;

public final class MsdfFontReloadListener implements SimpleSynchronousResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return Identifier.of("universalmod", "msdf_fonts");
    }

    @Override
    public void reload(ResourceManager resources) {
        MsdfIconRenderer.getInstance().close();
    }
}
