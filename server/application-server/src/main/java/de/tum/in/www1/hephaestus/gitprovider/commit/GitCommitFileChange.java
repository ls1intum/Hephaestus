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

    @Column(length = 16)
    @Enumerated(EnumType.STRING)
    private ChangeType changeType;

    @Column(length = 4096)
    private String path;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_sha", nullable = false)
    @ToString.Exclude
    private GitCommit commit;

    public enum ChangeType {
        ADDED,
        MODIFIED,
        REMOVED,
    }
    /*
     * Stored per-commit file deltas sourced from push payload arrays and GHCommit file stats.
     * Additional metadata like additions/deletions per file is available via GHCommit.File but left for future ETL cycles.
     */
}
