package universalmod.hud.registry;

import universalmod.hud.HudElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public final class HudElementRegistry {
    private final List<HudElementDescriptor> ordered;
    private final Map<HudElementId, HudElementDescriptor> byId;
    private final Map<HudElement, HudElementDescriptor> byElement;

    private HudElementRegistry(List<HudElementDescriptor> descriptors) {
        this.ordered = Collections.unmodifiableList(new ArrayList<>(descriptors));
        EnumMap<HudElementId, HudElementDescriptor> ids = new EnumMap<>(HudElementId.class);
        IdentityHashMap<HudElement, HudElementDescriptor> elements = new IdentityHashMap<>();
        for (HudElementDescriptor descriptor : descriptors) {
            if (ids.putIfAbsent(descriptor.id(), descriptor) != null) {
                throw new IllegalArgumentException("Duplicate HUD element id: " + descriptor.id());
            }
            if (elements.put(descriptor.element(), descriptor) != null) {
                throw new IllegalArgumentException("Duplicate HUD element instance: " + descriptor.element().getClass().getName());
            }
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.byElement = Collections.unmodifiableMap(elements);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<HudElementDescriptor> ordered() {
        return ordered;
    }

    public Optional<HudElementDescriptor> byId(HudElementId id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    public Optional<HudElementDescriptor> byElement(HudElement element) {
        return Optional.ofNullable(element == null ? null : byElement.get(element));
    }

    public static final class Builder {
        private final List<HudElementDescriptor> descriptors = new ArrayList<>();

        public Builder add(HudElementId id, HudElement element) {
            descriptors.add(new HudElementDescriptor(id, element));
            return this;
        }

        public HudElementRegistry build() {
            if (descriptors.isEmpty()) {
                throw new IllegalStateException("HUD element registry cannot be empty");
            }
            HudElementRegistry registry = new HudElementRegistry(descriptors);
            for (HudElementId id : HudElementId.values()) {
                if (!registry.byId.containsKey(id)) {
                    throw new IllegalStateException("HUD registry is missing element: " + id);
                }
            }
            return registry;
        }
    }
}
