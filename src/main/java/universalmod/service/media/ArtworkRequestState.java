package universalmod.service.media;

import java.util.Arrays;
import java.util.Objects;


final class ArtworkRequestState {
    enum DecisionType { NONE, CLEAR, APPLY }

    record Decision(DecisionType type, byte[] bytes) {
        static Decision none() { return new Decision(DecisionType.NONE, null); }
        static Decision clear() { return new Decision(DecisionType.CLEAR, null); }
        static Decision apply(byte[] bytes) { return new Decision(DecisionType.APPLY, bytes); }
    }

    private volatile String artworkTrackKey = "";
    private volatile int artworkHash;
    private volatile byte[] lastArtworkBytes;

    Decision observe(String trackKey, byte[] bytes) {
        String key = trackKey == null ? "" : trackKey;
        int hash = bytes == null || bytes.length == 0 ? 0 : Arrays.hashCode(bytes);

        if (hash == 0 && Objects.equals(key, artworkTrackKey) && artworkHash != 0) {
            return Decision.none();
        }
        if (hash == artworkHash && Objects.equals(key, artworkTrackKey)) {
            return Decision.none();
        }

        artworkTrackKey = key;
        artworkHash = hash;
        if (hash == 0) {
            lastArtworkBytes = null;
            return Decision.clear();
        }

        lastArtworkBytes = bytes.clone();
        return Decision.apply(lastArtworkBytes.clone());
    }

    byte[] cachedBytesFor(String trackKey) {
        if (!Objects.equals(artworkTrackKey, trackKey) || lastArtworkBytes == null) {
            return null;
        }
        return lastArtworkBytes.clone();
    }

    boolean hasArtworkState() {
        return lastArtworkBytes != null || artworkHash != 0 || !artworkTrackKey.isBlank();
    }

    void resetTrackState() {
        artworkTrackKey = "";
        artworkHash = 0;
        lastArtworkBytes = null;
    }
}
