package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLister;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ItemListerRegistry {
    private final List<ItemLister> listers;

    public ItemListerRegistry(List<ItemLister> listers) {
        this.listers = List.copyOf(listers);
    }

    public ItemLister listerFor(ItemRef container, ItemKind memberKind) {
        List<ItemLister> matching = listers.stream()
                .filter(l -> l.containerKind().equals(container.kind())
                        && l.memberKinds().contains(memberKind)
                        && l.supports(container))
                .toList();
        if (matching.size() != 1)
            throw new ItemCaptureException(
                    ItemCaptureCode.SOURCE_NOT_READY,
                    matching.isEmpty() ? "No lister supports item kind" : "Multiple listers support item kind");
        return matching.getFirst();
    }
}
