package universalmod.render.font.msdf;

import net.minecraft.resources.Identifier;

import java.util.Map;

record MsdfAtlas(
        String path,
        Map<Integer, MsdfGlyph> glyphs,
        MsdfGlyph[] asciiGlyphs,
        Map<Long, Float> kernings,
        Identifier textureId,
        float fontSize,
        float distanceRange,
        float baselineHeight,
        float lineHeight
) {
    boolean ready() {
        return !glyphs.isEmpty();
    }

}
