package universalmod.hud.element;

import universalmod.hud.HudMouseEvent;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.Identifier;
import universalmod.hud.editor.HudPanel;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.service.media.MusicTracker;
import universalmod.gui.screen.media.NowPlayingScreen;

import java.util.Locale;

public final class MusicPlayerPanel extends HudPanel {
    private static final float MIN_WIDTH = 116.0F;
    private static final float MAX_WIDTH = 170.0F;
    private static final float FIXED_META_WIDTH = 62.0F;
    private static final float MAIN_HEIGHT = 21.0F;
    private static final float LYRICS_HEIGHT = 12.8F;
    private static final float COVER = 13.4F;
    private static final float SLOT = 7.0F;
    private static final float LYRICS_BUTTON_SIZE = 7.2F;
    private static final float EQ_SIZE = 7.8F;
    private static final float BUTTON_HIT = 10.5F;
    private static final float IMAGE_SIZE = 5.2F;
    private static final String IMAGE_PREFIX = "music_player/";
    private static final String SOURCE_ICON_PREFIX = IMAGE_PREFIX + "source_icons/";
    private static final int EQUALIZER_BARS = 4;
    private static final float[] EQUALIZER_FREQUENCY_A = {1.0F, 2.0F, 0.5F, 1.5F};
    private static final float[] EQUALIZER_FREQUENCY_B = {3.0F, 1.5F, 2.5F, 4.0F};
    private static final float[] EQUALIZER_PHASE = {0.0F, 1.9F, 4.2F, 2.7F};
    private static final double[] FAST_SIN_TABLE = createFastSinTable();

    private final MusicTracker music;
    private final MusicLyricsRenderer lyricsRenderer;
    private final SmoothAnimation previousHover = new SmoothAnimation();
    private final SmoothAnimation playHover = new SmoothAnimation();
    private final SmoothAnimation nextHover = new SmoothAnimation();
    private final SmoothAnimation lyricsHover = new SmoothAnimation();
    private final SmoothAnimation previousPress = new SmoothAnimation();
    private final SmoothAnimation playPress = new SmoothAnimation();
    private final SmoothAnimation nextPress = new SmoothAnimation();
    private final SmoothAnimation lyricsPress = new SmoothAnimation();
    private final SmoothAnimation lyricsOpen = new SmoothAnimation();
    private final SmoothAnimation playStateAnimation = new SmoothAnimation();
    private final SmoothAnimation artworkReveal = new SmoothAnimation();

    private boolean lyricsExpanded = true;
    private String cachedSourceIdentity = "\u0000";
    private String cachedSourceIcon;
    private final float[] equalizerBars = new float[EQUALIZER_BARS];
    private long lastEqualizerUpdateMs = System.currentTimeMillis();

    public MusicPlayerPanel() {
        this(MusicTracker.getInstance());
    }

    public MusicPlayerPanel(MusicTracker music) {
        super("music_player", "Music Player", 10.0F, 29.0F, MIN_WIDTH, MAIN_HEIGHT + LYRICS_HEIGHT);
        this.music = java.util.Objects.requireNonNull(music, "music");
        this.lyricsRenderer = new MusicLyricsRenderer(this.music);
        sizeImmediate(MIN_WIDTH, MAIN_HEIGHT + LYRICS_HEIGHT);
        previousHover.set(0.0D);
        playHover.set(0.0D);
        nextHover.set(0.0D);
        lyricsHover.set(0.0D);
        previousPress.set(0.0D);
        playPress.set(0.0D);
        nextPress.set(0.0D);
        lyricsPress.set(0.0D);
        lyricsOpen.set(1.0D);
        playStateAnimation.set(0.0D);
        artworkReveal.set(0.0D);
        for (int i = 0; i < equalizerBars.length; i++) {
            equalizerBars[i] = 3.0F;
        }
    }

    @Override
    public boolean usesProjectedScissor() {
        return true;
    }

