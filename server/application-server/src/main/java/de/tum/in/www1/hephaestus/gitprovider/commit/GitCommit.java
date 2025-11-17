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
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Supported via webhook push payloads (GHEventPayload.Push) and REST {@code GHCommit} fetches.
 * Data sources:
 * - Webhooks persist message/timestamps/distinct flags plus push pusher + git identities.
 * - REST sync augments additions/deletions/totalChanges and enriches {@link GitCommitFileChange} rows with stats/patches.
 * - Relationships: push.repository -> repository, fileChanges cascade via GitCommitFileChange.
 *
 * Still intentionally omitted:
 * - Commit parent SHA list ({@code GHCommit#getParents()}) and head tree IDs (no downstream consumer today).
 * - Push delivery IDs (not surfaced via hub4j yet) and status/signature metadata (requires GraphQL batching).
 * - Compare API hydration for >20 commit pushes (tracked separately via backfill jobs).
 */
@Entity
@Table(name = "git_commit")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class GitCommit {

    @Id
    @Column(length = 40)
    private String sha;

    @Column(columnDefinition = "TEXT")
    @ToString.Exclude
    private String message;

    private Instant authoredAt;

    private Instant committedAt;

    @Column(name = "is_distinct", nullable = false)
    private boolean distinct;

    @Column(length = 512)
    private String refName;

    @Column(length = 255)
    private String pusherName;

    @Column(length = 320)
    private String pusherEmail;

    @Column(length = 255)
    private String authorName;

    @Column(length = 320)
    private String authorEmail;

    @Column(length = 64)
    private String authorLogin;

    @Column(length = 255)
    private String committerName;

    @Column(length = 320)
    private String committerEmail;

    @Column(length = 64)
    private String committerLogin;

    private Integer additions;

    private Integer deletions;

    private Integer totalChanges;

    @Column(columnDefinition = "TEXT")
    private String commitUrl;

    @Column(columnDefinition = "TEXT")
    private String compareUrl;

    @Column(name = "before_sha", length = 40)
    private String beforeSha;

    @Column(name = "merge_commit", nullable = false)
    private boolean mergeCommit = false;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    @ToString.Exclude
    private Repository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committer_id")
    @ToString.Exclude
    private User committer;

    @OneToMany(mappedBy = "commit", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<GitCommitFileChange> fileChanges = new HashSet<>();

    public void replaceFileChanges(Set<GitCommitFileChange> newChanges) {
        fileChanges.clear();
        newChanges.forEach(change -> change.setCommit(this));
        fileChanges.addAll(newChanges);
    }
}
