package universalmod.theme;

public class ThemeManager {
    private static ThemeManager instance;
    private final Theme defaultTheme = new Theme("Default", -11890462, -7334914);

    private ThemeManager() {
    }

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }

        return instance;
    }

    public Theme getCurrentTheme() {
        return this.defaultTheme;
    }
}
