package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.integration.core.framework.RegisteredItemLabels;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedbackItemReferences {

    private final FeedbackBasisRepository basis;
    private final RegisteredItemLabels labels;

    public Map<Long, ItemRefDTO> find(long workspaceId, Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        var rows = basis.findItems(workspaceId, itemIds);
        var references = rows.stream()
                .map(row -> new ItemRef(
                        workspaceId,
                        row.getId(),
                        new ItemKind(row.getKind()),
                        row.getSourceId(),
                        row.getParentId(),
                        row.getPath()))
                .toList();
        var itemLabels = labels.labels(workspaceId, references);
        Map<Long, ItemRefDTO> result = new LinkedHashMap<>();
        for (var row : rows) {
            var label = itemLabels.get(row.getId());
            Long parentId = row.getParentId();
            result.put(
                    row.getId(),
                    new ItemRefDTO(
                            Long.toString(row.getId()),
                            row.getKind(),
                            parentId == null ? null : parentId.toString(),
                            label == null ? null : label.integration(),
                            label == null ? row.getKind() : label.noun(),
                            label == null ? null : label.title(),
                            label == null ? null : label.url(),
                            label == null ? null : label.container(),
                            row.getPath(),
                            row.getRemovedAt()));
        }
        return result;
    }
}
