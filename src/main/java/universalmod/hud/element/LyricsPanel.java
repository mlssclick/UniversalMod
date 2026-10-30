package universalmod.hud.element;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import universalmod.service.media.LyricsTimeline;
import universalmod.service.media.MusicTracker;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2D;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.blur.BlurPass;
import universalmod.render.font.FontType;
import universalmod.hud.editor.HudPanel;

import java.util.List;

public final class LyricsPanel extends HudPanel {
    private static final float WIDTH = 230.0F;
    private static final float HEIGHT = 76.0F;
    private static final float PADDING_X = 9.0F;
    private static final float LINE_HEIGHT = 11.0F;
    private static final float TEXT_SIZE = 6.35F;
    private static final float ACTIVE_SIZE_BOOST = 0.85F;
    private static final float TITLE_SIZE = 6.0F;
    private static final float BLUR_RADIUS = 4.0F;
    private static final float BLUR_SMOOTHNESS = 0.55F;
    private static final long BACKWARD_TOLERANCE_MS = 80L;
    private static final long KARAOKE_SWEEP_LEAD_MS = 180L;
    private static final float VERTICAL_STIFFNESS = 205.0F;
    private static final float VERTICAL_DAMPING = 27.5F;
    private static final float HORIZONTAL_STIFFNESS = 235.0F;
    private static final float HORIZONTAL_DAMPING = 30.0F;

    private final MusicTracker musicTracker;
    private LyricsTimeline.Document lastDocument = LyricsTimeline.empty();
    private float activeVisualIndex;
    private float verticalVelocity;
    private float activeTextOffsetX;
    private float horizontalVelocity;
    private int lastPrimary = -1;
    private long lastRenderPositionMs;
    private long lastFrameNanos;
    private boolean documentChanged;

    public LyricsPanel() {
        this(MusicTracker.getInstance());
    }

    public LyricsPanel(MusicTracker musicTracker) {
        super("lyrics", "Lyrics", 10.0F, 90.0F, WIDTH, HEIGHT);
        this.musicTracker = java.util.Objects.requireNonNull(musicTracker, "musicTracker");
    }

    @Override
    public boolean usesProjectedScissor() {
        return true;
    }

    @Override
    public void render() {
        IMediaSession session = musicTracker.getSession();
        MediaInfo media = session == null ? null : session.getMedia();
        LyricsTimeline.Document document = musicTracker.getLyricsDocument();
        boolean preview = editPreview();
        boolean visible = preview || !musicTracker.getMediaRuntimeError().isBlank() || (musicTracker.haveActiveSession() && media != null);
        float alpha = contentAlpha(visible);
        if (alpha <= 0.0F) {
            return;
        }

        size(WIDTH, HEIGHT);
        float x = drag.x();
        float y = drag.y();
        float width = logicalWidth();
        float height = logicalHeight();

        HudRenderCompat.background(new BlurPass(x, y, width, height, 6.0F, BLUR_SMOOTHNESS, BLUR_RADIUS)
                .withColor(ColorUtil.rgba(0, 0, 0, Math.round(alpha * 255.0F))));

        if (preview && (document == null || document.isEmpty())) {
            renderPreview(x, y, alpha);
            return;
        }
        if (document == null || document.isEmpty()) {
            renderEmpty(media, x, y, width, alpha);
            return;
        }
        if (document != lastDocument) {
            lastDocument = document;
            activeVisualIndex = 0.0F;
            verticalVelocity = 0.0F;
            activeTextOffsetX = 0.0F;
            horizontalVelocity = 0.0F;
            lastPrimary = -1;
            lastRenderPositionMs = 0L;
            lastFrameNanos = 0L;
            documentChanged = true;
        }

        String title = media == null ? "Lyrics" : trimToWidth(trackTitle(media), FontType.BOLD, TITLE_SIZE, width - PADDING_X * 2.0F);
        Render2D.text(FontType.BOLD, title, x + PADDING_X, y + 5.0F, TITLE_SIZE, withAlpha(hudTextColor(220), alpha));

        List<LyricsTimeline.Line> lines = document.lines();
        long positionMs = stabilizedPosition(musicTracker.getSyncedPositionMillis());
        int primary = LyricsTimeline.activeIndexMillis(lines, positionMs);
        if (primary < 0) {
            primary = 0;
        }
        primary = Math.min(primary, lines.size() - 1);

        float dt = frameDeltaSeconds();
        if (documentChanged || lastPrimary < 0 || Math.abs(primary - activeVisualIndex) > 4.0F) {
            activeVisualIndex = primary;
            verticalVelocity = 0.0F;
            activeTextOffsetX = 0.0F;
            horizontalVelocity = 0.0F;
            documentChanged = false;
        } else {
            updateVerticalSpring(primary, dt);
        }

        float listX = x + PADDING_X;
        float listY = y + 18.0F;
        float listWidth = width - PADDING_X * 2.0F;
        float listHeight = height - 22.0F;
        float centerLineY = listY + listHeight * 0.48F;

        LyricsTimeline.Line activeLine = lines.get(primary);
        float activeSize = TEXT_SIZE + ACTIVE_SIZE_BOOST;
        float targetHorizontal = targetTextOffset(activeLine, positionMs, activeSize, listWidth);
        if (primary != lastPrimary) {
            horizontalVelocity *= 0.25F;
        }
        updateHorizontalSpring(targetHorizontal, dt);
        lastPrimary = primary;

        Render2D.pushScissor(Render2D.currentGraphics(), listX, listY, listWidth, listHeight);
        try {
            int from = Math.max(0, primary - 4);
            int to = Math.min(lines.size() - 1, primary + 4);
            for (int i = from; i <= to; i++) {
                float visualDistance = Math.abs(i - activeVisualIndex);
                float lineY = centerLineY + (i - activeVisualIndex) * LINE_HEIGHT;
                if (lineY < listY - LINE_HEIGHT * 1.5F || lineY > listY + listHeight + LINE_HEIGHT) {
                    continue;
                }
                LyricsTimeline.Line line = lines.get(i);
                boolean active = i == primary && LyricsTimeline.isLineActive(line, positionMs);
                float distanceAlpha = clamp(1.0F - visualDistance * 0.29F, 0.12F, 1.0F);
                float lineAlpha = active ? alpha : alpha * distanceAlpha;
                float size;
                float renderY = lineY;
                float renderX = listX;
                if (active) {
                    size = activeSize;
                    renderX += activeTextOffsetX;
                    renderActiveLine(line, positionMs, renderX, renderY, size, lineAlpha, listX, listWidth);
                } else {
                    float shrink = Math.min(0.42F, visualDistance * 0.16F);
                    size = TEXT_SIZE - shrink;
                    int color = i < primary ? withAlpha(hudTextColor(205), lineAlpha) : withAlpha(hudMutedColor(190), lineAlpha);
                    Render2D.textFade(FontType.BOLD, line.text(), renderX, renderY, size, color,
                            listX, listX + listWidth, 11.0F, 0.0F, 1.0F);
                }
            }
        } finally {
            Render2D.popScissor(Render2D.currentGraphics());
        }
    }

