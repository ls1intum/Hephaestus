package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabels;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RegisteredItemLabels implements ItemLabels {
    private final ItemSourceRegistry sources;

    public RegisteredItemLabels(ItemSourceRegistry sources) {
        this.sources = sources;
    }

    @Override
    public Map<Long, ItemLabel> labels(long workspaceId, Collection<ItemRef> items) {
        Map<Long, ItemLabel> labels = new HashMap<>();
        for (ItemRef item : items)
            if (item.workspaceId() != workspaceId)
                throw new IllegalArgumentException("Item belongs to another workspace");
        var grouped = items.stream().distinct().collect(Collectors.groupingBy(sources::sourceFor));
        grouped.forEach((source, refs) -> {
            Map<Long, ItemLabel> batch = source.labels(workspaceId, refs);
            var requested = refs.stream().map(ItemRef::itemId).collect(Collectors.toSet());
            if (!requested.containsAll(batch.keySet()))
                throw new IllegalStateException("Source returned an unrequested item label");
            batch.forEach((id, label) -> {
                if (id.longValue() != label.itemId()) throw new IllegalStateException("Label has a different item id");
            });
            labels.putAll(batch);
        });
        return Map.copyOf(labels);
    }
}
