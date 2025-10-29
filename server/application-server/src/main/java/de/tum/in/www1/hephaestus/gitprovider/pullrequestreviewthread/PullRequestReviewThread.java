package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
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
    @Column(nullable = false, length = 20)
    private PullRequestReviewThread.State state;

    private Instant resolvedAt;

    @OneToOne
    @MapsId
    @JoinColumn(name = "root_comment_id")
    @ToString.Exclude
    private PullRequestReviewComment rootComment;

    @ManyToOne
    @JoinColumn(name = "pull_request_id", nullable = false)
    @ToString.Exclude
    private PullRequest pullRequest;

    @ManyToOne
    @JoinColumn(name = "last_actor_id")
    @ToString.Exclude
    private User lastActor;

    @OneToMany(mappedBy = "thread")
    @ToString.Exclude
    private Set<PullRequestReviewComment> comments = new HashSet<>();

    public enum State {
        RESOLVED,
        UNRESOLVED,
    }
}
