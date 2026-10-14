package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PracticeItemOptions {
    private final ItemCatalog items;

    public ItemDescriptor require(ItemKind kind) {
        return items.descriptorFor(kind)
                .orElseThrow(() -> new IllegalArgumentException("Choose a supported item kind."));
    }

    public Collection<ItemDescriptor> all() {
        return items.all();
    }
}
