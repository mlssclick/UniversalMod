package universalmod.service.media;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class WindowsMediaHelper implements AutoCloseable {
    private static final String RESOURCE = "/assets/universalmod/media/helper.ps1";
    private final AtomicBoolean started = new AtomicBoolean();
    private final Object sendLock = new Object();
    private volatile Process process;
    private volatile BufferedWriter writer;
    private volatile String source = "";
    private volatile boolean ready;
    private volatile boolean shuffleSupported;
    private volatile boolean repeatSupported;
    private volatile boolean shuffleEnabled;
    private volatile int repeatMode = -1;
    private volatile float peak = -1F;
    private volatile long lastPeakNanos;
    private volatile long lastStateRequest;
    private volatile boolean closed;

    void observe(String appId) {
        if (closed || !isEnabled()) return;
        String normalized = appId == null ? "" : appId.replace('|', ' ').trim();
        if (normalized.isBlank() || normalized.equalsIgnoreCase("OptMedia")) return;
        if (started.compareAndSet(false, true)) {
            Thread bootstrap = new Thread(this::run, "UniversalMod-WindowsMedia-Helper");
            bootstrap.setDaemon(true);
            bootstrap.start();
        }
        if (!normalized.equals(source)) {
            source = normalized;
            shuffleSupported = false;
            repeatSupported = false;
            repeatMode = -1;
            peak = -1F;
            if (ready) {
                send("meter|" + source);
                send("state|" + source);
            }
        }
        long now = System.currentTimeMillis();
        if (ready && now - lastStateRequest > 4_000L) {
            lastStateRequest = now;
            send("state|" + source);
        }
    }

    boolean shuffleSupported() { return ready && shuffleSupported; }
    boolean repeatSupported() { return ready && repeatSupported; }
    boolean shuffleActive() { return ready && shuffleEnabled; }
    int repeatMode() { return ready ? repeatMode : -1; }
    float peak() {
        return ready && lastPeakNanos > 0L && System.nanoTime() - lastPeakNanos < 1_000_000_000L
                ? peak : -1F;
    }

    void shuffle() { if (shuffleSupported()) send("shuffle|" + source); }
    void repeat() { if (repeatSupported()) send("repeat|" + source); }

    private static boolean isEnabled() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                && !"false".equalsIgnoreCase(System.getProperty("universalmod.music.windowsHelper", "true"));
    }

    private void run() {
        try {
            Path script = extract();
            Process startedProcess = new ProcessBuilder("powershell.exe", "-NoProfile",
                    "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toAbsolutePath().toString())
                    .redirectErrorStream(true).start();
            process = startedProcess;
            writer = new BufferedWriter(new OutputStreamWriter(startedProcess.getOutputStream(), StandardCharsets.UTF_8));
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    startedProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!closed && (line = reader.readLine()) != null) {
                    decode(line.trim());
                }
            }
        } catch (IOException | RuntimeException unavailable) {

        } finally {
            ready = false;
            peak = -1F;
            shuffleSupported = false;
            repeatSupported = false;
            Process p = process;
            if (p != null) p.destroy();
        }
    }

    private void decode(String line) {
        if ("ready".equals(line)) {
            ready = true;
            if (!source.isBlank()) {
                send("meter|" + source);
                send("state|" + source);
            }
        } else if (line.startsWith("peak ")) {
            try {
                float level = Float.parseFloat(line.substring(5).trim());
                if (Float.isFinite(level)) {
                    peak = Math.max(0F, Math.min(1F, level));
                    lastPeakNanos = System.nanoTime();
                }
            } catch (NumberFormatException ignored) { }
        } else if (line.startsWith("state ")) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 5) {
                shuffleEnabled = "True".equalsIgnoreCase(parts[1]);
                try { repeatMode = Integer.parseInt(parts[2]); }
                catch (NumberFormatException ignored) { repeatMode = -1; }
                shuffleSupported = "True".equalsIgnoreCase(parts[3]);
                repeatSupported = "True".equalsIgnoreCase(parts[4]);
            }
        }
    }

    private void send(String command) {
        synchronized (sendLock) {
            BufferedWriter output = writer;
            if (!ready || output == null || closed) return;
            try {
                output.write(command);
                output.newLine();
                output.flush();
            } catch (IOException ignored) { ready = false; }
        }
    }

    private static Path extract() throws IOException {
        byte[] bytes;
        try (InputStream in = WindowsMediaHelper.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("Missing media helper resource");
            bytes = in.readAllBytes();
        }
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 8); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "universalmod-media");
        Files.createDirectories(directory);
        Path file = directory.resolve("media-" + hash + ".ps1");

        if (!Files.isRegularFile(file) || !MessageDigest.isEqual(
                bytes, Files.readAllBytes(file))) {
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        return file;
    }

    @Override
    public void close() {
        closed = true;
        ready = false;
        synchronized (sendLock) {
            try { if (writer != null) writer.close(); } catch (IOException ignored) { }
            writer = null;
        }
        Process p = process;
        if (p != null) p.destroy();
    }
}
