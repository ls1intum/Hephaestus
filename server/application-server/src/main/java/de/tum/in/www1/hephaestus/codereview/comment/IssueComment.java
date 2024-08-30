package de.tum.in.www1.hephaestus.codereview.comment;

import jakarta.persistence.Table;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.codereview.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "issue_comment")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class IssueComment extends BaseGitServiceEntity {
    @NonNull
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pullrequest_id", referencedColumnName = "id")
    @ToString.Exclude
    private PullRequest pullRequest;

    public IssueComment(Long id, String body, String createdAt, String updatedAt, User author,
            PullRequest pullRequest) {
        super(id, createdAt, updatedAt);
        this.body = body;
        this.author = author;
        this.pullRequest = pullRequest;
    }

    public IssueComment(Long id, String body, String createdAt, String updatedAt) {
        this(id, body, createdAt, updatedAt, null, null);
    }
}
