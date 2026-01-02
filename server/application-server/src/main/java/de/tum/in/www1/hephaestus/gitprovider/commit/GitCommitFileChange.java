package de.tum.in.www1.hephaestus.gitprovider.commit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

/**
 * Entity representing a file changed in a Git commit.
 * <p>
 * This tracks individual file changes within a commit, including
 * the type of change (added, modified, deleted, renamed) and
 * optionally the line-level statistics.
 */
@Entity
@Table(name = "git_commit_file_change")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class GitCommitFileChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The type of change made to this file.
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ChangeType changeType;

    /**
     * The file path (after the change, for renames).
     */
    @NonNull
    @Column(length = 1024)
    private String path;

    /**
     * The previous file path (for renames/moves).
     * Null for non-rename changes.
     */
    @Column(length = 1024)
    private String previousPath;

    /**
     * Number of lines added in this file.
     */
    private Integer additions;

    /**
     * Number of lines deleted in this file.
     */
    private Integer deletions;

    /**
     * Whether this is a binary file (diffs not available).
     */
    private boolean isBinary;

    // ========== Relationships ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_sha")
    @ToString.Exclude
    private GitCommit commit;

    // ========== Enums ==========

    /**
     * The type of change made to a file in a commit.
     * These values are common across Git providers.
     */
    public enum ChangeType {
        /** File was added to the repository. */
        ADDED,
        /** File was modified. */
        MODIFIED,
        /** File was deleted from the repository. */
        REMOVED,
        /** File was renamed (and possibly modified). */
        RENAMED,
        /** File was copied (and possibly modified). */
        COPIED,
        /** File mode was changed (e.g., permissions). */
        CHANGED,
        /** Unknown change type. */
        UNKNOWN,
    }

    // ========== Helper Methods ==========

    /**
     * Get the total lines changed in this file.
     */
    public int getTotalChanges() {
        int add = additions != null ? additions : 0;
        int del = deletions != null ? deletions : 0;
        return add + del;
    }

    /**
     * Check if this file was renamed or moved.
     */
    public boolean isRenamed() {
        return changeType == ChangeType.RENAMED || previousPath != null;
    }
}
