package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "pull_request_review")
@Getter
@Setter
@NoArgsConstructor
public class PullRequestReview {

    @Id
    protected Long id;

    // Note: This entity does not have a createdAt and updatedAt field

    @Lob
    private String body;

    // We handle dismissed in a separate field to keep the original state
    @NonNull
    @Enumerated(EnumType.STRING)
    private PullRequestReview.State state;

    private boolean isDismissed;

    @NonNull
    private String htmlUrl;

    @NonNull
    private Instant submittedAt;

    private String commitId;

    @ManyToOne
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToOne
    @JoinColumn(name = "pull_request_id")
    @ToString.Exclude
    private PullRequest pullRequest;

    @OneToMany(mappedBy = "review", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<PullRequestReviewComment> comments = new HashSet<>();

    public enum State {
        COMMENTED,
        APPROVED,
        CHANGES_REQUESTED,
        UNKNOWN,
    }
    /*
     * Supported webhook fields/relationships (GHEventPayload.PullRequestReview, REST `pull_request_review` payload):
     * Fields:
     * - review.id → entity primary key
     * - review.body → `body`
     * - review.state → `state`
     * - review.state == "dismissed" → `isDismissed`
     * - review.html_url → `htmlUrl`
     * - review.submitted_at → `submittedAt`
     * - review.commit_id → `commitId`
     * Relationships:
     * - review.user → `author`
     * - pull_request → `pullRequest`
     * - review.comments[*] → persisted indirectly via pull_request_review_comment events (`comments` association)
     *
     * Ignored although hub4j 2.0-rc.5 exposes them without extra REST calls:
     * Fields:
     * - review.node_id (GHObject#getNodeId())
     * - review.pull_request_url / review._links.*
     * Relationships:
     * - Embedded avatar/profile fields on review.user (handled by dedicated user sync)
     *
     * Missing from hub4j 2.0-rc.5 (present in GitHub REST/GraphQL payloads):
     * Fields:
     * - review.body_html / review.body_text (REST)
     * - review.author_association (REST `pull_request_review.author_association`)
     * - review.dismissed_at & review.dismissal_message (REST `dismissed_at`, `dismissal_message`)
     * - GraphQL PullRequestReview.lastEditedAt, editor, bodyVersion, includesCreatedEdit, reactionGroups, viewerDidAuthor
     * Relationships:
     * - review.dismissed_by (REST `dismissed_by`, GraphQL `dismissedBy`)
     * - review.commit (GraphQL `commit` connection)
     *
     * Requires additional REST/GraphQL fetch (explicitly out of scope here):
     * - Calling `GHPullRequestReview#listReviewComments()` / `GET /repos/{owner}/{repo}/pulls/{pull_number}/reviews/{review_id}/comments`
     *   to hydrate inline review comments (handled by separate webhook events).
     * - `GET /repos/{owner}/{repo}/pulls/{pull_number}/reviews/{review_id}` to retrieve dismissal metadata (`dismissed_by`, `dismissed_at`).
     */
}
