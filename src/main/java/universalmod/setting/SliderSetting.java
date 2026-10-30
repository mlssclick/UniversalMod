package universalmod.setting;

import universalmod.setting.Setting;

import java.util.function.Supplier;

import universalmod.util.text.ValueUnit;

public class SliderSetting extends Setting {
    private double value;
    private final double defaultValue;
    private ValueUnit unit;
    private final double min;
    private final double max;
    private final double step;

    public SliderSetting(String name, ValueUnit unit, double defaultValue, double min, double max, double step) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public SliderSetting(String name, double defaultValue, double min, double max, double step) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public String getFormattedValue() {
        return this.unit == null ? Double.toString(value) : this.unit.format(this.value);
    }

    public int getIntValue() {
        return (int) this.value;
    }

    public float getFloatValue() {
        return (float) this.value;
    }

    public float getFloat() {
        return (float) this.value;
    }

    public void setValue(double value) {
        double normalized = Math.max(this.min, Math.min(this.max, value));
        if (Double.compare(this.value, normalized) == 0) return;
        this.value = normalized;
        fireChanged();
    }

    @Override
    public void reset() {
        this.setValue(this.defaultValue);
    }

    public double getDefaultValue() {
        return this.defaultValue;
    }

    public void increment() {
        this.setValue(this.value + this.step);
    }

    public void decrement() {
        this.setValue(this.value - this.step);
    }

    @Override
    public String getValueAsString() {
        return Double.toString(this.value);
    }

    @Override
    public void setValueFromString(String value) {
        try {
            this.setValue(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
        }
    }

    public SliderSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }

    public double getValue() {
        return this.value;
    }

    public ValueUnit getUnit() {
        return this.unit;
    }

    public double getMin() {
        return this.min;
    }

    public double getMax() {
        return this.max;
    }

    public double getStep() {
        return this.step;
    }

    public void setUnit(ValueUnit unit) {
        this.unit = unit;
    }
}
