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
 * Fields:
 * - commit.sha -> primary key.
 * - commit.message/timestamp/distinct -> mapped to message/committedAt/distinct.
 * - push.ref/head/before -> stored as refName/headCommit (compare URLs can be recomputed when needed).
 * - push.pusher plus commit author/committer git identities captured via the author_* and committer_* columns.
 * - GHCommit stats/files populate additions/deletions/totalChanges & GitCommitFileChange rows.
 * - Relationships: push.repository -> repository, fileChanges cascade via GitCommitFileChange.
 *
 * Ignored even though available without extra fetch:
 * - push.before, created/deleted/forced booleans (derivable from commit graph, not persisted yet).
 * - commit.parent SHA list ({@code GHCommit#getParents()}).
 * - head_commit.tree_id (not required for ETL consumers today).
 *
 * Desired but missing from hub4j/github-api 2.0-rc.5 (needs GraphQL or manual REST parsing):
 * - push.distinct_commits count, repository.full_push payload metadata (deployment contexts, branch protections).
 * - GraphQL Commit.checkSuites, status contexts, signature verification blocks, reaction summaries.
 * - REST push_id / installation delivery id (not surfaced by hub4j {@code GHEventPayload}).
 *
 * Requires additional fetch (out-of-scope for this iteration):
 * - GET /repos/{owner}/{repo}/commits/{sha} to capture files/stats when webhook omitted them (for example >300 files).
 * - Compare API to reconstruct pushes spanning >20 commits when webhook truncates the commits list.
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
    private String message;

    private Instant authoredAt;

    private Instant committedAt;

    @Column(name = "is_distinct")
    private boolean distinct;

    private boolean headCommit;

    @Column(length = 512)
    private String refName;

    private String pusherName;

    @Column(length = 320)
    private String pusherEmail;

    private String authorName;

    @Column(length = 320)
    private String authorEmail;

    @Column(length = 64)
    private String authorLogin;

    private String committerName;

    @Column(length = 320)
    private String committerEmail;

    @Column(length = 64)
    private String committerLogin;

    private Integer additions;

    private Integer deletions;

    private Integer totalChanges;

    private Instant lastSyncedAt;

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
