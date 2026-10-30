package universalmod.gui.clickgui;

import net.minecraft.client.texture.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import universalmod.render.optimize.UiFrameContext;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;












final class ClickGuiProfileMedia {
    private static final Identifier AVATAR_ID = new Identifier("universalmod", "runtime/clickgui_avatar");
    private static final Identifier COVER_ID = new Identifier("universalmod", "runtime/clickgui_cover");
    private static final long CHECK_INTERVAL_MS = 650L;

    private static NativeImageBackedTexture avatarTexture;
    private static NativeImageBackedTexture coverTexture;
    private static Identifier avatarIdentifier;
    private static Identifier coverIdentifier;
    private static long avatarModified = Long.MIN_VALUE;
    private static long coverModified = Long.MIN_VALUE;
    private static long nextCheckAt;

    private ClickGuiProfileMedia() {}

    static Path directory() {
        return FabricLoader.getInstance().getGameDir().resolve("universalmod").resolve("clickgui");
    }

    static Path avatarPath() {
        return directory().resolve("avatar.png");
    }

    static Path coverPath() {
        return directory().resolve("cover.png");
    }

    static void ensureDirectory() {
        try {
            Files.createDirectories(directory());
        } catch (Throwable ignored) {
        }
    }

    static void tick() {
        long now = UiFrameContext.nowMillis();
        if (now < nextCheckAt) return;
        nextCheckAt = now + CHECK_INTERVAL_MS;
        ensureDirectory();
        avatarIdentifier = refresh(avatarPath(), AVATAR_ID, true);
        coverIdentifier = refresh(coverPath(), COVER_ID, false);
    }

    static Identifier avatar() {
        tick();
        return avatarIdentifier;
    }

    static Identifier cover() {
        tick();
        return coverIdentifier;
    }

    static boolean importDropped(List<Path> paths, boolean avatarTarget) {
        if (paths == null || paths.isEmpty()) return false;
        Path source = paths.stream().filter(ClickGuiProfileMedia::isImage).findFirst().orElse(null);
        if (source == null) return false;
        ensureDirectory();
        Path target = avatarTarget ? avatarPath() : coverPath();
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            if (avatarTarget) avatarModified = Long.MIN_VALUE;
            else coverModified = Long.MIN_VALUE;
            nextCheckAt = 0L;
            tick();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isImage(Path path) {
        if (path == null || !Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static Identifier refresh(Path path, Identifier id, boolean avatar) {
        long modified = modified(path);
        long previous = avatar ? avatarModified : coverModified;
        Identifier current = avatar ? avatarIdentifier : coverIdentifier;
        if (modified == previous) return current;

        if (avatar) avatarModified = modified;
        else coverModified = modified;

        if (modified < 0L) {
            closeTexture(avatar);
            return null;
        }

        NativeImage image = null;
        try (InputStream in = Files.newInputStream(path)) {
            image = NativeImage.read(in);
            NativeImageBackedTexture replacement = new NativeImageBackedTexture(image);
            image = null;
            MinecraftClient minecraft = MinecraftClient.getInstance();
            if (minecraft == null) {
                replacement.close();
                return null;
            }
            closeTexture(avatar);
            minecraft.getTextureManager().registerTexture(id, replacement);
            if (avatar) avatarTexture = replacement;
            else coverTexture = replacement;
            return id;
        } catch (Throwable ignored) {
            if (image != null) {
                try { image.close(); } catch (Throwable ignoredClose) {}
            }
            return null;
        }
    }

    private static long modified(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static void closeTexture(boolean avatar) {
        NativeImageBackedTexture texture = avatar ? avatarTexture : coverTexture;
        if (texture != null) {
            try { texture.close(); } catch (Throwable ignored) {}
        }
        if (avatar) {
            avatarTexture = null;
            avatarIdentifier = null;
        } else {
            coverTexture = null;
            coverIdentifier = null;
        }
    }
}
