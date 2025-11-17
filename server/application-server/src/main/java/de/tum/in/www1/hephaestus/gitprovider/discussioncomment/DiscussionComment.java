package de.tum.in.www1.hephaestus.gitprovider.discussioncomment;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(
    name = "discussion_comment",
    indexes = {
        @Index(name = "idx_discussion_comment_author", columnList = "author_id"),
        @Index(name = "idx_discussion_comment_discussion_created", columnList = "discussion_id, created_at"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class DiscussionComment extends BaseGitServiceEntity {

    @Column(columnDefinition = "TEXT", nullable = false)
    @NonNull
    private String body;

    @Column(length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    @NonNull
    private AuthorAssociation authorAssociation;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id", insertable = false, updatable = false)
    @ToString.Exclude
    private DiscussionComment parentComment;

    // Timestamp of the last webhook/sync touching this comment.
    private Instant lastSyncAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discussion_id")
    @ToString.Exclude
    private Discussion discussion;
    /*
     * Supported via webhook payload (GHEventPayload.DiscussionComment -> GHRepositoryDiscussionComment):
     * Fields:
     * - comment.id/created_at/updated_at -> BaseGitServiceEntity fields.
     * - comment.body -> `body`.
     * - comment.author_association -> `authorAssociation`.
     * - comment.parent_id -> `parentCommentId`.
     * - HTML anchors are derived (discussion + id) instead of being stored verbatim to avoid redundant REST URLs.
     * - `lastSyncAt` reflects when the comment was last observed (for idempotency + backfill heuristics).
     * Relationships:
     * - comment.user -> `author`.
     * - enclosing discussion (payload.discussion) -> `discussion`.
     *
     * Ignored although hub4j exposes them without extra fetch:
     * - comment.html/body node ids, REST `_links.*`, `performed_via_github_app` (not persisted anywhere else either).
     *
     * Missing in hub4j/github-api 2.0-rc.5:
     * - REST payload `is_answer`, `reactions`, `body_html`, `body_text`, `author_association_humanized`.
     * - GraphQL fields (`lastEditedAt`, `editor`, `isMinimized`, `minimizedReason`, `reactionGroups`, `upvoteCount`, `viewer*` booleans, threaded `replies`).
     *
     * Requires extra fetch today (out-of-scope):
     * - `GET /repos/{owner}/{repo}/discussions/comments/{id}` or GraphQL `DiscussionComment` queries for reactions/upvotes/edit history.
     */
}