    @Override
    public void render() {
        music.ensureStarted();
        boolean active = music.getSession() != null;
        boolean editorPreview = !active && editPreview();
        float alpha = contentAlpha(active || editorPreview);
        if (alpha <= 0.01F) {
            return;
        }

        lyricsOpen.update();
        lyricsOpen.run(lyricsExpanded ? 1.0D : 0.0D, 0.18D, Easings.EXPO_OUT, true);
        float lyricsVisibility = (float) lyricsOpen.get();

        float x = drag.x();
        float y = drag.y();
        PlayerLayout layout = buildLayout(x, y, editorPreview);
        float totalHeight = MAIN_HEIGHT + LYRICS_HEIGHT * lyricsVisibility;
        sizeImmediate(layout.width(), totalHeight);

        HudVisualStyle.blackPanel(x, y, layout.width(), totalHeight, 4.8F, alpha);

        drawCover(layout.coverX(), layout.coverY(), alpha, active);
        drawMetadata(layout, alpha);
        updateInteractionAnimations(active || editorPreview, layout);

        drawSeparator(layout.separator1X(), layout.centerY(), alpha);
        drawTransport(IMAGE_PREFIX + "previous.png", layout.previousX(), layout.slotY(), alpha,
                previousHover.get(), previousPress.get());
        drawPlayPauseTransport(layout.playX(), layout.slotY(), alpha, active && music.isPlaying());
        drawTransport(IMAGE_PREFIX + "next.png", layout.nextX(), layout.slotY(), alpha,
                nextHover.get(), nextPress.get());
        drawSeparator(layout.separator2X(), layout.centerY(), alpha);
        drawLyricsButton(layout.lyricsX(), layout.centerY(), alpha);
        drawSeparator(layout.separator3X(), layout.centerY(), alpha);
        drawEqualizer(layout.equalizerX(), layout.centerY(), alpha);

        float barX = x + 2.4F;
        float barY = y + MAIN_HEIGHT - 1.65F;
        float barW = layout.width() - 4.8F;
        Render2D.rect(barX, barY, barW, 1.35F, 0.67F,
                ColorUtil.rgba(58, 56, 68, Math.round(214.0F * alpha)));
        float progress = active ? music.progress() : 0.52F;
        if (progress > 0.001F) {
            Render2D.rect(barX, barY, Math.max(1.35F, barW * progress), 1.35F, 0.67F,
                    HudVisualStyle.accent(alpha));
        }

        if (lyricsVisibility > 0.01F) {
            lyricsRenderer.render(
                    x + 3.7F,
                    y + MAIN_HEIGHT + 1.0F,
                    layout.width() - 7.4F,
                    Math.max(2.0F, LYRICS_HEIGHT * lyricsVisibility - 1.7F),
                    alpha * lyricsVisibility,
                    editorPreview
            );
        }

    }

    @Override
    public boolean mouseClicked(HudMouseEvent event, boolean doubled) {
        if (event == null || event.button() != 0) {
            return false;
        }
        float mx = localMouseX((float) event.x());
        float my = localMouseY((float) event.y());
        PlayerLayout layout = buildLayout(drag.x(), drag.y(), editPreview() && music.getSession() == null);

        if (inside(mx, my, layout.coverX(), layout.coverY(), COVER, COVER)
                && music.haveActiveSession()) {
            mc.setScreen(new NowPlayingScreen());
            return true;
        }

        if (insideCentered(mx, my, layout.previousX(), layout.slotY(), SLOT, SLOT, BUTTON_HIT, BUTTON_HIT)) {
            previousPress.set(1.0D);
            music.previousTrack();
            return true;
        }
        if (insideCentered(mx, my, layout.playX(), layout.slotY(), SLOT, SLOT, BUTTON_HIT, BUTTON_HIT)) {
            playPress.set(1.0D);
            music.togglePlay();
            return true;
        }
        if (insideCentered(mx, my, layout.nextX(), layout.slotY(), SLOT, SLOT, BUTTON_HIT, BUTTON_HIT)) {
            nextPress.set(1.0D);
            music.nextTrack();
            return true;
        }
        if (insideCentered(mx, my, layout.lyricsX(), layout.centerY() - LYRICS_BUTTON_SIZE * 0.5F,
                LYRICS_BUTTON_SIZE, LYRICS_BUTTON_SIZE, BUTTON_HIT, BUTTON_HIT)) {
            lyricsPress.set(1.0D);
            lyricsExpanded = !lyricsExpanded;
            return true;
        }
        return false;
    }

