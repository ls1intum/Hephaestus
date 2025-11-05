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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "issue_comment")
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
    /*
     * Supported webhook fields/relationships (GHEventPayload.IssueComment, REST `issue_comment`):
     * Fields:
     * - comment.id → primary key (BaseGitServiceEntity)
     * - comment.created_at / updated_at → `createdAt` / `updatedAt`
     * - comment.body → `body`
     * - comment.html_url → `htmlUrl`
     * - comment.author_association → `authorAssociation`
     * Relationships:
     * - comment.user → `author`
     * - issue → `issue` (covers both classic issues and PR-backed issues via `Issue.hasPullRequest`)
     *
     * Ignored although hub4j 2.0-rc.5 exposes them without extra REST calls:
     * Fields:
     * - comment.node_id (GHObject#getNodeId())
     * - comment.url / issue_url / `_links.*`
     * - comment.performed_via_github_app
     * Relationships:
     * - Embedded user profile fields (managed by dedicated user synchronization)
     *
     * Missing from hub4j 2.0-rc.5 (present in GitHub REST/GraphQL payloads):
     * Fields:
     * - comment.body_html / body_text (REST)
     * - GraphQL IssueComment.lastEditedAt, editor, includesCreatedEdit, isMinimized, minimizedReason, viewerDidAuthor, viewerCanUpdate
     * - REST `author_association_humanized`
     * - REST/GraphQL reactions total counts (only accessible via reactions sub-resource)
     * Relationships:
     * - GraphQL `reactionGroups` with viewer context
     * - GraphQL `userContentEdits` history stream
     *
     * Requires additional REST/GraphQL fetch (deferred for webhook-only processing):
     * - `GHIssueComment#listReactions()` / `GET .../reactions` for reaction details or totals
     * - `GET /repos/{owner}/{repo}/issues/comments/{comment_id}` for app installation metadata and edit history when needed.
     */
}
