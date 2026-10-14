package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Per-kind {@link ItemDescriptor} registry, built from constructor-injected beans.
 *
 * <p>Two descriptors for one kind is fatal rather than last-one-wins: the descriptor decides which
 * events exist and which copy capabilities a kind has, so shadowing one with another would make what practices
 * can be bound to depend on bean ordering.
 */
@Component
public class ItemDescriptorRegistry implements ItemCatalog {

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

    @Override
    public Collection<ItemDescriptor> all() {
        return byKind.values();
    }

    @Override
    public Optional<ItemDescriptor> descriptorFor(ItemKind kind) {
        return Optional.ofNullable(byKind.get(kind));
    }
}
