package universalmod.storage.json;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;


final class AtomicFileOps {
    private AtomicFileOps() {
    }

    static void replace(Path target, String marker, TempWriter writer) throws IOException {
        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = createSiblingTemp(absoluteTarget, marker);
        Throwable primaryFailure = null;
        try {
            writer.write(temp);
            forceFile(temp);
            replaceAtomically(temp, absoluteTarget);
        } catch (IOException | RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    static void copyReplace(Path source, Path target, String marker) throws IOException {
        replace(target, marker, temp -> Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING));
    }

    private static Path createSiblingTemp(Path target, String marker) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }
        String prefix = target.getFileName() + marker;
        if (prefix.length() < 3) {
            prefix = "json" + marker;
        }
        return Files.createTempFile(parent, prefix, ".tmp");
    }

    private static void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    interface TempWriter {
        void write(Path temp) throws IOException;
    }
}
