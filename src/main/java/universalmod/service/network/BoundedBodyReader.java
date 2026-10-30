package universalmod.service.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;


final class BoundedBodyReader {
    private static final int BUFFER_SIZE = 8 * 1024;

    private BoundedBodyReader() {
    }

    static byte[] read(InputStream raw, long declaredLength, int cap, URI uri) throws IOException {
        if (cap <= 0) {
            throw new IllegalArgumentException("cap must be positive");
        }
        if (raw == null) {
            return new byte[0];
        }

        try (InputStream input = raw) {
            if (declaredLength > cap) {
                throw tooLarge(cap, uri);
            }
            int initialCapacity = declaredLength >= 0L
                    ? (int) Math.min(declaredLength, cap)
                    : Math.min(cap, BUFFER_SIZE);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity)) {
                byte[] buffer = new byte[Math.min(BUFFER_SIZE, cap)];
                int total = 0;
                while (true) {
                    int remaining = cap - total;
                    int requested = Math.min(buffer.length, remaining + 1);
                    int read = input.read(buffer, 0, requested);
                    if (read == -1) {
                        return output.toByteArray();
                    }
                    if (read > remaining) {
                        throw tooLarge(cap, uri);
                    }
                    output.write(buffer, 0, read);
                    total += read;
                }
            }
        }
    }

    private static IOException tooLarge(int cap, URI uri) {
        return new IOException("HTTP response exceeds " + cap + " bytes: " + uri);
    }
}