    private PlayerLayout buildLayout(float x, float y, boolean editorPreview) {
        float centerY = y + MAIN_HEIGHT * 0.5F;
        float slotY = centerY - SLOT * 0.5F;
        float coverX = x + 3.6F;
        float coverY = centerY - COVER * 0.5F;
        float metaX = coverX + COVER + 3.6F;

        Metadata raw = normalizedMetadata(editorPreview);
        Metadata fitted = fitMetadata(raw, FIXED_META_WIDTH);

        float cursor = metaX + FIXED_META_WIDTH + 2.7F;
        float separator1X = cursor;
        cursor += 2.5F;
        float previousX = cursor;
        cursor += SLOT + 0.9F;
        float playX = cursor;
        cursor += SLOT + 0.9F;
        float nextX = cursor;
        cursor += SLOT + 1.8F;
        float separator2X = cursor;
        cursor += 2.2F;
        float lyricsX = cursor;
        cursor += LYRICS_BUTTON_SIZE + 1.8F;
        float separator3X = cursor;
        cursor += 2.2F;
        float equalizerX = cursor;
        cursor += EQ_SIZE + 3.6F;

        float width = clampLocal(cursor - x, MIN_WIDTH, MAX_WIDTH);
        return new PlayerLayout(
                width, centerY, slotY, coverX, coverY, metaX, fitted,
                separator1X, previousX, playX, nextX,
                separator2X, lyricsX, separator3X, equalizerX
        );
    }

    private Metadata normalizedMetadata(boolean editorPreview) {
        String title = firstNonBlank(music.title(), music.getSourceName(), music.getSourceAppName(), editorPreview ? "Music Player" : "");
        String author = safe(music.author()).trim();
        title = safe(title).trim();

        int pipe = title.lastIndexOf(" | ");
        if (pipe > 0 && pipe + 3 < title.length()) {
            String suffix = title.substring(pipe + 3).trim();
            if (author.isBlank() || suffix.equalsIgnoreCase(author)) {
                if (author.isBlank()) {
                    author = suffix;
                }
                title = title.substring(0, pipe).trim();
            }
        }
        return new Metadata(title, author, 0.0F, 0.0F, 0.0F);
    }

    private Metadata fitMetadata(Metadata raw, float maxWidth) {
        float titleSize = 4.75F;
        float authorSize = 4.15F;
        float gap = raw.author().isBlank() ? 0.0F : 2.0F;

        float rawAuthorW = raw.author().isBlank() ? 0.0F : Render2D.textWidth(FontType.BOLD, raw.author(), authorSize);
        float authorBudget = raw.author().isBlank() ? 0.0F : Math.min(Math.max(16.0F, maxWidth * 0.38F), rawAuthorW);
        String author = raw.author().isBlank() ? "" : trimToWidth(raw.author(), FontType.BOLD, authorSize, authorBudget);
        float authorW = author.isBlank() ? 0.0F : Render2D.textWidth(FontType.BOLD, author, authorSize);
        float titleBudget = Math.max(10.0F, maxWidth - authorW - gap);
        String title = trimToWidth(raw.title(), FontType.BOLD, titleSize, titleBudget);
        float titleW = Render2D.textWidth(FontType.BOLD, title, titleSize);
        float used = titleW + (author.isBlank() ? 0.0F : gap + authorW);
        return new Metadata(title, author, titleW, authorW, used);
    }

