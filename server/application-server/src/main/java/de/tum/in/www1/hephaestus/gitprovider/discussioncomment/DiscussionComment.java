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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

/**
 * Entity representing a comment on a GitHub Discussion.
 * <p>
 * Discussion comments can be top-level or replies to other comments.
 * In Q&amp;A category discussions, a comment can be marked as the accepted answer.
 */
@Entity
@Table(name = "discussion_comment")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class DiscussionComment extends BaseGitServiceEntity {

    @Column(columnDefinition = "TEXT")
    @ToString.Exclude
    private String body;

    @Column(length = 512)
    @NonNull
    private String htmlUrl;

    /**
     * Whether this comment has been marked as the answer for its discussion.
     */
    private boolean isAnswer;

    /**
     * Whether this comment has been minimized (collapsed).
     */
    private boolean isMinimized;

    /**
     * The reason for minimizing this comment, if minimized.
     */
    @Column(length = 64)
    private String minimizedReason;

    /**
     * Author's association with the repository.
     * Maps to GitHub's CommentAuthorAssociation enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AuthorAssociation authorAssociation;

    /**
     * The last time this comment was synced.
     */
    private Instant lastSyncAt;

    // ========== Relationships ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discussion_id")
    @ToString.Exclude
    private Discussion discussion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    /**
     * The parent comment if this is a reply, null for top-level comments.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    @ToString.Exclude
    private DiscussionComment parentComment;

    /**
     * Replies to this comment.
     */
    @OneToMany(mappedBy = "parentComment")
    @ToString.Exclude
    private Set<DiscussionComment> replies = new HashSet<>();

    /**
     * Check if this is a top-level comment (not a reply).
     */
    public boolean isTopLevel() {
        return parentComment == null;
    }
}
