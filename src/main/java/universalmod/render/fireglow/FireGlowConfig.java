package universalmod.render.fireglow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.util.Mth;
import universalmod.config.ConfigManager;
import universalmod.storage.json.JsonDocumentStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public final class FireGlowConfig {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonDocumentStore STORE = new JsonDocumentStore(GSON);
    private static final Path PATH = ConfigManager.systemDirectory().resolve("fire_glow" + ConfigManager.CONFIG_EXTENSION);

    private static boolean customColor;
    private static int red = 255;
    private static int green = 255;
    private static int blue = 255;

    private FireGlowConfig() {
    }

    public static void load() {
        Path source = PATH;
        if (!Files.isRegularFile(source)) {
            return;
        }

        try {
            JsonObject root = STORE.readObject(source);
            if (root == null) {
                return;
            }
            customColor = root.get("customColor").getAsBoolean();
            red = Mth.clamp(root.get("red").getAsInt(), 0, 255);
            green = Mth.clamp(root.get("green").getAsInt(), 0, 255);
            blue = Mth.clamp(root.get("blue").getAsInt(), 0, 255);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load fire glow config: " + source, exception);
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("customColor", customColor);
        root.addProperty("red", red);
        root.addProperty("green", green);
        root.addProperty("blue", blue);

        try {
            STORE.writeObject(PATH, root);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save fire glow config: " + PATH, exception);
        }
    }

    public static boolean hasCustomColor() {
        return customColor;
    }

    public static void setCustomColor(boolean value) {
        customColor = value;
    }

    public static int getRed() {
        return red;
    }

    public static void setRed(int value) {
        red = Mth.clamp(value, 0, 255);
    }

    public static int getGreen() {
        return green;
    }

    public static void setGreen(int value) {
        green = Mth.clamp(value, 0, 255);
    }

    public static int getBlue() {
        return blue;
    }

    public static void setBlue(int value) {
        blue = Mth.clamp(value, 0, 255);
    }

}
