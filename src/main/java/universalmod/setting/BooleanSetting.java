package universalmod.setting;

import universalmod.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import universalmod.render.animation.Animation;
import universalmod.render.animation.Easing;

public class BooleanSetting extends Setting {
    @FunctionalInterface
    public interface ChangeListener {
        void onChanged(BooleanSetting setting, Boolean oldValue, Boolean newValue);
    }

    private final Animation animation = new Animation(Easing.QUINTIC_OUT, 400L);
    private final Animation clickAnimation = new Animation(Easing.QUINTIC_OUT, 400L);
    private boolean clicked;
    private boolean value;
    private final boolean defaultValue;
    private int key = -1;
    private final List<ChangeListener> listeners = new ArrayList<>();

    public BooleanSetting(String name, boolean defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public BooleanSetting(String name, String description, boolean defaultValue) {
        this(name, defaultValue);
        setDescription(description);
    }

    public void reset() {
        this.setValue(this.defaultValue);
    }

    public boolean getDefaultValue() {
        return this.defaultValue;
    }

    public boolean getValue() {
        return this.value;
    }

    @Override
    public String getValueAsString() {
        return Boolean.toString(this.value);
    }

    @Override
    public void setValueFromString(String value) {
        this.setValue(Boolean.parseBoolean(value));
    }

    public void toggle() {
        this.setValue(!this.value);
    }

    public BooleanSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }

    public Animation getAnimation() {
        return this.animation;
    }

    public Animation getClickAnimation() {
        return this.clickAnimation;
    }

    public boolean isClicked() {
        return this.clicked;
    }

    public int getKey() {
        return this.key;
    }

    public void setClicked(boolean clicked) {
        this.clicked = clicked;
    }

    public void setValue(boolean value) {
        if (this.value == value) {
            return;
        }
        boolean oldValue = this.value;
        this.value = value;
        for (ChangeListener listener : List.copyOf(this.listeners)) {
            listener.onChanged(this, oldValue, value);
        }
        fireChanged();
    }

    public BooleanSetting addListener(ChangeListener listener) {
        if (listener != null) {
            this.listeners.add(listener);
        }
        return this;
    }

    public void setKey(int key) {
        this.key = key;
    }
}
