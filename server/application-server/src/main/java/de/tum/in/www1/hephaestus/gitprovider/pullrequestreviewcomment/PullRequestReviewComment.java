package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "pull_request_review_comment")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PullRequestReviewComment extends BaseGitServiceEntity {

    // The diff of the line that the comment refers to.
    @Lob
    @NonNull
    private String diffHunk;

    // The relative path of the file to which the comment applies.
    @NonNull
    private String path;

    // The SHA of the commit to which the comment applies.
    @NonNull
    private String commitId;

    // The SHA of the original commit to which the comment applies.
    @NonNull
    private String originalCommitId;

    @Lob
    @NonNull
    private String body;

    @NonNull
    private String htmlUrl;

    @NonNull
    @Enumerated(EnumType.STRING)
    private AuthorAssociation authorAssociation;

    // The first line of the range for a multi-line comment.
    private Integer startLine;

    // The first line of the range for a multi-line comment.
    private Integer originalStartLine;

    // The side of the first line of the range for a multi-line comment.
    @Enumerated(EnumType.STRING)
    private PullRequestReviewComment.Side startSide;

    // The line of the blob to which the comment applies. The last line of the range for a multi-line comment
    private int line;

    // The line of the blob to which the comment applies. The last line of the range for a multi-line comment
    private int originalLine;

    // The side of the diff to which the comment applies. The side of the last line of the range for a multi-line comment
    @NonNull
    @Enumerated(EnumType.STRING)
    private PullRequestReviewComment.Side side;

    // The line index in the diff to which the comment applies. This field is deprecated; use `line` instead.
    private int position;

    // The index of the original line in the diff to which the comment applies. This field is deprecated; use `original_line` instead.
    private int originalPosition;

    @ManyToOne
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToOne
    @JoinColumn(name = "review_id")
    @ToString.Exclude
    private PullRequestReview review;

    @ManyToOne
    @JoinColumn(name = "pull_request_id")
    @ToString.Exclude
    private PullRequest pullRequest;

    @ManyToOne
    @JoinColumn(name = "thread_id", nullable = false)
    @ToString.Exclude
    private PullRequestReviewThread thread;

    @ManyToOne
    @JoinColumn(name = "in_reply_to_id")
    @ToString.Exclude
    private PullRequestReviewComment inReplyTo;

    @OneToMany(mappedBy = "inReplyTo")
    @ToString.Exclude
    private Set<PullRequestReviewComment> replies = new HashSet<>();

    public enum Side {
        LEFT,
        RIGHT,
        UNKNOWN,
    }
    // Ignored GitHub properties:
    // - subject_type (FILE, LINE is not supported by our API client)
}
