package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ItemSourceRegistry {
    private final List<ItemSource> sources;

    public ItemSourceRegistry(List<ItemSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public ItemSource sourceFor(ItemRef item) {
        List<ItemSource> matching = sources.stream()
                .filter(s -> s.kind().equals(item.kind()) && s.supports(item))
                .toList();
        if (matching.size() != 1)
            throw new ItemCaptureException(
                    ItemCaptureCode.SOURCE_NOT_READY,
                    matching.isEmpty() ? "No source supports item" : "Multiple sources support item");
        return matching.getFirst();
    }
}
