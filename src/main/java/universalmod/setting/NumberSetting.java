package universalmod.setting;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue, SettingType.NUMBER);
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        this.step = Math.max(0.0, step);
        setValue(defaultValue);
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

    public float getFloat() {
        return getValue().floatValue();
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("value")) {
                setValue(object.get("value").getAsDouble());
            }
            return;
        }
        if (element.isJsonPrimitive()) {
            setValue(element.getAsDouble());
        }
    }

    @Override
    protected Double normalize(Double value) {
        double safeValue = value == null ? min : value;
        double clamped = Math.max(min, Math.min(max, safeValue));
        if (step <= 0.0) {
            return clamped;
        }
        double steps = Math.round((clamped - min) / step);
        double snapped = min + steps * step;
        int decimals = stepDecimals();
        if (decimals >= 0) {
            snapped = new BigDecimal(Double.toString(snapped)).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
        }
        return Math.max(min, Math.min(max, snapped));
    }

    private int stepDecimals() {
        if (step <= 0.0) {
            return -1;
        }
        return Math.max(0, new BigDecimal(Double.toString(step)).stripTrailingZeros().scale());
    }
}
