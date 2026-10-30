package universalmod.module;


public interface ModuleAccessPolicy {
    ModuleAccessPolicy ALLOW_ALL = new ModuleAccessPolicy() {
        @Override
        public boolean isRuntimeAllowed(Module module) {
            return true;
        }

        @Override
        public boolean isVisible(Module module) {
            return true;
        }
    };

    boolean isRuntimeAllowed(Module module);

    boolean isVisible(Module module);

    
    default void onEnableRequested(Module module) {
    }
}
