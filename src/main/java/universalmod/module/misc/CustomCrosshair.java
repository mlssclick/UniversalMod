package universalmod.module.misc;

import java.awt.Color;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ButtonSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.StringSetting;
import universalmod.render.crosshair.CrosshairCanvas;
import universalmod.render.crosshair.CrosshairCanvasPreset;
import universalmod.render.crosshair.CrosshairRenderState;

@ModuleInformation(
        moduleName = "Custom Crosshair",
        moduleCategory = ModuleCategory.MISC
)
public final class CustomCrosshair extends Module {
    private static CustomCrosshair instance;

    private final StringSetting canvasData = new StringSetting(
            "Canvas Data",
            CrosshairCanvasPreset.DEFAULT.getCanvas().encode(),
            512
    ).setVisible(() -> false);

    private final ButtonSetting editCrosshair = new ButtonSetting("Edit Crosshair", this::openEditor);
    private final BooleanSetting displayInThirdPerson = new BooleanSetting("Display in Third Person", false);
    private final BooleanSetting vanillaBlending = new BooleanSetting("Vanilla Blending", true);
    private final ColorSetting color = new ColorSetting("Color", new Color(255, 255, 255, 255).getRGB())
            .setVisible(() -> !vanillaBlending.getValue());
    private final BooleanSetting dynamicColor = new BooleanSetting("Dynamic Color", false)
            .setVisible(() -> !vanillaBlending.getValue());
    private final ColorSetting neutralColor = new ColorSetting("Neutral", new Color(255, 255, 255, 255).getRGB())
            .setVisible(() -> !vanillaBlending.getValue() && dynamicColor.getValue());
    private final ColorSetting hostileColor = new ColorSetting("Hostile", new Color(255, 64, 64, 255).getRGB())
            .setVisible(() -> !vanillaBlending.getValue() && dynamicColor.getValue());
    private final ColorSetting playersColor = new ColorSetting("Players", new Color(64, 180, 255, 255).getRGB())
            .setVisible(() -> !vanillaBlending.getValue() && dynamicColor.getValue());

    private CrosshairCanvas canvas;
    private String decodedValue;
    private Runnable editorOpener = () -> {
    };

    public CustomCrosshair() {
        instance = this;
        canvasData.setDescription("Stored custom crosshair canvas.");
        editCrosshair.setDescription("Open the custom crosshair editor.");
        displayInThirdPerson.setDescription("Show the custom crosshair outside first person.");
        vanillaBlending.setDescription("Use the vanilla blended crosshair mode.");
        color.setDescription("Crosshair color when vanilla blending is disabled.");
        dynamicColor.setDescription("Use separate colors for neutral, hostile and player targets.");
        neutralColor.setDescription("Color used for neutral living entities.");
        hostileColor.setDescription("Color used for hostile entities.");
        playersColor.setDescription("Color used for players.");
        reloadCanvasIfNeeded();
    }

    public static CustomCrosshair getInstance() {
        return instance;
    }

    public CrosshairCanvas getCanvas() {
        reloadCanvasIfNeeded();
        return canvas == null ? CrosshairCanvasPreset.DEFAULT.getCanvas() : canvas.copy();
    }

    public void setCanvas(CrosshairCanvas canvas) {
        CrosshairCanvas safeCanvas = canvas == null ? CrosshairCanvasPreset.DEFAULT.getCanvas() : canvas.copy();
        this.canvas = safeCanvas;
        this.decodedValue = safeCanvas.encode();
        this.canvasData.setValue(decodedValue);
    }

    public boolean shouldDisplayInThirdPerson() {
        return displayInThirdPerson.getValue();
    }

    public boolean isVanillaBlending() {
        return vanillaBlending.getValue();
    }

    public boolean isDynamicColor() {
        return dynamicColor.getValue();
    }

    public Color getCrosshairColor() {
        return new Color(color.getValue(), true);
    }

    public Color getNeutralColor() {
        return new Color(neutralColor.getValue(), true);
    }

    public Color getHostileColor() {
        return new Color(hostileColor.getValue(), true);
    }

    public Color getPlayersColor() {
        return new Color(playersColor.getValue(), true);
    }

    public CrosshairRenderState renderState() {
        return new CrosshairRenderState(
                isEnabled(), shouldDisplayInThirdPerson(), isVanillaBlending(), isDynamicColor(), getCanvas(),
                getCrosshairColor(), getNeutralColor(), getHostileColor(), getPlayersColor()
        );
    }

    private void reloadCanvasIfNeeded() {
        String encoded = canvasData.getValue();
        if (canvas == null || !encoded.equals(decodedValue)) {
            canvas = CrosshairCanvas.decode(encoded);
            decodedValue = encoded;
        }
    }

    public void setEditorOpener(Runnable opener) {
        editorOpener = opener == null ? () -> {
        } : opener;
    }

    private void openEditor() {
        editorOpener.run();
    }
}
