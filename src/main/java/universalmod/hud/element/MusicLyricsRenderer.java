package universalmod.hud.element;

import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.font.FontType;
import universalmod.service.media.LyricsTimeline;
import universalmod.service.media.MusicTracker;

final class MusicLyricsRenderer {
    private static final float LYRICS_RIGHT_PADDING = 4.0F;
    private static final long LYRICS_BACKWARD_TOLERANCE_MS = 80L;
    private static final long KARAOKE_LEAD_MS = 150L;
    private static final long KARAOKE_GRACE_MS = 3_000L;
    private static final long KARAOKE_OLD_LINE_LIFETIME_MS = 400L;
    private static final long KARAOKE_NEW_LINE_DELAY_MS = 90L;
    private static final float LYRICS_TEXT_SIZE = 4.75F;

    private final MusicTracker music;
    private final SmoothAnimation lyricRevealAnimation = new SmoothAnimation();
    private int compactLyricIndex = -1;
    private String compactLyricText = "";
    private CompactLyricLine currentCompactLine;
    private CompactLyricLine outgoingCompactLine;
    private long lastCompactLyricFrameMs = System.currentTimeMillis();
    private String renderedLyricsTrackKey = "";
    private long lastLyricsRenderPositionMs;

    MusicLyricsRenderer(MusicTracker music) {
        this.music = java.util.Objects.requireNonNull(music, "music");
        lyricRevealAnimation.set(0.0D);
    }

    void render(float x, float y, float width, float height, float alpha, boolean editorPreview) {
        if (height <= 1.5F || alpha <= 0.01F) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        String trackKey = safe(music.author()).strip() + "\u0000" + safe(music.title()).strip();
        if (!trackKey.equals(renderedLyricsTrackKey)) {
            renderedLyricsTrackKey = trackKey;
            resetCompactLyricsForTrackChange(nowMs);
        }

        LyricsTimeline.Document document = music.getLyricsDocument();
        if (document == null || document.isEmpty() || !hasActualLyrics(document)) {
            compactLyricIndex = -1;
            compactLyricText = "";
            currentCompactLine = null;
            outgoingCompactLine = null;
            lyricRevealAnimation.set(0.0D);
            renderLyricsFallback(x, y + height * 0.50F, width, alpha, editorPreview, LyricsTimeline.empty());
            return;
        }
        long rawPosition = stabilizedLyricsPosition(music.getLyricsPositionMillis());
        double karaokePositionMs = Math.max(0L, rawPosition + KARAOKE_LEAD_MS);
        long durationMs = Math.max(0L, Math.round(music.durationSeconds() * 1000.0D));
        float centerY = y + height * 0.50F;

        int activeIndex = -1;
        float karaokeProgress = 0.0F;
        if (document != null && !document.isEmpty() && document.hasLineTiming()) {
            activeIndex = activeLineIndex(document, karaokePositionMs, durationMs, KARAOKE_GRACE_MS);
            if (activeIndex >= 0 && activeIndex < document.lines().size()) {
                karaokeProgress = karaokeProgressForLine(document, activeIndex, karaokePositionMs, durationMs);
            }
        }

        lyricRevealAnimation.update();
        lyricRevealAnimation.run(activeIndex >= 0 ? 1.0D : 0.0D, 0.260D, Easings.FIGMA_EASE_IN_OUT, true);
        float reveal = (float) lyricRevealAnimation.get();

        if (activeIndex >= 0 && document != null) {
            String text = document.lines().get(activeIndex).text();
            refreshCompactLine(activeIndex, text, nowMs);
            if (currentCompactLine != null) {
                currentCompactLine.karaokeProgress = clampLocal(karaokeProgress, 0.0F, 1.0F);
            }
        }

        float dt = Math.min(0.05F, Math.max(0.0F, (nowMs - lastCompactLyricFrameMs) / 1000.0F));
        lastCompactLyricFrameMs = nowMs;

        if (reveal > 0.003F && currentCompactLine != null) {
            Render2D.pushScissor(Render2D.currentGraphics(), x - 1.0F, y - 1.5F,
                    width + 2.0F, height + 4.5F);
            try {
                if (outgoingCompactLine != null) {
                    if (nowMs - outgoingCompactLine.exitAtMs >= KARAOKE_OLD_LINE_LIFETIME_MS) {
                        outgoingCompactLine = null;
                    } else {
                        drawCompactLine(outgoingCompactLine, -1, x, centerY, width,
                                alpha * reveal, nowMs, dt, true);
                    }
                }
                int activeWord = activeWordIndex(currentCompactLine);
                animateWordScales(currentCompactLine, activeWord, dt);
                layoutWords(currentCompactLine);
                updateCompactScroll(currentCompactLine, width, activeWord, dt);
                currentCompactLine.karaokeX = karaokeRevealX(currentCompactLine);
                drawCompactLine(currentCompactLine, activeWord, x, centerY, width,
                        alpha * reveal, nowMs, dt, false);
            } finally {
                Render2D.popScissor(Render2D.currentGraphics());
            }
            return;
        }

        if (reveal <= 0.003F) {
            renderLyricsFallback(x, centerY, width, alpha, editorPreview, document);
        }
    }

