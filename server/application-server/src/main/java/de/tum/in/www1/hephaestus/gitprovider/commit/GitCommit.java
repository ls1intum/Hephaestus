package de.tum.in.www1.hephaestus.gitprovider.commit;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
 * Entity representing a Git commit.
 * <p>
 * This entity is provider-agnostic and can represent commits from
 * GitHub, GitLab, or other Git providers. The webhook payload structure
 * for push events is nearly identical across providers.
 * <p>
 * Note: Unlike other entities that use database ID as primary key,
 * commits use the SHA hash as the natural primary key since it's
 * globally unique and immutable.
 */
@Entity
@Table(name = "git_commit", uniqueConstraints = @UniqueConstraint(columnNames = { "repository_id", "sha" }))
@Getter
@Setter
@NoArgsConstructor
@ToString
public class GitCommit {

    /**
     * The full 40-character SHA hash of the commit.
     * This is the primary key since it's globally unique.
     */
    @Id
    @Column(length = 40)
    private String sha;

    /**
     * Abbreviated SHA (typically 7 characters) for display purposes.
     */
    @Column(length = 12)
    private String abbreviatedSha;

    /**
     * The full commit message including body.
     */
    @Column(columnDefinition = "TEXT")
    @ToString.Exclude
    private String message;

    /**
     * The first line of the commit message (subject/headline).
     */
    @Column(length = 512)
    private String messageHeadline;

    /**
     * URL to view the commit on the git provider's web interface.
     */
    private String htmlUrl;

    /**
     * Timestamp when the commit was authored (from git metadata).
     */
    private Instant authoredAt;

    /**
     * Timestamp when the commit was made (from git metadata).
     */
    private Instant committedAt;

    /**
     * Timestamp when the commit was pushed to the repository.
     * This is set from webhook data and may differ from committedAt.
     */
    private Instant pushedAt;

    // ========== Git Author/Committer Metadata ==========
    // These fields store the raw git author/committer info which may
    // not correspond to a User entity (e.g., if using a different email)

    @Column(length = 256)
    private String authorName;

    @Column(length = 256)
    private String authorEmail;

    @Column(length = 256)
    private String committerName;

    @Column(length = 256)
    private String committerEmail;

    // ========== Statistics ==========

    /**
     * Number of lines added in this commit.
     */
    private Integer additions;

    /**
     * Number of lines deleted in this commit.
     */
    private Integer deletions;

    /**
     * Number of files changed in this commit.
     */
    private Integer changedFiles;

    // ========== Metadata ==========

    /**
     * The branch or tag reference this commit was pushed to.
     * Example: "refs/heads/main" or "refs/tags/v1.0.0"
     */
    @Column(length = 256)
    private String refName;

    /**
     * Whether this commit is a merge commit (has multiple parents).
     */
    private boolean isMergeCommit;

    /**
     * Whether this commit was marked as distinct in a push event.
     * A distinct commit is new to the branch in this push.
     */
    private boolean isDistinct;

    /**
     * The last time this commit was synced.
     */
    private Instant lastSyncAt;

    /**
     * Timestamp when this entity was created.
     */
    @NonNull
    private Instant createdAt;

    /**
     * Timestamp when this entity was last updated.
     */
    @NonNull
    private Instant updatedAt;

    // ========== Relationships ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    @ToString.Exclude
    private Repository repository;

    /**
     * The linked Hephaestus User for the commit author.
     * May be null if the git author email doesn't match a known user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    /**
     * The linked Hephaestus User for the committer.
     * May be null if the git committer email doesn't match a known user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committer_id")
    @ToString.Exclude
    private User committer;

    /**
     * Files changed in this commit.
     */
    @OneToMany(mappedBy = "commit", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<GitCommitFileChange> fileChanges = new HashSet<>();

    // ========== Helper Methods ==========

    /**
     * Check if statistics (additions/deletions) are available.
     * Statistics may not be available immediately from push webhooks.
     */
    public boolean hasStatistics() {
        return additions != null && deletions != null;
    }

    /**
     * Get the total lines changed (additions + deletions).
     */
    public int getTotalChanges() {
        int add = additions != null ? additions : 0;
        int del = deletions != null ? deletions : 0;
        return add + del;
    }
}
