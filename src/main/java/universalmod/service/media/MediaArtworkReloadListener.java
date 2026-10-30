package universalmod.service.media;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.util.Identifier;
import net.minecraft.resource.ResourceManager;
import universalmod.lifecycle.Manager;

public final class MediaArtworkReloadListener implements SimpleSynchronousResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return Identifier.of("universalmod", "music_artwork");
    }

    @Override
    public void reload(ResourceManager resources) {
        Manager manager = Manager.getInstance();
        if (manager != null && manager.getMusicTracker() != null) {
            manager.getMusicTracker().onResourceReload();
        }
    }
}
