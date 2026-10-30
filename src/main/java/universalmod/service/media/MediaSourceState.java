package universalmod.service.media;

import dev.redstones.mediaplayerinfo.IMediaSession;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;


final class MediaSourceState {
    private final AtomicReference<View> current = new AtomicReference<>(View.empty());

    View observe(IMediaSession session) {
        if (session == null) {
            return current.get();
        }

        String owner = readString(session::getOwner, "");
        String appId = readString(session::getSourceAppId, owner);
        String appName = readString(session::getSourceAppName, appId);
        String sourceName = readString(session::getSourceName, appName);
        String sourceType = readString(session::getSourceType, "app");
        String serviceName = readString(session::getServiceName, "");

        boolean repeatSupported;
        int repeatMode;
        try {
            repeatSupported = session.isRepeatSupported();
            repeatMode = repeatSupported ? session.getCycleType() : -1;
        } catch (RuntimeException | LinkageError unavailable) {
            repeatSupported = false;
            repeatMode = -1;
        }

        View next = new View(appId, appName, sourceName, sourceType, serviceName, repeatSupported, repeatMode);
        current.set(next);
        return next;
    }

    View snapshot() {
        return current.get();
    }

    void updateRepeatMode(int repeatMode) {
        current.updateAndGet(previous -> previous.withRepeatMode(repeatMode));
    }

    void clear() {
        current.set(View.empty());
    }

    private static String readString(Supplier<String> supplier, String fallback) {
        try {
            String value = supplier.get();
            return value == null ? fallback : value;
        } catch (RuntimeException | LinkageError unavailable) {
            return fallback;
        }
    }

    record View(
            String appId,
            String appName,
            String sourceName,
            String sourceType,
            String serviceName,
            boolean repeatSupported,
            int repeatMode
    ) {
        View {
            appId = safe(appId);
            appName = safe(appName);
            sourceName = safe(sourceName);
            sourceType = safe(sourceType);
            serviceName = safe(serviceName);
            if (!repeatSupported) {
                repeatMode = -1;
            }
        }

        static View empty() {
            return new View("", "", "", "", "", false, -1);
        }

        View withRepeatMode(int mode) {
            return new View(appId, appName, sourceName, sourceType, serviceName, repeatSupported,
                    repeatSupported ? mode : -1);
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
