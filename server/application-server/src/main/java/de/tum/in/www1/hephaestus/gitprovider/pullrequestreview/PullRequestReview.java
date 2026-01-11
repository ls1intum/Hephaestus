package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

    @Column(columnDefinition = "TEXT")
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

    /**
     * Review state. Maps directly to GitHub GraphQL PullRequestReviewState enum.
     * <p>
     * Note: The {@code isDismissed} field provides additional context for dismissed
     * reviews while preserving the original review state (e.g., an APPROVED review
     * that was later dismissed).
     *
     * @see <a href="https://docs.github.com/en/graphql/reference/enums#pullrequestreviewstate">GitHub PullRequestReviewState</a>
     */
    public enum State {
        /** Review with comments only, no approval/rejection. */
        COMMENTED,
        /** Review approving the changes. */
        APPROVED,
        /** Review requesting changes before merge. */
        CHANGES_REQUESTED,
        /** Review is pending and not yet submitted (only visible to the author). */
        PENDING,
        /** Review was dismissed by a maintainer. */
        DISMISSED,
        /** Unknown or unmapped state (fallback for forward compatibility). */
        UNKNOWN,
    }
}
