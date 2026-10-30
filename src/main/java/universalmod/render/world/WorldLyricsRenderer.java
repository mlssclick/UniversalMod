package universalmod.render.world;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import universalmod.event.type.WorldRenderEvent;
import universalmod.render.Render3D;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.msdf.MsdfIconRenderer;
import universalmod.service.media.LyricsTimeline;
import universalmod.service.media.MusicTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorldLyricsRenderer {
    private static final Pattern WORDS = Pattern.compile("\\S+");
    private static final long SPAWN_LEAD_MS = 260L;

    private static final int TEXT_COLOR = ColorUtil.rgba(201, 209, 234, 255);
    private static final int HIGHLIGHT_COLOR = ColorUtil.rgba(118, 213, 255, 255);

    private LyricsTimeline.Document activeDocument = LyricsTimeline.empty();
    private ClientWorld activeWorld;
    private int activeLine = -1;
    private long previousPosition = -1L;
    private List<WorldWord> activeWords = List.of();

    private record Token(String text, int begin, int end) {}

    private static final class WorldWord {
        private final MsdfIconRenderer.GlyphLayout glyphs;
        private final WorldLyricsSpatialLayout.Spot spot;
        private final long startMs;
        private final long endMs;
        private final int index;
        private Vec3d worldPosition;
        private Quaternionf worldOrientation;

        private WorldWord(MsdfIconRenderer.GlyphLayout glyphs, WorldLyricsSpatialLayout.Spot spot,
                          long startMs, long endMs, int index) {
            this.glyphs = glyphs;
            this.spot = spot;
            this.startMs = startMs;
            this.endMs = endMs;
            this.index = index;
        }
    }

    public void reset() {
        activeDocument = LyricsTimeline.empty();
        activeWorld = null;
        activeLine = -1;
        previousPosition = -1L;
        activeWords = List.of();
    }

    public void render(WorldRenderEvent event, MusicTracker music,
                       float distance, float height, float size, float spread,
                       int transitionMs, float motion,
                       boolean respectWalls) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (event == null || minecraft.world == null || minecraft.player == null) {
            reset();
            return;
        }
        if (activeWorld != minecraft.world) {
            reset();
            activeWorld = minecraft.world;
        }
        LyricsTimeline.Document document = music.getLyricsDocument();
        if (document == null || document.isEmpty() || !document.hasLineTiming()) {
            clearLine();
            return;
        }
        long position = Math.max(0L, music.getSyncedPositionMillis());

        if (document != activeDocument || (previousPosition >= 0L && position + 1300L < previousPosition)) {
            clearLine();
            activeDocument = document;
        }
        previousPosition = position;
        int index = LyricsTimeline.activeLineIndex(document, position,
                Math.max(0L, Math.round(music.durationSeconds() * 1000.0D)), 0L);
        if (index < 0) {
            clearLine();
            return;
        }
        LyricsTimeline.Line line = document.lines().get(index);
        if (index != activeLine || activeWords.isEmpty()) {
            activeLine = index;
            activeWords = createWorldWords(line, distance, size, spread);
        }
        if (activeWords.isEmpty()) return;

        float lineFade = WorldLyricsAnimation.linePresence(position,
                line.startMs(), line.endMs(), transitionMs);
        if (lineFade <= 0.002F) return;
        MatrixStack pose = event.getStack();
        Vec3d camera = Render3D.lastCameraPos;
        if (!WorldMsdfTextRenderer.beginBatch(activeWords.get(0).glyphs.texture())) return;
        try {
        for (WorldWord word : activeWords) {

            if (position + SPAWN_LEAD_MS < word.startMs) continue;
            if (word.worldPosition == null) {
                anchorWord(minecraft, word, distance, height, respectWalls);
            }
            float reveal = WorldLyricsAnimation.wordReveal(position, word.startMs, word.endMs);
            float appeared = WorldLyricsAnimation.wordAppearing(position, word.startMs, transitionMs, SPAWN_LEAD_MS);
            float opacity = appeared * lineFade;
            if (opacity <= 0.005F) continue;

            float entrance = 1.0F - appeared;
            float pulse = 1.0F + 0.09F * (float) Math.sin(Math.PI * appeared) * motion;
            float lift = entrance * -0.11F * motion;
            int base = withOpacity(TEXT_COLOR, opacity * 0.90F);
            int accent = withOpacity(HIGHLIGHT_COLOR, opacity * 0.95F);
            Vec3d local = word.worldPosition.subtract(camera);
            pose.push();
            pose.translate(local.x, local.y + lift, local.z);
            pose.multiply(word.worldOrientation);
            pose.multiply(new Quaternionf().rotateZ((float) Math.toRadians(Math.sin(word.index * 2.4) * 2.1F)));
            pose.scale(word.spot.scale() * pulse, -word.spot.scale() * pulse, word.spot.scale() * pulse);
            pose.translate(-word.glyphs.width() * 0.5F, -word.glyphs.lineHeight() * 0.38F, 0.0F);
            WorldMsdfTextRenderer.drawWord(pose.peek(), word.glyphs, reveal, base, accent);
            pose.pop();
        }
        } finally {
            WorldMsdfTextRenderer.flush();
        }
    }

    private List<WorldWord> createWorldWords(LyricsTimeline.Line line,
                                            float distance, float size, float spread) {
        String raw = line.text() == null ? "" : line.text().strip().replace('\n', ' ');
        Matcher matcher = WORDS.matcher(raw);
        List<Token> tokens = new ArrayList<>();

        while (matcher.find()) {
            tokens.add(new Token(matcher.group(), matcher.start(), matcher.end()));
        }
        if (tokens.isEmpty()) return List.of();
        List<MsdfIconRenderer.GlyphLayout> layouts = new ArrayList<>(tokens.size());
        float[] widths = new float[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            MsdfIconRenderer.GlyphLayout glyphs = WorldMsdfTextRenderer.layout(tokens.get(i).text());

            if (glyphs.empty()) return List.of();
            layouts.add(glyphs);
            widths[i] = glyphs.width();
        }
        List<WorldLyricsSpatialLayout.Spot> spots = WorldLyricsSpatialLayout.arrange(widths, distance, size, spread);
        List<WorldWord> result = new ArrayList<>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            long[] timing = wordTiming(line, tokens, i);
            result.add(new WorldWord(layouts.get(i), spots.get(i), timing[0], timing[1], i));
        }
        return List.copyOf(result);
    }

    private static void anchorWord(MinecraftClient minecraft, WorldWord word,
                                   float distance, float height, boolean respectWalls) {
        Vec3d camera = Render3D.lastCameraPos;
        Quaternionf facing = new Quaternionf(Render3D.lastCameraRotation);
        Vector3f f = facing.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        Vector3f r = facing.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f u = facing.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vec3d forward = new Vec3d(f.x, f.y, f.z).normalize();
        Vec3d right = new Vec3d(r.x, r.y, r.z).normalize();
        Vec3d up = new Vec3d(u.x, u.y, u.z).normalize();

        float depth = ((word.index * 73 % 11) - 5) * 0.048F;
        Vec3d anchor = camera.add(forward.multiply(distance + depth))
                .add(right.multiply(word.spot.x())).add(up.multiply(height + word.spot.y()));
        if (respectWalls) {
            BlockHitResult hit = minecraft.world.raycast(new RaycastContext(camera, anchor,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, minecraft.player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3d direction = anchor.subtract(camera);
                double hitDistance = hit.getPos().distanceTo(camera);
                double targetDistance = Math.max(0.85D, hitDistance - 0.30D);
                anchor = camera.add(direction.normalize().multiply(Math.min(targetDistance, direction.length())));
            }
        }
        word.worldPosition = anchor;
        word.worldOrientation = facing;
    }

    private static long[] wordTiming(LyricsTimeline.Line line, List<Token> tokens, int index) {
        Token token = tokens.get(index);
        List<LyricsTimeline.Word> timed = line.words();
        if (!timed.isEmpty()) {
            if (timed.size() == tokens.size()) {
                LyricsTimeline.Word word = timed.get(index);
                return new long[]{word.startMs(), Math.max(word.startMs() + 1L, word.endMs())};
            }
            for (LyricsTimeline.Word word : timed) {
                if (word.startChar() <= token.begin() && word.endChar() >= token.end()) {
                    return new long[]{word.startMs(), Math.max(word.startMs() + 1L, word.endMs())};
                }
            }
        }

        long span = Math.max(1L, line.endMs() - line.startMs());
        int total = 0, before = 0;
        for (int i = 0; i < tokens.size(); i++) {
            int weight = Math.max(1, tokens.get(i).text().codePointCount(0, tokens.get(i).text().length()));
            total += weight;
            if (i < index) before += weight;
        }
        int weight = Math.max(1, token.text().codePointCount(0, token.text().length()));
        long start = line.startMs() + Math.round(span * (before / (double) total));
        long end = line.startMs() + Math.round(span * ((before + weight) / (double) total));
        return new long[]{start, Math.max(start + 1L, end)};
    }

    private static int withOpacity(int color, float factor) {
        return ColorUtil.withAlpha(color, Math.round(ColorUtil.getAlpha(color)
                * WorldLyricsAnimation.clamp01(factor)));
    }

    private void clearLine() {
        activeLine = -1;
        activeWords = List.of();
    }
}
