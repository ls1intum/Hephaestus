package de.tum.in.www1.hephaestus.gitprovider.discussion;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(
    name = "discussion",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_discussion_repo_number",
        columnNames = { "repository_id", "number" }
    ),
    indexes = {
        @Index(name = "idx_discussion_repo_last_sync", columnList = "repository_id, last_sync_at"),
        @Index(name = "idx_discussion_answer_author", columnList = "answer_author_id"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Discussion extends BaseGitServiceEntity {

    @Column(nullable = false)
    private int number;

    @NonNull
    @Column(length = 256, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    @ToString.Exclude
    private String body;

    @Column(columnDefinition = "TEXT")
    private String htmlUrl;

    private Integer commentCount;

    @NonNull
    @Column(length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private State state;

    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private AuthorAssociation authorAssociation;

    @Column(nullable = false)
    private boolean locked;

    @Column(length = 64)
    private String activeLockReason;

    private Instant answerChosenAt;

    // Last time we processed a webhook or sync for this discussion (used for comment catch-up scheduling)
    private Instant lastSyncAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @ToString.Exclude
    private DiscussionCategory category;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_comment_id")
    @ToString.Exclude
    private DiscussionComment answerComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    @ToString.Exclude
    private Repository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_author_id")
    @ToString.Exclude
    private User answerChosenBy;

    @OneToMany(mappedBy = "discussion", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<DiscussionComment> comments = new HashSet<>();

    public enum State {
        OPEN,
        LOCKED,
        ANSWERED,
        UNKNOWN,
    }
    /*
     * Supported via webhook payload (GHEventPayload.Discussion, hub4j GHRepositoryDiscussion):
     * Fields:
     * - discussion.id/created_at/updated_at -> BaseGitServiceEntity fields.
     * - discussion.number/title/body/html_url/state/locked/active_lock_reason -> dedicated columns (html_url stays TEXT so we never truncate discussion permalinks).
     * - discussion.answer_* -> answerChosenAt, answerChosenBy, answerComment.
     * - discussion.author_association -> authorAssociation (converted through GitHubAuthorAssociationConverter).
     * - discussion.category.* -> normalized DiscussionCategory rows keyed by GitHub's category id.
     * Relationships:
     * - discussion.user -> author.
     * - discussion.answer_chosen_by -> answerChosenBy.
     * - `answer_html_url` anchors -> answerComment (linked after comment sync, avoiding URL persistence).
     * - webhook.repository -> repository.
     * - DiscussionComment entities map comment collections (one-to-many) without extra fetches.
     *
     * Ignored although surfaced by hub4j without additional calls:
     * - discussion.labels from label/unlabel events (hub4j only exposes the single GHLabel on the payload; we defer until label editing is wired end-to-end).
     * - discussion.reactions URL/counters (requires follow-up REST call to populate meaningful aggregates).
     *
     * Desired but missing in hub4j/github-api 2.0-rc.5 (available in REST/GraphQL payloads):
     * - discussion.state_reason, answer block (REST), `isAnswered`, `upvoteCount`, `reactionGroups`, `viewer*` capability booleans, poll metadata.
     * - HTML/text bodies (bodyHTML/bodyText) and editor/lastEditedAt/userContentEdits metadata.
     *
     * Requires extra fetch (out-of-scope for now):
     * - `GET /repos/{owner}/{repo}/discussions/{number}` for reactions, poll options, watchers.
     * - GraphQL connections (labels, timelineItems, subscribers) to backfill historical analytics or patch dropped webhooks.
     */
}
