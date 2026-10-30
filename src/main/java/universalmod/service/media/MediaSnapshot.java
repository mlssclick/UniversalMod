package universalmod.service.media;

import dev.redstones.mediaplayerinfo.IMediaSession;
import net.minecraft.util.Identifier;

public record MediaSnapshot(
        long generation,
        IMediaSession session,
        boolean playing,
        long positionMillis,
        long durationMillis,
        boolean preciseTimeline,
        long syncWallClockMillis,
        long syncNanoTime,
        double playbackRate,
        String trackKey,
        String title,
        String artist,
        Identifier artwork,
        int mediaColor,
        String sourceAppId,
        String sourceAppName,
        String sourceName,
        String sourceType,
        String serviceName,
        boolean repeatSupported,
        int repeatMode,
        boolean repeatOneRequested,
        float bpm,
        String lyrics,
        LyricsTimeline.Document lyricsDocument,
        String lyricsStatus,
        boolean lyricsLoading,
        String runtimeError
) {
    public MediaSnapshot {
        trackKey = safe(trackKey);
        title = safe(title);
        artist = safe(artist);
        sourceAppId = safe(sourceAppId);
        sourceAppName = safe(sourceAppName);
        sourceName = safe(sourceName);
        sourceType = safe(sourceType);
        serviceName = safe(serviceName);
        lyrics = safe(lyrics);
        lyricsDocument = lyricsDocument == null ? LyricsTimeline.empty() : lyricsDocument;
        lyricsStatus = safe(lyricsStatus);
        runtimeError = safe(runtimeError);
        positionMillis = Math.max(0L, positionMillis);
        durationMillis = Math.max(0L, durationMillis);
        playbackRate = Double.isFinite(playbackRate) && playbackRate > 0.0D ? playbackRate : 1.0D;
        repeatMode = repeatMode < -1 || repeatMode > 2 ? -1 : repeatMode;
        bpm = Float.isFinite(bpm) && bpm > 0.0F ? bpm : 0.0F;
    }

    public static MediaSnapshot empty(int defaultColor) {
        return new MediaSnapshot(
                0L, null, false, 0L, 0L, false, 0L, 0L, 1.0D,
                "", "", "", null, defaultColor,
                "", "", "", "", "", false, -1, false,
                0.0F, "", LyricsTimeline.empty(), "Waiting for media", false, ""
        );
    }

    public boolean hasSession() {
        return session != null;
    }

    public long interpolatedPositionMillis(long nowMillis, long nowNanos) {
        double current = positionMillis;
        if (playing) {
            if (syncNanoTime > 0L && nowNanos >= syncNanoTime) {
                current += (nowNanos - syncNanoTime) / 1_000_000.0D * playbackRate;
            } else if (syncWallClockMillis > 0L && nowMillis > syncWallClockMillis) {
                current += (nowMillis - syncWallClockMillis) * playbackRate;
            }
        }
        if (durationMillis > 0L) {
            current = Math.min(current, durationMillis);
        }
        return Math.max(0L, Math.round(current));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