    private void resetCompactLyricsForTrackChange(long nowMs) {
        compactLyricIndex = -1;
        compactLyricText = "";
        currentCompactLine = null;
        outgoingCompactLine = null;
        lyricRevealAnimation.set(0.0D);
        lastLyricsRenderPositionMs = 0L;
        lastCompactLyricFrameMs = nowMs;
    }

    private void refreshCompactLine(int activeIndex, String text, long nowMs) {
        String safeText = text == null ? "" : text;
        if (activeIndex < 0 || (activeIndex == compactLyricIndex && safeText.equals(compactLyricText))) {
            return;
        }
        compactLyricIndex = activeIndex;
        compactLyricText = safeText;
        long enterAt = nowMs;
        if (currentCompactLine != null) {
            currentCompactLine.exitAtMs = nowMs;
            outgoingCompactLine = currentCompactLine;
            enterAt += KARAOKE_NEW_LINE_DELAY_MS;
        }
        currentCompactLine = new CompactLyricLine(FontType.SEMIBOLD, LYRICS_TEXT_SIZE, safeText, enterAt);
    }

    private static int activeLineIndex(LyricsTimeline.Document document, double positionMs,
                                                long trackDurationMs, long graceMs) {
        if (document == null || document.isEmpty() || !document.hasLineTiming()) {
            return -1;
        }
        int index = -1;
        for (int i = 0; i < document.lines().size(); i++) {
            LyricsTimeline.Line line = document.lines().get(i);
            if (line.startMs() > (long) positionMs) {
                break;
            }
            if (isMeaningfulTimedLyric(line.text())) {
                index = i;
            }
        }
        return index;
    }

