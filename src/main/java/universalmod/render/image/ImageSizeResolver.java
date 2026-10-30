package universalmod.render.image;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

 
final class ImageSizeResolver {
    private static final Map<Identifier, Dimensions> PNG_DIMENSIONS = new HashMap<>();

    record Dimensions(int width, int height) {
        float drawWidth(float size) {
            return width >= height ? size : size * (width / (float) height);
        }

        float drawHeight(float size) {
            return height >= width ? size : size * (height / (float) width);
        }
    }

    private ImageSizeResolver() {
    }

    static Dimensions dimensions(Identifier id, AbstractTexture texture) {
        if (texture instanceof NativeImageBackedTexture nativeTexture && nativeTexture.getImage() != null) {
            return new Dimensions(Math.max(1, nativeTexture.getImage().getWidth()),
                    Math.max(1, nativeTexture.getImage().getHeight()));
        }
        return PNG_DIMENSIONS.computeIfAbsent(id, ImageSizeResolver::readPngSize);
    }

    static void clearCache() {
        PNG_DIMENSIONS.clear();
    }

    private static Dimensions readPngSize(Identifier id) {
        try {
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().getInputStream()) {
                    byte[] header = stream.readNBytes(24);
                    if (header.length == 24 && (header[0] & 255) == 137 && header[1] == 80
                            && header[2] == 78 && header[3] == 71 && header[12] == 'I'
                            && header[13] == 'H' && header[14] == 'D' && header[15] == 'R') {
                        int width = ((header[16] & 255) << 24) | ((header[17] & 255) << 16)
                                | ((header[18] & 255) << 8) | (header[19] & 255);
                        int height = ((header[20] & 255) << 24) | ((header[21] & 255) << 16)
                                | ((header[22] & 255) << 8) | (header[23] & 255);
                        if (width > 0 && height > 0) return new Dimensions(width, height);
                    }
                }
            }
        } catch (Exception ignored) {
            
        }
        return new Dimensions(1, 1);
    }
}
