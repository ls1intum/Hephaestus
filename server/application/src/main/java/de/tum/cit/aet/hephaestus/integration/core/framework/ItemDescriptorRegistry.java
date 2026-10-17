package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// Duplicate descriptors must fail: bean order cannot determine a kind's events or delivery capabilities.
@Component
public class ItemDescriptorRegistry {

    private final Map<ItemKind, ItemDescriptor> byKind;

    public ItemDescriptorRegistry(List<ItemDescriptor> descriptors) {
        this.byKind = descriptors.stream()
                .collect(Collectors.toUnmodifiableMap(ItemDescriptor::kind, d -> d, (a, b) -> {
                    throw new IllegalStateException("Duplicate ItemDescriptor for kind="
                            + a.kind()
                            + ": "
                            + a.getClass().getName()
                            + " vs "
                            + b.getClass().getName());
                }));
    }

    public Collection<ItemDescriptor> all() {
        return byKind.values();
    }

    public Optional<ItemDescriptor> descriptorFor(ItemKind kind) {
        return Optional.ofNullable(byKind.get(kind));
    }
}
