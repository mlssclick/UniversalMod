package universalmod.service.media;

import net.minecraft.client.texture.NativeImage;
import universalmod.render.color.ColorUtil;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

final class ArtworkProcessor {
    private ArtworkProcessor() {
    }

    static PreparedArtwork prepare(byte[] imageBytes, int maxSize) throws IOException {
        return prepare(imageBytes, maxSize, false);
    }

    static PreparedArtwork prepare(byte[] imageBytes, int maxSize, boolean cropSpotify) throws IOException {
        if (imageBytes == null || imageBytes.length > 16 * 1024 * 1024) {
            throw new IOException("Artwork exceeds the maximum allowed PNG size");
        }
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (decoded == null) {
            return null;
        }
        if (decoded.getWidth() > 4096 || decoded.getHeight() > 4096) {
            throw new IOException("Artwork image dimensions exceed safety limit");
        }
        BufferedImage processed = cropSpotify ? cropSpotifyArtwork(decoded) : decoded;
        int average = averageColor(processed);
        BufferedImage uploadImage = scale(processed, maxSize);
        ArtworkPalette colors = palette(processed);
        NativeImage foreground = toNativeImage(uploadImage);
        try {
            return new PreparedArtwork(foreground, toNativeImage(blurredBackground(processed)), average, colors);
        } catch (RuntimeException | Error failure) {
            foreground.close();
            throw failure;
        }
    }

    private static BufferedImage cropSpotifyArtwork(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int sideCrop = (int) Math.floor(width * 0.11D);
        int targetWidth = width - sideCrop * 2;
        int targetHeight = height - (int) Math.floor(height * 0.22D);
        if (targetWidth <= 0 || targetHeight <= 0) {
            return source;
        }
        int safeHeight = Math.min(height, targetHeight);
        BufferedImage cropped = new BufferedImage(targetWidth, safeHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cropped.createGraphics();
        try {
            graphics.drawImage(source,
                    0, 0, targetWidth, safeHeight,
                    sideCrop, 0, sideCrop + targetWidth, safeHeight,
                    null);
        } finally {
            graphics.dispose();
        }
        return cropped;
    }

    private static BufferedImage scale(BufferedImage source, int maxSize) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxSize && height <= maxSize) {
            return source;
        }
        double scale = Math.min((double) maxSize / width, (double) maxSize / height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static NativeImage toNativeImage(BufferedImage source) {
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), true);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                image.setColorArgb(x, y, source.getRGB(x, y));
            }
        }
        return image;
    }

    private static int averageColor(BufferedImage image) {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        long alpha = 0L;
        int pixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                alpha += a;
                red += (argb >>> 16) & 0xFF;
                green += (argb >>> 8) & 0xFF;
                blue += argb & 0xFF;
                pixels++;
            }
        }
        if (pixels == 0) {
            return ColorUtil.WHITE;
        }
        return ColorUtil.rgba((int) (red / pixels), (int) (green / pixels), (int) (blue / pixels), (int) (alpha / pixels));
    }

    private static BufferedImage blurredBackground(BufferedImage original) {
        BufferedImage small = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = small.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(original, 0, 0, 96, 96, null);
        } finally { graphics.dispose(); }
        int[] a = small.getRGB(0, 0, 96, 96, null, 0, 96);
        int[] b = new int[a.length];
        final int radius = 7;

        for (int iteration = 0; iteration < 2; iteration++) {
            for (int y = 0; y < 96; y++) {
                for (int x = 0; x < 96; x++) {
                    int red = 0, green = 0, blue = 0, alpha = 0, count = 0;
                    for (int dx = -radius; dx <= radius; dx++) {
                        int p = a[y * 96 + Math.max(0, Math.min(95, x + dx))];
                        alpha += p >>> 24;
                        red += (p >>> 16) & 255;
                        green += (p >>> 8) & 255;
                        blue += p & 255;
                        count++;
                    }
                    b[y*96+x] = (alpha/count << 24) | (red/count << 16)
                            | (green/count << 8) | blue/count;
                }
            }
            for (int y = 0; y < 96; y++) {
                for (int x = 0; x < 96; x++) {
                    int red = 0, green = 0, blue = 0, alpha = 0, count = 0;
                    for (int dy = -radius; dy <= radius; dy++) {
                        int p = b[Math.max(0, Math.min(95, y + dy)) * 96 + x];
                        alpha += p >>> 24;
                        red += (p >>> 16) & 255;
                        green += (p >>> 8) & 255;
                        blue += p & 255;
                        count++;
                    }
                    a[y*96+x] = (alpha/count << 24) | (red/count << 16)
                            | (green/count << 8) | blue/count;
                }
            }
        }
        small.setRGB(0, 0, 96, 96, a, 0, 96);
        return small;
    }

    private static ArtworkPalette palette(BufferedImage source) {
        final float[][] hueBins = new float[12][5];
        final float[] average = new float[4];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int rgb = source.getRGB(x * source.getWidth() / 32, y * source.getHeight() / 32);
                int r = (rgb >>> 16) & 255;
                int g = (rgb >>> 8) & 255;
                int b = rgb & 255;
                float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
                average[0] += r;
                average[1] += g;
                average[2] += b;
                average[3]++;
                float weight = hsb[1] * hsb[1] * (.25f + hsb[2]);
                if (hsb[2] < .12f) continue;
                float[] bin = hueBins[Math.min(11, (int) (hsb[0] * 12f))];
                bin[0] += r * weight;
                bin[1] += g * weight;
                bin[2] += b * weight;
                bin[3] += weight;
                bin[4]++;
            }
        }
        java.util.List<float[]> sorted = new java.util.ArrayList<>();
        for (float[] bin : hueBins) if (bin[3] > 0.0001f) sorted.add(bin);
        sorted.sort((a, b) -> Float.compare(b[3], a[3]));
        int averageColor = (0xff << 24)
                | ((int)(average[0] / average[3]) << 16)
                | ((int)(average[1] / average[3]) << 8)
                | (int)(average[2] / average[3]);
        int[] colors = new int[3];
        for (int i = 0; i < 3; i++) {
            if (i < sorted.size()) {
                float[] bin = sorted.get(i);
                colors[i] = vividColor((0xff << 24)
                        | ((int)(bin[0] / bin[3]) << 16)
                        | ((int)(bin[1] / bin[3]) << 8)
                        | (int)(bin[2] / bin[3]));
            } else if (i == 0) {
                colors[i] = vividColor(averageColor);
            } else {
                colors[i] = lerp(colors[0], averageColor, .5f * i);
            }
        }

        int fourth = darken(lerp(colors[0], colors[2], .5f), .6f);
        return new ArtworkPalette(colors[0], colors[1], colors[2], 0xFFFFFFFF, fourth);
    }

    private static int vividColor(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >>> 16) & 255,
                (rgb >>> 8) & 255, rgb & 255, null);
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hsb[0],
                Math.min(1f, Math.max(.35f, hsb[1] * 1.15f)),
                Math.min(.95f, Math.max(.55f, hsb[2]))) & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(((a >>> 16) & 255) * (1 - t) + ((b >>> 16) & 255) * t);
        int g = Math.round(((a >>> 8) & 255) * (1 - t) + ((b >>> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static int darken(int color, float factor) {
        return 0xFF000000 | ((int)(((color >>> 16) & 255) * factor) << 16)
                | ((int)(((color >>> 8) & 255) * factor) << 8)
                | (int)((color & 255) * factor);
    }

    private static int argbToAbgr(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    record PreparedArtwork(NativeImage image, NativeImage background, int averageColor, ArtworkPalette palette) {
    }
}
