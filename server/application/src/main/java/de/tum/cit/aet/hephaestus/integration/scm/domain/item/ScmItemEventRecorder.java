package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.metrics.ReviewRefusalRecorder;
import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmEventPayload;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnServerRole
@WorkspaceAgnostic("Fans out one exact provider mirror event to explicitly monitored workspace items")
@RequiredArgsConstructor
public class ScmItemEventRecorder {
    private final ItemIndexService writer;
    private final ScmItemService items;
    private final ScmRepositoryAccess access;
    private final RepositoryRepository repositories;
    private final ScmItemSourceQuery queries;
    private final MeterRegistry meters;
    private final IssueRepository issues;
    private final IssueEvidenceVersion issueVersions;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPullRequest(ScmDomainEvent.PullRequestEvent event) {
        ItemEventName name =
                switch (event) {
                    case ScmDomainEvent.PullRequestCreated ignored -> ScmItemEvents.PULL_REQUEST_OPENED;
                    case ScmDomainEvent.PullRequestReady ignored -> ScmItemEvents.PULL_REQUEST_READY;
                    case ScmDomainEvent.PullRequestSynchronized ignored -> ScmItemEvents.PULL_REQUEST_SYNCHRONIZED;
                    case ScmDomainEvent.PullRequestMerged ignored -> ScmItemEvents.PULL_REQUEST_MERGED;
                    case ScmDomainEvent.PullRequestClosed closed ->
                        closed.wasMerged() ? null : ScmItemEvents.PULL_REQUEST_CLOSED;
                    case ScmDomainEvent.PullRequestUpdated updated ->
                        Collections.disjoint(updated.changedFields(), ScmItemEvents.LINKED_PULL_REQUEST_MATERIAL_FIELDS)
                                ? null
                                : ScmItemEvents.PULL_REQUEST_EDITED;
                    default -> null;
                };
        if (name != null)
            record(
                    event.pullRequest().repository().id(),
                    event.pullRequest().id(),
                    ScmItemEvents.PULL_REQUEST,
                    null,
                    name,
                    event.context());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onIssue(ScmDomainEvent.IssueEvent event) {
        if (event instanceof ScmDomainEvent.IssueDeleted) return;
        var issue = Objects.requireNonNull(event.issue());
        if (issue.isPullRequest()) return;
        ItemEventName name =
                switch (event) {
                    case ScmDomainEvent.IssueCreated ignored -> ScmItemEvents.ISSUE_OPENED;
                    case ScmDomainEvent.IssueClosed ignored -> ScmItemEvents.ISSUE_CLOSED;
                    case ScmDomainEvent.IssueUpdated updated ->
                        Collections.disjoint(updated.changedFields(), ScmItemEvents.REVIEWABLE_ISSUE_FIELDS)
                                ? null
                                : ScmItemEvents.ISSUE_UPDATED;
                    case ScmDomainEvent.IssueReopened ignored -> ScmItemEvents.ISSUE_UPDATED;
                    default -> null;
                };
        if (name != null) record(issue.repository().id(), issue.id(), ScmItemEvents.ISSUE, null, name, event.context());
        if (event instanceof ScmDomainEvent.IssueUpdated updated
                && !Collections.disjoint(updated.changedFields(), ScmItemEvents.LINKED_ISSUE_MATERIAL_FIELDS)) {
            var parents = queries.closingPullRequests(issue.id());
            for (var parent : parents)
                record(
                        parent.getRepositoryId(),
                        parent.getSourceId(),
                        ScmItemEvents.PULL_REQUEST,
                        null,
                        ScmItemEvents.PULL_REQUEST_LINKED_ISSUE_UPDATED,
                        event.context());
        }
    }

    // The publisher still holds the mirror until synchronous consumers capture its final version.
    @EventListener
    @Transactional
    public void onDeleted(ScmDomainEvent.IssueDeleted event) {
        var issue = issues.findByIdWithRepository(event.issueId()).orElseThrow();
        var version = issueVersions.of(ScmEventPayload.IssueData.from(issue));
        for (var item : queries.deletedItems(ScmItemEvents.ISSUE.value(), event.issueId())) {
            if (writer.remove(
                    item.getWorkspaceId(), item.getItemId(), event.context().occurredAt())) {
                writer.record(new ItemEventInput(
                        item.getWorkspaceId(),
                        item.getItemId(),
                        ScmItemEvents.ISSUE_DELETED,
                        version.value(),
                        event.context().occurredAt(),
                        event.context().isWebhook() ? FoundBy.WEBHOOK : FoundBy.SYNC,
                        event.context().actorUserId(),
                        null));
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onReview(ScmDomainEvent.ReviewSubmitted event) {
        var review = event.review();
        if (review.repositoryId() != null)
            record(
                    review.repositoryId(),
                    review.id(),
                    ScmItemEvents.PULL_REQUEST_REVIEW,
                    review.pullRequestId(),
                    ScmItemEvents.PULL_REQUEST_REVIEW_SUBMITTED,
                    event.context());
    }

    private void record(
            long repositoryId,
            long sourceId,
            ItemKind kind,
            @Nullable Long parentSourceId,
            ItemEventName name,
            EventContext context) {
        var repository = repositories.findById(repositoryId).orElseThrow();
        List<Long> workspaces = queries.monitoredWorkspaces(repository.getNameWithOwner());
        for (long workspace : workspaces) {
            try {
                access.authorize(workspace, repository);
            } catch (ItemCaptureException denied) {
                if (denied.code() == ItemCaptureCode.ITEM_NOT_VISIBLE) continue;
                throw denied;
            }
            ItemRef holder =
                    writer.upsert(new ItemRegistration(workspace, ScmItemEvents.REPOSITORY, repositoryId, null, null));
            writer.restore(workspace, holder.itemId());
            Long parent = holder.itemId();
            if (parentSourceId != null)
                parent = writer.upsert(new ItemRegistration(
                                workspace, ScmItemEvents.PULL_REQUEST, parentSourceId, holder.itemId(), null))
                        .itemId();
            ItemRef item = writer.upsert(new ItemRegistration(workspace, kind, sourceId, parent, null));
            writer.restore(workspace, item.itemId());
            ItemVersion version;
            try {
                version = items.pin(item, null).version();
            } catch (ItemCaptureException unavailable) {
                if (unavailable.code() != ItemCaptureCode.VERSION_UNAVAILABLE
                        && unavailable.code() != ItemCaptureCode.SOURCE_NOT_READY) throw unavailable;
                // A partial provider read can update the mirror without proving an event version.
                ReviewRefusalRecorder.record(
                        meters, "submission", unavailable.code().name());
                continue;
            }
            writer.record(new ItemEventInput(
                    workspace,
                    item.itemId(),
                    name,
                    version.value(),
                    context.occurredAt(),
                    context.isWebhook() ? FoundBy.WEBHOOK : FoundBy.SYNC,
                    context.actorUserId(),
                    null));
        }
    }
}
