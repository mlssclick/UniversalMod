package universalmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import universalmod.render.font.msdf.MsdfFontReloadListener;
import universalmod.service.media.MediaArtworkReloadListener;
import universalmod.lifecycle.Manager;

public final class Engine implements ClientModInitializer {
    private final Manager manager = new Manager();

    @Override
    public void onInitializeClient() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new MsdfFontReloadListener());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new MediaArtworkReloadListener());
        manager.initClient();
    }

    public static void shutdown() {
        Manager activeManager = Manager.getInstance();
        if (activeManager == null) {
            return;
        }
        activeManager.close();
    }
}