    private void renderActiveLine(LyricsTimeline.Line line, long positionMs, float x, float y, float size, float alpha, float clipX, float clipWidth) {
        long sweepPositionMs = sweepPosition(line, positionMs);
        int muted = withAlpha(hudMutedColor(185), alpha);
        int completed = withAlpha(hudTextColor(255), alpha);
        int accent = withAlpha(hudAccentColor(255), alpha);

        Render2D.textFade(FontType.BOLD, line.text(), x, y, size, muted,
                clipX, clipX + clipWidth, 11.0F, 0.0F, 1.0F);

        if (line.words().isEmpty()) {
            Render2D.textFade(FontType.BOLD, line.text(), x, y, size, completed,
                    clipX, clipX + clipWidth, 11.0F, 0.0F, 1.0F);
            return;
        }

        int completedCharEnd = 0;
        for (LyricsTimeline.Word word : line.words()) {
            if (sweepPositionMs >= word.endMs()) {
                completedCharEnd = Math.max(completedCharEnd, Math.min(line.text().length(), word.endChar()));
            } else {
                break;
            }
        }
        if (completedCharEnd > 0) {
            String completedText = line.text().substring(0, completedCharEnd);
            Render2D.textFade(FontType.BOLD, completedText, x, y, size, completed,
                    clipX, clipX + clipWidth, 11.0F, 0.0F, 1.0F);
        }

        int activeWord = LyricsTimeline.activeWordIndex(line, sweepPositionMs);
        if (activeWord < 0) {
            return;
        }
        LyricsTimeline.Word word = line.words().get(activeWord);
        int start = Math.max(0, Math.min(line.text().length(), word.startChar()));
        int end = Math.max(start, Math.min(line.text().length(), word.endChar()));
        if (end <= start) {
            return;
        }
        String prefix = line.text().substring(0, start);
        String wordText = line.text().substring(start, end);
        float wordX = x + Render2D.textWidth(FontType.BOLD, prefix, size);
        float wordWidth = Math.max(0.0F, Render2D.textWidth(FontType.BOLD, wordText, size));
        float progress = LyricsTimeline.wordProgress(word, sweepPositionMs);
        float sweep = wordWidth * progress;
        if (sweep > 0.0F) {
            float scissorX = Math.max(clipX, wordX);
            float scissorRight = Math.min(clipX + clipWidth, wordX + sweep + 0.45F);
            if (scissorRight > scissorX) {
                Render2D.pushScissor(Render2D.currentGraphics(), scissorX, y - 2.2F, scissorRight - scissorX, size + 5.4F);
                try {
                    Render2D.text(FontType.BOLD, wordText, wordX, y, size, accent);
                } finally {
                    Render2D.popScissor(Render2D.currentGraphics());
                }
            }
        }
    }

