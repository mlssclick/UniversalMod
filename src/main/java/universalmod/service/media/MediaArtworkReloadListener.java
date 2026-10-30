package universalmod.service.media;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import universalmod.lifecycle.Manager;

public final class MediaArtworkReloadListener implements SimpleSynchronousResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath("universalmod", "music_artwork");
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        Manager manager = Manager.getInstance();
        if (manager != null && manager.getMusicTracker() != null) {
            manager.getMusicTracker().onResourceReload();
        }
    }
}
