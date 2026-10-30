package universalmod.util.text;

public final class ValueUnit {
    private final ValueUnit.Kind kind;
    private final String one;
    private final String few;
    private final String many;
    private final String raw;

    private ValueUnit(ValueUnit.Kind kind, String one, String few, String many, String raw) {
        this.kind = kind;
        this.one = one;
        this.few = few;
        this.many = many;
        this.raw = raw;
    }

    public static ValueUnit countable(String one, String few, String many) {
        return new ValueUnit(ValueUnit.Kind.COUNTABLE, one, few, many, null);
    }

    public static ValueUnit abbreviation(String raw) {
        return new ValueUnit(ValueUnit.Kind.ABBREVIATION, null, null, null, raw);
    }

    public String format(double value) {
        if (this.kind == ValueUnit.Kind.ABBREVIATION) {
            return this.raw;
        } else {
            double abs = Math.abs(value);
            double fraction = abs - Math.floor(abs);
            if (fraction > 0.0) {
                return fraction < 0.5 ? this.few : this.many;
            } else {
                int v = (int) abs % 100;
                int v1 = v % 10;
                if (v >= 11 && v <= 19) {
                    return this.many;
                } else if (v1 == 1) {
                    return this.one;
                } else {
                    return v1 >= 2 && v1 <= 4 ? this.few : this.many;
                }
            }
        }
    }

    public static enum Kind {
        COUNTABLE,
        ABBREVIATION;
    }
}
