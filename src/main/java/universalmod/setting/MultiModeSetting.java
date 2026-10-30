package universalmod.setting;




public final class MultiModeSetting extends ModeListSetting {
    public MultiModeSetting(String name, String description, String[] modes, String... selectedByDefault) {
        super(name, createSettings(modes, selectedByDefault));
    }

    public MultiModeSetting(String name, String[] modes, String... selectedByDefault) {
        this(name, "", modes, selectedByDefault);
    }

    private static BooleanSetting[] createSettings(String[] modes, String[] selectedByDefault) {
        String[] safeModes = modes == null ? new String[0] : modes;
        BooleanSetting[] settings = new BooleanSetting[safeModes.length];
        for (int i = 0; i < safeModes.length; i++) {
            String mode = safeModes[i];
            boolean selected = false;
            if (selectedByDefault != null) {
                for (String defaultMode : selectedByDefault) {
                    if (mode != null && defaultMode != null && mode.equalsIgnoreCase(defaultMode)) {
                        selected = true;
                        break;
                    }
                }
            }
            settings[i] = new BooleanSetting(mode, selected);
        }
        return settings;
    }
}
