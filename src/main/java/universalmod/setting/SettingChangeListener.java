package universalmod.setting;

@FunctionalInterface
public interface SettingChangeListener {
    void onChanged(Setting setting, Object oldValue, Object newValue);
}
