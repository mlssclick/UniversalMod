package universalmod.setting;

import universalmod.util.text.ValueUnit;




public final class NumberSetting extends SliderSetting {
    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, defaultValue, min, max, step);
        setDescription(description);
    }

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        super(name, defaultValue, min, max, step);
    }

    public NumberSetting(String name, ValueUnit unit, double defaultValue, double min, double max, double step) {
        super(name, unit, defaultValue, min, max, step);
    }

    @Override
    public NumberSetting setVisible(java.util.function.Supplier<Boolean> visible) {
        super.setVisible(visible);
        return this;
    }
}
