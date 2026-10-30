package dev.redstones.mediaplayerinfo;

import dev.redstones.mediaplayerinfo.impl.DummyMediaPlayerInfo;
import dev.redstones.mediaplayerinfo.impl.win.WindowsMediaPlayerInfo;

import java.util.List;

public interface MediaPlayerInfo {
    MediaPlayerInfo INSTANCE = SystemMediaPlayerInfo.getInstance();

    List<IMediaSession> getMediaSessions();

    default IMediaSession getCurrentMediaSession() {
        List<IMediaSession> sessions = getMediaSessions();
        return sessions == null || sessions.isEmpty() ? null : sessions.get(0);
    }


    default byte[] getCurrentArtworkPng() {
        IMediaSession current = getCurrentMediaSession();
        MediaInfo media = current == null ? null : current.getMedia();
        byte[] artwork = media == null ? null : media.getArtworkPng();
        return artwork == null ? null : artwork.clone();
    }

    default TimelineSnapshot getCurrentTimelineSnapshot() {
        IMediaSession current = getCurrentMediaSession();
        MediaInfo media = current == null ? null : current.getMedia();
        if (media == null) {
            return TimelineSnapshot.unavailable();
        }
        return new TimelineSnapshot(
                Math.max(0L, media.getPositionMillis()),
                Math.max(0L, media.getDurationMillis()),
                media.isPlaying(),
                1.0D,
                true);
    }

    default long getCurrentTimelineStateMillis() {
        TimelineSnapshot snapshot = getCurrentTimelineSnapshot();
        if (!snapshot.available()) {
            return Long.MIN_VALUE;
        }
        long position = Math.max(0L, snapshot.positionMillis());
        return snapshot.playing() ? position : -(position + 1L);
    }

    default void cleanup() {
    }

    record TimelineSnapshot(long positionMillis, long durationMillis, boolean playing, double playbackRate, boolean available) {
        public TimelineSnapshot {
            positionMillis = Math.max(0L, positionMillis);
            durationMillis = Math.max(0L, durationMillis);
            playbackRate = Double.isFinite(playbackRate) && playbackRate > 0.0D ? playbackRate : 1.0D;
        }

        public static TimelineSnapshot unavailable() {
            return new TimelineSnapshot(0L, 0L, false, 1.0D, false);
        }
    }

    final class SystemMediaPlayerInfo {
        private static final MediaPlayerInfo INSTANCE = create();

        private SystemMediaPlayerInfo() {
        }

        public static MediaPlayerInfo getInstance() {
            return INSTANCE;
        }

        private static MediaPlayerInfo create() {
            if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
                return new WindowsMediaPlayerInfo();
            }
            return new DummyMediaPlayerInfo();
        }
    }
}
