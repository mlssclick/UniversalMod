package dev.redstones.mediaplayerinfo.impl.win;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class WindowsMediaPlayerInfo implements MediaPlayerInfo {
    private static final int REQUIRED_NATIVE_ABI = 4;
    private static final boolean NATIVE_READY;
    private static final String NATIVE_ERROR;

    static {
        boolean ready = false;
        String error = "";
        try {
            byte[] dllBytes;
            try (InputStream stream = WindowsMediaPlayerInfo.class.getResourceAsStream("/mediaplayerinfo/natives/win/MediaPlayerInfo.dll")) {
                if (stream == null) {
                    throw new IOException("MediaPlayerInfo.dll is missing from resources");
                }
                dllBytes = stream.readAllBytes();
            }
            String fingerprint = sha256(dllBytes).substring(0, 16);
            Path cacheDirectory = Path.of(System.getProperty("java.io.tmpdir"), "universalmod-mediaplayerinfo");
            Files.createDirectories(cacheDirectory);
            Path dllPath = cacheDirectory.resolve("MediaPlayerInfo-" + fingerprint + ".dll");
            if (!Files.exists(dllPath)) {
                Files.write(dllPath, dllBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            System.load(dllPath.toAbsolutePath().toString());
            int abi = getNativeAbiVersionNative();
            if (abi != REQUIRED_NATIVE_ABI) {
                throw new IllegalStateException("MediaPlayerInfo ABI " + abi + " != " + REQUIRED_NATIVE_ABI);
            }
            ready = true;
            try (var files = Files.list(cacheDirectory)) {
                files.filter(path -> !path.equals(dllPath))
                        .filter(path -> path.getFileName().toString().startsWith("MediaPlayerInfo-"))
                        .filter(path -> path.getFileName().toString().endsWith(".dll"))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (Throwable throwable) {
            error = throwable.getClass().getSimpleName() + ": " + (throwable.getMessage() == null ? "native library unavailable" : throwable.getMessage());
        }
        NATIVE_READY = ready;
        NATIVE_ERROR = error;
    }

    @Override
    public native List<IMediaSession> getMediaSessions();

    @Override
    public IMediaSession getCurrentMediaSession() {
        requireNative();
        return getCurrentMediaSessionNative();
    }

    @Override
    public byte[] getCurrentArtworkPng() {
        requireNative();
        return getCurrentArtworkPngNative();
    }

    @Override
    public TimelineSnapshot getCurrentTimelineSnapshot() {
        requireNative();
        long[] values = getCurrentTimelineSnapshotNative();
        if (values == null || values.length < 4) {
            return TimelineSnapshot.unavailable();
        }
        double rate = values[3] / 1_000_000.0D;
        return new TimelineSnapshot(values[0], values[1], values[2] != 0L, rate, true);
    }

    @Override
    public long getCurrentTimelineStateMillis() {
        TimelineSnapshot snapshot = getCurrentTimelineSnapshot();
        if (!snapshot.available()) {
            return Long.MIN_VALUE;
        }
        long position = snapshot.positionMillis();
        return snapshot.playing() ? position : -(position + 1L);
    }

    public static boolean isNativeReady() {
        return NATIVE_READY;
    }

    public static String getNativeError() {
        return NATIVE_ERROR;
    }

    private static void requireNative() {
        if (!NATIVE_READY) {
            throw new IllegalStateException(NATIVE_ERROR.isBlank() ? "MediaPlayerInfo native library unavailable" : NATIVE_ERROR);
        }
    }

    private static native int getNativeAbiVersionNative();

    private native IMediaSession getCurrentMediaSessionNative();

    private native long[] getCurrentTimelineSnapshotNative();

    private native byte[] getCurrentArtworkPngNative();

    @Override
    public native void cleanup();

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
