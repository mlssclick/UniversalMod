package universalmod.module;

@FunctionalInterface
public interface ModuleStateListener {
    void onChanged(Module module);
}
