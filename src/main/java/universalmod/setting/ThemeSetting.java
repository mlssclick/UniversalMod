package universalmod.setting;

import java.util.function.Supplier;

import universalmod.setting.Setting;
import universalmod.theme.Theme;

public class ThemeSetting extends Setting {
    private final Theme[] themes;
    private final Theme def;
    private Theme current;

    public ThemeSetting(Theme d, Theme... t) {
        super("Theme");
        current = def = d;
        themes = t;
    }

    public Theme getValue() {
        return current;
    }

    public Theme[] getThemes() {
        return themes;
    }

    public Theme getCurrent() {
        return current;
    }

    public void setValue(Theme t) {
        Theme old = current;
        current = t;
        changed(old, t);
    }

    @Override
    public void reset() {
        setValue(def);
    }

    public ThemeSetting setVisible(Supplier<Boolean> v) {
        visible = v;
        return this;
    }

    public String getValueAsString() {
        return current == null ? "" : current.name;
    }

    public void setValueFromString(String v) {
        for (Theme t : themes)
            if (t.name.equals(v)) {
                setValue(t);
                break;
            }
    }
}
