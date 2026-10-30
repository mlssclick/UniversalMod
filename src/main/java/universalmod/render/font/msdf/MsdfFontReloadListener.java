package universalmod.render.font.msdf;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

 

public final class MsdfFontReloadListener implements SimpleSynchronousResourceReloadListener {
    private static final Identifier ID = new Identifier("universalmod", "msdf_fonts");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager resources) {
        Fonts.invalidate();
    }
}
