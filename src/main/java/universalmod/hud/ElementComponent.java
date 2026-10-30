package universalmod.hud;

import universalmod.hud.interaction.DragSession;
import universalmod.hud.interaction.HudElementTransform;

public final class ElementComponent {
    private static final float LERP_SPEED = 0.19F;

    private final String id;
    private final String title;
    private final HudElementTransform transform;
    private final DragSession drag = new DragSession();
    private boolean visible = true;
    private boolean locked;
    private boolean positionCustomized;
    private int order;

    ElementComponent(String id, String title, float defaultX, float defaultY, int order) {
        this.id = id;
        this.title = title;
        this.transform = new HudElementTransform(defaultX, defaultY);
        this.order = order;
    }

    public String id() { return id; }
    public String title() { return title; }
    public float x() { return transform.x(); }
    public float y() { return transform.y(); }
    public float baseX() { return transform.baseX(); }
    public float baseY() { return transform.baseY(); }
    public float targetX() { return transform.targetX(); }
    public float targetY() { return transform.targetY(); }
    public float width() { return transform.width(); }
    public float height() { return transform.height(); }
    public boolean visible() { return visible; }
    public boolean locked() { return locked; }
    public boolean moving() { return drag.moving(); }
    public boolean positionCustomized() { return positionCustomized; }
    public int order() { return order; }
    public float dragTiltDegrees() { return drag.tiltDegrees(); }
    public float dragScale() { return drag.scale(); }
    public float lineAlpha() { return drag.lineAlpha(); }
    public boolean snapVertical() { return drag.snapVertical(); }
    public boolean snapHorizontal() { return drag.snapHorizontal(); }
    public float centerX() { return transform.centerX(); }
    public float centerY() { return transform.centerY(); }
    public ElementBounds bounds() { return transform.bounds(); }
    public ElementBounds dragBounds() { return transform.dragBounds(); }

    public boolean containsBounds(float mouseX, float mouseY, float padding) {
        return transform.containsBounds(mouseX, mouseY, padding);
    }

    public boolean containsDragBounds(float mouseX, float mouseY, float padding) {
        return transform.containsDragBounds(mouseX, mouseY, padding);
    }

    public ElementComponent size(float width, float height) {
        transform.size(width, height);
        return this;
    }

    public ElementComponent minimumSize(float minWidth, float minHeight) {
        transform.minimumSize(minWidth, minHeight);
        return this;
    }

    public ElementComponent hitExpansion(float left, float top, float right, float bottom) {
        transform.hitExpansion(left, top, right, bottom);
        return this;
    }

    public ElementComponent screenMargins(float left, float top, float right, float bottom) {
        transform.screenMargins(left, top, right, bottom);
        return this;
    }

    public ElementComponent position(float x, float y) {
        transform.position(x, y);
        return this;
    }

    public ElementComponent visualOffset(float offsetX, float offsetY) {
        transform.visualOffset(offsetX, offsetY);
        return this;
    }

    public ElementComponent clearVisualOffset() {
        transform.clearVisualOffset();
        return this;
    }

    public float visualOffsetX() { return transform.visualOffsetX(); }
    public float visualOffsetY() { return transform.visualOffsetY(); }

    public ElementComponent visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public ElementComponent locked(boolean locked) {
        this.locked = locked;
        if (locked) {
            drag.cancel();
        }
        return this;
    }

    public ElementComponent resetPosition() {
        positionCustomized = false;
        transform.resetPosition();
        return this;
    }

    void order(int order) {
        this.order = order;
    }

    boolean hit(float mouseX, float mouseY, float padding) {
        return visible && !locked && transform.containsDragBounds(mouseX, mouseY, padding);
    }

    void beginResize() {
        positionCustomized = true;
        drag.cancel();
    }

    void resizeTo(float x, float y, float width, float height) {
        transform.size(width, height).position(x, y);
    }

    void beginMove(float mouseX, float mouseY) {
        positionCustomized = true;
        drag.begin(mouseX, mouseY, x(), y());
    }

    void moveTo(float mouseX, float mouseY, ElementScreen screen) {
        if (!drag.moving()) {
            return;
        }
        DragSession.DragTarget target = drag.update(
                mouseX, mouseY,
                transform.visualOffsetX(), transform.visualOffsetY(),
                transform.width(), transform.height(), screen
        );
        transform.target(
                transform.clampedX(screen, target.x()),
                transform.clampedY(screen, target.y())
        );
        transform.approachTarget(LERP_SPEED);
    }

    void endMove() {
        drag.end();
    }

    void applyState(float x, float y, float width, float height, boolean visible, int order, boolean positionCustomized) {
        transform.position(x, y).size(width, height);
        this.visible = visible;
        this.order = order;
        this.positionCustomized = positionCustomized;
    }

    public void clamp(ElementScreen screen) {
        transform.clamp(screen, drag.moving());
    }

    public void commitClamp(ElementScreen screen) {
        transform.commitClamp(screen);
    }

    void updateAnimation() {
        drag.tickAnimation();
    }
}
