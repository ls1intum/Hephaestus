package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Registers mirrored work as members of an authorized repository item. Registration only indexes the work: it records
 * no event and pins no version, so half-synced work is indexed and no review occasion is invented.
 */
@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
class ScmWorkItemIndex {
    private final ItemIndexService writer;
    private final ScmItemQueryRepository queries;

    /** Indexes the repository's live issues and pull requests that have no active item in its workspace. */
    void reconcile(ItemRef repository) {
        long repositoryId = Objects.requireNonNull(repository.sourceId());
        register(
                repository,
                ScmItemEvents.PULL_REQUEST,
                unindexed(repository, repositoryId, ScmItemEvents.PULL_REQUEST));
        register(repository, ScmItemEvents.ISSUE, unindexed(repository, repositoryId, ScmItemEvents.ISSUE));
    }

    List<ItemRef> register(ItemRef repository, ItemKind kind, List<ScmItemQueryRepository.WorkRow> rows) {
        List<ItemRef> registered = new ArrayList<>(rows.size());
        for (var row : rows) {
            Long parent = repository.itemId();
            if (kind.equals(ScmItemEvents.PULL_REQUEST_REVIEW)) {
                long pull = Objects.requireNonNull(row.getParentSourceId());
                var parentItem = writer.upsert(
                        new ItemRegistration(repository.workspaceId(), ScmItemEvents.PULL_REQUEST, pull, parent, null));
                writer.restore(parentItem.workspaceId(), parentItem.itemId());
                parent = parentItem.itemId();
            }
            var item = writer.upsert(
                    new ItemRegistration(repository.workspaceId(), kind, row.getSourceId(), parent, null));
            writer.restore(item.workspaceId(), item.itemId());
            registered.add(item);
        }
        return registered;
    }

    private List<ScmItemQueryRepository.WorkRow> unindexed(ItemRef repository, long repositoryId, ItemKind kind) {
        return queries.unindexedWork(
                repository.workspaceId(),
                repositoryId,
                kind.value(),
                kind.equals(ScmItemEvents.ISSUE) ? "ISSUE" : "PULL_REQUEST");
    }
}
