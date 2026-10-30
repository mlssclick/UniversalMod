package universalmod.render.font.msdf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import universalmod.render.font.FontType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class MsdfAtlasLoader {
    private MsdfAtlasLoader() {
    }

    static MsdfAtlas load(FontType font, ResourceManager resources) {
        Identifier textureId = id("ui/msdf/" + font.path() + ".png");
        Identifier jsonId = id("ui/msdf/" + font.path() + ".json");
        Resource resource = resources.getResource(jsonId)
                .orElseThrow(() -> new IllegalStateException("MSDF metadata missing: " + jsonId));
        Map<Integer, MsdfGlyph> glyphs = new HashMap<>();
        MsdfGlyph[] asciiGlyphs = new MsdfGlyph[128];
        Map<Long, Float> kernings = new HashMap<>();
        try (InputStream stream = resource.open();
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject atlas = root.getAsJsonObject("atlas");
            if (atlas == null || root.getAsJsonObject("metrics") == null) {
                throw new IllegalStateException("Invalid MSDF atlas/metrics: " + jsonId);
            }
            float atlasWidth = requiredFloat(atlas, "width", jsonId);
            float atlasHeight = requiredFloat(atlas, "height", jsonId);
            float fontSize = requiredFloat(atlas, "size", jsonId);
            float distanceRange = requiredFloat(atlas, "distanceRange", jsonId);
            boolean originBottom = atlas.has("yOrigin") && "bottom".equalsIgnoreCase(atlas.get("yOrigin").getAsString());
            JsonObject metrics = root.getAsJsonObject("metrics");
            float lineHeight = requiredFloat(metrics, "lineHeight", jsonId);
            float baselineHeight = lineHeight + requiredFloat(metrics, "descender", jsonId);
            if (!Float.isFinite(atlasWidth) || atlasWidth <= 0.0f
                    || !Float.isFinite(atlasHeight) || atlasHeight <= 0.0f
                    || !Float.isFinite(fontSize) || fontSize <= 0.0f
                    || !Float.isFinite(distanceRange) || distanceRange <= 0.0f
                    || !Float.isFinite(lineHeight) || lineHeight <= 0.0f
                    || !Float.isFinite(baselineHeight)) {
                throw new IllegalStateException("Invalid MSDF dimensions: " + jsonId);
            }
            if (!root.has("glyphs") || !root.get("glyphs").isJsonArray()) {
                throw new IllegalStateException("MSDF glyph array missing: " + jsonId);
            }
            validateTexture(resources, textureId, atlasWidth, atlasHeight);
            readGlyphs(root, glyphs, asciiGlyphs, fontSize, baselineHeight, atlasWidth, atlasHeight, originBottom);
            readKernings(root, kernings, fontSize);
            if (glyphs.isEmpty()) {
                throw new IllegalStateException("MSDF contains no glyphs: " + jsonId);
            }
            return new MsdfAtlas(font.path(), glyphs, asciiGlyphs, kernings, textureId,
                    fontSize, distanceRange, baselineHeight, lineHeight);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load MSDF atlas " + jsonId, failure);
        }
    }

    private static void validateTexture(ResourceManager resources, Identifier textureId,
                                        float atlasWidth, float atlasHeight) throws IOException {
        Resource image = resources.getResource(textureId)
                .orElseThrow(() -> new IllegalStateException("MSDF texture missing: " + textureId));
        byte[] header;
        try (InputStream stream = image.open()) {
            header = stream.readNBytes(24);
        }
        if (header.length != 24
                || (header[0] & 0xFF) != 0x89
                || header[1] != 'P' || header[2] != 'N' || header[3] != 'G'
                || (header[4] & 0xFF) != 13 || header[5] != 10 || header[6] != 26 || header[7] != 10
                || ByteBuffer.wrap(header).getInt(8) != 13
                || header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') {
            throw new IllegalStateException("MSDF texture has invalid PNG header: " + textureId);
        }
        int width = ByteBuffer.wrap(header).getInt(16);
        int height = ByteBuffer.wrap(header).getInt(20);
        if (width <= 0 || height <= 0 || width != atlasWidth || height != atlasHeight) {
            throw new IllegalStateException("MSDF texture dimensions mismatch: " + textureId);
        }
    }

    private static float requiredFloat(JsonObject object, String key, Identifier file) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalStateException("MSDF field " + key + " missing: " + file);
        }
        return object.get(key).getAsFloat();
    }

    private static void readGlyphs(
            JsonObject root,
            Map<Integer, MsdfGlyph> glyphs,
            MsdfGlyph[] asciiGlyphs,
            float fontSize,
            float baselineHeight,
            float atlasWidth,
            float atlasHeight,
            boolean originBottom
    ) {
        if (!root.has("glyphs") || !root.get("glyphs").isJsonArray()) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray("glyphs")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject glyph = element.getAsJsonObject();
            if (!glyph.has("unicode")) {
                continue;
            }
            int unicode = glyph.get("unicode").getAsInt();
            MsdfGlyph parsed = parseGlyph(glyph, fontSize, baselineHeight, atlasWidth, atlasHeight, originBottom);
            glyphs.put(unicode, parsed);
            if (unicode >= 0 && unicode < asciiGlyphs.length) {
                asciiGlyphs[unicode] = parsed;
            }
        }
    }

    private static MsdfGlyph parseGlyph(
            JsonObject glyph,
            float fontSize,
            float baselineHeight,
            float atlasWidth,
            float atlasHeight,
            boolean originBottom
    ) {
        if (!glyph.has("atlasBounds") || !glyph.has("planeBounds")) {
            return new MsdfGlyph(0.0f, 0.0f, 0.0f, 0.0f,
                    getFloat(glyph, "advance", 1.0f) * fontSize, 0.0f, 0.0f, 0.0f, 0.0f);
        }
        JsonObject atlasBounds = glyph.getAsJsonObject("atlasBounds");
        JsonObject planeBounds = glyph.getAsJsonObject("planeBounds");
        float left = getFloat(atlasBounds, "left", 0.0f);
        float bottom = getFloat(atlasBounds, "bottom", 0.0f);
        float right = getFloat(atlasBounds, "right", 0.0f);
        float top = getFloat(atlasBounds, "top", 0.0f);
        float textureY = originBottom ? atlasHeight - top : bottom;
        float planeLeft = getFloat(planeBounds, "left", 0.0f);
        float planeBottom = getFloat(planeBounds, "bottom", 0.0f);
        float planeRight = getFloat(planeBounds, "right", 0.0f);
        float planeTop = getFloat(planeBounds, "top", 0.0f);
        return new MsdfGlyph(
                planeLeft * fontSize,
                (baselineHeight - planeTop) * fontSize,
                (planeRight - planeLeft) * fontSize,
                (planeTop - planeBottom) * fontSize,
                getFloat(glyph, "advance", 1.0f) * fontSize,
                left / atlasWidth,
                textureY / atlasHeight,
                right / atlasWidth,
                (textureY + top - bottom) / atlasHeight
        );
    }

    private static void readKernings(JsonObject root, Map<Long, Float> kernings, float fontSize) {
        if (!root.has("kerning") || !root.get("kerning").isJsonArray()) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray("kerning")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject pair = element.getAsJsonObject();
            if (!pair.has("unicode1") || !pair.has("unicode2")) {
                continue;
            }
            int left = pair.get("unicode1").getAsInt();
            int right = pair.get("unicode2").getAsInt();
            kernings.put(kerningKey(left, right), getFloat(pair, "advance", 0.0f) * fontSize);
        }
    }

    private static float getFloat(JsonObject object, String key, float fallback) {
        return object != null && object.has(key) ? object.get(key).getAsFloat() : fallback;
    }

    private static long kerningKey(int left, int right) {
        return ((long) left << 32) ^ (right & 0xFFFFFFFFL);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }
}
