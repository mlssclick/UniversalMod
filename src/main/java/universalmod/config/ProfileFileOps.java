package universalmod.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;


final class ProfileFileOps {
    private ProfileFileOps() {
    }

    static String sanitize(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                builder.append(c);
            } else if (Character.isWhitespace(c)) {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    static Path profileFile(Path directory, String extension, String name) {
        return directory.resolve(sanitize(name) + extension);
    }

    static String uniqueName(Path directory, String extension, String base) {
        String sanitizedBase = sanitize(base);
        if (sanitizedBase.isBlank()) {
            sanitizedBase = "config";
        }
        String candidate = sanitizedBase;
        int index = 1;
        while (Files.exists(profileFile(directory, extension, candidate))) {
            candidate = sanitizedBase + "_" + index++;
        }
        return candidate;
    }

    static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    static void copyAtomically(Path source, Path target) throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("Profile target has no parent directory: " + target);
        }
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
