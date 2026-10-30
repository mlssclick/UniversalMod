package universalmod.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import universalmod.config.ConfigManager;
import universalmod.hud.interaction.HudResizeSession;
import universalmod.hud.interaction.HudResizeCursor;
import universalmod.storage.json.JsonDocumentStore;
import universalmod.render.hud.LoadingVisualGuard;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2D;
import universalmod.render.optimize.UiFrameContext;
import universalmod.module.render.Hud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ElementManager {
    private static final ElementManager INSTANCE = new ElementManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final JsonDocumentStore STORE = new JsonDocumentStore(GSON);
    private static final String FILE_NAME = "drags" + ConfigManager.CONFIG_EXTENSION;
    private static final float HIT_PADDING = 3.0f;

    private final Map<String, ElementComponent> components = new LinkedHashMap<>();
    private final Map<String, SavedElement> pendingStates = new LinkedHashMap<>();
    private final List<ElementComponent> sortedScratch = new ArrayList<>();
    private long orderRevision;
    private long sortedOrderRevision = Long.MIN_VALUE;
    private List<ElementComponent> componentsView = List.of();
    private long lastFrameId = Long.MIN_VALUE;
    private float lastFrameWidth = Float.NaN;
    private float lastFrameHeight = Float.NaN;
    private float lastFrameScale = Float.NaN;
    private ElementComponent active;
    private final HudResizeSession resizing = new HudResizeSession();
    private ElementScreen screen = new ElementScreen(1.0f, 1.0f, 1.0f);
    private int nextOrder;
    private boolean loaded;

    private ElementManager() {
    }

    public static ElementManager getInstance() {
        return INSTANCE;
    }

    public ElementComponent register(String id, String title, float defaultX, float defaultY) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Element id cannot be empty");
        }

        String key = normalize(id);
        ElementComponent existing = components.get(key);
        if (existing != null) {
            return existing;
        }

        ElementComponent component = new ElementComponent(key, title == null || title.isBlank() ? key : title, defaultX, defaultY, nextOrder++);
        SavedElement saved = pendingStates.get(key);
        if (saved != null) {
            component.applyState(saved.x, saved.y, saved.width, saved.height, saved.visible, saved.order, saved.positionCustomized);
            nextOrder = Math.max(nextOrder, saved.order + 1);
        }
        components.put(key, component);
        componentsView = List.copyOf(components.values());
        orderRevision++;
        return component;
    }

    public List<ElementComponent> components() {
        return componentsView;
    }

    public void frame(ElementScreen screen) {
        if (screen != null && screen.valid()) {
            this.screen = screen;
        }

        if (UiFrameContext.active()) {
            long frameId = UiFrameContext.frameId();
            if (frameId == lastFrameId
                    && this.screen.width() == lastFrameWidth
                    && this.screen.height() == lastFrameHeight
                    && this.screen.coordinateScale() == lastFrameScale) {
                return;
            }
            lastFrameId = frameId;
            lastFrameWidth = this.screen.width();
            lastFrameHeight = this.screen.height();
            lastFrameScale = this.screen.coordinateScale();
        }

        for (ElementComponent component : componentsView) {
            component.clamp(this.screen);
            component.updateAnimation();
        }
    }

    public boolean handleResizeMouseClicked(MouseButtonEvent event) {
        if (event == null || event.button() != 0 || !canEdit()) return false;
        frame(ElementScreen.current());
        float x = (float) event.x(), y = (float) event.y();
        ElementComponent hovered = topmostResizeAt(x, y);
        if (hovered == null || !resizing.begin(hovered.bounds(), x, y,
                HudStyleOverrides.getInstance().getSizePercent(hovered.id()))) return false;
        active = hovered;
        active.beginResize();
        active.order(nextOrder++);
        orderRevision++;
        return true;
    }

    public boolean resizing() { return resizing.active(); }

    private void updateActive(float x, float y) {
        if (resizing.active()) {
            HudResizeSession.Result result = resizing.update(x, y, screen);
            if (result != null) {
                HudStyleOverrides.getInstance().previewSizePercent(active.id(), result.sizePercent());
                active.resizeTo(result.x() - active.visualOffsetX(),
                        result.y() - active.visualOffsetY(), result.width(), result.height());
                active.commitClamp(screen);
            }
            return;
        }
        active.moveTo(x, y, screen);
    }

    private void finishActive() {
        if (resizing.active()) {
            HudStyleOverrides.getInstance().commitSizePercent(active.id());
            resizing.end();
        }
        active.endMove();
        active.commitClamp(screen);
        active = null;
        save();
    }

    private HudResizeSession.Handle resizeHandleAt(float x, float y) {
        ElementComponent hit = topmostResizeAt(x, y);
        return hit == null ? HudResizeSession.Handle.NONE : HudResizeSession.hit(hit.bounds(), x, y);
    }

    private ElementComponent topmostResizeAt(float x, float y) {
        ElementComponent candidate = null;
        int order = Integer.MIN_VALUE;
        for (ElementComponent component : componentsView) {
            if (!component.visible() || component.locked() || component.order() < order
                    || !component.id().startsWith("hud.")) continue;
            if (HudResizeSession.hit(component.bounds(), x, y).active()) {
                candidate = component;
                order = component.order();
            }
        }
        return candidate;
    }

    public boolean handleMouseClicked(MouseButtonEvent event) {
        if (event == null || event.button() != 0 || !canEdit()) {
            return false;
        }

        if (handleResizeMouseClicked(event)) return true;
        frame(ElementScreen.current());
        ElementComponent hovered = topmostAt((float) event.x(), (float) event.y());
        if (hovered == null) {
            return false;
        }

        active = hovered;
        active.order(nextOrder++);
        orderRevision++;
        active.beginMove((float) event.x(), (float) event.y());
        return true;
    }

    public boolean handleMouseDragged(MouseButtonEvent event) {
        if (event == null || active == null || !canEdit()) {
            return false;
        }

        updateActive((float) event.x(), (float) event.y());
        return true;
    }

    public void updateActiveElementFromMouse() {
        if (active == null || !canEdit()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            return;
        }

        ElementScreen currentScreen = ElementScreen.current();
        frame(currentScreen);
        float scale = Math.max(0.0001f, currentScreen.coordinateScale());
        float mouseX = (float) client.mouseHandler.getScaledXPos(client.getWindow()) / scale;
        float mouseY = (float) client.mouseHandler.getScaledYPos(client.getWindow()) / scale;
        updateActive(mouseX, mouseY);
    }

    public boolean handleMouseReleased(MouseButtonEvent event) {
        if (active == null) {
            return false;
        }

        boolean editing = canEdit();
        finishActive();
        return editing;
    }

    public void cancelActiveElement() {
        HudResizeCursor.reset();
        if (active == null) return;
        finishActive();
    }

    public boolean canEditCurrentScreen() {
        return canEdit();
    }

    public void renderEditorOverlay(GuiGraphics graphics, ElementScreen screen) {
        if (!canEdit()) { HudResizeCursor.reset(); return; }
        frame(screen);
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.mouseHandler != null && client.getWindow() != null) {
            float scale = Math.max(0.0001F, this.screen.coordinateScale());
            float mx = (float) client.mouseHandler.getScaledXPos(client.getWindow()) / scale;
            float my = (float) client.mouseHandler.getScaledYPos(client.getWindow()) / scale;
            HudResizeSession.Handle handle = resizing.active() ? resizing.handle() : resizeHandleAt(mx, my);
            HudResizeCursor.update(client.getWindow().handle(), handle);
        }
        for (ElementComponent component : sortedComponents()) {
            if (!component.visible()) {
                continue;
            }
            renderComponentOverlay(component);
        }
    }

    public void load() {
        loaded = true;
        Path file = configFile();
        if (!Files.exists(file)) {
            return;
        }

        try {
            JsonObject root = STORE.readObject(file);
            if (root == null || !root.has("components") || !root.get("components").isJsonObject()) {
                return;
            }

            JsonObject componentsObject = root.getAsJsonObject("components");
            for (String id : componentsObject.keySet()) {
                SavedElement saved = GSON.fromJson(componentsObject.get(id), SavedElement.class);
                if (saved == null || !saved.valid()) {
                    continue;
                }

                String key = normalize(id);
                pendingStates.put(key, saved);
                ElementComponent component = components.get(key);
                if (component != null) {
                    component.applyState(saved.x, saved.y, saved.width, saved.height, saved.visible, saved.order, saved.positionCustomized);
                    nextOrder = Math.max(nextOrder, saved.order + 1);
                }
            }
            orderRevision++;
        } catch (IOException | RuntimeException exception) {
        }
    }

    public void save() {
        if (!loaded) {
            return;
        }

        Path file = configFile();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonObject componentsObject = new JsonObject();
            for (ElementComponent component : components.values()) {
                componentsObject.add(component.id(), GSON.toJsonTree(SavedElement.from(component)));
            }
            root.add("components", componentsObject);
            STORE.writeObject(file, root);
        } catch (IOException exception) {
        }
    }

    private ElementComponent topmostAt(float mouseX, float mouseY) {
        ElementComponent topmost = null;
        int topmostOrder = Integer.MIN_VALUE;
        for (ElementComponent component : components.values()) {
            if (!component.visible()) {
                continue;
            }
            int order = component.order();
            if (order > topmostOrder && component.hit(mouseX, mouseY, HIT_PADDING)) {
                topmost = component;
                topmostOrder = order;
            }
        }
        return topmost;
    }

    private List<ElementComponent> sortedComponents() {
        if (sortedOrderRevision != orderRevision) {
            sortedScratch.clear();
            sortedScratch.addAll(components.values());
            sortedScratch.sort(Comparator.comparingInt(ElementComponent::order));
            sortedOrderRevision = orderRevision;
        }
        return sortedScratch;
    }

    private boolean canEdit() {
        Minecraft client = Minecraft.getInstance();
        Hud hud = Hud.getInstance();
        return hud != null
                && hud.isEnabled()
                && client != null
                && client.player != null
                && client.level != null
                && client.screen instanceof ChatScreen
                && !LoadingVisualGuard.shouldSuppressHud(client);
    }

    private void renderComponentOverlay(ElementComponent component) {
        float alpha = component.lineAlpha();
        if (alpha <= 0.0f) {
            return;
        }
        int color = ColorUtil.rgba(255, 255, 255, Math.round(180.0f * alpha));
        if (component.snapVertical()) {
            Render2D.rect(component.centerX() - 0.5f, 0.0f, 1.0f, screen.height(), color);
        }
        if (component.snapHorizontal()) {
            Render2D.rect(0.0f, component.centerY() - 0.5f, screen.width(), 1.0f, color);
        }
    }

    private Path configFile() {
        return ConfigManager.systemDirectory().resolve(FILE_NAME);
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase().replace(' ', '_');
    }

    private static final class SavedElement {
        private float x;
        private float y;
        private float width;
        private float height;
        private boolean visible = true;
        private int order;
        private boolean positionCustomized;

        private static SavedElement from(ElementComponent component) {
            SavedElement saved = new SavedElement();
            saved.x = component.targetX();
            saved.y = component.targetY();
            saved.width = component.width();
            saved.height = component.height();
            saved.visible = component.visible();
            saved.order = component.order();
            saved.positionCustomized = component.positionCustomized();
            return saved;
        }

        private boolean valid() {
            return Float.isFinite(x)
                    && Float.isFinite(y)
                    && Float.isFinite(width)
                    && Float.isFinite(height)
                    && width > 0.0f
                    && height > 0.0f;
        }
    }
}
