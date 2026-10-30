package universalmod.identity;

import java.util.Locale;


public final class StableId {
    private StableId() {
    }

    public static String canonical(String value) {
        if (value == null) {
            return "";
        }
        String input = value.trim();
        StringBuilder out = new StringBuilder(input.length());
        boolean separator = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (separator && out.length() > 0) {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
                separator = false;
            } else {
                separator = out.length() > 0;
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    public static String fromClassSimpleName(Class<?> type) {
        if (type == null) {
            return "";
        }
        String name = type.getSimpleName();
        StringBuilder out = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                char previous = name.charAt(i - 1);
                char next = i + 1 < name.length() ? name.charAt(i + 1) : 0;
                if (Character.isLowerCase(previous) || (next != 0 && Character.isLowerCase(next))) {
                    out.append('_');
                }
            }
            out.append(Character.toLowerCase(c));
        }
        return canonical(out.toString());
    }
}