    private void drawCover(float x, float y, float alpha, boolean active) {
        Identifier image = active ? music.getImage() : null;
        artworkReveal.update();
        artworkReveal.run(image == null ? 0.0D : 1.0D, 0.22D, Easings.EXPO_OUT, true);
        float reveal = clampLocal((float) artworkReveal.get(), 0.0F, 1.0F);

        HudVisualStyle.inset(x, y, COVER, COVER, 3.0F, alpha);
        float fallbackAlpha = alpha * (1.0F - reveal);
        if (fallbackAlpha > 0.002F) {
            String sourceIcon = sourceIcon();
            if (sourceIcon != null) {
                float padding = 2.15F;
                Render2D.image(sourceIcon, x + padding, y + padding,
                        COVER - padding * 2.0F, COVER - padding * 2.0F, 2.1F,
                        ColorUtil.rgba(255, 255, 255, Math.round(238.0F * fallbackAlpha)));
            } else {
                drawFallbackMusicNote(x, y, fallbackAlpha);
            }
        }

        if (image != null && reveal > 0.002F) {
            Identifier previous = music.getPreviousImage();
            float transition = music.getArtworkTransition();
            if (previous != null && transition < 1.0F) {
                Render2D.image(previous.toString(), x, y, COVER, COVER, 3.0F,
                        ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha * reveal)));
            }
            float incoming = previous == null ? reveal : reveal * transition;
            Render2D.image(image.toString(), x, y, COVER, COVER, 3.0F,
                    ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha * incoming)));
        }
    }

    private String sourceIcon() {
        String identity = String.join(" ",
                safe(music.getSourceAppId()), safe(music.getSourceAppName()),
                safe(music.getSourceName()), safe(music.getServiceName()))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        if (identity.equals(cachedSourceIdentity)) {
            return cachedSourceIcon;
        }

        cachedSourceIdentity = identity;
        cachedSourceIcon = resolveSourceIcon(identity);
        return cachedSourceIcon;
    }

    private static String resolveSourceIcon(String source) {
        if (source == null || source.isBlank()) return null;
        if (source.contains("youtubemusic")) return SOURCE_ICON_PREFIX + "youtubemusic.png";
        if (source.contains("youtube")) return SOURCE_ICON_PREFIX + "youtube.png";
        if (source.contains("yandexmusic")) return SOURCE_ICON_PREFIX + "yandexmusic.png";
        if (source.contains("yandexbrowser")) return SOURCE_ICON_PREFIX + "yandexbrowser.png";
        if (source.contains("applemusic")) return SOURCE_ICON_PREFIX + "applemusic.png";
        if (source.contains("vkmusic") || source.contains("vkcom")) return SOURCE_ICON_PREFIX + "vkmusic.png";
        if (source.contains("soundcloud")) return SOURCE_ICON_PREFIX + "soundcloud.png";
        if (source.contains("foobar2000")) return SOURCE_ICON_PREFIX + "foobar2000.png";
        if (source.contains("spotify")) return SOURCE_ICON_PREFIX + "spotify.png";
        if (source.contains("bandcamp")) return SOURCE_ICON_PREFIX + "bandcamp.png";
        if (source.contains("mixcloud")) return SOURCE_ICON_PREFIX + "mixcloud.png";
        if (source.contains("deezer")) return SOURCE_ICON_PREFIX + "deezer.png";
        if (source.contains("tidal")) return SOURCE_ICON_PREFIX + "tidal.png";
        if (source.contains("twitch")) return SOURCE_ICON_PREFIX + "twitch.png";
        if (source.contains("winamp")) return SOURCE_ICON_PREFIX + "winamp.png";
        if (source.contains("vlc")) return SOURCE_ICON_PREFIX + "vlc.png";
        if (source.contains("vivaldi")) return SOURCE_ICON_PREFIX + "vivaldi.png";
        if (source.contains("firefox")) return SOURCE_ICON_PREFIX + "firefox.png";
        if (source.contains("floorp")) return SOURCE_ICON_PREFIX + "floorp.png";
        if (source.contains("brave")) return SOURCE_ICON_PREFIX + "brave.png";
        if (source.contains("chrome")) return SOURCE_ICON_PREFIX + "chrome.png";
        if (source.contains("opera")) return SOURCE_ICON_PREFIX + "opera.png";
        if (source.contains("edge")) return SOURCE_ICON_PREFIX + "edge.png";
        if (source.contains("arc")) return SOURCE_ICON_PREFIX + "arc.png";
        return null;
    }

    private static void drawFallbackMusicNote(float x, float y, float alpha) {
        int color = HudVisualStyle.alpha(HudVisualStyle.MUTED, alpha);
        float stemX = x + 8.05F;
        float top = y + 3.15F;
        Render2D.rect(stemX, top, 1.15F, 6.35F, 0.57F, color);
        Render2D.rect(stemX, top, 3.15F, 1.15F, 0.57F, color);
        Render2D.rect(stemX + 2.0F, top, 1.15F, 5.15F, 0.57F, color);
        Render2D.rect(x + 5.15F, y + 8.35F, 4.0F, 2.85F, 1.42F, color);
        Render2D.rect(x + 7.15F, y + 7.15F, 4.0F, 2.85F, 1.42F, color);
    }

    private void drawMetadata(PlayerLayout layout, float alpha) {
        Metadata meta = layout.metadata();
        float titleSize = 4.75F;
        float authorSize = 4.15F;
        if (!meta.title().isBlank()) {
            Render2D.text(FontType.BOLD, meta.title(), layout.metaX(),
                    centeredTextY(FontType.BOLD, meta.title(), titleSize, layout.centerY()), titleSize,
                    HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha));
        }
        if (!meta.author().isBlank()) {
            float authorX = layout.metaX() + meta.titleWidth() + 2.0F;
            Render2D.text(FontType.BOLD, meta.author(), authorX,
                    centeredTextY(FontType.BOLD, meta.author(), authorSize, layout.centerY()), authorSize,
                    HudVisualStyle.alpha(HudVisualStyle.MUTED, alpha));
        }
    }

    private void updateInteractionAnimations(boolean usable, PlayerLayout layout) {
        float mx = localMouseX(mouseX());
        float my = localMouseY(mouseY());
        boolean hoverReady = usable && Float.isFinite(mx) && Float.isFinite(my);
        animate(previousHover, hoverReady && insideCentered(mx, my, layout.previousX(), layout.slotY(), SLOT, SLOT, BUTTON_HIT, BUTTON_HIT), 0.14D);
        animate(playHover, hoverReady && insideCentered(mx, my, layout.playX(), layout.slotY(), SLOT, SLOT, BUTTON_HIT, BUTTON_HIT), 0.14D);
        animate(nextHover, hoverReady && insideCentered(mx, my, layout.nextX(), layout.slotY(), SLOT, SLOT, BUTTON_HIT, BUTTON_HIT), 0.14D);
        animate(lyricsHover, hoverReady && insideCentered(mx, my, layout.lyricsX(), layout.centerY() - LYRICS_BUTTON_SIZE * 0.5F,
                LYRICS_BUTTON_SIZE, LYRICS_BUTTON_SIZE, BUTTON_HIT, BUTTON_HIT), 0.14D);
        decayPress(previousPress);
        decayPress(playPress);
        decayPress(nextPress);
        decayPress(lyricsPress);
    }

    private void drawPlayPauseTransport(float x, float y, float alpha, boolean playing) {
        playStateAnimation.update();
        playStateAnimation.run(playing ? 1.0D : 0.0D, 0.60D, Easings.EXPO_OUT, true);
        float t = (float) playStateAnimation.get();
        float hover = (float) playHover.get();
        float press = (float) playPress.get();
        float baseScale = pressScale(press) * (1.0F + hover * 0.035F);
        float cx = x + SLOT * 0.5F;
        float cy = y + SLOT * 0.5F;

        float playAlpha = clampLocal(1.0F - t, 0.0F, 1.0F) * alpha * (1.0F - hover * 0.22F);
        if (playAlpha > 0.002F) {
            float scale = baseScale * Math.max(0.02F, 1.0F - t);
            float size = IMAGE_SIZE * scale;
            float dx = cx - size * 0.5F;
            float dy = cy - size * 0.5F;
            Render2D.image(IMAGE_PREFIX + "play.png", dx, dy, size, 0.0F,
                    90.0F * t, cx, cy, ColorUtil.rgba(255, 255, 255, Math.round(255.0F * playAlpha)));
        }

        float pauseAlpha = clampLocal(t, 0.0F, 1.0F) * alpha * (1.0F - hover * 0.22F);
        if (pauseAlpha > 0.002F) {
            float scale = baseScale * Math.max(0.02F, t);
            float size = IMAGE_SIZE * scale;
            float dx = cx - size * 0.5F;
            float dy = cy - size * 0.5F;
            Render2D.image(IMAGE_PREFIX + "pause.png", dx, dy, size, 0.0F,
                    -90.0F + 90.0F * t, cx, cy, ColorUtil.rgba(255, 255, 255, Math.round(255.0F * pauseAlpha)));
        }
    }

    private void drawLyricsButton(float x, float centerY, float alpha) {
        float hover = (float) lyricsHover.get();
        float press = (float) lyricsPress.get();
        float scale = pressScale(press) * (1.0F + hover * 0.04F);
        float icon = 5.8F * scale;
        float ix = x + (LYRICS_BUTTON_SIZE - icon) * 0.5F;
        float iy = centerY - icon * 0.5F;
        float iconAlpha = alpha * (1.0F - hover * 0.30F);
        var graphics = Render2D.currentGraphics();
        if (graphics == null) {
            return;
        }
        float cx = ix + icon * 0.5F;
        float cy = iy + icon * 0.5F;
        graphics.getMatrices().push();
        try {
            graphics.getMatrices().translate(cx, cy, 0.0F);
            graphics.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F));
            graphics.getMatrices().translate(-cx, -cy, 0.0F);
            universalmod.gui.clickgui.ClickGuiMsdfIcons.drawCentered(
                    FontType.DELTA_ICONS, "e", ix, iy, icon, icon, 0.76F,
                    ColorUtil.rgba(255, 255, 255, Math.round(255.0F * iconAlpha)));
        } finally {
            graphics.getMatrices().pop();
        }
    }

    private void drawEqualizer(float x, float centerY, float alpha) {
        updateEqualizer();
        float startX = x + (EQ_SIZE - 7.0F) * 0.5F;
        float top = centerY - 3.5F;
        for (int i = 0; i < equalizerBars.length; i++) {
            float height = equalizerBars[i];
            Render2D.rect(startX + i * 2.0F, top + (7.0F - height) * 0.5F,
                    1.0F, height, 0.5F, HudVisualStyle.accent(alpha));
        }
    }

    private void updateEqualizer() {
        long nowMs = System.currentTimeMillis();
        float dt = Math.min((nowMs - lastEqualizerUpdateMs) / 1000.0F, 0.05F);
        dt = Math.max(0.0F, dt);
        lastEqualizerUpdateMs = nowMs;

        float resolvedBpm = music.getBpm();
        float bpm = resolvedBpm > 0.0F ? resolvedBpm : 120.0F;
        double beat = music.positionSeconds() * bpm / 60.0D;
        for (int i = 0; i < equalizerBars.length; i++) {
            float target;
            if (music.isPlaying()) {
                double shiftedBeat = beat - i * 0.05D;
                float fraction = (float) (shiftedBeat - Math.floor(shiftedBeat));
                float attack = (float) Math.pow(1.0F - fraction, 6.0D) * 4.0F;
                float release = fraction >= 0.5F
                        ? (float) Math.pow(1.0F - (fraction - 0.5F) * 2.0F, 6.0D) * 1.6F
                        : 0.0F;
                float harmonic = (float) (Math.abs(fastSin(shiftedBeat * Math.PI * EQUALIZER_FREQUENCY_A[i]
                        + EQUALIZER_PHASE[i])) * 0.65D
                        + Math.abs(fastSin(shiftedBeat * Math.PI * EQUALIZER_FREQUENCY_B[i]
                        + EQUALIZER_PHASE[i] * 1.7D)) * 0.35D);
                float wobble = 0.5F + 0.5F * (float) fastSin(shiftedBeat * Math.PI * 0.25D + i * 2.1D);
                target = Math.min(10.0F,
                        1.2F + harmonic * (2.2F + 2.6F * wobble)
                                + (attack + release) * (0.7F + 0.3F * wobble));
            } else {
                target = 3.0F;
            }
            float rate = target > equalizerBars[i] ? 45.0F : 9.0F;
            equalizerBars[i] += (target - equalizerBars[i]) * Math.min(1.0F, dt * rate);
        }
    }

    private static double[] createFastSinTable() {
        double[] table = new double[65_536];
        for (int i = 0; i < table.length; i++) {
            table[i] = Math.sin(i * (Math.PI * 2.0D) / 65_536.0D);
        }
        return table;
    }

    private static double fastSin(double radians) {
        int index = ((int) (radians * 10430.378350470453D)) & 65_535;
        return FAST_SIN_TABLE[index];
    }

    private static void drawSeparator(float x, float centerY, float alpha) {
        Render2D.rect(x, centerY - 2.8F, 0.5F, 5.6F, 0.25F,
                ColorUtil.rgba(76, 74, 86, Math.round(118.0F * alpha)));
    }

    private static void animate(SmoothAnimation animation, boolean on, double duration) {
        animation.update();
        animation.run(on ? 1.0D : 0.0D, duration, Easings.EXPO_OUT, true);
    }

    private static void decayPress(SmoothAnimation animation) {
        animation.update();
        animation.run(0.0D, 0.16D, Easings.EXPO_OUT, true);
    }

    private static float pressScale(float press) {
        return 1.0F - Math.max(0.0F, Math.min(1.0F, press)) * 0.12F;
    }

    private static void drawTransport(String texture, float x, float y, float alpha, double hover, double press) {
        float h = Math.max(0.0F, Math.min(1.0F, (float) hover));
        float p = Math.max(0.0F, Math.min(1.0F, (float) press));
        float size = IMAGE_SIZE * (1.0F + 0.08F * h) * (1.0F - 0.12F * p);
        float dx = x + (SLOT - size) * 0.5F;
        float dy = y + (SLOT - size) * 0.5F;
        int value = Math.min(255, Math.round(238.0F + 17.0F * h));
        Render2D.image(texture, dx, dy, size, size, 0.0F,
                ColorUtil.rgba(value, value, Math.min(255, value + 3), Math.round(245.0F * alpha)));
    }

    private static float centeredTextY(FontType font, String text, float size, float centerY) {
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(font, text, size);
        if (bounds == null || bounds.empty()) {
            return centerY - size * 0.5F;
        }
        return centerY - bounds.centerY();
    }

    private float mouseX() {
        return mc.mouse == null || mc.getWindow() == null
                ? Float.NaN
                : (float) Render2D.guiToFixed(mc.mouse.getX() / mc.getWindow().getScaleFactor());
    }

    private float mouseY() {
        return mc.mouse == null || mc.getWindow() == null
                ? Float.NaN
                : (float) Render2D.guiToFixed(mc.mouse.getY() / mc.getWindow().getScaleFactor());
    }

    private static boolean insideCentered(float mx, float my, float x, float y, float visualW, float visualH, float hitW, float hitH) {
        float hitX = x + (visualW - hitW) * 0.5F;
        float hitY = y + (visualH - hitH) * 0.5F;
        return inside(mx, my, hitX, hitY, hitW, hitH);
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx <= x + w && my <= y + h;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String safe = safe(value);
            if (!safe.isBlank()) {
                return safe;
            }
        }
        return "";
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static float clampLocal(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Metadata(String title, String author, float titleWidth, float authorWidth, float usedWidth) {
    }

    private record PlayerLayout(
            float width,
            float centerY,
            float slotY,
            float coverX,
            float coverY,
            float metaX,
            Metadata metadata,
            float separator1X,
            float previousX,
            float playX,
            float nextX,
            float separator2X,
            float lyricsX,
            float separator3X,
            float equalizerX
    ) {
    }
}
