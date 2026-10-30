package universalmod.setting;

@FunctionalInterface
public interface SettingChangeListener<T> {
    void onChanged(Setting setting, T oldValue, T newValue);
}
