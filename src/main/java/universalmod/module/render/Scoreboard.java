package universalmod.module.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.DrawEvent;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import universalmod.module.utility.CustomDonate;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.hud.ElementComponent;
import universalmod.hud.HudStyleOverrides;
import universalmod.render.hud.HudAppearance;
import universalmod.hud.HudManager;
import universalmod.hud.ElementScreen;
import universalmod.hud.editor.ScoreboardEditorModel;
import universalmod.render.Render2D;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.Setting;
import universalmod.setting.NumberSetting;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class Scoreboard extends Module implements ScoreboardEditorModel {
    private static final int VANILLA_LINE_HEIGHT = 9;
    private static final int VANILLA_LEFT_PADDING = 2;
    private static final int VANILLA_RIGHT_EXTENSION = 2;
    private static final int VANILLA_SCREEN_RIGHT_MARGIN = 1;
    private static final int VANILLA_MAX_LINES = 15;
    private static final String VANILLA_SPACER = ": ";
    private static final int VANILLA_TEXT_COLOR = 0xFFFFFFFF;
    private static final Comparator<ScoreboardPlayerScore> VANILLA_ORDER = Comparator
            .comparingInt(ScoreboardPlayerScore::getScore).reversed()
            .thenComparing(ScoreboardPlayerScore::getPlayerName, String.CASE_INSENSITIVE_ORDER);
    private static Scoreboard instance;
    private final BooleanSetting showNumbers = register(new BooleanSetting("Show Numbers", true));
    private final BooleanSetting textShadow = register(new BooleanSetting("Text Shadow", false));
    private final NumberSetting scale = register(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.01));
    private final ModeSetting backgroundMode = register(new ModeSetting("Background", "HUD", "HUD", "Vanilla", "Custom", "None"));
    private final ColorSetting backgroundColor = register(new ColorSetting("Background Color", 1291845632));
    private final ColorSetting titleBackgroundColor = register(new ColorSetting("Title Background Color", 1711276032));
    private final NumberSetting cornerRadius = register(new NumberSetting("Corner Radius", 0.0, 0.0, 12.0, 0.5));
    private final ModeSetting textColorMode = register(new ModeSetting("Text Colors", "Vanilla", "Vanilla", "Custom"));
    private final ColorSetting titleColor = register(new ColorSetting("Title Color", -1));
    private final ColorSetting lineColor = register(new ColorSetting("Line Color", -1));
    private final ColorSetting numberColor = register(new ColorSetting("Number Color", -43691));
    private final ElementComponent drag;
    private final SmoothAnimation renderedScale = new SmoothAnimation();

    public Scoreboard() {
        super("Custom Scoreboard", ModuleCategory.RENDER);
        instance = this;
        backgroundColor.visibleWhen(() -> backgroundMode.is("Custom"));
        titleBackgroundColor.visibleWhen(() -> backgroundMode.is("Custom"));
        cornerRadius.visibleWhen(() -> backgroundMode.is("Vanilla") || backgroundMode.is("Custom"));
        titleColor.visibleWhen(() -> textColorMode.is("Custom"));
        lineColor.visibleWhen(() -> textColorMode.is("Custom"));
        numberColor.visibleWhen(() -> textColorMode.is("Custom") && showNumbers.getValue());
        ElementScreen screen = ElementScreen.current();
        float defaultX = Math.max(0.0F, screen.width() - 154.0F);
        float defaultY = Math.max(0.0F, screen.height() * 0.5F - 70.0F);
        drag = HudManager.getInstance().register("hud.scoreboard", "Custom Scoreboard", defaultX, defaultY)
                .minimumSize(12.0F, 9.0F)
                .screenMargins(0.0F, 0.0F, 0.0F, 0.0F);
        drag.visible(false);
        renderedScale.set(safeScale());
    }

    public static Scoreboard getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public boolean editorHit(float x, float y) {
        return isEnabled() && drag.visible() && drag.dragBounds().contains(x, y, 3.0F);
    }

    public float editorX() {
        return drag.x();
    }

    public float editorY() {
        return drag.y();
    }

    public float editorWidth() {
        return drag.width();
    }

    public float editorHeight() {
        return drag.height();
    }

    public String editorBackgroundMode() {
        return backgroundMode == null ? "HUD" : backgroundMode.getValue();
    }

    public void editorBackgroundMode(String value) {
        if (backgroundMode != null) backgroundMode.setValue(value);
    }

    public List<Setting> editorSettings() {
        return List.of(showNumbers, textShadow, backgroundMode, backgroundColor, titleBackgroundColor, cornerRadius, textColorMode, titleColor, lineColor, numberColor, scale);
    }

    public NumberSetting editorScaleSetting() {
        return scale;
    }

    public float editorScalePercent() {
        return safeScale() * 100.0F;
    }

    public void editorScalePercent(float percent) {
        if (scale != null) scale.setValue(clamp(percent / 100.0F, 0.5F, 2.0F));
    }

    public void renderEditorPopupBackground(DrawContext graphics, float x, float y, float width, float height, float radius) {
        HudAppearance.background(x, y, width, height, radius, 8.0F, 1.0F, 0xEE000000);
    }

    @SubscribeEvent
    private void onHud(DrawEvent event) {
        if (event == null || event.layer() != DrawEvent.Layer.GAME
                || mc.currentScreen instanceof ChatScreen) return;
        render(event.graphics(), false);
    }

    public void render(DrawContext graphics, boolean editorLayer) {
        if (!isEnabled() || NoRender.isActive("Scoreboard") || graphics == null || mc.player == null || mc.world == null || mc.options.hudHidden) {
            drag.visible(false);
            return;
        }
        BoardData data = readBoardData();
        if (data == null && editorLayer) data = editorPlaceholder();
        if (data == null) {
            drag.visible(false);
            return;
        }
        Layout layout = createLayout(data);
        if (layout.width <= 0 || layout.height <= 0) {
            drag.visible(false);
            return;
        }
        float hudResizeFactor = HudStyleOverrides.getInstance().getSizePercent("hud.scoreboard") / 100.0F;
        renderedScale.run(safeScale() * hudResizeFactor, 0.11, Easings.CUBIC_OUT, true);
        renderedScale.update();
        float userScale = clamp(renderedScale.get(), 0.5F * hudResizeFactor, 2.0F * hudResizeFactor);
        float coordinateScale = Math.max(0.0001F, Render2DCoordinateSpace.guiIndependentScale());
        drag.visible(true);
        drag.size(layout.width * userScale / coordinateScale, layout.height * userScale / coordinateScale);
        if (!drag.positionCustomized()) {
            VanillaPlacement vanilla = vanillaPlacement(graphics, layout, userScale, coordinateScale);
            drag.position(vanilla.x, vanilla.y);
        }
        drag.clamp(ElementScreen.current());
        if (editorLayer && drag.moving()) {
            drawVanillaPlacementGhost(graphics, layout, userScale, coordinateScale);
        }
        drawBackgrounds(graphics, layout, userScale, coordinateScale);
        int guiX = Math.round(drag.x() * coordinateScale);
        int guiY = Math.round(drag.y() * coordinateScale);
        graphics.getMatrices().push();
        try {
            graphics.getMatrices().translate(guiX, guiY, 0.0F);
            graphics.getMatrices().scale(userScale, userScale, 1.0F);
            drawBoard(graphics, data, layout);
        } finally {
            graphics.getMatrices().pop();
        }
    }

    private BoardData readBoardData() {
        net.minecraft.scoreboard.Scoreboard board = mc.world.getScoreboard();
        ScoreboardObjective objective = resolveObjective(board, mc.player.getEntityName());
        if (objective == null) return null;
        Collection<ScoreboardPlayerScore> raw = board.getAllPlayerScores(objective);
        List<LineData> lines = raw.stream()
                .filter(score -> score != null && score.getPlayerName() != null && !score.getPlayerName().startsWith("#"))
                .sorted(VANILLA_ORDER)
                .limit(VANILLA_MAX_LINES)
                .map(score -> createLine(board, score))
                .toList();
        if (lines.isEmpty()) return null;
        Text title = objective.getDisplayName();
        return new BoardData(title == null ? Text.empty() : title, lines);
    }

    private ScoreboardObjective resolveObjective(net.minecraft.scoreboard.Scoreboard board, String playerName) {
        ScoreboardObjective objective = board.getObjectiveForSlot(net.minecraft.scoreboard.Scoreboard.SIDEBAR_DISPLAY_SLOT_ID);
        Team team = playerName == null ? null : board.getPlayerTeam(playerName);
        if (team != null && team.getColor() != null) {
            int colorIndex = team.getColor().getColorIndex();
            if (colorIndex >= 0) {
                int slot = net.minecraft.scoreboard.Scoreboard.MIN_SIDEBAR_TEAM_DISPLAY_SLOT_ID + colorIndex;
                ScoreboardObjective teamObjective = board.getObjectiveForSlot(slot);
                if (teamObjective != null) objective = teamObjective;
            }
        }
        return objective;
    }

    private LineData createLine(net.minecraft.scoreboard.Scoreboard board, ScoreboardPlayerScore entry) {
        String playerName = entry.getPlayerName();
        Text name = Team.decorateName(board.getPlayerTeam(playerName), Text.literal(playerName));
        name = CustomDonate.replaceScoreboardLine(name);
        Text score = showNumbers() ? Text.literal(Integer.toString(entry.getScore())) : Text.empty();
        int scoreWidth = showNumbers() ? mc.textRenderer.getWidth(displayText(score)) : 0;
        return new LineData(name == null ? Text.empty() : name, score, scoreWidth);
    }

    private BoardData editorPlaceholder() {
        return new BoardData(Text.literal("Custom Scoreboard"), List.of(new LineData(Text.literal("Drag to move"), Text.empty(), 0)));
    }

    private Layout createLayout(BoardData data) {
        int titleWidth = mc.textRenderer.getWidth(displayText(data.title));
        int titleHeight = titleWidth > 0 ? VANILLA_LINE_HEIGHT : 0;
        int contentWidth = titleWidth;
        int spacer = mc.textRenderer.getWidth(VANILLA_SPACER);
        for (LineData line : data.lines) {
            int width = mc.textRenderer.getWidth(displayText(line.name));
            if (showNumbers() && line.scoreWidth > 0) width += spacer + line.scoreWidth;
            contentWidth = Math.max(contentWidth, width);
        }
        int width = Math.max(1, contentWidth + VANILLA_LEFT_PADDING + VANILLA_RIGHT_EXTENSION);
        int body = data.lines.size() * VANILLA_LINE_HEIGHT;
        int height = Math.max(VANILLA_LINE_HEIGHT, body + titleHeight);
        return new Layout(width, height, contentWidth, titleWidth, body, titleHeight);
    }

    private VanillaPlacement vanillaPlacement(DrawContext graphics, Layout layout, float userScale, float coordinateScale) {
        float nativeWidth = layout.width * userScale;
        float nativeHeight = layout.height * userScale;
        float nativeBodyHeight = layout.bodyHeight * userScale;
        float nativeX = graphics.getScaledWindowWidth() - nativeWidth - VANILLA_SCREEN_RIGHT_MARGIN;
        float nativeBottom = graphics.getScaledWindowHeight() * 0.5F + nativeBodyHeight / 3.0F;
        return new VanillaPlacement(Math.max(0.0F, nativeX / coordinateScale), Math.max(0.0F, (nativeBottom - nativeHeight) / coordinateScale));
    }

    private void drawVanillaPlacementGhost(DrawContext graphics, Layout layout, float userScale, float coordinateScale) {
        VanillaPlacement vanilla = vanillaPlacement(graphics, layout, userScale, coordinateScale);
        float width = layout.width * userScale / coordinateScale;
        float height = layout.height * userScale / coordinateScale;
        Render2D.beginFrame(graphics);
        try {
            Render2D.rect(vanilla.x, vanilla.y, width, height, 0.0F, 0x48FFFFFF);
        } finally {
            Render2D.flush();
        }
    }

    private void drawBoard(DrawContext graphics, BoardData data, Layout layout) {
        boolean shadow = textShadow != null && textShadow.getValue();
        if (layout.titleHeight > 0) {
            int titleX = VANILLA_LEFT_PADDING + (layout.contentWidth - layout.titleWidth) / 2;
            graphics.drawText(mc.textRenderer, displayText(data.title), titleX, 0, displayColor(value(titleColor, 0xFFFFFFFF)), shadow);
        }
        int scoreRight = layout.width;
        for (int i = 0; i < data.lines.size(); i++) {
            LineData line = data.lines.get(i);
            int y = layout.titleHeight + i * VANILLA_LINE_HEIGHT;
            graphics.drawText(mc.textRenderer, displayText(line.name), VANILLA_LEFT_PADDING, y, displayColor(value(lineColor, 0xFFFFFFFF)), shadow);
            if (showNumbers() && line.scoreWidth > 0) {
                graphics.drawText(mc.textRenderer, displayText(line.score), scoreRight - line.scoreWidth, y, displayColor(value(numberColor, 0xFFFF5555)), shadow);
            }
        }
    }

    private void drawBackgrounds(DrawContext graphics, Layout layout, float userScale, float coordinateScale) {
        if (backgroundMode != null && backgroundMode.is("None")) return;
        float x = drag.x();
        float y = drag.y() - 3.0F;
        float width = layout.width * userScale / coordinateScale;
        float height = layout.height * userScale / coordinateScale + 6.0F;
        float headerHeight = layout.titleHeight * userScale / coordinateScale + 3.0F;
        float radius = safeCornerRadius() * userScale / coordinateScale;
        Render2D.beginFrame(graphics);
        try {
            if (backgroundMode == null || backgroundMode.is("HUD")) {
                HudAppearance.background(x, y, width, height, 5.0F, 8.0F, 1.0F, 0xEE000000);
                return;
            }
            int body = backgroundMode.is("Custom") ? value(backgroundColor, 0x4D000000) : mc.options.getTextBackgroundColor(0.3F);
            int head = backgroundMode.is("Custom") ? value(titleBackgroundColor, 0x66000000) : mc.options.getTextBackgroundColor(0.4F);
            Render2D.rect(x, y, width, height, radius, body);
            if (layout.titleHeight > 0 && headerHeight < height) {
                Render2D.rect(x, y, width, headerHeight, radius, head);
            }
        } finally {
            Render2D.flush();
        }
    }

    private Text displayText(Text text) {
        Text safe = text == null ? Text.empty() : text;
        return textColorMode != null && textColorMode.is("Custom") ? Text.literal(safe.getString()) : safe;
    }

    private int displayColor(int custom) {
        return textColorMode != null && textColorMode.is("Custom") ? custom : VANILLA_TEXT_COLOR;
    }

    private boolean showNumbers() {
        return showNumbers == null || showNumbers.getValue();
    }

    private float safeScale() {
        float value = scale == null ? 1.0F : scale.getFloatValue();
        return Float.isFinite(value) ? clamp(value, 0.5F, 2.0F) : 1.0F;
    }

    private float safeCornerRadius() {
        float value = cornerRadius == null ? 0.0F : cornerRadius.getFloatValue();
        return Float.isFinite(value) ? clamp(value, 0.0F, 12.0F) : 0.0F;
    }

    private int value(ColorSetting setting, int fallback) {
        return setting == null || setting.getValue() == null ? fallback : setting.getValue();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onDisable() {
        drag.visible(false);
    }

    private record BoardData(Text title, List<LineData> lines) {
    }

    private record LineData(Text name, Text score, int scoreWidth) {
    }

    private record Layout(int width, int height, int contentWidth, int titleWidth, int bodyHeight, int titleHeight) {
    }

    private record VanillaPlacement(float x, float y) {
    }
}
