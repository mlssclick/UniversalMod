package universalmod.event.type;

import universalmod.event.CancellableEvent;

public final class MouseRotationEvent extends CancellableEvent {
    private float cursorDeltaX, cursorDeltaY;

    public MouseRotationEvent(float x, float y) {
        cursorDeltaX = x;
        cursorDeltaY = y;
    }

    public float getCursorDeltaX() {
        return cursorDeltaX;
    }

    public void setCursorDeltaX(float v) {
        cursorDeltaX = v;
    }

    public float getCursorDeltaY() {
        return cursorDeltaY;
    }

    public void setCursorDeltaY(float v) {
        cursorDeltaY = v;
    }
}