    private float targetTextOffset(LyricsTimeline.Line line, long positionMs, float size, float availableWidth) {
        long sweepPositionMs = sweepPosition(line, positionMs);
        float textWidth = Render2D.textWidth(FontType.BOLD, line.text(), size);
        if (textWidth <= availableWidth) {
            return 0.0F;
        }
        float minOffset = availableWidth - textWidth;
        int activeWord = LyricsTimeline.activeWordIndex(line, sweepPositionMs);
        float anchor;
        if (!line.words().isEmpty()) {
            if (activeWord >= 0) {
                LyricsTimeline.Word word = line.words().get(activeWord);
                int start = Math.max(0, Math.min(line.text().length(), word.startChar()));
                int end = Math.max(start, Math.min(line.text().length(), word.endChar()));
                String prefix = line.text().substring(0, start);
                String wordText = line.text().substring(start, end);
                float wordStart = Render2D.textWidth(FontType.BOLD, prefix, size);
                float wordWidth = Render2D.textWidth(FontType.BOLD, wordText, size);
                anchor = wordStart + wordWidth * LyricsTimeline.wordProgress(word, sweepPositionMs);
            } else if (sweepPositionMs < line.words().get(0).startMs()) {
                anchor = 0.0F;
            } else {
                LyricsTimeline.Word lastWord = line.words().get(line.words().size() - 1);
                int end = Math.max(0, Math.min(line.text().length(), lastWord.endChar()));
                anchor = Render2D.textWidth(FontType.BOLD, line.text().substring(0, end), size);
            }
        } else {
            long span = Math.max(1L, line.endMs() - line.startMs());
            float progress = clamp((sweepPositionMs - line.startMs()) / (float) span, 0.0F, 1.0F);
            anchor = textWidth * progress;
        }
        float desired = availableWidth * 0.43F - anchor;
        return clamp(desired, minOffset, 0.0F);
    }

    private static long sweepPosition(LyricsTimeline.Line line, long positionMs) {
        long led = Math.max(0L, positionMs) + KARAOKE_SWEEP_LEAD_MS;
        long lineLimit = Math.max(line.startMs(), line.endMs() - 1L);
        return Math.min(led, lineLimit);
    }

    private float frameDeltaSeconds() {
        long now = UiFrameContext.nowNanos();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 1.0F / 60.0F;
        }
        float dt = (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        return clamp(dt, 1.0F / 240.0F, 1.0F / 20.0F);
    }

    private void updateVerticalSpring(float target, float dt) {
        float acceleration = (target - activeVisualIndex) * VERTICAL_STIFFNESS - verticalVelocity * VERTICAL_DAMPING;
        verticalVelocity += acceleration * dt;
        activeVisualIndex += verticalVelocity * dt;
        if (Math.abs(target - activeVisualIndex) < 0.0015F && Math.abs(verticalVelocity) < 0.01F) {
            activeVisualIndex = target;
            verticalVelocity = 0.0F;
        }
    }

    private void updateHorizontalSpring(float target, float dt) {
        float acceleration = (target - activeTextOffsetX) * HORIZONTAL_STIFFNESS - horizontalVelocity * HORIZONTAL_DAMPING;
        horizontalVelocity += acceleration * dt;
        activeTextOffsetX += horizontalVelocity * dt;
        if (Math.abs(target - activeTextOffsetX) < 0.01F && Math.abs(horizontalVelocity) < 0.02F) {
            activeTextOffsetX = target;
            horizontalVelocity = 0.0F;
        }
    }

    private long stabilizedPosition(long positionMs) {
        long safePosition = Math.max(0L, positionMs);
        if (musicTracker.isPlaying() && lastRenderPositionMs > 0L && safePosition < lastRenderPositionMs
                && lastRenderPositionMs - safePosition <= BACKWARD_TOLERANCE_MS) {
            safePosition = lastRenderPositionMs;
        }
        lastRenderPositionMs = safePosition;
        return safePosition;
    }

    private void renderPreview(float x, float y, float alpha) {
        Render2D.text(FontType.BOLD, "Lyrics", x + PADDING_X, y + 5.0F, TITLE_SIZE, withAlpha(hudTextColor(220), alpha));
    }

    private void renderEmpty(MediaInfo media, float x, float y, float width, float alpha) {
        String title = media == null ? "Lyrics" : trimToWidth(trackTitle(media), FontType.BOLD, TITLE_SIZE, width - PADDING_X * 2.0F);
        Render2D.text(FontType.BOLD, title, x + PADDING_X, y + 5.0F, TITLE_SIZE, withAlpha(hudTextColor(220), alpha));
        String status = trimToWidth(musicTracker.getLyricsStatus(), FontType.DEFAULT, TEXT_SIZE, width - PADDING_X * 2.0F);
        Render2D.text(FontType.DEFAULT, status, x + PADDING_X, y + 34.0F, TEXT_SIZE, withAlpha(hudMutedColor(210), alpha));
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String trackTitle(MediaInfo media) {
        String artist = safe(media.getArtist()).trim();
        String title = safe(media.getTitle()).trim();
        if (artist.isBlank()) {
            return title.isBlank() ? "Lyrics" : title;
        }
        if (title.isBlank()) {
            return artist;
        }
        return artist + " - " + title;
    }
}