    private static boolean isMeaningfulTimedLyric(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static long lineEnd(LyricsTimeline.Document document, int index, long trackDurationMs) {
        LyricsTimeline.Line line = document.lines().get(index);
        long start = line.startMs();
        long next = nextLineStart(document, index);
        long natural = Math.max(1_500L, Math.min(8_000L,
                (long) line.text().codePointCount(0, line.text().length()) * 150L));
        long end = Math.min(next, start + natural);
        if (trackDurationMs > start) {
            end = Math.min(trackDurationMs, end);
        }
        return Math.max(start, end);
    }

    private static long nextLineStart(LyricsTimeline.Document document, int index) {
        long current = document.lines().get(index).startMs();
        for (int i = index + 1; i < document.lines().size(); i++) {
            long start = document.lines().get(i).startMs();
            if (start > current) {
                return start;
            }
        }
        return Long.MAX_VALUE;
    }

    private static float karaokeProgressForLine(LyricsTimeline.Document document, int index,
                                                  double positionMs, long trackDurationMs) {
        LyricsTimeline.Line line = document.lines().get(index);
        long start = line.startMs();
        long end = lineEnd(document, index, trackDurationMs);
        if (positionMs <= start) return 0.0F;
        if (positionMs >= end) return 1.0F;
        int cpCount = line.text().codePointCount(0, line.text().length());
        if (cpCount <= 0) return 0.0F;
        if (line.words().isEmpty()) {
            return (float) ((positionMs - start) / Math.max(1.0D, end - start));
        }

        long previousTime = start;
        int previousCp = 0;
        for (LyricsTimeline.Word word : line.words()) {
            long markerTime = word.startMs();
            int markerCp = line.text().codePointCount(0, Math.max(0, Math.min(line.text().length(), word.startChar())));
            if (markerTime > positionMs) {
                return interpolateKaraokeProgress(cpCount, positionMs, previousTime, markerTime, previousCp, markerCp);
            }
            previousTime = Math.max(start, markerTime);
            previousCp = Math.max(0, Math.min(cpCount, markerCp));
        }
        return interpolateKaraokeProgress(cpCount, positionMs, previousTime, end, previousCp, cpCount);
    }

    private static float interpolateKaraokeProgress(int totalCp, double positionMs, long fromMs, long toMs,
                                                       int fromCp, int toCp) {
        if (toMs <= fromMs || totalCp == 0) {
            return fromCp / (float) Math.max(1, totalCp);
        }
        double t = Math.max(0.0D, Math.min(1.0D, (positionMs - fromMs) / (double) (toMs - fromMs)));
        return (float) ((fromCp + (toCp - fromCp) * t) / totalCp);
    }

    private void animateWordScales(CompactLyricLine line, int activeWord, float dt) {
        float factor = Math.min(1.0F, dt * 11.0F);
        for (int i = 0; i < line.wordCount(); i++) {
            line.wordScale[i] += (1.0F - line.wordScale[i]) * factor;
            if (Math.abs(line.wordScale[i] - 1.0F) < 0.0005F) {
                line.wordScale[i] = 1.0F;
            }
        }
    }

    private void layoutWords(CompactLyricLine line) {
        float cumulative = 0.0F;
        for (int i = 0; i < line.wordCount(); i++) {
            float extra = (line.wordScale[i] - 1.0F) * (line.wordEndX[i] - line.wordStartX[i] + 5.0F);
            line.wordExtraCenter[i] = cumulative + extra * 0.5F;
            cumulative += extra;
        }
        line.totalExtraWidth = cumulative;
    }

    private int activeWordIndex(CompactLyricLine line) {
        for (int i = line.wordCount() - 1; i > 0; i--) {
            if (line.karaokeProgress * line.characters.length >= line.wordStartChar[i]) {
                return i;
            }
        }
        return 0;
    }

    private void updateCompactScroll(CompactLyricLine line, float viewportWidth, int activeWord, float dt) {
        float visibleWidth = Math.max(1.0F, viewportWidth - LYRICS_RIGHT_PADDING);
        float overflow = Math.max(0.0F, line.textWidth - visibleWidth);
        line.overflow = overflow;
        if (overflow <= 0.5F) {
            line.scroll -= line.scroll * Math.min(1.0F, dt * 9.0F);
            return;
        }
        float maxScroll = Math.max(0.0F, line.textWidth + line.totalExtraWidth - visibleWidth);
        float follow = line.wordEndX[activeWord] + line.wordExtraCenter[activeWord] - visibleWidth * 0.6F;
        float endLock = line.karaokeProgress >= 0.92F ? maxScroll : 0.0F;
        float target = Math.max(endLock, Math.max(0.0F, Math.min(maxScroll, follow)));
        if (target <= line.scroll) {
            return;
        }
        line.scroll += (target - line.scroll) * (1.0F - (float) Math.exp(-9.0F * dt));
        if (line.karaokeProgress >= 0.995F && maxScroll - line.scroll < 0.35F) {
            line.scroll = maxScroll;
        }
    }

    private float karaokeRevealX(CompactLyricLine line) {
        float position = clampLocal(line.karaokeProgress, 0.0F, 1.0F) * line.characters.length;
        if (position >= line.characters.length) {
            return line.textWidth;
        }
        int index = (int) position;
        return line.charX[index] + line.charAdvance(index) * (position - index);
    }

    private void drawCompactLine(CompactLyricLine line, int activeWord, float x, float centerY,
                                          float viewportWidth, float alpha, long nowMs, float dt, boolean exiting) {
        if (line == null || line.wordCount() == 0) return;
        float staggerMs = Math.min(16.0F, (exiting ? 220.0F : 320.0F) / line.characters.length);
        float elapsed = nowMs - (exiting ? line.exitAtMs : line.enterAtMs);
        if (elapsed <= 0.0F) return;
        if (!exiting && elapsed - (line.characters.length - 1) * staggerMs >= 240.0F) {
            drawStableCompactLine(line, x, centerY, viewportWidth, alpha);
            return;
        }
        for (int word = 0; word < line.wordCount(); word++) {
            drawCompactWord(line, word, x, centerY, viewportWidth, alpha,
                    elapsed, staggerMs, exiting);
        }
    }

    private void drawStableCompactLine(CompactLyricLine line, float x, float centerY,
                                                float viewportWidth, float alpha) {
        float visibleWidth = Math.max(1.0F, viewportWidth - LYRICS_RIGHT_PADDING);
        float drawX = x - line.scroll;
        float textY = centeredTextY(FontType.SEMIBOLD, line.text, LYRICS_TEXT_SIZE, centerY);
        float leftFade = line.textWidth > visibleWidth ? clampLocal(line.scroll / 9.0F, 0.0F, 1.0F) : 0.0F;
        float rightFade = line.textWidth > visibleWidth
                ? clampLocal((line.overflow - line.scroll) / 9.0F, 0.0F, 1.0F) : 0.0F;
        Render2D.textFade(FontType.SEMIBOLD, line.text, drawX, textY, LYRICS_TEXT_SIZE,
                HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha * 0.42F),
                x, x + visibleWidth, 9.0F, leftFade, rightFade);
        float revealRight = Math.min(x + visibleWidth, x - line.scroll + line.karaokeX);
        if (revealRight <= x) {
            return;
        }
        Render2D.pushScissor(Render2D.currentGraphics(), x, centerY - 10.0F,
                revealRight - x, 20.0F);
        try {
            Render2D.textFade(FontType.SEMIBOLD, line.text, drawX, textY, LYRICS_TEXT_SIZE,
                    HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha * 0.18F),
                    x, x + visibleWidth, 9.0F, leftFade, rightFade);
            Render2D.textFade(FontType.SEMIBOLD, line.text, drawX, textY, LYRICS_TEXT_SIZE,
                    HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha * 0.38F),
                    x, x + visibleWidth, 9.0F, leftFade, rightFade);
            Render2D.textFade(FontType.SEMIBOLD, line.text, drawX, textY, LYRICS_TEXT_SIZE,
                    HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha),
                    x, x + visibleWidth, 9.0F, leftFade, rightFade);
        } finally {
            Render2D.popScissor(Render2D.currentGraphics());
        }
    }

    private void drawCompactWord(CompactLyricLine line, int word, float x, float centerY,
                                          float viewportWidth, float alpha, float elapsed,
                                          float staggerMs, boolean exiting) {
        float scale = line.wordScale[word];
        float extraCenter = line.wordExtraCenter[word];
        float yShift = (scale - 1.0F) * 3.0F;
        float pivotX = x - line.scroll + extraCenter + (line.wordStartX[word] + line.wordEndX[word]) * 0.5F;
        float pivotY = centerY;
        boolean scaled = Math.abs(scale - 1.0F) > 0.002F;


        boolean fullyEntered = !exiting && elapsed - line.wordEndChar[word] * staggerMs >= 240.0F;
        boolean fullyExited = exiting && elapsed - line.wordEndChar[word] * staggerMs >= 180.0F;
        if (fullyExited) {
            return;
        }

        if (fullyEntered) {
            drawShapedWord(line, word, x, centerY, viewportWidth, alpha, 0.0F,
                    scale, extraCenter, yShift, pivotX, pivotY);
            return;
        }

        float wordRaw = exiting
                ? 1.0F - clampLocal((elapsed - line.wordStartChar[word] * staggerMs) / 180.0F, 0.0F, 1.0F)
                : clampLocal((elapsed - line.wordStartChar[word] * staggerMs) / 240.0F, 0.0F, 1.0F);
        float wordVertical = (1.0F - easeOutCubic(wordRaw)) * (exiting ? -6.0F : 6.0F);
        for (int charIndex = line.wordStartChar[word]; charIndex <= line.wordEndChar[word]; charIndex++) {
            float raw = exiting
                    ? 1.0F - clampLocal((elapsed - charIndex * staggerMs) / 180.0F, 0.0F, 1.0F)
                    : clampLocal((elapsed - charIndex * staggerMs) / 240.0F, 0.0F, 1.0F);
            if (raw <= 0.001F) continue;
            float eased = easeOutCubic(raw);
            float vertical = wordVertical;

            float rawLeft = x - line.scroll + extraCenter + line.charX[charIndex];
            float rawRight = rawLeft + line.charAdvance(charIndex);
            float clipLeft = rawLeft;
            float clipRight = rawRight;
            if (scaled) {
                clipLeft = pivotX + (clipLeft - pivotX) * scale;
                clipRight = pivotX + (clipRight - pivotX) * scale;
            }
            if (clipRight < clipLeft) {
                float swap = clipLeft;
                clipLeft = clipRight;
                clipRight = swap;
            }


            float visibleLeft = Math.max(x, clipLeft);
            float visibleRight = Math.min(x + viewportWidth, clipRight);
            if (visibleRight - visibleLeft <= 0.001F) continue;

            Render2D.pushScissor(Render2D.currentGraphics(), visibleLeft, centerY - 10.0F,
                    visibleRight - visibleLeft, 20.0F);
            try {
                drawShapedWord(line, word, x, centerY, viewportWidth,
                        alpha * eased, vertical, scale, extraCenter, yShift, pivotX, pivotY);
            } finally {
                Render2D.popScissor(Render2D.currentGraphics());
            }
        }
    }

    private void drawShapedWord(CompactLyricLine line, int word, float x, float centerY,
                                         float viewportWidth, float alpha, float vertical,
                                         float scale, float extraCenter, float yShift,
                                         float pivotX, float pivotY) {
        if (alpha <= 0.001F) return;
        String text = line.words[word];
        float baseX = x - line.scroll + extraCenter + line.wordStartX[word];
        float baseY = centeredTextY(FontType.SEMIBOLD, text, LYRICS_TEXT_SIZE, centerY) - yShift + vertical;
        if (Math.abs(scale - 1.0F) > 0.002F) {
            baseX = pivotX + (baseX - pivotX) * scale;
            baseY = pivotY + (baseY - pivotY) * scale;
        }
        float size = LYRICS_TEXT_SIZE * scale;


        float leftFade = line.textWidth > viewportWidth ? clampLocal(line.scroll / 9.0F, 0.0F, 1.0F) : 0.0F;
        float rightFade = line.textWidth > viewportWidth
                ? clampLocal((line.overflow - line.scroll) / 9.0F, 0.0F, 1.0F) : 0.0F;

        float rawWordStart = x - line.scroll + extraCenter + line.wordStartX[word];
        float rawWordEnd = x - line.scroll + extraCenter + line.wordEndX[word];
        float wordStart = rawWordStart;
        float wordEnd = rawWordEnd;
        float karaoke = x - line.scroll + extraCenter + line.karaokeX;
        if (Math.abs(scale - 1.0F) > 0.002F) {
            wordStart = pivotX + (rawWordStart - pivotX) * scale;
            wordEnd = pivotX + (rawWordEnd - pivotX) * scale;
            karaoke = pivotX + (karaoke - pivotX) * scale;
        }
        if (wordEnd < wordStart) {
            float swap = wordStart;
            wordStart = wordEnd;
            wordEnd = swap;
        }


        Render2D.textFade(FontType.SEMIBOLD, text, baseX, baseY, size,
                HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha * 0.42F),
                x, x + viewportWidth, 9.0F, leftFade, rightFade);


        float revealRight = Math.min(wordEnd, karaoke);
        float revealLeft = Math.max(wordStart, x);
        revealRight = Math.min(revealRight, x + viewportWidth);
        if (revealRight - revealLeft > 0.001F) {
            Render2D.pushScissor(Render2D.currentGraphics(), revealLeft, centerY - 10.0F,
                    revealRight - revealLeft, 20.0F);
            try {
                Render2D.textFade(FontType.SEMIBOLD, text, baseX, baseY, size,
                        HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha),
                        x, x + viewportWidth, 9.0F, leftFade, rightFade);
            } finally {
                Render2D.popScissor(Render2D.currentGraphics());
            }
        }
    }


    private void renderLyricsFallback(
            float x,
            float centerY,
            float width,
            float alpha,
            boolean editorPreview,
            LyricsTimeline.Document document
    ) {


        String fallback = "";
        if (document != null && !document.isEmpty() && !document.hasLineTiming()) {
            for (LyricsTimeline.Line line : document.lines()) {
                String candidate = line == null ? "" : safe(line.text()).strip();
                if (isActualLyricLine(candidate)) {
                    fallback = candidate;
                    break;
                }
            }
        }


        if (fallback.isBlank()) {
            if (document == null || document.isEmpty()) {
                fallback = universalmod.service.language.LanguageManager.translate("hud.music.no_lyrics");
            } else {
                return;
            }
        }

        fallback = trimToWidth(fallback, FontType.SEMIBOLD, LYRICS_TEXT_SIZE, Math.max(10.0F, width - LYRICS_RIGHT_PADDING));
        float textY = centeredTextY(FontType.SEMIBOLD, fallback, LYRICS_TEXT_SIZE, centerY);
        Render2D.textFade(FontType.SEMIBOLD, fallback, x + 1.0F, textY, LYRICS_TEXT_SIZE,
                HudVisualStyle.alpha(HudVisualStyle.MUTED, alpha), x, x + width, 8.0F, 0.0F, 1.0F);
    }

    private static boolean hasActualLyrics(LyricsTimeline.Document document) {
        if (document == null || document.isEmpty()) {
            return false;
        }
        for (LyricsTimeline.Line line : document.lines()) {
            if (line != null && isActualLyricLine(line.text())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isActualLyricLine(String value) {
        if (value == null) return false;
        String text = value.strip();
        if (text.isEmpty()) return false;
        String lower = text.toLowerCase(java.util.Locale.ROOT);


        if (lower.matches("\\d+\\s+contributors?")) return false;
        if (lower.equals("contributors") || lower.equals("translations") || lower.startsWith("translations ") ) return false;
        if (lower.equals("read more") || lower.equals("embed") || lower.matches("\\d+\\s+embed")) return false;
        if (lower.startsWith("you might also like")) return false;
        return true;
    }

    private long stabilizedLyricsPosition(long positionMs) {
        long safePosition = Math.max(0L, positionMs);
        if (music.isPlaying() && lastLyricsRenderPositionMs > 0L && safePosition < lastLyricsRenderPositionMs
                && lastLyricsRenderPositionMs - safePosition <= LYRICS_BACKWARD_TOLERANCE_MS) {
            safePosition = lastLyricsRenderPositionMs;
        }
        lastLyricsRenderPositionMs = safePosition;
        return safePosition;
    }

    private static float easeOutCubic(float value) {
        float t = clampLocal(value, 0.0F, 1.0F);
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }


    private static String trimToWidth(String text, FontType font, float size, float width) {
        if (text == null) {
            return "";
        }
        if (Render2D.textWidth(font, text, size) <= width) {
            return text;
        }
        String suffix = "..";
        if (Render2D.textWidth(font, suffix, size) > width) {
            return suffix;
        }
        int low = 0;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String candidate = text.substring(0, mid) + suffix;
            if (Render2D.textWidth(font, candidate, size) <= width) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, best) + suffix;
    }

    private static float centeredTextY(FontType font, String text, float size, float centerY) {
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(font, text, size);
        if (bounds == null || bounds.empty()) {
            return centerY - size * 0.5F;
        }
        return centerY - bounds.centerY();
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static float clampLocal(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class CompactLyricLine {
        final String text;
        final String[] characters;
        final float[] charX;
        final String[] words;
        final int[] wordStartChar;
        final int[] wordEndChar;
        final float[] wordStartX;
        final float[] wordEndX;
        final float[] wordScale;
        final float[] wordExtraCenter;
        final float textWidth;
        final long enterAtMs;
        long exitAtMs;
        float overflow;
        float scroll;
        float totalExtraWidth;
        float karaokeX;
        float karaokeProgress;

        CompactLyricLine(FontType font, float fontSize, String text, long enterAtMs) {
            this.text = text == null ? "" : text;
            this.enterAtMs = enterAtMs;


            java.text.BreakIterator iterator = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT);
            iterator.setText(this.text);
            java.util.ArrayList<Integer> starts = new java.util.ArrayList<>();
            java.util.ArrayList<Integer> ends = new java.util.ArrayList<>();
            int boundary = iterator.first();
            for (int next = iterator.next(); next != java.text.BreakIterator.DONE; boundary = next, next = iterator.next()) {
                starts.add(boundary);
                ends.add(next);
            }

            int length = starts.size();
            this.characters = new String[length];
            this.charX = new float[length];
            for (int i = 0; i < length; i++) {
                int from = starts.get(i);
                int to = ends.get(i);
                characters[i] = this.text.substring(from, to);
                charX[i] = from == 0 ? 0.0F : Render2D.textWidth(font, this.text.substring(0, from), fontSize);
            }

            int wordCount = 0;
            boolean inWord = false;
            for (int i = 0; i < length; i++) {
                boolean whitespace = characters[i].codePoints().allMatch(Character::isWhitespace);
                if (!whitespace && !inWord) wordCount++;
                inWord = !whitespace;
            }
            this.words = new String[wordCount];
            this.wordStartChar = new int[wordCount];
            this.wordEndChar = new int[wordCount];
            this.wordStartX = new float[wordCount];
            this.wordEndX = new float[wordCount];
            this.wordScale = new float[wordCount];
            this.wordExtraCenter = new float[wordCount];
            java.util.Arrays.fill(this.wordScale, 1.0F);

            int word = -1;
            inWord = false;
            for (int i = 0; i < length; i++) {
                boolean whitespace = characters[i].codePoints().allMatch(Character::isWhitespace);
                if (!whitespace) {
                    if (!inWord) {
                        word++;
                        wordStartChar[word] = i;
                        wordStartX[word] = charX[i];
                    }
                    wordEndChar[word] = i;
                    int utf16End = ends.get(i);
                    wordEndX[word] = Render2D.textWidth(font, this.text.substring(0, utf16End), fontSize);
                }
                inWord = !whitespace;
            }
            for (int i = 0; i < wordCount; i++) {
                int from = starts.get(wordStartChar[i]);
                int to = ends.get(wordEndChar[i]);
                words[i] = this.text.substring(from, to);
            }
            this.textWidth = Render2D.textWidth(font, this.text, fontSize);
        }

        int wordCount() {
            return words.length;
        }

        float charAdvance(int index) {
            return (index + 1 < charX.length ? charX[index + 1] : textWidth) - charX[index];
        }
    }
}
