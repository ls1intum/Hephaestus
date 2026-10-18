package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Finds a workspace's canonical item for a mirrored pull or merge request. */
@Component
@RequiredArgsConstructor
public class ScmPullRequestItems {
    private final ItemIndexService items;

    public Optional<ItemRef> find(long workspaceId, long pullRequestId) {
        return items.findSource(workspaceId, ScmItemEvents.PULL_REQUEST.value(), pullRequestId);
    }
}
