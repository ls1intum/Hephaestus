package de.tum.in.www1.hephaestus.gitprovider.commit;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
 * Uses an auto-generated Long ID as primary key. The SHA is stored as a regular
 * column with a unique constraint on (sha, repository_id) since the same commit
 * SHA can appear in multiple repositories (e.g., forks).
 * <p>
 * This is provider-agnostic and does not extend BaseGitServiceEntity since
 * commits don't have provider-specific database IDs.
 */
@Entity
@Table(
    name = "git_commit",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_git_commit_sha_repository",
        columnNames = { "sha", "repository_id" }
    ),
    indexes = {
        @Index(name = "idx_git_commit_repository_id", columnList = "repository_id"),
        @Index(name = "idx_git_commit_author_id", columnList = "author_id"),
        @Index(name = "idx_git_commit_authored_at", columnList = "authored_at"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Commit {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The full 40-character SHA hash of the commit.
     * Unique within a repository (enforced by table constraint on sha + repository_id).
     */
    @Column(length = 40, nullable = false)
    @NonNull
    private String sha;

    /**
     * The commit message (first line / subject).
     */
    @NonNull
    @Column(length = 1024, nullable = false)
    private String message;

    /**
     * The full commit message body (excluding subject line), if available.
     */
    @Column(columnDefinition = "TEXT")
    @ToString.Exclude
    private String messageBody;

    /**
     * The URL to view this commit on the Git provider's web interface.
     */
    @Column(length = 512)
    private String htmlUrl;

    /**
     * The timestamp when the commit was authored.
     * This is different from committedAt - authoredAt is when the original
     * code change was created, committedAt is when it was applied.
     */
    @NonNull
    @Column(nullable = false)
    private Instant authoredAt;

    /**
     * The timestamp when the commit was committed.
     */
    @NonNull
    @Column(nullable = false)
    private Instant committedAt;

    /**
     * Number of additions in this commit.
     */
    private int additions;

    /**
     * Number of deletions in this commit.
     */
    private int deletions;

    /**
     * Number of files changed in this commit.
     */
    private int changedFiles;

    /**
     * The time when this commit was last synced.
     */
    private Instant lastSyncAt;

    /**
     * Audit timestamp: when this entity was first persisted.
     */
    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * Audit timestamp: when this entity was last modified.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ========== Relationships ==========

    /**
     * The repository this commit belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    @ToString.Exclude
    private Repository repository;

    /**
     * The user who authored the commit.
     * May be null if the author is not a GitHub user or cannot be resolved.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    /**
     * The user who committed the commit (may differ from author in cherry-picks, rebases, etc.).
     * May be null if the committer is not a GitHub user or cannot be resolved.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committer_id")
    @ToString.Exclude
    private User committer;

    /**
     * The file changes in this commit.
     */
    @OneToMany(mappedBy = "commit", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<CommitFileChange> fileChanges = new HashSet<>();

    // ========== Helper Methods ==========

    /**
     * Returns the short SHA (first 7 characters) for display purposes.
     */
    public String getShortSha() {
        return sha != null && sha.length() >= 7 ? sha.substring(0, 7) : sha;
    }

    /**
     * Adds a file change to this commit and maintains bidirectional consistency.
     */
    public void addFileChange(CommitFileChange fileChange) {
        if (fileChange != null) {
            this.fileChanges.add(fileChange);
            fileChange.setCommit(this);
        }
    }

    /**
     * Removes a file change from this commit and maintains bidirectional consistency.
     */
    public void removeFileChange(CommitFileChange fileChange) {
        if (fileChange != null) {
            this.fileChanges.remove(fileChange);
            fileChange.setCommit(null);
        }
    }
}
