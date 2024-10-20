package de.tum.in.www1.hephaestus.gitprovider.issuecomment;

import org.springframework.lang.NonNull;

import jakarta.persistence.Table;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.user.User;

@Entity
@Table(name = "issue_comment")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class IssueComment extends BaseGitServiceEntity {

    @Lob
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

    // Ignored GitHub properties:
    // - performed_via_github_app
    // - reactions
}
