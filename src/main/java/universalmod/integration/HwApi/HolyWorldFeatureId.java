package universalmod.integration.HwApi;

import universalmod.module.Module;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;


public final class HolyWorldFeatureId {
    private HolyWorldFeatureId() {
    }

    public static String forModule(Module module) {
        return normalize(Objects.requireNonNull(module, "module").getIdentifier());
    }

    public static Set<String> forModules(Collection<? extends Module> modules) {
        if (modules == null || modules.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Module module : modules) {
            if (module == null) {
                continue;
            }
            String id = forModule(module);
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return Set.copyOf(ids);
    }

    public static boolean matches(Module module, String networkId) {
        return module != null && forModule(module).equals(normalize(networkId));
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
