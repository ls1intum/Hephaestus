package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultStore;
import java.time.Instant;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ScmItemChangeListener {
    private final ScmItemSourceQuery items;
    private final ResultStore results;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onUpdated(ScmDomainEvent.IssueUpdated event) {
        if (!event.issue().isPullRequest()
                && !Collections.disjoint(event.changedFields(), ScmItemEvents.REVIEWABLE_ISSUE_FIELDS))
            advance(event.issue().id());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onClosed(ScmDomainEvent.IssueClosed event) {
        if (!event.issue().isPullRequest()) advance(event.issue().id());
    }

    private void advance(long sourceId) {
        Instant at = Instant.now();
        for (var item : items.issueItems(ScmItemEvents.ISSUE.value(), sourceId))
            results.endCurrent(item.getWorkspaceId(), item.getItemId(), ResultEndReason.WORK_CHANGED, at);
    }
}
