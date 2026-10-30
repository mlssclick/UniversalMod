package universalmod.render.world;

import java.util.ArrayList;
import java.util.List;

public final class WorldLyricsSpatialLayout {
    private WorldLyricsSpatialLayout() {}

    public record Spot(float x, float y, float scale) {}

    public static List<Spot> arrange(float[] widths, float distance, float requestedSize, float spread) {
        if (widths == null || widths.length == 0) return List.of();
        float maxWidth = Math.max(1.10F, distance * 0.77F * Math.max(0.65F, spread));
        float scale = 0.012F * Math.max(0.50F, requestedSize);
        float spacing = 0.18F * Math.max(0.65F, spread);
        List<List<Integer>> rows = List.of();

        for (int tries = 0; tries < 18; tries++) {
            rows = pack(widths, scale, spacing, maxWidth);
            if (rows.size() <= 4 && tallest(widths, scale) < maxWidth * 0.87F) break;
            scale *= 0.88F;
            spacing *= 0.96F;
        }
        int count = rows.size();
        float step = Math.min(0.66F, distance * 0.37F / Math.max(1, count - 1));
        float maxX = maxWidth * 0.5F;
        Spot[] spots = new Spot[widths.length];
        for (int row = 0; row < count; row++) {
            List<Integer> indices = rows.get(row);
            float rowWidth = 0.0F;
            for (int i : indices) rowWidth += widths[i] * scale;
            rowWidth += Math.max(0, indices.size() - 1) * spacing;
            float x = -rowWidth * 0.5F;
            float rowY = (count - 1) * step * 0.5F - row * step;
            for (int i : indices) {
                float width = widths[i] * scale;
                float jitter = (hashUnit(i * 17 + row * 67) - 0.5F) * 0.09F;
                float rise = (hashUnit(i * 79 + row * 11) - 0.5F) * Math.min(0.16F, step * 0.24F);

                float middle = Math.max(-maxX + width * 0.5F,
                        Math.min(maxX - width * 0.5F, x + width * 0.5F + jitter));
                spots[i] = new Spot(middle, rowY + rise, scale);
                x += width + spacing;
            }
        }
        return List.of(spots);
    }

    private static float tallest(float[] widths, float scale) {
        float max = 0.0F;
        for (float width : widths) max = Math.max(max, width * scale);
        return max;
    }

    private static List<List<Integer>> pack(float[] widths, float scale, float spacing, float maxWidth) {
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        float occupied = 0.0F;
        for (int i = 0; i < widths.length; i++) {
            float wordWidth = Math.max(0.0F, widths[i]) * scale;
            float needed = wordWidth + (current.isEmpty() ? 0.0F : spacing);
            if (!current.isEmpty() && occupied + needed > maxWidth) {
                rows.add(current);
                current = new ArrayList<>();
                occupied = 0.0F;
                needed = wordWidth;
            }
            current.add(i);
            occupied += needed;
        }
        if (!current.isEmpty()) rows.add(current);
        return rows;
    }

    private static float hashUnit(int value) {
        int n = value ^ 0x52AD9E41;
        n ^= n >>> 16;
        n *= 0x7feb352d;
        n ^= n >>> 15;
        n *= 0x846ca68b;
        n ^= n >>> 16;
        return (n & 0x00FFFFFF) / 16777215.0F;
    }
}
