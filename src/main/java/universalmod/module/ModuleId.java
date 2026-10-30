package universalmod.module;

import universalmod.identity.StableId;

import java.util.Objects;


public record ModuleId(String value) {
    public ModuleId {
        value = StableId.canonical(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Module id cannot be empty");
        }
    }

    public static ModuleId forType(Class<? extends Module> type) {
        Objects.requireNonNull(type, "type");
        return new ModuleId(StableId.fromClassSimpleName(type));
    }

    @Override
    public String toString() {
        return value;
    }
}
