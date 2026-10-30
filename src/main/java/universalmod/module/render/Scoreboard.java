package universalmod.module.render;

import universalmod.lifecycle.Manager;
import universalmod.event.SubscribeEvent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import universalmod.event.type.DrawEvent;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.hud.ElementComponent;
import universalmod.hud.ElementManager;
import universalmod.hud.ElementScreen;
import universalmod.render.Render2D;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.mixin.accessor.InGameHudScoreboardAccessor;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.module.utility.CustomDonate;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.Setting;
import universalmod.setting.SliderSetting;

import java.awt.Color;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@ModuleInformation(
        moduleName = "Custom Scoreboard",
        moduleCategory = ModuleCategory.RENDER
)
public final class Scoreboard extends Module {
    private static final int VANILLA_LINE_HEIGHT = 9;
    private static final int VANILLA_LEFT_PADDING = 2;
    private static final int VANILLA_RIGHT_EXTENSION = 2;
    private static final int VANILLA_SCREEN_RIGHT_MARGIN = 1;
    private static final int VANILLA_MAX_LINES = 15;
    private static final String VANILLA_SPACER_FALLBACK = ": ";
    private static final int VANILLA_TEXT_COLOR = 0xFFFFFFFF;
    private static final Comparator<ScoreboardEntry> FALLBACK_ORDER = Comparator
            .comparingInt(ScoreboardEntry::value).reversed()
            .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private static Scoreboard instance;

    private final BooleanSetting showNumbers = new BooleanSetting("Show Numbers", true);
    private final BooleanSetting textShadow = new BooleanSetting("Text Shadow", false);
    private final SliderSetting scale = new SliderSetting("Scale", 1.0D, 0.5D, 2.0D, 0.01D);
    private final ModeSetting backgroundMode = new ModeSetting("Background", "HUD", "HUD", "Vanilla", "Custom", "None");
    private final ColorSetting backgroundColor = new ColorSetting("Background Color", new Color(0, 0, 0, 77).getRGB()).setVisible(() -> backgroundMode.is("Custom"));
    private final ColorSetting titleBackgroundColor = new ColorSetting("Title Background Color", new Color(0, 0, 0, 102).getRGB()).setVisible(() -> backgroundMode.is("Custom"));
    private final SliderSetting cornerRadius = new SliderSetting("Corner Radius", 0.0D, 0.0D, 12.0D, 0.5D).setVisible(() -> backgroundMode.is("Vanilla") || backgroundMode.is("Custom"));
    private final ModeSetting textColorMode = new ModeSetting("Text Colors", "Vanilla", "Vanilla", "Custom");
    private final ColorSetting titleColor = new ColorSetting("Title Color", 0xFFFFFFFF).setVisible(() -> textColorMode.is("Custom"));
    private final ColorSetting lineColor = new ColorSetting("Line Color", 0xFFFFFFFF).setVisible(() -> textColorMode.is("Custom"));
    private final ColorSetting numberColor = new ColorSetting("Number Color", new Color(255, 85, 85, 255).getRGB()).setVisible(() -> textColorMode.is("Custom") && showNumbers.getValue());

    private final ElementComponent drag;
    private final SmoothAnimation renderedScale = new SmoothAnimation();

    public Scoreboard() {
        instance = this;
        ElementScreen screen = ElementScreen.current();
        float defaultX = Math.max(0.0F, screen.width() - 154.0F);
        float defaultY = Math.max(0.0F, screen.height() * 0.5F - 70.0F);
        drag = ElementManager.getInstance().register("hud.scoreboard", "Custom Scoreboard", defaultX, defaultY)
                .minimumSize(12.0F, 9.0F).screenMargins(0.0F, 0.0F, 0.0F, 0.0F);
        drag.visible(false);
        renderedScale.set(scale.getFloatValue());
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
        return backgroundMode.getValue();
    }

    public void editorBackgroundMode(String value) {
        backgroundMode.setValue(value);
    }

    public List<Setting> editorSettings() {
        return List.of(showNumbers, textShadow, backgroundMode, backgroundColor, titleBackgroundColor, cornerRadius, textColorMode, titleColor, lineColor, numberColor, scale);
    }

    public SliderSetting editorScaleSetting() {
        return scale;
    }

    public float editorScalePercent() {
        return safeScale() * 100.0F;
    }

    public void editorScalePercent(float percent) {
        scale.setValue(clamp(percent / 100.0F, 0.5F, 2.0F));
    }

    public void renderEditorPopupBackground(DrawContext graphics, float x, float y, float width, float height, float radius) {
        Hud.renderHudBackground(x, y, width, height, radius, 8.0F, 1.0F, 0xEE000000);
    }

    @SubscribeEvent
    private void onHud(DrawEvent event) {

        if (event == null || event.getLayer() != DrawEvent.Layer.GAME
                || mc.currentScreen instanceof ChatScreen) {
            return;
        }
        render(event.getGraphics(), false);
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

        renderedScale.run(safeScale(), 0.11, Easings.CUBIC_OUT, true);
        renderedScale.update();
        float userScale = clamp(renderedScale.get(), 0.5F, 2.0F);
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
        graphics.getMatrices().translate(guiX, guiY, 0.0F);
        graphics.getMatrices().scale(userScale, userScale, 1.0F);
        drawBoard(graphics, data, layout);
        graphics.getMatrices().pop();
    }

    private BoardData readBoardData() {
        net.minecraft.scoreboard.Scoreboard board = mc.world.getScoreboard();
        ScoreboardObjective objective = resolveObjective(board, mc.player.getNameForScoreboard());
        if (objective == null) return null;
        NumberFormat format = objective.getNumberFormatOr(StyledNumberFormat.RED);
        Collection<ScoreboardEntry> raw = board.getScoreboardEntries(objective);
        List<LineData> lines = raw.stream().filter(e -> e != null && !e.hidden())
                .sorted(vanillaOrder()).limit(VANILLA_MAX_LINES)
                .map(e -> createLine(board, e, format)).toList();
        if (lines.isEmpty()) return null;
        return new BoardData(objective.getDisplayName() == null ? Text.empty() : objective.getDisplayName(), lines);
    }

