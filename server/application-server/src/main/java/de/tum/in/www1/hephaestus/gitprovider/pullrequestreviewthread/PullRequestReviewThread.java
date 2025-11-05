package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "pull_request_review_thread")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PullRequestReviewThread extends BaseGitServiceEntity {

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private State state = State.UNRESOLVED;

    private Instant resolvedAt;

    @OneToOne
    @JoinColumn(name = "root_comment_id")
    @ToString.Exclude
    private PullRequestReviewComment rootComment;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pull_request_id")
    @ToString.Exclude
    private PullRequest pullRequest;

    @OneToMany(
        mappedBy = "thread",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.EAGER
    )
    @ToString.Exclude
    private Set<PullRequestReviewComment> comments = new HashSet<>();

    public enum State {
        RESOLVED,
        UNRESOLVED,
    }

    /*
     * Supported webhook fields/relationships (GHEventPayload.PullRequestReviewThread, REST `pull_request_review_thread`):
     * Fields:
     * - thread.id → primary key (derived from root comment id)
     * - thread.created_at / updated_at → aggregated from contained comments (`createdAt` / `updatedAt`)
     * - payload.action `resolved` / `unresolved` → `state`
     * - latest comment timestamp when resolved → `resolvedAt`
     * Relationships:
     * - pull_request → `pullRequest`
     * - thread.comments[*] → `comments` (persisted via comment sync service)
     * - first comment (`in_reply_to_id` == 0) → `rootComment`
     *
     * Ignored although accessible without extra API calls:
     * Fields:
     * - thread.node_id (present in webhook JSON)
     * - implicit ordering metadata (comment array order) beyond what we persist in `comments`
     * Relationships:
     * - Embedded user snapshots on each comment (handled in `PullRequestReviewComment`/`User` synchronization)
     *
     * Missing from hub4j 2.0-rc.5 (cannot be read from GHEventPayloadPullRequestReviewThread):
     * Fields:
     * - thread.path, thread.line, thread.start_line, thread.side, thread.start_side (REST review thread payload, GraphQL `PullRequestReviewThread`)
     * - thread.is_outdated, thread.is_collapsed, thread.resolved (REST payload)
     * - thread.viewer capability flags (GraphQL `viewerCanResolve`, `viewerCanUnresolve`, `viewerCanReply`)
     * - thread.diff location metadata (`startDiffSide`, `originalLine`, etc.)
     * - thread.resolved_by (REST) / resolvedBy (GraphQL)
     * Relationships:
     * - GraphQL `PullRequestReviewThread.comments` connection metadata (totalCount, pageInfo)
     * - GraphQL `repository` back-reference and `commit` edges
     *
     * Requires additional GraphQL fetch (deferred by design):
     * - GraphQL `PullRequestReviewThread` query for path/line metadata, `resolvedBy`, `isOutdated`, `viewerCanResolve`, and pagination details.
     * - Raw webhook payload parsing when we need the immediate `thread` snapshot (`resolved`, `is_outdated`, `path`, etc.) without storing it.
     */
}
