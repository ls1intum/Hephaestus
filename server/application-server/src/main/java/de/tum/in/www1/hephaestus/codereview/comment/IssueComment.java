package de.tum.in.www1.hephaestus.codereview.comment;

import jakarta.persistence.Table;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

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
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
    @JsonIgnore
    @ToString.Exclude
    private PullRequest pullrequest;

    public IssueComment(String body, String createdAt, String updatedAt) {
        this.body = body;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
