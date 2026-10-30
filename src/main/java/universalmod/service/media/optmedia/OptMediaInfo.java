package universalmod.service.media.optmedia;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class OptMediaInfo extends Structure {
    public byte[] title = new byte[512];
    public byte[] artist = new byte[512];
    public byte[] sourceApp = new byte[256];
    public Pointer albumArtPng;
    public int albumArtSize;
    public byte isPlaying;
    public long durationMs;
    public long positionMs;

    public OptMediaInfo() {
        super(1);
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("title", "artist", "sourceApp", "albumArtPng", "albumArtSize", "isPlaying", "durationMs", "positionMs");
    }

    public String title() { return decode(title); }
    public String artist() { return decode(artist); }
    public String source() { return decode(sourceApp); }
    public boolean playing() { return isPlaying != 0; }

    public byte[] artwork() {
        return albumArtPng != null && albumArtSize > 0 ? albumArtPng.getByteArray(0L, albumArtSize) : null;
    }

    public void clearNativePointers() {
        albumArtPng = null;
        albumArtSize = 0;
    }

    private static String decode(byte[] value) {
        int length = 0;
        while (length < value.length && value[length] != 0) length++;
        return new String(value, 0, length, StandardCharsets.UTF_8);
    }
}
