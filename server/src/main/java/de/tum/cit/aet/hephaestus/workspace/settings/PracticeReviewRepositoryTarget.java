package de.tum.cit.aet.hephaestus.workspace.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "practice_review_repository_target",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_practice_review_repository_target",
            columnNames = { "workspace_id", "repository_monitor_id" }
        ),
        @UniqueConstraint(
            name = "uk_practice_review_repository_target_workspace_id",
            columnNames = { "workspace_id", "id" }
        ),
    },
    indexes = @Index(name = "idx_practice_review_repository_target_workspace", columnList = "workspace_id")
)
@Getter
@Setter
@NoArgsConstructor
public class PracticeReviewRepositoryTarget {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "repository_monitor_id", nullable = false)
    private Long repositoryMonitorId;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
