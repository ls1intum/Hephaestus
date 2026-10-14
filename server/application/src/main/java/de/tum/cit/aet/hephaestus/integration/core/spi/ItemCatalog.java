package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Collection;
import java.util.Optional;

public interface ItemCatalog {
    Collection<ItemDescriptor> all();

    Optional<ItemDescriptor> descriptorFor(ItemKind kind);
}
