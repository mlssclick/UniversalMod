package universalmod.render.font.msdf;


import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import universalmod.access.GuiRenderStateLayerAccessor;
import universalmod.mixin.accessor.GuiGraphicsExtractorAccessor;
import universalmod.render.ScissorUtil;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.font.FontType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public final class MsdfIconRenderer implements AutoCloseable {
    private static volatile MsdfIconRenderer instance;
    private static final int MAX_WIDTH_CACHE = 512;
    private static final int MAX_LAYOUT_CACHE = 256;
    private static final int MAX_BOUNDS_CACHE = 512;
    private static final VertexFormat MSDF_ICON_VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("LineWidth", VertexFormatElement.LINE_WIDTH)
            .build();

    public static final RenderPipeline MSDF_ICON_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/msdf_icon"))
            .withVertexShader(id("ui/msdf_icon/msdf_icon"))
            .withFragmentShader(id("ui/msdf_icon/msdf_icon"))
            .withVertexFormat(MSDF_ICON_VERTEX_FORMAT, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final Map<String, MsdfAtlas> atlases = new HashMap<>();
    private final Map<String, TextureBinding> textureBindings = new HashMap<>();
    private final Map<FrameBatchKey, MsdfIconRenderState> frameBatches = new LinkedHashMap<>(32);
    private final Map<WidthKey, Float> widthCache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<WidthKey, Float> eldest) {
            return size() > MAX_WIDTH_CACHE;
        }
    };
    private final Map<MetricsKey, GlyphLayout> layoutCache = new LinkedHashMap<>(96, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MetricsKey, GlyphLayout> eldest) {
            return size() > MAX_LAYOUT_CACHE;
        }
    };
    private final Map<MetricsKey, VisualBounds> boundsCache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MetricsKey, VisualBounds> eldest) {
            return size() > MAX_BOUNDS_CACHE;
        }
    };
    private GuiGraphics activeGraphics;


    private String lastWidthFontName;
    private String lastWidthText;
    private float lastWidthSize;
    private float lastWidthValue;
    private boolean lastWidthValid;

    private String lastLayoutFontName;
    private String lastLayoutText;
    private int lastLayoutSizeBits;
    private GlyphLayout lastLayoutValue;

    private String lastBoundsFontName;
    private String lastBoundsText;
    private int lastBoundsSizeBits;
    private VisualBounds lastBoundsValue;

    private GuiRenderState lastBatchGuiState;
    private int lastBatchLayerSerial = Integer.MIN_VALUE;
    private float lastBatchM00;
    private float lastBatchM01;
    private float lastBatchM10;
    private float lastBatchM11;
    private float lastBatchM20;
    private float lastBatchM21;
    private ScreenRectangle lastBatchScissor;
    private TextureSetup lastBatchTextureSetup;
    private MsdfIconRenderState lastBatchState;

    private MsdfIconRenderer() {
    }

    public static MsdfIconRenderer getInstance() {
        MsdfIconRenderer local = instance;
        if (local == null) {
            synchronized (MsdfIconRenderer.class) {
                local = instance;
                if (local == null) {
                    local = new MsdfIconRenderer();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        MsdfIconRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginFrame(GuiGraphics graphics) {
        if (activeGraphics != graphics) {
            frameBatches.clear();
            clearLastBatch();
        }
        activeGraphics = graphics;
    }

    public void draw(FontType font, String text, float x, float y, float size, int color) {
        draw(font, text, x, y, size, color, color, color, color, 0.0f, x, y, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    public void draw(
            FontType font,
            String text,
            float x,
            float y,
            float size,
            int colorTopLeft,
            int colorTopRight,
            int colorBottomRight,
            int colorBottomLeft,
            float rotationDegrees,
            float rotationOriginX,
            float rotationOriginY,
            boolean fadeLeft,
            boolean fadeRight,
            float fadeLeftX,
            float fadeRightX,
            float fadeWidth,
            float fadeLeftStrength,
            float fadeRightStrength
    ) {
        if (activeGraphics == null || font == null || text == null || text.isEmpty()
                || size <= 0.0f
                || maxAlpha(colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft) <= 0) {
            return;
        }
        MsdfAtlas atlas = atlas(font);
        TextureSetup setup = textureSetup(font, atlas);
        if (setup == null) {
            return;
        }

        try {
            GuiRenderState guiState = ((GuiGraphicsExtractorAccessor) activeGraphics).universalmod$getGuiRenderState();
            int layerSerial = ((GuiRenderStateLayerAccessor) guiState).universalmod$getLayerSerial();
            Matrix3x2f pose = Render2DCoordinateSpace.pose(activeGraphics);
            ScreenRectangle scissorArea = ScissorUtil.current();
            MsdfIconRenderState state = lastBatch(
                    guiState, layerSerial, pose, scissorArea, setup
            );
            if (state == null) {
                FrameBatchKey key = new FrameBatchKey(guiState, layerSerial, PoseKey.of(pose), scissorArea, setup);
                state = frameBatches.get(key);
                if (state == null) {
                    state = new MsdfIconRenderState(pose, setup, scissorArea, atlas.distanceRange());
                    frameBatches.put(key, state);
                    guiState.submitGlyphToCurrentLayer(state);
                }
                rememberLastBatch(guiState, layerSerial, pose, scissorArea, setup, state);
            }
            float cursorX = x;
            float scale = size / atlas.fontSize();
            int previousCodePoint = -1;
            int index = 0;
            while (index < text.length()) {
                int codePoint = text.codePointAt(index);
                index += Character.charCount(codePoint);
                MsdfGlyph glyph = glyph(atlas, codePoint);
                if (glyph == null) {
                    continue;
                }

                cursorX += kerning(atlas, previousCodePoint, codePoint) * scale;

                if (glyph.drawable()) {
                    float glyphX = cursorX + glyph.xOffset() * scale;
                    float glyphY = y + glyph.yOffset() * scale;
                    float glyphWidth = glyph.width() * scale;
                    float glyphHeight = glyph.height() * scale;
                    state.add(
                            glyphX,
                            glyphY,
                            glyphWidth,
                            glyphHeight,
                            glyph,
                            normalizeColor(colorTopLeft),
                            normalizeColor(colorTopRight),
                            normalizeColor(colorBottomRight),
                            normalizeColor(colorBottomLeft),
                            rotationDegrees,
                            rotationOriginX,
                            rotationOriginY,
                            fadeLeft,
                            fadeRight,
                            fadeLeftX,
                            fadeRightX,
                            fadeWidth,
                            fadeLeftStrength,
                            fadeRightStrength
                    );
                }
                cursorX += glyph.advance() * scale;
                previousCodePoint = codePoint;
            }
        } catch (RuntimeException failure) {
            throw new IllegalStateException("MSDF glyph rendering failed: " + font.path(), failure);
        }
    }

    public boolean canRender(FontType font, String text) {
        if (font == null || text == null || text.isEmpty()) {
            return false;
        }
        MsdfAtlas atlas = atlas(font);
        if (!atlas.ready()) {
            return false;
        }
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) {
                return false;
            }
            if (glyph(atlas, codePoint) == null) {
                return false;
            }
        }
        return true;
    }

    public float width(FontType font, String text, float size) {
        if (font == null || text == null || text.isEmpty() || size <= 0.0f) {
            return 0.0f;
        }
        MsdfAtlas atlas = atlas(font);
        if (!atlas.ready()) {
            return 0.0f;
        }
        if (text.codePointCount(0, text.length()) == 1) {
            MsdfGlyph glyph = glyph(atlas, text.codePointAt(0));
            return glyph == null ? 0.0f : glyph.advance() * (size / atlas.fontSize());
        }
        String fontName = font.fontName();
        float normalizedSize = normalizeSize(size);
        if (lastWidthValid
                && lastWidthSize == normalizedSize
                && Objects.equals(lastWidthFontName, fontName)
                && Objects.equals(lastWidthText, text)) {
            return lastWidthValue;
        }
        WidthKey key = new WidthKey(fontName, text, normalizedSize);
        Float cached = widthCache.get(key);
        if (cached != null) {
            rememberLastWidth(fontName, text, normalizedSize, cached);
            return cached;
        }
        float scale = size / atlas.fontSize();
        float width = 0.0f;
        int previousCodePoint = -1;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            MsdfGlyph glyph = glyph(atlas, codePoint);
            if (glyph != null) {
                width += (kerning(atlas, previousCodePoint, codePoint) + glyph.advance()) * scale;
                previousCodePoint = codePoint;
            }
        }
        widthCache.put(key, width);
        rememberLastWidth(fontName, text, normalizedSize, width);
        return width;
    }

    public float lineHeight(FontType font, float size) {
        if (font == null || size <= 0.0f) {
            return 0.0f;
        }
        MsdfAtlas atlas = atlas(font);
        return atlas.ready() ? atlas.lineHeight() * size : 0.0f;
    }

    public VisualBounds visualBounds(FontType font, String text, float size) {
        if (font == null || text == null || text.isEmpty() || size <= 0.0f) {
            return VisualBounds.EMPTY;
        }
        String fontName = font.fontName();
        int sizeBits = Float.floatToIntBits(size);
        if (lastBoundsValue != null
                && lastBoundsSizeBits == sizeBits
                && Objects.equals(lastBoundsFontName, fontName)
                && Objects.equals(lastBoundsText, text)) {
            return lastBoundsValue;
        }
        MetricsKey key = new MetricsKey(fontName, text, sizeBits);
        VisualBounds cached = boundsCache.get(key);
        if (cached != null) {
            rememberLastBounds(fontName, text, sizeBits, cached);
            return cached;
        }
        GlyphLayout layout = glyphLayout(font, text, size);
        if (layout.empty()) {
            boundsCache.put(key, VisualBounds.EMPTY);
            rememberLastBounds(fontName, text, sizeBits, VisualBounds.EMPTY);
            return VisualBounds.EMPTY;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Glyph glyph : layout.glyphs()) {
            minX = Math.min(minX, glyph.x0());
            minY = Math.min(minY, glyph.y0());
            maxX = Math.max(maxX, glyph.x1());
            maxY = Math.max(maxY, glyph.y1());
        }
        VisualBounds result;
        if (!Float.isFinite(minX) || !Float.isFinite(minY)
                || !Float.isFinite(maxX) || !Float.isFinite(maxY)) {
            result = VisualBounds.EMPTY;
        } else {
            result = new VisualBounds(minX, minY, maxX, maxY);
        }
        boundsCache.put(key, result);
        rememberLastBounds(fontName, text, sizeBits, result);
        return result;
    }

    public GlyphLayout glyphLayout(FontType font, String text, float size) {
        if (font == null || text == null || text.isEmpty() || size <= 0.0f) {
            return GlyphLayout.EMPTY;
        }
        String fontName = font.fontName();
        int sizeBits = Float.floatToIntBits(size);
        if (lastLayoutValue != null
                && lastLayoutSizeBits == sizeBits
                && Objects.equals(lastLayoutFontName, fontName)
                && Objects.equals(lastLayoutText, text)) {
            return lastLayoutValue;
        }
        MetricsKey key = new MetricsKey(fontName, text, sizeBits);
        GlyphLayout cached = layoutCache.get(key);
        if (cached != null) {
            rememberLastLayout(fontName, text, sizeBits, cached);
            return cached;
        }
        MsdfAtlas atlas = atlas(font);
        if (!atlas.ready()) {
            return GlyphLayout.EMPTY;
        }
        float scale = size / atlas.fontSize();
        float cursorX = 0.0f;
        int previousCodePoint = -1;
        List<Glyph> glyphs = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            MsdfGlyph glyph = glyph(atlas, codePoint);
            if (glyph == null) {
                continue;
            }
            cursorX += kerning(atlas, previousCodePoint, codePoint) * scale;
            if (glyph.drawable()) {
                float x0 = cursorX + glyph.xOffset() * scale;
                float y0 = glyph.yOffset() * scale;
                glyphs.add(new Glyph(
                        x0,
                        y0,
                        x0 + glyph.width() * scale,
                        y0 + glyph.height() * scale,
                        glyph.u0(),
                        glyph.v0(),
                        glyph.u1(),
                        glyph.v1()
                ));
            }
            cursorX += glyph.advance() * scale;
            previousCodePoint = codePoint;
        }
        GlyphLayout result = new GlyphLayout(atlas.textureId(), atlas.distanceRange(), List.copyOf(glyphs), cursorX, atlas.lineHeight() * size);
        layoutCache.put(key, result);
        rememberLastLayout(fontName, text, sizeBits, result);
        return result;
    }

    public void flush() {
        activeGraphics = null;
        frameBatches.clear();
        clearLastBatch();
    }

    private MsdfIconRenderState lastBatch(
            GuiRenderState guiState,
            int layerSerial,
            Matrix3x2f pose,
            ScreenRectangle scissorArea,
            TextureSetup setup
    ) {
        if (lastBatchState == null
                || lastBatchGuiState != guiState
                || lastBatchLayerSerial != layerSerial
                || lastBatchM00 != pose.m00()
                || lastBatchM01 != pose.m01()
                || lastBatchM10 != pose.m10()
                || lastBatchM11 != pose.m11()
                || lastBatchM20 != pose.m20()
                || lastBatchM21 != pose.m21()
                || !Objects.equals(lastBatchScissor, scissorArea)
                || !Objects.equals(lastBatchTextureSetup, setup)) {
            return null;
        }
        return lastBatchState;
    }

    private void rememberLastBatch(
            GuiRenderState guiState,
            int layerSerial,
            Matrix3x2f pose,
            ScreenRectangle scissorArea,
            TextureSetup setup,
            MsdfIconRenderState state
    ) {
        lastBatchGuiState = guiState;
        lastBatchLayerSerial = layerSerial;
        lastBatchM00 = pose.m00();
        lastBatchM01 = pose.m01();
        lastBatchM10 = pose.m10();
        lastBatchM11 = pose.m11();
        lastBatchM20 = pose.m20();
        lastBatchM21 = pose.m21();
        lastBatchScissor = scissorArea;
        lastBatchTextureSetup = setup;
        lastBatchState = state;
    }

    private void clearLastBatch() {
        lastBatchGuiState = null;
        lastBatchLayerSerial = Integer.MIN_VALUE;
        lastBatchScissor = null;
        lastBatchTextureSetup = null;
        lastBatchState = null;
    }

    private void rememberLastWidth(String fontName, String text, float size, float value) {
        lastWidthFontName = fontName;
        lastWidthText = text;
        lastWidthSize = size;
        lastWidthValue = value;
        lastWidthValid = true;
    }

    private void rememberLastLayout(String fontName, String text, int sizeBits, GlyphLayout value) {
        lastLayoutFontName = fontName;
        lastLayoutText = text;
        lastLayoutSizeBits = sizeBits;
        lastLayoutValue = value;
    }

    private void rememberLastBounds(String fontName, String text, int sizeBits, VisualBounds value) {
        lastBoundsFontName = fontName;
        lastBoundsText = text;
        lastBoundsSizeBits = sizeBits;
        lastBoundsValue = value;
    }

    public void invalidateTextureBindings() {
        textureBindings.clear();
        frameBatches.clear();
        clearLastBatch();
        activeGraphics = null;
    }

    public static void applyResourceReload(ResourceManager resources) {
        Map<String, MsdfAtlas> prepared = new HashMap<>();
        for (FontType font : FontType.values()) {
            prepared.put(font.fontName(), MsdfAtlasLoader.load(font, resources));
        }
        getInstance().replaceAtlases(prepared);
    }

    private void replaceAtlases(Map<String, MsdfAtlas> prepared) {
        frameBatches.clear();
        clearLastBatch();
        activeGraphics = null;
        textureBindings.clear();
        atlases.clear();
        atlases.putAll(prepared);
        widthCache.clear();
        layoutCache.clear();
        boundsCache.clear();
        lastWidthValid = false;
        lastWidthFontName = null;
        lastWidthText = null;
        lastLayoutValue = null;
        lastLayoutFontName = null;
        lastLayoutText = null;
        lastBoundsValue = null;
        lastBoundsFontName = null;
        lastBoundsText = null;
    }

    private MsdfAtlas atlas(FontType font) {
        MsdfAtlas cached = atlases.get(font.fontName());
        if (cached != null) {
            return cached;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            throw new IllegalStateException("MSDF resources are not initialized");
        }
        MsdfAtlas loaded = MsdfAtlasLoader.load(font, minecraft.getResourceManager());
        atlases.put(font.fontName(), loaded);
        invalidateMetrics(font.fontName());
        return loaded;
    }

    private void invalidateMetrics(String fontName) {
        widthCache.keySet().removeIf(key -> Objects.equals(key.fontName(), fontName));
        layoutCache.keySet().removeIf(key -> Objects.equals(key.fontName(), fontName));
        boundsCache.keySet().removeIf(key -> Objects.equals(key.fontName(), fontName));

        if (Objects.equals(lastWidthFontName, fontName)) {
            lastWidthValid = false;
            lastWidthFontName = null;
            lastWidthText = null;
        }
        if (Objects.equals(lastLayoutFontName, fontName)) {
            lastLayoutFontName = null;
            lastLayoutText = null;
            lastLayoutValue = null;
        }
        if (Objects.equals(lastBoundsFontName, fontName)) {
            lastBoundsFontName = null;
            lastBoundsText = null;
            lastBoundsValue = null;
        }
    }

    private TextureSetup textureSetup(FontType font, MsdfAtlas atlas) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getTextureManager() == null) {
            return null;
        }
        AbstractTexture texture = minecraft.getTextureManager().getTexture(atlas.textureId());
        if (texture == null || texture.getTextureView() == null) {
            return null;
        }
        Object liveView = texture.getTextureView();
        TextureBinding existing = textureBindings.get(font.fontName());
        if (existing != null && existing.texture() == texture && existing.textureView() == liveView) {
            return existing.setup();
        }
        TextureSetup setup = TextureSetup.singleTexture(
                texture.getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        );
        textureBindings.put(font.fontName(), new TextureBinding(texture, liveView, setup));
        clearLastBatch();
        return setup;
    }

    private record TextureBinding(AbstractTexture texture, Object textureView, TextureSetup setup) {
    }

    private static int normalizeColor(int color) {
        return color;
    }

    private static int effectiveAlpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    private static int maxAlpha(int... colors) {
        int alpha = 0;
        for (int color : colors) {
            alpha = Math.max(alpha, effectiveAlpha(color));
        }
        return alpha;
    }

    private static MsdfGlyph glyph(MsdfAtlas atlas, int codePoint) {
        if (codePoint >= 0 && codePoint < atlas.asciiGlyphs().length) {
            return atlas.asciiGlyphs()[codePoint];
        }
        return atlas.glyphs().get(codePoint);
    }

    private static float kerning(MsdfAtlas atlas, int left, int right) {
        if (left < 0 || right < 0 || atlas.kernings().isEmpty()) {
            return 0.0f;
        }
        return atlas.kernings().getOrDefault(kerningKey(left, right), 0.0f);
    }

    private static long kerningKey(int left, int right) {
        return ((long) left << 32) ^ (right & 0xFFFFFFFFL);
    }

    private static float normalizeSize(float size) {
        return Math.max(1.0f, Math.min(512.0f, Math.round(size * 4.0f) / 4.0f));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    @Override
    public void close() {
        textureBindings.clear();
        atlases.clear();
        frameBatches.clear();
        widthCache.clear();
        layoutCache.clear();
        boundsCache.clear();
        activeGraphics = null;
        lastWidthValid = false;
        lastWidthFontName = null;
        lastWidthText = null;
        lastLayoutFontName = null;
        lastLayoutText = null;
        lastLayoutValue = null;
        lastBoundsFontName = null;
        lastBoundsText = null;
        lastBoundsValue = null;
        clearLastBatch();
    }

    private record MetricsKey(String fontName, String text, int sizeBits) {
        private static MetricsKey of(FontType font, String text, float size) {
            return new MetricsKey(font.fontName(), text, Float.floatToIntBits(size));
        }
    }

    public record VisualBounds(float minX, float minY, float maxX, float maxY) {
        public static final VisualBounds EMPTY = new VisualBounds(0.0f, 0.0f, 0.0f, 0.0f);

        public float width() {
            return Math.max(0.0f, maxX - minX);
        }

        public float height() {
            return Math.max(0.0f, maxY - minY);
        }

        public boolean empty() {
            return width() <= 0.0f || height() <= 0.0f;
        }
    }

    public record Glyph(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
    }

    public record GlyphLayout(Identifier textureId, float distanceRange, List<Glyph> glyphs, float width, float height) {
        public static final GlyphLayout EMPTY = new GlyphLayout(null, 0.0f, List.of(), 0.0f, 0.0f);

        public boolean empty() {
            return textureId == null || glyphs.isEmpty();
        }
    }

    private record FrameBatchKey(GuiRenderState state, int layerSerial, PoseKey pose, ScreenRectangle scissorArea, TextureSetup textureSetup) {
    }

    private record WidthKey(String fontName, String text, float size) {
    }

    private record PoseKey(float m00, float m01, float m10, float m11, float m20, float m21) {
        static PoseKey of(Matrix3x2f matrix) {
            return new PoseKey(matrix.m00(), matrix.m01(), matrix.m10(), matrix.m11(), matrix.m20(), matrix.m21());
        }
    }
}
