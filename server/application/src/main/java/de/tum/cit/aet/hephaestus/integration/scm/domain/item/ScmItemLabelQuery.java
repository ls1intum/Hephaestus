package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Names only native rows reachable through this workspace's authoritative item index. */
@Component
@RequiredArgsConstructor
public class ScmItemLabelQuery {
    private final ScmItemQueryRepository queries;

    public Map<Long, ItemLabel> labels(long workspaceId, Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, ItemLabel> labels = new HashMap<>();
        for (var row : queries.labels(
                workspaceId,
                List.copyOf(ids),
                ScmItemEvents.REPOSITORY.value(),
                ScmItemEvents.FILE.value(),
                ScmItemEvents.PULL_REQUEST_REVIEW.value(),
                ScmItemEvents.PULL_REQUEST.value(),
                ScmItemEvents.ISSUE.value())) {
            labels.put(
                    row.getId(),
                    new ItemLabel(
                            row.getId(),
                            row.getTitle(),
                            row.getNoun(),
                            row.getNumber(),
                            row.getContainer(),
                            row.getUrl(),
                            IntegrationKind.valueOf(row.getProviderType())));
        }
        return Map.copyOf(labels);
    }
}
