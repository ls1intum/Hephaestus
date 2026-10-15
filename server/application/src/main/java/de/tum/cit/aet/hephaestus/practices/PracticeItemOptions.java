package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PracticeItemOptions {
    private final ItemDescriptorRegistry items;

    public ItemDescriptor require(ItemKind kind) {
        return items.descriptorFor(kind)
                .orElseThrow(() -> new IllegalArgumentException("Choose a supported item kind."));
    }

    public Collection<ItemDescriptor> all() {
        return items.all();
    }
}
