package universalmod.setting;

import universalmod.setting.Setting;

import java.util.Arrays;
import java.util.function.Supplier;

import universalmod.theme.Theme;

public class ThemeSetting extends Setting {
    private final Theme[] themes;
    private final Theme defaultValue;
    private Theme current;

    public ThemeSetting(Theme defaultValue, Theme... themes) {
        super("Гавно");
        this.themes = themes == null ? new Theme[0] : themes;
        this.defaultValue = defaultValue;
        this.current = defaultValue;
    }

    public void setValue(Theme theme) {
        if (theme == null || this.current == theme) return;
        Theme old = this.current;
        if (old != null) theme.startAnimation(old.color1, old.color2);
        this.current = theme;
        fireChanged();
    }

    @Override
    public void reset() {
        setValue(defaultValue);
    }

    public Theme getDefaultValue() {
        return defaultValue;
    }

    public Theme getValue() {
        return this.current;
    }

    @Override
    public String getValueAsString() {
        return this.current == null ? "" : this.current.name;
    }

    @Override
    public void setValueFromString(String value) {
        Arrays.stream(this.themes).filter(theme -> theme.name.equals(value)).findFirst().ifPresent(this::setValue);
    }

    public ThemeSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }

    public Theme[] getThemes() {
        return this.themes;
    }

    public Theme getCurrent() {
        return this.current;
    }
}
