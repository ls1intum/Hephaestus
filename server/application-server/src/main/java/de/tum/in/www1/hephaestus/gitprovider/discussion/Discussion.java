package de.tum.in.www1.hephaestus.gitprovider.discussion;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

/**
 * Entity representing a GitHub Discussion.
 * <p>
 * Discussions are a forum-like feature in GitHub repositories
 * that support categories, comments, and optional Q&amp;A format
 * where comments can be marked as accepted answers.
 */
@Entity
@Table(name = "discussion", uniqueConstraints = @UniqueConstraint(columnNames = { "repository_id", "number" }))
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Discussion extends BaseGitServiceEntity {

    private int number;

    @NonNull
    @Column(length = 1024)
    private String title;

    @Column(columnDefinition = "TEXT")
    @ToString.Exclude
    private String body;

    @NonNull
    @Column(length = 512)
    private String htmlUrl;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private State state;

    /**
     * The reason the discussion was closed, if applicable.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private StateReason stateReason;

    @Column(name = "is_locked")
    private boolean locked;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private LockReason activeLockReason;

    private Instant closedAt;

    /**
     * The time when a user chose this discussion's answer.
     */
    private Instant answerChosenAt;

    /**
     * Denormalized comment count for efficient querying.
     */
    @Column(name = "comment_count")
    private int commentsCount;

    /**
     * The last time the discussion and its comments were synced.
     */
    private Instant lastSyncAt;

    // ========== Relationships ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    @ToString.Exclude
    private Repository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @ToString.Exclude
    private DiscussionCategory category;

    /**
     * The user who chose the accepted answer, if any.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_chosen_by_id")
    @ToString.Exclude
    private User answerChosenBy;

    /**
     * The comment marked as the accepted answer, if any.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_comment_id")
    @ToString.Exclude
    private DiscussionComment answerComment;

    @OneToMany(mappedBy = "discussion", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<DiscussionComment> comments = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "discussion_label",
        joinColumns = @JoinColumn(name = "discussion_id"),
        inverseJoinColumns = @JoinColumn(name = "label_id")
    )
    @ToString.Exclude
    private Set<Label> labels = new HashSet<>();

    // ========== Enums ==========

    /**
     * Discussion state - derived from whether the discussion is closed.
     */
    public enum State {
        OPEN,
        CLOSED,
    }

    /**
     * The reason for closing the discussion.
     * Maps to GitHub's DiscussionCloseReason enum.
     */
    public enum StateReason {
        /** The discussion has been resolved. */
        RESOLVED,
        /** The discussion is no longer relevant. */
        OUTDATED,
        /** The discussion is a duplicate. */
        DUPLICATE,
        /** Unknown or unspecified reason. */
        UNKNOWN,
    }

    /**
     * The reason for locking the discussion.
     * Maps to GitHub's LockReason enum.
     */
    public enum LockReason {
        OFF_TOPIC,
        RESOLVED,
        SPAM,
        TOO_HEATED,
    }

    /**
     * Check if this discussion has an accepted answer.
     */
    public boolean isAnswered() {
        return answerComment != null;
    }
}