    private ScoreboardObjective resolveObjective(net.minecraft.scoreboard.Scoreboard board, String playerName) {
        ScoreboardObjective objective = board.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        Team team = playerName == null ? null : board.getScoreHolderTeam(playerName);
        if (team != null) {
            ScoreboardDisplaySlot slot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
            if (slot != null) {
                ScoreboardObjective teamObjective = board.getObjectiveForSlot(slot);
                if (teamObjective != null) objective = teamObjective;
            }
        }
        return objective;
    }

    private LineData createLine(net.minecraft.scoreboard.Scoreboard board, ScoreboardEntry entry, NumberFormat format) {
        Text name = Team.decorateName(board.getScoreHolderTeam(entry.owner()), entry.name());
        name = CustomDonate.replaceScoreboardLine(name);
        if (name == null) name = Text.empty();
        Text score = showNumbers.getValue() ? entry.formatted(format) : Text.empty();
        int scoreWidth = showNumbers.getValue() ? mc.textRenderer.getWidth(displayText(score)) : 0;
        return new LineData(name, score, scoreWidth);
    }

    private BoardData editorPlaceholder() {
        return new BoardData(Text.literal("Custom Scoreboard"), List.of(new LineData(Text.literal("Drag to move"), Text.empty(), 0)));
    }

    private Layout createLayout(BoardData data) {
        int titleWidth = mc.textRenderer.getWidth(displayText(data.title));
        int titleHeight = titleWidth > 0 ? VANILLA_LINE_HEIGHT : 0;
        int contentWidth = titleWidth;
        int spacer = mc.textRenderer.getWidth(vanillaSpacer());
        for (LineData line : data.lines) {
            int w = mc.textRenderer.getWidth(displayText(line.name));
            if (showNumbers.getValue() && line.scoreWidth > 0) w += spacer + line.scoreWidth;
            contentWidth = Math.max(contentWidth, w);
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
        return new VanillaPlacement(
                Math.max(0.0F, nativeX / coordinateScale),
                Math.max(0.0F, (nativeBottom - nativeHeight) / coordinateScale)
        );
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
        boolean shadow = textShadow.getValue();
        if (layout.titleHeight > 0) {
            int titleX = VANILLA_LEFT_PADDING + (layout.contentWidth - layout.titleWidth) / 2;
            graphics.drawText(mc.textRenderer, displayText(data.title), titleX, 0, displayColor(titleColor.getValue()), shadow);
        }
        int scoreRight = layout.width;
        for (int i = 0; i < data.lines.size(); i++) {
            LineData line = data.lines.get(i);
            int y = layout.titleHeight + i * VANILLA_LINE_HEIGHT;
            graphics.drawText(mc.textRenderer, displayText(line.name), VANILLA_LEFT_PADDING, y, displayColor(lineColor.getValue()), shadow);
            if (showNumbers.getValue() && line.scoreWidth > 0) {
                graphics.drawText(mc.textRenderer, displayText(line.score), scoreRight - line.scoreWidth, y, displayColor(numberColor.getValue()), shadow);
            }
        }
    }

    private void drawBackgrounds(DrawContext graphics, Layout layout, float userScale, float coordinateScale) {
        if (backgroundMode.is("None")) return;
        float x = drag.x();
        float y = drag.y() - 3.0F;
        float width = layout.width * userScale / coordinateScale;
        float height = layout.height * userScale / coordinateScale + 6.0F;
        float headerHeight = layout.titleHeight * userScale / coordinateScale + 3.0F;
        float radius = safeCornerRadius() * userScale / coordinateScale;

        Render2D.beginFrame(graphics);
        try {
            if (backgroundMode.is("HUD")) {
                Hud.renderHudBackground(x, y, width, height, 5.0F, 8.0F, 1.0F, 0xEE000000);
                return;
            }
            int body = backgroundMode.is("Custom") ? backgroundColor.getValue() : mc.options.getTextBackgroundColor(0.3F);
            int head = backgroundMode.is("Custom") ? titleBackgroundColor.getValue() : mc.options.getTextBackgroundColor(0.4F);
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
        return textColorMode.is("Custom") ? Text.literal(safe.getString()) : safe;
    }

    private int displayColor(int custom) {
        return textColorMode.is("Custom") ? custom : VANILLA_TEXT_COLOR;
    }

    private float safeScale() {
        float v = scale.getFloatValue();
        return Float.isFinite(v) ? clamp(v, 0.5F, 2.0F) : 1.0F;
    }

    private float safeCornerRadius() {
        float v = cornerRadius.getFloatValue();
        return Float.isFinite(v) ? clamp(v, 0.0F, 12.0F) : 0.0F;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Comparator<ScoreboardEntry> vanillaOrder() {
        try {
            var c = InGameHudScoreboardAccessor.universalmod$getScoreboardEntryComparator();
            return c == null ? FALLBACK_ORDER : c;
        } catch (Throwable ignored) {
            return FALLBACK_ORDER;
        }
    }

    private static String vanillaSpacer() {
        try {
            String s = InGameHudScoreboardAccessor.universalmod$getScoreboardJoiner();
            return s == null ? VANILLA_SPACER_FALLBACK : s;
        } catch (Throwable ignored) {
            return VANILLA_SPACER_FALLBACK;
        }
    }

    @Override
    public void onDisable() {
        drag.visible(false);
        super.onDisable();
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
