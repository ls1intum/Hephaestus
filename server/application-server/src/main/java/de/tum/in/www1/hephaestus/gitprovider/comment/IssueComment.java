package de.tum.in.www1.hephaestus.gitprovider.comment;

import jakarta.persistence.Table;
import de.tum.in.www1.hephaestus.gitprovider.base.Comment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import jakarta.persistence.Entity;
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
public class IssueComment extends Comment {
    @ManyToOne
    @JoinColumn(name = "pullrequest_id", referencedColumnName = "id")
    @ToString.Exclude
    private PullRequest pullRequest;
}
