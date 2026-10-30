package universalmod.setting;

import universalmod.setting.Setting;

import java.util.function.Supplier;

import universalmod.util.text.ValueUnit;

public class NumberSetting extends Setting {
    private double value;
    private final double def, min, max, step;
    private ValueUnit unit;

    public NumberSetting(String n, double d, double min, double max, double step) {
        this(n, (String) null, d, min, max, step);
    }

    public NumberSetting(String n, String description, double d, double min, double max, double step) {
        super(n, description);
        this.value = this.def = d;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public NumberSetting(String n, ValueUnit u, double d, double min, double max, double step) {
        super(n);
        this.unit = u;
        this.value = this.def = d;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getValue() {
        return value;
    }

    public float getFloatValue() {
        return (float) value;
    }

    public float getFloat() {
        return (float) value;
    }

    public void setValue(double v) {
        double nv = Math.max(min, Math.min(max, v));
        double old = value;
        value = nv;
        changed(old, nv);
    }

    @Override
    public void reset() {
        setValue(def);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public ValueUnit getUnit() {
        return unit;
    }

    @Override
    public NumberSetting setVisible(Supplier<Boolean> v) {
        visible = v;
        return this;
    }

    @Override
    public NumberSetting visibleWhen(Supplier<Boolean> v) {
        return setVisible(v);
    }

    public String getValueAsString() {
        return Double.toString(value);
    }

    public void setValueFromString(String v) {
        try {
            setValue(Double.parseDouble(v));
        } catch (Exception ignored) {
        }
    }
}
