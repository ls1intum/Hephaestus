package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmDomainEvent;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Provider mirror events become immutable events for each authorized workspace item. */
@Component
@ConditionalOnServerRole
@WorkspaceAgnostic("Fans out one exact provider mirror event to explicitly monitored workspace items")
@RequiredArgsConstructor
public class ScmItemEventRecorder {
    private final ItemWriter writer;
    private final ScmItemService items;
    private final ScmRepositoryAccess access;
    private final RepositoryRepository repositories;
    private final JdbcTemplate jdbc;

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
        if (event instanceof ScmDomainEvent.IssueDeleted deleted) {
            jdbc.query(
                            "SELECT workspace_id,id FROM item WHERE kind='scm.issue' AND" + " source_id=?",
                            (rs, row) -> new long[] {rs.getLong(1), rs.getLong(2)},
                            deleted.issueId())
                    .forEach(
                            ids -> writer.remove(ids[0], ids[1], event.context().occurredAt()));
            return;
        }
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
            var parents = jdbc.query(
                    "SELECT p.id,p.repository_id FROM pull_request_closing_issue link JOIN"
                            + " issue p ON p.id=link.pull_request_id WHERE link.issue_id=? AND"
                            + " p.deleted_at IS NULL",
                    (rs, row) -> new long[] {rs.getLong(1), rs.getLong(2)},
                    issue.id());
            for (long[] parent : parents)
                record(
                        parent[1],
                        parent[0],
                        ScmItemEvents.PULL_REQUEST,
                        null,
                        ScmItemEvents.PULL_REQUEST_LINKED_ISSUE_UPDATED,
                        event.context());
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
        List<Long> workspaces = jdbc.query(
                "SELECT workspace_id FROM repository_to_monitor WHERE name_with_owner=?" + " ORDER BY workspace_id",
                (rs, rowNum) -> rs.getLong("workspace_id"),
                repository.getNameWithOwner());
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
            ItemVersion version = items.pin(item, null).version();
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
