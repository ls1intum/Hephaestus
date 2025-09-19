package de.tum.in.www1.hephaestus.workspace;

import jakarta.persistence.Entity;
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

    private Instant repositorySyncedAt;
    private Instant labelsSyncedAt;
    private Instant milestonesSyncedAt;

    // The time up to which issues and pull requests have been synced in the recent sync
    private Instant issuesAndPullRequestsSyncedAt;

    @ManyToOne
    @JoinColumn(name = "workspace_id")
    @ToString.Exclude
    private Workspace workspace;
}
