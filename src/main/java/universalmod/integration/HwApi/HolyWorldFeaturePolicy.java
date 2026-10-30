package universalmod.integration.HwApi;

import universalmod.module.Module;
import universalmod.module.ModuleAccessPolicy;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class HolyWorldFeaturePolicy implements ModuleAccessPolicy {
    private static final Set<String> CORE_SHELL_FEATURES = Set.of(
            "click_gui",
            "hud"
    );

    private final AtomicReference<HolyWorldFeatureSnapshot> snapshot =
            new AtomicReference<>(HolyWorldFeatureSnapshot.disconnected(0L));
    private volatile Predicate<String> disabledChecker = ignored -> false;
    private volatile Consumer<String> enableRequester = ignored -> {};

    public HolyWorldFeatureSnapshot snapshot() {
        return snapshot.get();
    }

    void publish(HolyWorldFeatureSnapshot next) {
        snapshot.set(Objects.requireNonNull(next, "next"));
    }

    void bind(Predicate<String> disabledChecker, Consumer<String> enableRequester) {
        this.disabledChecker = disabledChecker == null ? ignored -> false : disabledChecker;
        this.enableRequester = enableRequester == null ? ignored -> {} : enableRequester;
    }

    public boolean isControlled(Module module) {
        return module != null;
    }

    public boolean isBlocked(Module module) {
        return module != null && !isAllowed(module);
    }

    public boolean isAllowed(Module module) {
        if (module == null) {
            return false;
        }

        HolyWorldFeatureSnapshot current = snapshot.get();
        if (!current.enforcementActive()) {
            return true;
        }

        String featureId = HolyWorldFeatureId.forModule(module);

        if (CORE_SHELL_FEATURES.contains(featureId)) {
            return true;
        }

        try {
            return !disabledChecker.test(featureId);
        } catch (RuntimeException failure) {
            
            
            return false;
        }
    }

    @Override
    public boolean isRuntimeAllowed(Module module) {
        return isAllowed(module);
    }

    @Override
    public boolean isVisible(Module module) {
        return isAllowed(module);
    }

    @Override
    public void onEnableRequested(Module module) {
        if (module == null) {
            return;
        }
        enableRequester.accept(HolyWorldFeatureId.forModule(module));
    }
}
