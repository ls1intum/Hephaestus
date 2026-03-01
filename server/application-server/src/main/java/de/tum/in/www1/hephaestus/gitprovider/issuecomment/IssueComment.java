package de.tum.in.www1.hephaestus.gitprovider.issuecomment;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(
    name = "issue_comment",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_issue_comment_provider_native_id", columnNames = { "provider_id", "native_id" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class IssueComment extends BaseGitServiceEntity {

    @Column(columnDefinition = "TEXT")
    @NonNull
    private String body;

    @NonNull
    private String htmlUrl;

    @NonNull
    @Enumerated(EnumType.STRING)
    private AuthorAssociation authorAssociation;

    @ManyToOne
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToOne
    @JoinColumn(name = "issue_id")
    @ToString.Exclude
    private Issue issue;
}
