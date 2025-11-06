package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "repository_to_monitor")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class RepositoryToMonitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private String nameWithOwner;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private Source source = Source.PAT;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", foreignKey = @ForeignKey(name = "fk_repository_to_monitor_repository"))
    @ToString.Exclude
    private Repository repository;

    @Column(name = "installation_id")
    private Long installationId;

    private Instant linkedAt;

    private Instant unlinkedAt;

    private Instant repositorySyncedAt;
    private Instant labelsSyncedAt;
    private Instant milestonesSyncedAt;

    // The time up to which issues and pull requests have been synced in the recent sync
    private Instant issuesAndPullRequestsSyncedAt;

    @ManyToOne
    @JoinColumn(name = "workspace_id")
    @ToString.Exclude
    private Workspace workspace;

    public enum Source {
        PAT,
        INSTALLATION,
    }
}
