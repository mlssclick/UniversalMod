package universalmod.service.media;

interface LyricsProvider {
    LyricsTimeline.Document fetch(LyricsFetcher.TrackIdentity identity, long durationSeconds, long deadline);
}
