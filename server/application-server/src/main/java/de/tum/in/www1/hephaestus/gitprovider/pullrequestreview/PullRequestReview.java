package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.lang.NonNull;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.user.User;

@Entity
@Table(name = "pull_request_review")
@Getter
@Setter
@NoArgsConstructor
public class PullRequestReview {

    @Id
    protected Long id;
   
    // Note: This entity does not have a createdAt and updatedAt field

    @NonNull
    private String body;
    
    @Enumerated(EnumType.STRING)
    private PullRequestReview.State state;

    @NonNull
    private String htmlUrl;

    @NonNull
    private OffsetDateTime submittedAt;

    private String commitId;

    @ManyToOne
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToOne
    @JoinColumn(name = "pull_request_id")
    @ToString.Exclude
    private PullRequest pullRequest;
    
    @OneToMany(mappedBy = "review")
    @ToString.Exclude
    private Set<PullRequestReviewComment> comments = new HashSet<>();

    public enum State {
        COMMENTED, APPROVED, CHANGES_REQUESTED, DISMISSED
    }

    // Ignored GitHub properties:
    // - author_association (not provided by our GitHub API client)
}
