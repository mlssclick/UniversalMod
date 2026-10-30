package universalmod.service.media.optmedia;

import com.sun.jna.Native;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

final class OptMediaBridge {
    private static final String RESOURCE = "/assets/universalmod/natives/OptMedia.dll";
    private static final Object LOCK = new Object();
    private static volatile OptMediaNativeLibrary library;
    private static volatile boolean failed;
    private static volatile String failure = "";
    private static volatile boolean shutdownHookInstalled;

    private OptMediaBridge() {}

    static OptMediaNativeLibrary library() {
        OptMediaNativeLibrary current = library;
        if (current != null) return current;
        if (failed) throw new IllegalStateException(failure.isBlank() ? "OptMedia is unavailable" : failure);
        synchronized (LOCK) {
            if (library != null) return library;
            if (failed) throw new IllegalStateException(failure.isBlank() ? "OptMedia is unavailable" : failure);
            if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
                failed = true;
                failure = "OptMedia.dll is Windows-only";
                throw new IllegalStateException(failure);
            }
            try {
                Path dll = extractNative();
                Map<String, Object> options = new HashMap<>();
                options.put("allow-objects", Boolean.TRUE);
                library = Native.load(dll.toAbsolutePath().toString(), OptMediaNativeLibrary.class, options);
                installShutdownHook();
                return library;
            } catch (RuntimeException | LinkageError nativeFailure) {
                markFailed(nativeFailure);
                throw nativeFailure;
            } catch (IOException | GeneralSecurityException extractionFailure) {
                markFailed(extractionFailure);
                throw new IllegalStateException(failure, extractionFailure);
            }
        }
    }

    static boolean available() {
        try {
            return library() != null;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    static String failure() { return failure; }

    private static void installShutdownHook() {
        if (shutdownHookInstalled) return;
        synchronized (LOCK) {
            if (shutdownHookInstalled) return;
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(OptMediaBridge::cleanup, "OptMedia-cleanup"));
                shutdownHookInstalled = true;
            } catch (IllegalStateException jvmShuttingDown) {
                
            }
        }
    }

    static void cleanup() {
        synchronized (LOCK) {
            OptMediaNativeLibrary current = library;
            library = null;
            if (current != null) {
                try {
                    current.Deinitialize();
                } catch (RuntimeException | LinkageError cleanupFailure) {
            
        }
            }
        }
    }

    private static void markFailed(Throwable cause) {
        failed = true;
        failure = "Failed to load OptMedia.dll: " + cause;
    }

    private static Path extractNative() throws IOException, GeneralSecurityException {
        byte[] bytes;
        try (InputStream input = OptMediaBridge.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("OptMedia.dll not found at " + RESOURCE);
            }
            bytes = input.readAllBytes();
        }

        byte[] expectedDigest = MessageDigest.getInstance("SHA-256").digest(bytes);
        String hash = HexFormat.of().formatHex(expectedDigest, 0, 8);
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "universalmod-optmedia");
        Files.createDirectories(directory);
        Path target = directory.resolve("OptMedia-" + hash + ".dll");

        if (!matchesDigest(target, expectedDigest)) {
            replaceNative(target, bytes);
            if (!matchesDigest(target, expectedDigest)) {
                Files.deleteIfExists(target);
                throw new IOException("Extracted OptMedia.dll failed SHA-256 verification");
            }
        }

        target.toFile().deleteOnExit();
        return target;
    }

    private static void replaceNative(Path target, byte[] bytes) throws IOException {
        Path directory = target.getParent();
        Path temp = Files.createTempFile(directory, "OptMedia-", ".tmp");
        try {
            Files.write(temp, bytes);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean matchesDigest(Path path, byte[] expectedDigest) throws IOException, GeneralSecurityException {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return MessageDigest.isEqual(expectedDigest, digest.digest());
    }

}
