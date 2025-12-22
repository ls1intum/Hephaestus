package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(name = "provider_thread_id")
    private Long providerThreadId;

    @Column(length = 128)
    private String nodeId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private State state = State.UNRESOLVED;

    private Instant resolvedAt;

    @Column(columnDefinition = "TEXT")
    private String path;

    private Integer line;

    private Integer startLine;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PullRequestReviewComment.Side side;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PullRequestReviewComment.Side startSide;

    private Boolean outdated;

    private Boolean collapsed;

    @OneToOne
    @JoinColumn(name = "root_comment_id")
    @ToString.Exclude
    private PullRequestReviewComment rootComment;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pull_request_id")
    @ToString.Exclude
    private PullRequest pullRequest;

    @ManyToOne
    @JoinColumn(name = "resolved_by_id")
    @ToString.Exclude
    private User resolvedBy;

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<PullRequestReviewComment> comments = new HashSet<>();

    public enum State {
        RESOLVED,
        UNRESOLVED,
    }
    /*
     * Supported webhook fields/relationships (GitHub `pull_request_review_thread`,
     * GraphQL `PullRequestReviewThread`):
     * Fields:
     * - thread.id → `providerThreadId` (mirrors provider identifier; column name
     * keeps provider-agnostic wording)
     * - thread.node_id → `nodeId`
     * - thread.created_at / updated_at → aggregated from contained comments
     * (`createdAt` / `updatedAt`)
     * - payload.action `resolved` / `unresolved` → `state`
     * - latest comment timestamp when resolved → `resolvedAt`
     * - thread.path → `path`
     * - thread.line / start_line → `line` / `startLine`
     * - thread.side / start_side → `side` / `startSide`
     * - thread.is_outdated → `outdated` (nullable when provider omits it)
     * - thread.is_collapsed → `collapsed` (nullable when provider omits it)
     * - thread.resolved_by → `resolvedBy` (nullable when provider omits it)
     * Relationships:
     * - pull_request → `pullRequest`
     * - thread.comments[*] → `comments` (persisted via comment sync service)
     * - first comment (`in_reply_to_id` == 0) → `rootComment`
     *
     * Ignored although accessible without extra API calls:
     * - implicit ordering metadata (comment array order) beyond what we persist in
     * `comments`
     * - embedded user snapshots on each comment (handled in
     * `PullRequestReviewComment`/`User` synchronization)
     *
     * Missing from hub4j 2.0-rc.5 (`GHEventPayloadPullRequestReviewThread` does not
     * expose):
     * - viewer capability flags (GraphQL `viewerCanResolve`, `viewerCanUnresolve`,
     * `viewerCanReply`)
     * - diff location metadata (`startDiffSide`, `originalLine`, etc.)
     * - GraphQL connection metadata (`comments.totalCount`, `comments.pageInfo`)
     * - GraphQL `repository` back-reference and `commit` edges
     */
}
