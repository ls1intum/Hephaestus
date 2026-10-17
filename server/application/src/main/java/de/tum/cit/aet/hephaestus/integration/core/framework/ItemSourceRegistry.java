package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ItemSourceRegistry {
    private final List<ItemSource> sources;

    public ItemSourceRegistry(List<ItemSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public ItemSource sourceFor(ItemRef item) {
        return findSource(item)
                .orElseThrow(
                        () -> new ItemCaptureException(ItemCaptureCode.SOURCE_NOT_READY, "No source supports item"));
    }

    public Optional<ItemSource> findSource(ItemRef item) {
        List<ItemSource> matching =
                sources.stream().filter(s -> s.supports(item)).toList();
        if (matching.size() > 1)
            throw new ItemCaptureException(ItemCaptureCode.SOURCE_NOT_READY, "Multiple sources support item");
        return matching.stream().findFirst();
    }
}
